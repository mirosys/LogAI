package dev.mirosys.logai.watchdog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Der Begleitprozess. Läuft in einer eigenen JVM, damit er jede Art von Absturz überlebt -
 * saubere Exception, Freeze, nativer JVM-Crash oder Abschuss über den Task-Manager.
 *
 * <p>Diese Klasse und alles, was sie anfasst, darf keine Minecraft- oder Fabric-Klassen
 * berühren: der Prozess startet mit dem Mod-Jar als einzigem Classpath.
 *
 * <p>Aufruf: {@code java -cp logai.jar dev.mirosys.logai.watchdog.CrashWatcher <session.properties>}
 */
public final class CrashWatcher {
	/**
	 * Kurze Wartezeit nach dem Prozessende. Beim Absturz schreibt die JVM ihre letzten
	 * Log-Zeilen und die hs_err-Datei teils erst nach dem Verschwinden aus der Prozessliste.
	 */
	private static final long SETTLE_MILLIS = 2000L;

	/** Kuerzer gelaufen heisst: der Neustart wuerde nur eine Absturzschleife eroeffnen. */
	private static final long MIN_UPTIME_FOR_RESTART_MILLIS = 60_000L;

	/** So lange bekommt ein Launcher Zeit, auf den Startlink zu reagieren. */
	private static final long LAUNCHER_GRACE_MILLIS = 20_000L;

