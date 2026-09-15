package dev.mirosys.logai.watchdog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * The companion process. Runs in its own JVM so it survives whatever happens to the game:
 * a clean exception, a freeze, a native JVM crash, or a kill from the task manager.
 *
 * <p>This class and everything it touches must stay clear of Minecraft and Fabric classes.
 * The process starts with the mod jar as its only classpath.
 *
 * <p>Usage: {@code java -cp logai.jar dev.mirosys.logai.watchdog.CrashWatcher <session.properties>}
 */
public final class CrashWatcher {
	/**
	 * Short pause after the process is gone. On a crash the JVM sometimes writes its last
	 * log lines and the hs_err file only after it has left the process list.
	 */
	private static final long SETTLE_MILLIS = 2000L;

	private CrashWatcher() {
	}

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

			// The user may have changed settings in-game after this process started. The
			// mod rewrites the session file on every save, so re-read it now.
			session = reread(sessionFile, session);

			ShutdownKind kind = effectiveKind(session);

			if (wantsReport(session, kind)) {
				handleCrash(session, kind, processEndMillis);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Exception e) {
			System.err.println("LogAI watcher failed: " + e);
			e.printStackTrace();
		} finally {
			cleanUp(sessionFile, session);
		}

		// Also ends the AWT thread, which would otherwise keep the JVM around.
		System.exit(0);
	}

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
			// Minecraft was already gone before the watcher came up.
			return;
		}

		// A foreign process's exit code is not available through ProcessHandle, which is
		// why the marker file decides between clean and crashed.
		handle.get().onExit().join();
	}

	/**
	 * No marker means the process never got to write one. And if the log contains a crash
	 * report, that outranks a marker claiming everything was voluntary.
	 */
	private static ShutdownKind effectiveKind(WatchSession session) {
		ShutdownKind kind = ShutdownKind.CRASH;

		if (Files.exists(session.markerFile)) {
			try {
				kind = ShutdownKind.fromMarker(Files.readString(session.markerFile));
			} catch (IOException ignored) {
				// Treat an unreadable marker like a missing one.
			}
		}

		if (kind == ShutdownKind.QUIT && CrashSignature.presentIn(session.logFile)) {
			return ShutdownKind.CRASH;
		}

		return kind;
	}

	private static boolean wantsReport(WatchSession session, ShutdownKind kind) {
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
		Restarter restarter = new Restarter(session);
		boolean canRestart = restarter.possible(processEndMillis);

		if (session.autoOpen) {
			// Automatic means automatic: no window that would sit in front of the browser
			// and take its focus. What to do is in the settings.
			UriOpener.open(session.provider.newChatUrl());

			if (session.autoRestart && canRestart) {
				restarter.restart();
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
			restarter.restart();
		}
	}

	/**
	 * The watcher cannot write the mod's JSON config (no Gson on the classpath), so it
	 * leaves a note that the mod picks up on the next start.
	 */
	private static void rememberAutoOpen(WatchSession session) {
		try {
			Files.writeString(session.markerFile.resolveSibling("pending-auto-open"), "true");
		} catch (IOException e) {
			System.err.println("LogAI watcher: could not store the auto-open choice: " + e);
		}
	}

	private static void cleanUp(Path sessionFile, WatchSession session) {
		deleteQuietly(sessionFile);

		if (session != null) {
			deleteQuietly(session.markerFile);
			deleteQuietly(session.testMarkerFile);
			// Contains the access token, so it goes as early as possible.
			deleteQuietly(session.restartCommandFile);
		}
	}

	private static void deleteQuietly(Path path) {
		try {
			Files.deleteIfExists(path);
		} catch (IOException ignored) {
			// The mod removes leftovers on the next start anyway.
		}
	}
}
