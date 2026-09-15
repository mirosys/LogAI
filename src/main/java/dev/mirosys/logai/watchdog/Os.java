package dev.mirosys.logai.watchdog;

import java.util.Locale;

/**
 * The operating system we are running on. Several places need to know, and they should
 * all agree on how to find out.
 */
public enum Os {
	WINDOWS,
	MAC,
	LINUX;

	private static final Os CURRENT = detect();

	public static Os current() {
		return CURRENT;
	}

	public static boolean isWindows() {
		return CURRENT == WINDOWS;
	}

	public static boolean isMac() {
		return CURRENT == MAC;
	}

	private static Os detect() {
		String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

		if (name.contains("win")) {
			return WINDOWS;
		}

		if (name.contains("mac")) {
			return MAC;
		}

		// Anything else behaves like Linux for our purposes: xdg-open, /proc, X11 clipboard.
		return LINUX;
	}
}
