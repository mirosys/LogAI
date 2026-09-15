package dev.mirosys.logai.watchdog;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Guesses the launcher from the game directory path. The result ends up in the prompt so
 * the AI knows the context.
 */
public final class LauncherDetector {
	/** Path fragment (lower case, forward slashes) and the launcher it points to. */
	private record Hint(String fragment, String launcher) {
	}

	private static final List<Hint> HINTS = List.of(
			new Hint("/modrinthapp/", "Modrinth App launcher"),
			new Hint("/com.modrinth.theseus/", "Modrinth App launcher"),
			new Hint("/prismlauncher/", "Prism Launcher"),
			new Hint("/prisminstances/", "Prism Launcher"),
			new Hint("/polymc/", "PolyMC"),
			new Hint("/multimc/", "MultiMC"),
			new Hint("/curseforge/", "CurseForge App launcher"),
			new Hint("/overwolf/", "CurseForge App launcher"),
			new Hint("/atlauncher/", "ATLauncher"),
			new Hint("/gdlauncher", "GDLauncher"),
			new Hint("/technic/", "Technic Launcher"),
			new Hint("/.minecraft", "official Minecraft launcher"));

	private LauncherDetector() {
	}

	public static String detect(Path gameDir) {
		if (gameDir == null) {
			return "unknown launcher";
		}

		String path = gameDir.toAbsolutePath().toString()
				.replace(File.separatorChar, '/')
				.toLowerCase(Locale.ROOT);

		return HINTS.stream()
				.filter(hint -> path.contains(hint.fragment()))
				.map(Hint::launcher)
				.findFirst()
				.orElse("unknown launcher");
	}
}
