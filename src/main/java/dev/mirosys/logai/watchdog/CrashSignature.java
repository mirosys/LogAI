package dev.mirosys.logai.watchdog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Zweite Meinung zur Marker-Datei.
 *
 * <p>Die Marker-Datei allein würde daneben liegen, falls Minecraft beim Absturz doch noch
 * ordentlich herunterfährt. Deshalb bekommt das Log-Ende zusätzlich einen Blick: findet
 * sich dort eine dieser sehr eindeutigen Zeilen, war es ein Absturz - Marker hin oder her.
 */
public final class CrashSignature {
	private static final List<String> MARKERS = List.of(
			"---- Minecraft Crash Report ----",
			"A fatal error has been detected by the Java Runtime Environment",
			"Minecraft has crashed!",
			"Failed to start Minecraft",
			"Exception in server tick loop",
			"Exception caught in server tick loop",
			"Unreported exception thrown!");

	private CrashSignature() {
	}

	public static boolean presentIn(Path logFile) {
		if (!Files.isReadable(logFile)) {
			return false;
		}

		try {
			String content = new String(Files.readAllBytes(logFile), StandardCharsets.UTF_8);

			for (String marker : MARKERS) {
				if (content.contains(marker)) {
					return true;
				}
			}
		} catch (IOException | OutOfMemoryError e) {
			return false;
		}

		return false;
	}
}
