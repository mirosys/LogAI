package dev.mirosys.logai.watchdog;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Fragt beim Betriebssystem nach der echten Startzeile des eigenen Prozesses.
 *
 * <p>Java selbst gibt sie nicht heraus: {@code sun.java.command} enthält die Argumente
 * schon zusammengefügt, die Anführungszeichen sind darin verloren. Ein Pfad wie
 * {@code --gameDir "…\main 1.0.0"} lässt sich daraus nicht mehr korrekt zerlegen. Das
 * Betriebssystem kennt dagegen die ursprünglichen Argumentgrenzen.
 */
public final class NativeCommandLine {
	private NativeCommandLine() {
	}

	/**
	 * @return die Argumente des eigenen Prozesses, oder eine leere Liste, wenn dieses
	 *         System das nicht hergibt.
	 */
	public static List<String> current() {
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

		try {
			if (os.contains("win")) {
				return fromWindows();
			}

			if (os.contains("mac")) {
				return fromMac();
			}

			return fromProc();
		} catch (Exception e) {
			return List.of();
		}
	}

	/** Linux: exakt und ohne Umwege, die Argumente sind mit Nullbytes getrennt. */
	private static List<String> fromProc() throws IOException {
		Path cmdline = Path.of("/proc/self/cmdline");

		if (!Files.isReadable(cmdline)) {
			return List.of();
		}

		String raw = new String(Files.readAllBytes(cmdline), StandardCharsets.UTF_8);
		List<String> parts = new ArrayList<>();

		for (String part : raw.split("\0")) {
			if (!part.isEmpty()) {
				parts.add(part);
			}
		}

		return parts;
	}

	/**
	 * Windows: die Kommandozeile steht in der Prozessliste, abrufbar über WMI. Sie kommt
	 * als eine Zeichenkette zurück, in der die Anführungszeichen noch stehen.
	 */
	private static List<String> fromWindows() throws IOException, InterruptedException {
		long pid = ProcessHandle.current().pid();
		String script = "[Console]::OutputEncoding = [System.Text.Encoding]::UTF8; "
				+ "(Get-CimInstance Win32_Process -Filter 'ProcessId=" + pid + "').CommandLine";

		String raw = run(List.of("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script));
		return raw.isBlank() ? List.of() : splitWindows(raw.strip());
	}

	/**
	 * macOS: {@code ps} fügt die Argumente mit Leerzeichen zusammen, die ursprünglichen
	 * Grenzen sind damit verloren. Nur brauchbar, solange keine Leerzeichen vorkommen.
	 */
	private static List<String> fromMac() throws IOException, InterruptedException {
		long pid = ProcessHandle.current().pid();
		String raw = run(List.of("ps", "-ww", "-o", "command=", "-p", Long.toString(pid)));

		if (raw.isBlank()) {
			return List.of();
		}

		return List.of(raw.strip().split(" +"));
	}

	private static String run(List<String> command) throws IOException, InterruptedException {
		ProcessBuilder builder = new ProcessBuilder(command);
		builder.redirectErrorStream(false);
		Process process = builder.start();

		String output;

		try (InputStream in = process.getInputStream()) {
			output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}

		if (!process.waitFor(20, TimeUnit.SECONDS)) {
			process.destroyForcibly();
			return "";
		}

		return output;
	}

	/**
	 * Zerlegt eine Windows-Kommandozeile in Argumente. Anführungszeichen fassen zusammen,
	 * ein Backslash ist nur direkt vor einem Anführungszeichen ein Fluchtsymbol - sonst
	 * wäre kein einziger Windows-Pfad zu gebrauchen.
	 */
	static List<String> splitWindows(String line) {
		List<String> parts = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		boolean quoted = false;
		int backslashes = 0;

		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);

			if (c == '\\') {
				backslashes++;
				continue;
			}

			if (c == '"') {
				// Ein Paar Backslashes steht für einen Backslash, ein einzelner davor
				// entwertet das Anführungszeichen.
				current.append("\\".repeat(backslashes / 2));

				if (backslashes % 2 == 1) {
					current.append('"');
				} else {
					quoted = !quoted;
				}

				backslashes = 0;
				continue;
			}

			current.append("\\".repeat(backslashes));
			backslashes = 0;

			if (Character.isWhitespace(c) && !quoted) {
				if (!current.isEmpty()) {
					parts.add(current.toString());
					current.setLength(0);
				}

				continue;
			}

			current.append(c);
		}

		current.append("\\".repeat(backslashes));

		if (!current.isEmpty()) {
			parts.add(current.toString());
		}

		return parts;
	}
}
