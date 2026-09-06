package dev.mirosys.logai.watchdog;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Macht aus der Startzeile des Launchers eine, die auch ohne ihn funktioniert.
 *
 * <p>Mehrere Launcher starten nicht Minecraft, sondern erst ein eigenes Hilfsprogramm,
 * das dann Minecraft aufruft. Die Modrinth App etwa legt dafür ein {@code theseus.jar} in
 * einen Wegwerf-Ordner und lässt es über einen lokalen Port mit dem Launcher sprechen.
 * Beides ist nach dem Spielende weg - startet man diese Zeile unverändert erneut, stirbt
 * der Prozess sofort.
 *
 * <p>Das Muster ist bei allen gleich: erst die Hauptklasse des Launchers, direkt danach
 * die echte Hauptklasse des Spiels. Genau daran ist es zu erkennen, ohne einen einzelnen
 * Launcher beim Namen zu nennen.
 */
public final class LaunchCommand {
	/** Sieht aus wie ein voll qualifizierter Klassenname und nicht wie ein Pfad. */
	private static final Pattern CLASS_NAME =
			Pattern.compile("[A-Za-z_$][\\w$]*(\\.[A-Za-z_$][\\w$]*)+");

	/**
	 * Launcher, die ihre Argumente über die Standardeingabe übergeben statt über die
	 * Kommandozeile. Bei denen lässt sich aus der Startzeile nichts wiederherstellen.
	 */
	private static final Set<String> UNSUPPORTED_LAUNCHERS = Set.of(
			"org.prismlauncher.EntryPoint",
			"org.multimc.EntryPoint",
			"org.polymc.EntryPoint");

	private LaunchCommand() {
	}

	/**
	 * @return dieselbe Startzeile ohne den Launcher-Aufsatz, oder eine leere Liste, wenn
	 *         sich daraus kein eigenständiger Start bauen lässt.
	 */
	public static List<String> standalone(List<String> command) {
		int mainIndex = mainClassIndex(command);

		if (mainIndex < 0) {
			return List.of();
		}

		if (UNSUPPORTED_LAUNCHERS.contains(command.get(mainIndex))) {
			// Dieser Launcher reicht seine Angaben über die Standardeingabe herein.
			return List.of();
		}

		// Folgen zwei Klassennamen aufeinander, ist der erste der Aufsatz des Launchers
		// und der zweite die Hauptklasse des Spiels.
		boolean wrapped = mainIndex + 1 < command.size() && isClassName(command.get(mainIndex + 1));

		String temp = temporaryDirectory();
		List<String> result = new ArrayList<>();

		for (int i = 0; i < command.size(); i++) {
			String part = command.get(i);

			// Der Bereich zwischen Programmdatei und Hauptklasse: die JVM-Optionen.
			if (i > 0 && i < mainIndex) {
				if (isClasspathOption(part) && i + 1 < mainIndex) {
					result.add(part);
					result.add(withoutTemporaryEntries(command.get(++i), temp));
					continue;
				}

				// Der Launcher hängt sich gern zusätzlich als Java-Agent ein, mit
				// derselben Wegwerf-Datei. Alles, was dorthin zeigt, ist nach dem
				// Spielende verschwunden und verhindert dann den Start der JVM.
				if (part.startsWith("-") && referencesTemporaryDirectory(part, temp)) {
					continue;
				}
			}

			if (i == mainIndex && wrapped) {
				continue;
			}

			result.add(part);
		}

		return result;
	}

	private static boolean isClasspathOption(String part) {
		return part.equals("-cp") || part.equals("-classpath") || part.equals("--class-path");
	}

	private static boolean referencesTemporaryDirectory(String option, String temp) {
		return !temp.isEmpty() && normalise(option).contains(temp);
	}

	private static String withoutTemporaryEntries(String classPath, String temp) {
		String separator = System.getProperty("path.separator", ";");
		List<String> kept = new ArrayList<>();

		for (String entry : classPath.split(Pattern.quote(separator))) {
			if (!entry.isBlank() && !normalise(entry).startsWith(temp)) {
				kept.add(entry);
			}
		}

		return String.join(separator, kept);
	}

	private static String temporaryDirectory() {
		String tempDir = System.getProperty("java.io.tmpdir");

		if (tempDir == null || tempDir.isBlank()) {
			return "";
		}

		return normalise(Path.of(tempDir).toAbsolutePath().toString());
	}

	/**
	 * Die Position der Hauptklasse: das erste Argument, das keine Option ist und nicht
	 * zum Wert einer Option gehört.
	 */
	private static int mainClassIndex(List<String> command) {
		// Das erste Element ist die Java-Programmdatei selbst.
		for (int i = 1; i < command.size(); i++) {
			String part = command.get(i);

			if (part.equals("-cp") || part.equals("-classpath") || part.equals("--class-path")
					|| part.equals("-p") || part.equals("--module-path")) {
				i++;
				continue;
			}

			if (part.startsWith("-")) {
				continue;
			}

			return isClassName(part) ? i : -1;
		}

		return -1;
	}

	private static boolean isClassName(String value) {
		return CLASS_NAME.matcher(value).matches()
				&& !value.toLowerCase(Locale.ROOT).endsWith(".jar")
				&& !value.contains("/")
				&& !value.contains("\\");
	}

	private static String normalise(String path) {
		return path.replace('\\', '/').toLowerCase(Locale.ROOT);
	}
}
