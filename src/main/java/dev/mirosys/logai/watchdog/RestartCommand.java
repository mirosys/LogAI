package dev.mirosys.logai.watchdog;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The command line Minecraft was started with, recorded so the watcher can start it again.
 *
 * <p>The watcher cannot find it out on its own: once the process is dead, its command line
 * is gone. So the mod records it at startup.
 *
 * <p>Note that this line contains the Minecraft access token. The file is kept apart from
 * everything else and deleted at the first opportunity.
 */
public final class RestartCommand {
	/** Arguments whose values must never end up in a log. */
	private static final Set<String> SECRET_OPTIONS =
			Set.of("--accesstoken", "--session", "--xuid", "--uuid");

	private RestartCommand() {
	}

	/**
	 * Works out the standalone command line for the current process and writes it to
	 * {@code file}. Called from inside Minecraft.
	 */
	public static void capture(Path file) throws IOException {
		// Ask the OS first: only there are the argument boundaries still the way the
		// launcher set them.
		List<String> command = NativeCommandLine.current();

		if (command.isEmpty()) {
			command = reconstruct();
		}

		List<String> standalone = LaunchCommand.standalone(command);

		if (standalone.isEmpty()) {
			throw new IOException("this launcher does not start Minecraft in a way that can be "
					+ "repeated on its own");
		}

		Files.createDirectories(file.getParent());
		// One argument per line: arguments may contain spaces, but never line breaks.
		Files.write(file, standalone, StandardCharsets.UTF_8);
		restrictToOwner(file);
	}

	public static List<String> read(Path file) throws IOException {
		return Files.readAllLines(file, StandardCharsets.UTF_8).stream()
				.filter(line -> !line.isEmpty())
				.toList();
	}

	/**
	 * Starts the game again. The new process is not attached to any launcher and runs on
	 * its own.
	 *
	 * <p>Its output goes to a file rather than nowhere: if the new process dies at once,
	 * that file is the only place the reason survives.
	 */
	public static void restart(List<String> command, Path workingDirectory, Path logFile)
			throws IOException {
		Files.createDirectories(logFile.getParent());
		Files.writeString(logFile, "LogAI restart attempt\n" + redacted(command) + "\n\n");

		ProcessBuilder builder = new ProcessBuilder(command);
		builder.directory(workingDirectory.toFile());
		builder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));
		builder.redirectErrorStream(true);
		builder.start();
	}

	/**
	 * Fallback when the OS gives nothing back: assemble the line from what the JVM knows
	 * about itself. Lossy, because {@code sun.java.command} has already dropped the
	 * quoting - paths with spaces fall apart.
	 */
	private static List<String> reconstruct() throws IOException {
		List<String> command = new ArrayList<>();
		command.add(ProcessHandle.current().info().command()
				.orElse(Path.of(System.getProperty("java.home"), "bin", "java").toString()));

		// -Xmx, --add-opens and whatever else the launcher passed.
		command.addAll(ManagementFactory.getRuntimeMXBean().getInputArguments());

		String classPath = System.getProperty("java.class.path", "");

		if (!classPath.isBlank()) {
			command.add("-cp");
			command.add(classPath);
		}

		// Main class plus game arguments, access token included.
		String mainCommand = System.getProperty("sun.java.command", "");

		if (mainCommand.isBlank()) {
			throw new IOException("this JVM does not expose sun.java.command, cannot restart");
		}

		command.addAll(splitArguments(mainCommand));
		return command;
	}

	/** The command line for the log, with secrets blanked out. */
	private static String redacted(List<String> command) {
		StringBuilder out = new StringBuilder();
		boolean hideNext = false;

		for (String part : command) {
			if (!out.isEmpty()) {
				out.append(' ');
			}

			out.append(hideNext ? "<hidden>" : part);
			hideNext = SECRET_OPTIONS.contains(part.toLowerCase());
		}

		return out.toString();
	}

	/**
	 * {@code sun.java.command} is a single string; arguments containing spaces are
	 * wrapped in quotes there.
	 */
	private static List<String> splitArguments(String line) {
		List<String> parts = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		boolean quoted = false;

		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);

			if (c == '"') {
				quoted = !quoted;
			} else if (c == ' ' && !quoted) {
				if (!current.isEmpty()) {
					parts.add(current.toString());
					current.setLength(0);
				}
			} else {
				current.append(c);
			}
		}

		if (!current.isEmpty()) {
			parts.add(current.toString());
		}

		return parts;
	}

	/** Best effort: owner-only on file systems that support POSIX permissions. */
	private static void restrictToOwner(Path file) {
		try {
			Files.setPosixFilePermissions(file,
					Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
		} catch (Exception ignored) {
			// Windows has no POSIX permissions; the folder's ACL applies instead.
		}
	}
}
