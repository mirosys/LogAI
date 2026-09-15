package dev.mirosys.logai.watchdog;

import java.io.IOException;
import java.nio.file.Files;

/**
 * Gets Minecraft running again after it ended.
 *
 * <p>Two ways: through a launcher link the user configured, or by running the recorded
 * command line directly. The link is tried first because the launcher then keeps its own
 * bookkeeping straight; the direct route always works and is the fallback.
 */
public final class Restarter {
	/** Below this uptime a restart would only open a crash-restart-crash loop. */
	private static final long MIN_UPTIME_MILLIS = 60_000L;

	/** How long a launcher gets to react to its link before we do it ourselves. */
	private static final long LAUNCHER_GRACE_MILLIS = 20_000L;

	private final WatchSession session;

	public Restarter(WatchSession session) {
		this.session = session;
	}

	/**
	 * A restart only makes sense if the game actually ran. If it died during loading, a
	 * restart would just die the same way.
	 */
	public boolean possible(long processEndMillis) {
		if (processEndMillis - session.startedAt < MIN_UPTIME_MILLIS) {
			return false;
		}

		// With a launcher link the recorded command line is not needed.
		return hasLaunchLink() || Files.isReadable(session.restartCommandFile);
	}

	public void restart() {
		if (hasLaunchLink()) {
			System.out.println("LogAI: asking the launcher to restart Minecraft");
			UriOpener.open(session.launchLink);

			// Some launchers accept the link and then do nothing, without saying so.
			// Whoever clicked "restart" should not sit in front of a game that never comes.
			if (gameAppeared()) {
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
	 * A launch link only counts if it actually is one. Just an instance id in the field
	 * would make the OS look for a file of that name, and the restart would silently fail.
	 */
	private boolean hasLaunchLink() {
		return session.launchLink != null
				&& session.launchLink.matches("(?i)[a-z][a-z0-9+.-]*://.+");
	}

	/**
	 * Waits for a game process to show up, recognised by the java binary from the recorded
	 * command line - the OS will not tell us a foreign process's arguments, but it does
	 * tell us the binary.
	 */
	private boolean gameAppeared() {
		String javaBinary;

		try {
			javaBinary = RestartCommand.read(session.restartCommandFile).get(0);
		} catch (IOException | IndexOutOfBoundsException e) {
			// Without something to compare against, rather assume it worked than start
			// the game a second time.
			return true;
		}

		long deadline = System.currentTimeMillis() + LAUNCHER_GRACE_MILLIS;

		while (System.currentTimeMillis() < deadline) {
			boolean running = ProcessHandle.allProcesses()
					.anyMatch(process -> process.info().command()
							.map(javaBinary::equalsIgnoreCase)
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
}
