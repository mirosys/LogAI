package dev.mirosys.logai.watchdog;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Asks the operating system for the real command line of the current process.
 *
 * <p>Java itself will not hand it over: {@code sun.java.command} has the arguments
 * already joined with spaces and the quotes gone, so {@code --gameDir "…\main 1.0.0"}
 * cannot be split back correctly. The OS still knows where each argument began and ended.
 */
public final class NativeCommandLine {
	private NativeCommandLine() {
	}

	/**
	 * @return the arguments of the current process, or an empty list if this system does
	 *         not give them out
	 */
	public static List<String> current() {
		try {
			return switch (Os.current()) {
				case WINDOWS -> fromWindows();
				case MAC -> fromMac();
				case LINUX -> fromProc();
			};
		} catch (Exception e) {
			return List.of();
		}
	}

	/** Linux: exact and cheap, the arguments are separated by NUL bytes. */
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
	 * Windows: the command line is in the process list, reachable through WMI. It comes
	 * back as a single string with the quotes still in place.
	 */
	private static List<String> fromWindows() throws IOException, InterruptedException {
		long pid = ProcessHandle.current().pid();
		String script = "[Console]::OutputEncoding = [System.Text.Encoding]::UTF8; "
				+ "(Get-CimInstance Win32_Process -Filter 'ProcessId=" + pid + "').CommandLine";

		String raw = run(List.of("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script));
		return raw.isBlank() ? List.of() : splitWindows(raw.strip());
	}

	/**
	 * macOS: {@code ps} joins the arguments with spaces, so the original boundaries are
	 * lost. Only good enough while no argument contains a space.
	 */
	private static List<String> fromMac() throws IOException, InterruptedException {
		long pid = ProcessHandle.current().pid();
		String raw = run(List.of("ps", "-ww", "-o", "command=", "-p", Long.toString(pid)));
		return raw.isBlank() ? List.of() : List.of(raw.strip().split(" +"));
	}

	private static String run(List<String> command) throws IOException, InterruptedException {
		Process process = new ProcessBuilder(command).start();
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
	 * Splits a Windows command line into arguments. Quotes group, and a backslash only
	 * escapes when it sits directly in front of a quote - otherwise no Windows path would
	 * survive.
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
				// Pairs of backslashes are literal; an odd one escapes the quote.
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
