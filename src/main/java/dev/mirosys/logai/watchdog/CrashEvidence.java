package dev.mirosys.logai.watchdog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Was der Watcher tatsächlich beobachtet hat.
 *
 * <p>Ohne diese Angaben muss die KI raten, wie das Spiel gestorben ist: ein Log, das
 * einfach aufhört, sieht identisch aus, egal ob eingefroren, vom Speicher erschlagen
 * oder absichtlich beendet. Genau dieses Rätselraten soll der Abschnitt ersparen.
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

	/** Ob das Spiel es überhaupt noch geschafft hat, sich ordentlich zu verabschieden. */
	public boolean orderlyShutdown() {
		return shutdownKind != ShutdownKind.CRASH;
	}

	/** Wie lange nach der letzten Log-Zeile der Prozess noch existierte. */
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
