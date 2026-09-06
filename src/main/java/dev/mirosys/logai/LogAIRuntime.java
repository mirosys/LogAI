package dev.mirosys.logai;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import net.fabricmc.loader.api.FabricLoader;

import dev.mirosys.logai.config.LogAIConfig;
import dev.mirosys.logai.watchdog.LauncherDetector;
import dev.mirosys.logai.watchdog.RestartCommand;
import dev.mirosys.logai.watchdog.WatchSession;
import dev.mirosys.logai.watchdog.WatcherLauncher;

/**
 * Der Teil des Mods, der so früh wie möglich laufen muss: Konfiguration lesen und den
 * Watcher starten.
 *
 * <p>Angestoßen wird das vom {@code preLaunch}-Einstiegspunkt, also noch bevor Minecraft
 * selbst startet. Damit ist auch ein Absturz während des Ladens abgedeckt - genau dann,
 * wenn es am ehesten kracht. Nichts hier darf Minecraft-Klassen anfassen.
 */
public final class LogAIRuntime {
	private static LogAIConfig config;
	private static Path stateDir;
	private static Path markerFile;
	private static String modVersion = "unknown";
	private static WatchSession session;
	private static Path sessionFile;
	private static boolean started;

	private LogAIRuntime() {
	}

	/**
	 * Mehrfach aufrufbar: der Client-Einstiegspunkt ruft das noch einmal auf, falls
	 * {@code preLaunch} aus irgendeinem Grund nicht durchgelaufen ist.
	 */
	public static synchronized void bootstrap() {
		if (started) {
			return;
		}

		started = true;

		try {
			FabricLoader loader = FabricLoader.getInstance();
			Path gameDir = loader.getGameDir();
			stateDir = gameDir.resolve("logai");
			markerFile = stateDir.resolve("clean-exit");
			modVersion = versionOf(LogAI.MOD_ID);

			config = LogAIConfig.load(loader.getConfigDir().resolve("logai.json"));
			// Jede gespeicherte Aenderung soll sofort beim Watcher ankommen, nicht erst
			// beim naechsten Spielstart.
			config.onSaved(LogAIRuntime::refreshWatchSession);
			applyPendingAutoOpen();

			// Marker der letzten Sitzung sind für diese bedeutungslos.
			deleteQuietly(markerFile);
			deleteQuietly(stateDir.resolve("test-crash"));

			startWatcher(gameDir);
		} catch (Throwable failure) {
			// Ein kaputter Crash-Melder darf niemals den Spielstart verhindern.
			LogAI.LOGGER.error("LogAI could not start up, crashes will not be reported", failure);

			if (config == null) {
				config = LogAIConfig.load(FabricLoader.getInstance().getConfigDir().resolve("logai.json"));
			}
		}
	}

	public static LogAIConfig config() {
		if (config == null) {
			bootstrap();
		}

		return config;
	}

	public static String modVersion() {
		return modVersion;
	}

	public static Path markerFile() {
		return markerFile;
	}

	/**
	 * Hinterlässt dem Watcher die Notiz, dass der nächste Absturz absichtlich war.
	 * Sonst schickt der Bericht die KI auf die Suche nach einer Ursache, die es nicht gibt.
	 */
	public static void markTestCrash() {
		try {
			Files.createDirectories(stateDir);
			Files.writeString(stateDir.resolve("test-crash"), Long.toString(System.currentTimeMillis()));
		} catch (IOException e) {
			LogAI.LOGGER.warn("Could not write the test-crash marker", e);
		}
	}

	/**
	 * Der Watcher kann die JSON-Konfiguration nicht schreiben, deshalb hinterlässt er dort
	 * nur einen Zettel, den wir hier einlösen.
	 */
	private static void applyPendingAutoOpen() {
		Path pending = stateDir.resolve("pending-auto-open");

		if (!Files.exists(pending)) {
			return;
		}

		config.autoOpen = true;
		config.save();
		deleteQuietly(pending);
		LogAI.LOGGER.info("Auto-open enabled, as chosen in the crash dialog");
	}

	private static void startWatcher(Path gameDir) {
		sessionFile = stateDir.resolve("watch-session.properties");

		session = new WatchSession();
		session.pid = ProcessHandle.current().pid();
		session.gameDir = gameDir;
		session.logFile = gameDir.resolve("logs").resolve("latest.log");
		session.markerFile = markerFile;
		session.testMarkerFile = stateDir.resolve("test-crash");
		session.reportDir = stateDir.resolve("reports");
		session.restartCommandFile = stateDir.resolve("restart-command");
		captureRestartCommand(session.restartCommandFile);
		session.launcher = config.launcherOverride == null || config.launcherOverride.isBlank()
				? LauncherDetector.detect(gameDir)
				: config.launcherOverride;
		session.minecraftVersion = versionOf("minecraft");
		session.loaderVersion = versionOf("fabricloader");
		session.modVersion = modVersion;
		session.startedAt = System.currentTimeMillis();
		applySettings(session);

		try {
			WatcherLauncher.start(session, sessionFile);
		} catch (IOException e) {
			LogAI.LOGGER.error("Could not start the crash watcher, crashes will not be reported", e);
		}
	}

	private static void applySettings(WatchSession target) {
		target.provider = config.provider();
		target.autoOpen = config.autoOpen;
		target.autoRestart = config.autoRestart;
		target.launchLink = config.launchLink == null ? "" : config.launchLink;
		target.triggerOnCrash = config.triggerOnCrash;
		target.triggerOnAltF4 = config.triggerOnAltF4;
		target.triggerOnWindowClose = config.triggerOnWindowClose;
		target.triggerOnQuit = config.triggerOnQuit;
	}

	/**
	 * Schreibt die geänderten Einstellungen für den bereits laufenden Watcher heraus.
	 *
	 * <p>Ohne das würde jede Änderung erst beim nächsten Spielstart wirken: der Watcher
	 * bekommt seine Werte beim Start als Momentaufnahme, und er ist nach dem Absturz der
	 * Einzige, der noch lebt. Er liest die Datei deshalb im Absturzmoment noch einmal.
	 */
	private static void refreshWatchSession() {
		if (session == null || sessionFile == null) {
			return;
		}

		applySettings(session);

		try {
			session.write(sessionFile);
		} catch (IOException e) {
			LogAI.LOGGER.warn("Could not hand the changed settings to the watcher", e);
		}
	}

	/**
	 * Legt die Startzeile ab, damit der Watcher das Spiel neu starten kann. Schlaegt das
	 * fehl, faellt nur der Neustart weg - alles andere funktioniert weiter.
	 */
	private static void captureRestartCommand(Path file) {
		// Das Betriebssystem nach der Startzeile zu fragen dauert unter Windows fast eine
		// Sekunde. Das gehört nicht in den Spielstart - gebraucht wird die Datei erst,
		// wenn das Spiel zu Ende ist.
		Thread capture = new Thread(() -> {
			try {
				RestartCommand.capture(file);
			} catch (Exception e) {
				LogAI.LOGGER.warn("Could not record the restart command, restarting after a crash "
						+ "will not be offered: {}", e.getMessage());
			}
		}, "LogAI-restart-command");
		capture.setDaemon(true);
		capture.start();
	}

	private static void deleteQuietly(Path path) {
		try {
			Files.deleteIfExists(path);
		} catch (IOException e) {
			LogAI.LOGGER.warn("Could not remove {}", path, e);
		}
	}

	private static String versionOf(String modId) {
		return FabricLoader.getInstance().getModContainer(modId)
				.map(container -> container.getMetadata().getVersion().getFriendlyString())
				.orElse("unknown");
	}
}
