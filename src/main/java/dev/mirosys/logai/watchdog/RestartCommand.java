package dev.mirosys.logai.watchdog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Die Startzeile des laufenden Minecraft, damit der Watcher das Spiel neu starten kann.
 *
 * <p>Der Watcher kann sie nicht selbst ermitteln: nach dem Tod des Prozesses ist die
 * Kommandozeile weg. Also merkt der Mod sie sich beim Start.
 *
 * <p>Achtung: diese Zeile enthält den Minecraft-Access-Token. Die Datei wird deshalb
 * getrennt von allem anderen abgelegt und bei der ersten Gelegenheit wieder gelöscht.
 */
public final class RestartCommand {
	private RestartCommand() {
	}

	/**
	 * Setzt die Startzeile aus dem zusammen, was die laufende JVM über sich weiß, und legt
	 * sie ab. Wird im Minecraft-Prozess aufgerufen.
	 */
	public static void capture(Path file) throws IOException {
		// Zuerst das Betriebssystem fragen: nur dort stehen die Argumentgrenzen noch so,
		// wie der Launcher sie gesetzt hat.
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
		// Eine Zeile pro Argument: Argumente enthalten Leerzeichen, Zeilenumbrüche nie.
		Files.write(file, standalone, StandardCharsets.UTF_8);
		restrictToOwner(file);
	}

	/**
	 * Notnagel, wenn das Betriebssystem nichts herausgibt: aus dem zusammensetzen, was die
	 * JVM über sich selbst weiß. Ungenau, weil {@code sun.java.command} die
	 * Anführungszeichen bereits verloren hat - Pfade mit Leerzeichen zerfallen dabei.
	 */
	private static List<String> reconstruct() throws IOException {
		List<String> command = new ArrayList<>();
		command.add(ProcessHandle.current().info().command()
				.orElse(Path.of(System.getProperty("java.home"), "bin", "java").toString()));

		// -Xmx, --add-opens und was der Launcher sonst noch mitgibt.
		command.addAll(java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments());

		String classPath = System.getProperty("java.class.path", "");

		if (!classPath.isBlank()) {
			command.add("-cp");
			command.add(classPath);
		}

		// Hauptklasse samt Spielargumenten, inklusive Access-Token.
		String mainCommand = System.getProperty("sun.java.command", "");

		if (mainCommand.isBlank()) {
			throw new IOException("this JVM does not expose sun.java.command, cannot restart");
		}

		command.addAll(splitArguments(mainCommand));
		return command;
	}

	public static List<String> read(Path file) throws IOException {
		return Files.readAllLines(file, StandardCharsets.UTF_8).stream()
				.filter(line -> !line.isEmpty())
				.toList();
	}

	/**
	 * Startet das Spiel neu. Der neue Prozess hängt an keinem Launcher mehr, läuft also
	 * eigenständig weiter.
	 *
	 * <p>Die Ausgabe landet in einer Datei statt im Nichts: stirbt der neue Prozess sofort,
	 * ist das der einzige Ort, an dem der Grund noch steht.
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
	 * Die Startzeile fürs Log, ohne den Access-Token. Der gehört in keine Datei, die
	 * jemand später harmlos weiterreicht.
	 */
	private static String redacted(List<String> command) {
		StringBuilder out = new StringBuilder();
		boolean hideNext = false;

		for (String part : command) {
			if (hideNext) {
				out.append(" <hidden>");
				hideNext = false;
				continue;
			}

			hideNext = part.equalsIgnoreCase("--accessToken") || part.equalsIgnoreCase("--session")
					|| part.equalsIgnoreCase("--xuid") || part.equalsIgnoreCase("--uuid");
			out.append(out.isEmpty() ? "" : " ").append(part);
		}

		return out.toString();
	}

	/**
	 * {@code sun.java.command} ist eine einzelne Zeichenkette. Argumente mit Leerzeichen
	 * sind darin in Anführungszeichen gesetzt.
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

	/** Best effort: auf Dateisystemen mit Rechten nur für den Besitzer lesbar machen. */
	private static void restrictToOwner(Path file) {
		try {
			Files.setPosixFilePermissions(file,
					java.util.Set.of(java.nio.file.attribute.PosixFilePermission.OWNER_READ,
							java.nio.file.attribute.PosixFilePermission.OWNER_WRITE));
		} catch (Exception ignored) {
			// Windows kennt keine POSIX-Rechte, dort bleibt es bei den Ordner-Rechten.
		}
	}
}
