package dev.mirosys.logai.watchdog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Baut die Textdatei, die der Nutzer in den Chat einfügt: Prompt-Kopf, danach das Log.
 */
public final class ReportBuilder {
	/** Zeilen vom Anfang des Logs - dort stehen Minecraft-Version, Loader und die komplette Modliste. */
	private static final int HEAD_LINES = 300;
	/** Zeilen vom Ende des Logs - dort steht, was tatsächlich schiefgegangen ist. */
	private static final int TAIL_LINES = 1800;
	/** Aus einer hs_err-Datei ist nur der Kopf interessant, der Rest ist Speicher-Dump. */
	private static final int HS_ERR_LINES = 220;

	public static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
	public static final DateTimeFormatter HUMAN_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private ReportBuilder() {
	}

	/**
	 * Schreibt den Bericht und gibt die erzeugte Datei zurück.
	 */
	public static Path build(WatchSession session, CrashEvidence evidence, LocalDateTime crashedAt)
			throws IOException {
		StringBuilder out = new StringBuilder(64 * 1024);

		appendPrompt(out, evidence);
		appendContext(out, session, evidence, crashedAt);
		appendLog(out, session.logFile);

		if (evidence.nativeCrashFile() != null) {
			out.append("\n\n===== NATIVE JVM CRASH FILE (")
					.append(evidence.nativeCrashFile().getFileName()).append(") =====\n");
			appendLimited(out, evidence.nativeCrashFile(), HS_ERR_LINES);
		}

		Files.createDirectories(session.reportDir);
		Path target = session.reportDir.resolve("LogAI-crash-" + FILE_STAMP.format(crashedAt) + ".txt");
		Files.writeString(target, out.toString(), StandardCharsets.UTF_8);
		return target;
	}

	private static void appendPrompt(StringBuilder out, CrashEvidence evidence) {
		if (evidence.deliberateTest()) {
			out.append("This is a TEST, not a real crash. I pressed the test button in the LogAI mod, ")
					.append("which terminates the game immediately, so the log ends mid-sentence on ")
					.append("purpose and contains no error. There is nothing to diagnose.\n\n")
					.append("Just confirm that you received the file, and tell me in two or three ")
					.append("sentences what you would look at first if this had been a real crash.\n\n");
			return;
		}

		if (evidence.shutdownKind().isForceClose()) {
			out.append("My Minecraft session did not crash on its own: it was force-closed - ")
					.append(evidence.shutdownKind() == ShutdownKind.ALT_F4
							? "I pressed Alt+F4. "
							: "the window was closed from outside the game. ")
					.append("I would not have done that without a reason - the game was most likely frozen, ")
					.append("unresponsive, stuttering or otherwise misbehaving, and quitting through ")
					.append("the in-game menu was not an option any more.\n\n")
					.append("Read the log with that in mind. Tell me what was going wrong shortly ")
					.append("before the end - a hang, a stall, a flood of warnings, a memory or ")
					.append("rendering problem - and what I should do about it. If the log genuinely ")
					.append("looks healthy, say so plainly instead of inventing a cause.\n\n");
			return;
		}

		if (evidence.shutdownKind() == ShutdownKind.QUIT) {
			out.append("Minecraft did not crash. I closed it normally with the quit button, and I ")
					.append("have LogAI set up to hand me the log every time anyway.\n\n")
					.append("So treat this as a routine check-up rather than a diagnosis: go through ")
					.append("the log and tell me whether anything deserves attention - errors that ")
					.append("were swallowed, mod conflicts, missing dependencies, repeated warnings, ")
					.append("or signs of a performance problem. If the session looks healthy, say so ")
					.append("plainly and keep it short.\n\n");
			return;
		}

		out.append("My Minecraft session just crashed. Analyse this log file, tell me exactly what ")
				.append("caused it and what I need to do to fix it.\n\n");
	}