	public static void main(String[] args) {
		if (args.length < 1) {
			System.err.println("LogAI watcher: expected the session file as first argument");
			System.exit(2);
		}

		Path sessionFile = Path.of(args[0]);
		WatchSession session;

		try {
			session = WatchSession.read(sessionFile);
		} catch (IOException e) {
			System.err.println("LogAI watcher: cannot read session file: " + e);
			System.exit(2);
			return;
		}

		try {
			awaitGameExit(session.pid);
			long processEndMillis = System.currentTimeMillis();
			Thread.sleep(SETTLE_MILLIS);

			// Der Nutzer kann die Einstellungen im Spiel geändert haben, nachdem der
			// Watcher gestartet ist. Der Mod schreibt sie bei jedem Speichern neu heraus,
			// also gilt hier der Stand von zuletzt und nicht der vom Spielstart.
			session = reread(sessionFile, session);

			ShutdownKind kind = effectiveKind(session);

			if (!triggers(session, kind)) {
				// Diese Art des Beendens hat der Nutzer abgeschaltet.
				cleanUp(sessionFile, session);
				return;
			}

			handleCrash(session, kind, processEndMillis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Exception e) {
			System.err.println("LogAI watcher failed: " + e);
			e.printStackTrace();
		} finally {
			cleanUp(sessionFile, session);
		}

		// Beendet auch den AWT-Thread, der sonst weiterlaufen würde.
		System.exit(0);
	}

	/** Liest die Sitzungsdatei erneut; bleibt beim alten Stand, wenn das nicht klappt. */
	private static WatchSession reread(Path sessionFile, WatchSession fallback) {
		try {
			return WatchSession.read(sessionFile);
		} catch (IOException e) {
			return fallback;
		}
	}

	private static void awaitGameExit(long pid) {
		Optional<ProcessHandle> handle = ProcessHandle.of(pid);

		if (handle.isEmpty()) {
			// Minecraft war schon weg, bevor der Watcher hochkam.
			return;
		}

		// Der Exit-Code eines fremden Prozesses ist über ProcessHandle nicht abrufbar,
		// deshalb entscheidet allein die Marker-Datei über sauber/abgestürzt.
		handle.get().onExit().join();
	}

	/**
	 * Kein Marker heisst: der Prozess hat es nicht mehr geschafft, einen zu schreiben.
	 *
	 * <p>Steht im Log ein Absturzbericht, gilt das mehr als ein Marker, der behauptet,
	 * es sei alles freiwillig gewesen.
	 */
	private static ShutdownKind effectiveKind(WatchSession session) {
		ShutdownKind kind = ShutdownKind.CRASH;

		if (Files.exists(session.markerFile)) {
			try {
				kind = ShutdownKind.fromMarker(Files.readString(session.markerFile));
			} catch (IOException e) {
				kind = ShutdownKind.CRASH;
			}
		}

		if (kind == ShutdownKind.QUIT && CrashSignature.presentIn(session.logFile)) {
			return ShutdownKind.CRASH;
		}

		return kind;
	}

	private static boolean triggers(WatchSession session, ShutdownKind kind) {
		return switch (kind) {
			case CRASH -> session.triggerOnCrash;
			case ALT_F4 -> session.triggerOnAltF4;
			case WINDOW_CLOSE -> session.triggerOnWindowClose;
			case QUIT -> session.triggerOnQuit;
		};
	}

	private static void handleCrash(WatchSession session, ShutdownKind kind, long processEndMillis)
			throws IOException {
		LocalDateTime crashedAt = LocalDateTime.now();

		boolean deliberateTest = Files.exists(session.testMarkerFile);
		CrashEvidence evidence = CrashEvidence.collect(session, kind, deliberateTest, processEndMillis);

		Path report = ReportBuilder.build(session, evidence, crashedAt);

		boolean copiedAsFile = ClipboardHelper.copyAsFile(report);
		boolean canRestart = canRestart(session, processEndMillis);

		if (session.autoOpen) {
			// Automatik heisst automatisch: kein Fenster, das sich vor den Browser schiebt
			// und ihm den Fokus wegnimmt. Was zu tun ist, steht in den Einstellungen.
			CrashDialog.openUri(session.provider.newChatUrl());

			if (session.autoRestart && canRestart) {
				restartGame(session);
			}

			ClipboardHelper.holdWhileNeeded();
			return;
		}

		CrashDialog.Choice choice = CrashDialog.show(session, evidence, report, crashedAt,
				copiedAsFile, canRestart);

		if (choice.alwaysAuto()) {
			rememberAutoOpen(session);
		}

		if (choice.restart()) {
			restartGame(session);
		}
	}

	/**
	 * Ein Neustart lohnt nur, wenn das Spiel vorher auch wirklich lief. Stirbt es schon
	 * beim Laden, würde ein Neustart bloss die Schleife Absturz-Neustart-Absturz eröffnen.
	 */
	private static boolean canRestart(WatchSession session, long processEndMillis) {
		if (processEndMillis - session.startedAt < MIN_UPTIME_FOR_RESTART_MILLIS) {
			return false;
		}

		// Mit hinterlegtem Startlink braucht es die aufgezeichnete Startzeile nicht.
		if (usableLaunchLink(session)) {
			return true;
		}

		return Files.isReadable(session.restartCommandFile);
	}

	/**
	 * Ein Startlink taugt nur, wenn er auch einer ist. Steht dort etwa nur eine
	 * Instanz-Kennung, wuerde das Betriebssystem sie fuer einen Dateinamen halten - und
	 * der Neustart faellt still aus. Dann lieber der eigene Weg, der immer funktioniert.
	 */
	private static boolean usableLaunchLink(WatchSession session) {
		return session.launchLink != null && session.launchLink.matches("(?i)[a-z][a-z0-9+.-]*://.+");
	}

	private static void restartGame(WatchSession session) {
		// Wenn der Nutzer einen Startlink hinterlegt hat, soll der Launcher das Spiel
		// starten - dann bleibt auch dessen eigene Anzeige richtig.
		if (usableLaunchLink(session)) {
			System.out.println("LogAI: asking the launcher to restart Minecraft");
			CrashDialog.openUri(session.launchLink);

			// Manche Launcher nehmen den Link entgegen und tun dann nichts, ohne das
			// irgendwo zu melden. Wer auf "Neustart" geklickt hat, soll deswegen nicht
			// vor einem Spiel sitzen, das nie kommt.
			if (gameAppeared(session)) {
				return;
			}

			System.out.println("LogAI: the launcher did not start the game, doing it directly");
		}

		try {
			RestartCommand.restart(RestartCommand.read(session.restartCommandFile), session.gameDir,
					session.reportDir.resolveSibling("restart.log"));
			System.out.println("LogAI: restarting Minecraft");
		} catch (IOException e) {
			System.err.println("LogAI watcher: could not restart Minecraft: " + e);
		}
	}

	/**
	 * Wartet darauf, dass wieder ein Spiel läuft.
	 *
	 * <p>Erkannt wird es an der Java-Programmdatei aus der aufgezeichneten Startzeile -
	 * die Argumente eines fremden Prozesses gibt das Betriebssystem nicht heraus, die
	 * Programmdatei schon.
	 */
	private static boolean gameAppeared(WatchSession session) {
		String javaBinary;

		try {
			javaBinary = RestartCommand.read(session.restartCommandFile).get(0);
		} catch (IOException | IndexOutOfBoundsException e) {
			// Ohne Vergleichswert lieber glauben, dass es geklappt hat, als das Spiel
			// womöglich zweimal zu starten.
			return true;
		}

		long deadline = System.currentTimeMillis() + LAUNCHER_GRACE_MILLIS;

		while (System.currentTimeMillis() < deadline) {
			boolean running = ProcessHandle.allProcesses()
					.anyMatch(process -> process.info().command()
							.map(command -> command.equalsIgnoreCase(javaBinary))
							.orElse(false));

			if (running) {
				return true;
			}

			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return true;
			}
		}

		return false;
	}

	/**
	 * Der Watcher kann die Mod-Konfiguration nicht selbst schreiben (kein Gson im Classpath),
	 * also legt er einen Wunsch ab, den der Mod beim nächsten Start einliest.
	 */
	private static void rememberAutoOpen(WatchSession session) {
		try {
			Path pending = session.markerFile.resolveSibling("pending-auto-open");
			Files.writeString(pending, "true", StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		} catch (IOException e) {
			System.err.println("LogAI watcher: could not store the auto-open choice: " + e);
		}
	}

	private static void cleanUp(Path sessionFile, WatchSession session) {
		try {
			Files.deleteIfExists(sessionFile);
		} catch (IOException ignored) {
			// Wird beim nächsten Start überschrieben.
		}

		if (session != null) {
			try {
				Files.deleteIfExists(session.markerFile);
				Files.deleteIfExists(session.testMarkerFile);
				// Enthaelt den Access-Token, also so frueh wie moeglich weg damit.
				Files.deleteIfExists(session.restartCommandFile);
			} catch (IOException ignored) {
				// Der Mod löscht sie beim nächsten Start ohnehin.
			}
		}
	}
}
