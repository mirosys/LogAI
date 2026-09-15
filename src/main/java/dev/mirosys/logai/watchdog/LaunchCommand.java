package dev.mirosys.logai.watchdog;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns the launcher's command line into one that works without the launcher.
 *
 * <p>Several launchers do not start Minecraft directly. They start a helper of their own
 * that then starts the game. The Modrinth App, for example, drops a {@code theseus.jar}
 * into a temp folder, puts it on the classpath, attaches it as a Java agent, and lets it
 * talk to the launcher over a local port. All of that is gone once the game ends, so
 * running the same command line again fails at once.
 *
 * <p>The pattern is the same everywhere: the launcher's main class first, the game's real
 * main class right after. That is what we look for, without naming any launcher.
 */
public final class LaunchCommand {
	/** Looks like a fully qualified class name rather than a path. */
	private static final Pattern CLASS_NAME =
			Pattern.compile("[A-Za-z_$][\\w$]*(\\.[A-Za-z_$][\\w$]*)+");

	/**
	 * Launchers that pass their arguments over stdin instead of the command line. Nothing
	 * can be recovered from the command line there.
	 */
	private static final Set<String> STDIN_LAUNCHERS = Set.of(
			"org.prismlauncher.EntryPoint",
			"org.multimc.EntryPoint",
			"org.polymc.EntryPoint");

	private LaunchCommand() {
	}

	/**
	 * @return the same command line minus the launcher's wrapper, or an empty list if no
	 *         standalone command can be built from it
	 */
	public static List<String> standalone(List<String> command) {
		int mainIndex = mainClassIndex(command);

		if (mainIndex < 0 || STDIN_LAUNCHERS.contains(command.get(mainIndex))) {
			return List.of();
		}

		// Two class names in a row: the first is the launcher's wrapper, the second is
		// the game's main class.
		boolean wrapped = mainIndex + 1 < command.size() && isClassName(command.get(mainIndex + 1));

		String temp = temporaryDirectory();
		List<String> result = new ArrayList<>(command.size());

		for (int i = 0; i < command.size(); i++) {
			String part = command.get(i);

			// Between the java binary and the main class: JVM options.
			if (i > 0 && i < mainIndex) {
				if (isClasspathOption(part) && i + 1 < mainIndex) {
					result.add(part);
					result.add(withoutTemporaryEntries(command.get(++i), temp));
					continue;
				}

				// Launchers like to attach themselves as a Java agent too, with the same
				// throwaway file. Anything pointing into the temp folder will be gone
				// later and would keep the JVM from starting at all.
				if (part.startsWith("-") && pointsIntoTemp(part, temp)) {
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

	/** The first argument that is neither an option nor the value of one. */
	private static int mainClassIndex(List<String> command) {
		// Index 0 is the java binary itself.
		for (int i = 1; i < command.size(); i++) {
			String part = command.get(i);

			if (isClasspathOption(part) || part.equals("-p") || part.equals("--module-path")) {
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

	private static boolean isClasspathOption(String part) {
		return part.equals("-cp") || part.equals("-classpath") || part.equals("--class-path");
	}

	private static boolean isClassName(String value) {
		return CLASS_NAME.matcher(value).matches()
				&& !value.toLowerCase(Locale.ROOT).endsWith(".jar")
				&& !value.contains("/")
				&& !value.contains("\\");
	}

	private static boolean pointsIntoTemp(String option, String temp) {
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
		return tempDir == null || tempDir.isBlank()
				? ""
				: normalise(Path.of(tempDir).toAbsolutePath().toString());
	}

	private static String normalise(String path) {
		return path.replace('\\', '/').toLowerCase(Locale.ROOT);
	}
}