	/**
	 * Der Abschnitt, der die KI davon abhält, den Absturzhergang zu erraten.
	 */
	private static void appendContext(StringBuilder out, WatchSession session,
			CrashEvidence evidence, LocalDateTime crashedAt) {
		out.append("Context:\n")
				.append("- Launcher: ").append(session.launcher).append('\n')
				.append("- Minecraft: ").append(session.minecraftVersion).append('\n')
				.append("- Fabric Loader: ").append(session.loaderVersion).append('\n')
				.append("- Crash detected at: ").append(HUMAN_STAMP.format(crashedAt)).append('\n')
				.append("- Collected by LogAI ").append(session.modVersion).append('\n');

		out.append("\nWhat LogAI observed (it watches the game from a separate process, so this is ")
				.append("first-hand, not guessed from the log):\n")
				.append("- How the game ended: ")
				.append(describe(evidence.shutdownKind(), evidence.crashSignatureInLog()))
				.append("- Crash report text inside the log: ")
				.append(evidence.crashSignatureInLog() ? "yes, see below.\n" : "none.\n")
				.append("- Native JVM crash file (hs_err_pid*.log) from this session: ")
				.append(evidence.nativeCrashFile() != null
						? evidence.nativeCrashFile().getFileName() + ", appended at the end.\n"
						: "none.\n");

		long silence = evidence.silenceSeconds();

		if (silence >= 0) {
			out.append("- The log stopped being written ").append(silence)
					.append(" seconds before the process disappeared.\n");
		}

		// Bei einem normalen Beenden ist "nichts gefunden" der Normalfall und keine Spur,
		// der jemand nachgehen müsste.
		if (!evidence.crashSignatureInLog() && evidence.nativeCrashFile() == null
				&& !evidence.deliberateTest() && evidence.shutdownKind() != ShutdownKind.QUIT) {
			out.append("\nSo there is no exception and no crash report anywhere. Do not look for a ")
					.append("stack trace, there is none - look at what the game was doing in the last ")
					.append(evidence.shutdownKind().isForceClose()
							? "log lines before I closed the window.\n"
							: "log lines instead, and treat this as a freeze, an out-of-memory kill, "
									+ "a graphics driver fault or a manual kill.\n");
		}

		out.append('\n');
	}

	private static String describe(ShutdownKind kind, boolean crashInLog) {
		String forceCloseTail = " Minecraft then shut down normally, so the log ends tidily - that "
				+ "tidy ending is NOT evidence that everything was fine.\n";

		return switch (kind) {
			case QUIT -> crashInLog
					? "through Minecraft's own quit button, but the log shows a crash anyway.\n"
					: "through Minecraft's own quit button - an ordinary, deliberate exit. Nothing "
							+ "went wrong here; this report exists because I asked for one on every "
							+ "session.\n";
			case ALT_F4 -> "I pressed Alt+F4. LogAI saw the key combination itself, so this is "
					+ "certain, not a guess." + forceCloseTail;
			case WINDOW_CLOSE -> "the window was closed from outside the game - the window's close "
					+ "button, the task manager, or something similar. Those all look identical to "
					+ "a program, so LogAI cannot narrow it down further." + forceCloseTail;
			case CRASH -> "the process disappeared WITHOUT going through Minecraft's shutdown at "
					+ "all - it was killed or died outright.\n";
		};
	}

	private static void appendLog(StringBuilder out, Path logFile) {
		out.append("===== MINECRAFT LOG (").append(logFile.getFileName()).append(") =====\n");

		if (!Files.isReadable(logFile)) {
			out.append("[LogAI could not read the log file at ").append(logFile).append("]\n");
			return;
		}

		List<String> lines = readLines(logFile);

		if (lines.size() <= HEAD_LINES + TAIL_LINES) {
			lines.forEach(line -> out.append(line).append('\n'));
			return;
		}

		int skipped = lines.size() - HEAD_LINES - TAIL_LINES;

		for (int i = 0; i < HEAD_LINES; i++) {
			out.append(lines.get(i)).append('\n');
		}

		out.append("\n[... LogAI removed ").append(skipped)
				.append(" lines from the middle of the log to keep this file readable ...]\n\n");

		for (int i = lines.size() - TAIL_LINES; i < lines.size(); i++) {
			out.append(lines.get(i)).append('\n');
		}
	}

	private static void appendLimited(StringBuilder out, Path file, int maxLines) {
		List<String> lines = readLines(file);
		int limit = Math.min(lines.size(), maxLines);

		for (int i = 0; i < limit; i++) {
			out.append(lines.get(i)).append('\n');
		}

		if (lines.size() > limit) {
			out.append("[... ").append(lines.size() - limit).append(" further lines omitted ...]\n");
		}
	}

	private static List<String> readLines(Path file) {
		try {
			// Logs enthalten gelegentlich kaputte Bytes aus Mod-Ausgaben, deshalb tolerant lesen.
			String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
			return new ArrayList<>(content.lines().toList());
		} catch (IOException e) {
			List<String> fallback = new ArrayList<>();
			fallback.add("[LogAI could not read " + file + ": " + e + "]");
			return fallback;
		}
	}

	/**
	 * Sucht eine hs_err-Datei, die nach dem Spielstart entstanden ist. Nur die zählt zu
	 * diesem Absturz, ältere liegen oft noch im Spielordner herum.
	 */
	static Path findRecentHsErr(WatchSession session) {
		try (Stream<Path> files = Files.list(session.gameDir)) {
			return files
					.filter(path -> path.getFileName().toString().startsWith("hs_err_pid"))
					.filter(path -> path.getFileName().toString().endsWith(".log"))
					.filter(path -> lastModified(path) >= session.startedAt)
					.max(Comparator.comparingLong(ReportBuilder::lastModified))
					.orElse(null);
		} catch (IOException e) {
			return null;
		}
	}

	private static long lastModified(Path path) {
		try {
			return Files.getLastModifiedTime(path).toMillis();
		} catch (IOException e) {
			return 0L;
		}
	}
}
