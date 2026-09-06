package dev.mirosys.logai.watchdog;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Rät anhand des Spielordner-Pfads, über welchen Launcher gestartet wurde.
 * Diese Zeile landet im Prompt, damit die KI den Kontext kennt.
 */
public final class LauncherDetector {
	private LauncherDetector() {
	}

	public static String detect(Path gameDir) {
		if (gameDir == null) {
			return "unknown launcher";
		}

		String path = gameDir.toAbsolutePath().toString()
				.replace(java.io.File.separatorChar, '/')
				.toLowerCase(Locale.ROOT);

		if (path.contains("/modrinthapp/") || path.contains("/com.modrinth.theseus/")) {
			return "Modrinth App launcher";
		}
		if (path.contains("/prismlauncher/") || path.contains("/prisminstances/")) {
			return "Prism Launcher";
		}
		if (path.contains("/polymc/")) {
			return "PolyMC";
		}
		if (path.contains("/multimc/")) {
			return "MultiMC";
		}
		if (path.contains("/curseforge/") || path.contains("/overwolf/")) {
			return "CurseForge App launcher";
		}
		if (path.contains("/atlauncher/")) {
			return "ATLauncher";
		}
		if (path.contains("/gdlauncher")) {
			return "GDLauncher";
		}
		if (path.contains("/technic/")) {
			return "Technic Launcher";
		}
		if (path.endsWith("/.minecraft") || path.contains("/.minecraft/")) {
			return "official Minecraft launcher";
		}

		return "unknown launcher";
	}
}
