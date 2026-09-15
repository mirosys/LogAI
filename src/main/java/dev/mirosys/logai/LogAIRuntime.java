package dev.mirosys.logai;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import net.fabricmc.loader.api.FabricLoader;

import dev.mirosys.logai.config.LogAIConfig;
import dev.mirosys.logai.watchdog.LauncherDetector;
import dev.mirosys.logai.watchdog.RestartCommand;
import dev.mirosys.logai.watchdog.WatchSession;
import dev.mirosys.logai.watchdog.WatcherLauncher;

/**
 * The part of the mod that has to run as early as possible: read the config and start
 * the watcher.
 *
 * <p>Triggered from the {@code preLaunch} entrypoint, before Minecraft itself starts, so a
 * crash during loading is covered too - that is when things fail most often. Nothing in
 * here may touch Minecraft classes.
 */
public final class LogAIRuntime {
	/** restart.log mirrors a whole session's console output; keep only the head. */
	private static final long RESTART_LOG_KEEP_BYTES = 32 * 1024;

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
	 * Safe to call more than once: the client entrypoint calls it again in case
	 * {@code preLaunch} did not run for whatever reason.
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
			// Every saved change should reach the watcher right away, not on the next start.
			config.onSaved(LogAIRuntime::refreshWatchSession);
			applyPendingAutoOpen();

			// Leftovers from the previous session mean nothing to this one.
			deleteQuietly(markerFile);
			deleteQuietly(stateDir.resolve("test-crash"));
			trimRestartLog();

			startWatcher(gameDir);
		} catch (Throwable failure) {
			// A broken crash reporter must never keep the game from starting.
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
	 * Leaves a note for the watcher that the next crash is deliberate. Otherwise the report
	 * would send the AI looking for a cause that does not exist.
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
	 * The watcher cannot write the JSON config, so it leaves a note instead, which we
	 * cash in here.
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
		session.restartCommandFile = stateDir.resolve("restart-command");
		session.reportDir = stateDir.resolve("reports");
		session.launcher = config.launcherOverride == null || config.launcherOverride.isBlank()
				? LauncherDetector.detect(gameDir)
				: config.launcherOverride;
		session.minecraftVersion = versionOf("minecraft");
		session.loaderVersion = versionOf("fabricloader");
		session.modVersion = modVersion;
		session.startedAt = System.currentTimeMillis();
		applySettings(session);

		captureRestartCommand(session.restartCommandFile);

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
	 * Writes the changed settings out for the watcher that is already running.
	 *
	 * <p>Without this every change would only take effect on the next game start: the
	 * watcher gets its values as a snapshot at startup, and after a crash it is the only
	 * one still alive. So it re-reads the file at that moment.
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
	 * Records the command line so the watcher can restart the game. Asking the OS for it
	 * takes close to a second on Windows, which does not belong in the game's startup -
	 * the file is only needed once the game has ended, so this runs in the background.
	 */
	private static void captureRestartCommand(Path file) {
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

	/**
	 * A restarted game writes its whole console output into restart.log. The head is what
	 * matters for diagnosing a failed start; the rest is a copy of latest.log.
	 */
	private static void trimRestartLog() {
		Path log = stateDir.resolve("restart.log");

		try {
			if (Files.exists(log) && Files.size(log) > RESTART_LOG_KEEP_BYTES) {
				try (FileChannel channel = FileChannel.open(log, StandardOpenOption.WRITE)) {
					channel.truncate(RESTART_LOG_KEEP_BYTES);
				}
			}
		} catch (IOException e) {
			LogAI.LOGGER.warn("Could not trim {}", log, e);
		}
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
