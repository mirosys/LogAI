package dev.mirosys.logai.watchdog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * A second opinion next to the marker file.
 *
 * <p>The marker alone would be wrong if Minecraft manages an orderly shutdown while
 * crashing. So the end of the log gets a look too: if one of these very specific lines is
 * in there, it was a crash, marker or not.
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
			return MARKERS.stream().anyMatch(content::contains);
		} catch (IOException | OutOfMemoryError e) {
			return false;
		}
	}
}
