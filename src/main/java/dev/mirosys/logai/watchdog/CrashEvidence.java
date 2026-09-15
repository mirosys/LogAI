package dev.mirosys.logai.watchdog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * What the watcher actually observed.
 *
 * <p>Without this the AI has to guess how the game died: a log that simply stops looks
 * the same whether the game froze, ran out of memory, or was closed on purpose. This is
 * the part of the report that saves it the guessing.
 */
public record CrashEvidence(
		ShutdownKind shutdownKind,
		boolean crashSignatureInLog,
		Path nativeCrashFile,
		long lastLogWriteMillis,
		long processEndMillis,
		boolean deliberateTest) {

	public static CrashEvidence collect(WatchSession session, ShutdownKind shutdownKind,
			boolean deliberateTest, long processEndMillis) {
		return new CrashEvidence(
				shutdownKind,
				CrashSignature.presentIn(session.logFile),
				ReportBuilder.findRecentHsErr(session),
				lastModified(session.logFile),
				processEndMillis,
				deliberateTest);
	}

	/** Seconds between the last log write and the process disappearing, or -1 if unknown. */
	public long silenceSeconds() {
		if (lastLogWriteMillis <= 0) {
			return -1;
		}

		return Math.max(0, (processEndMillis - lastLogWriteMillis) / 1000);
	}

	private static long lastModified(Path path) {
		try {
			return Files.getLastModifiedTime(path).toMillis();
		} catch (IOException e) {
			return 0L;
		}
	}
}
