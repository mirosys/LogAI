package dev.mirosys.logai.watchdog;

import java.awt.Desktop;
import java.net.URI;
import java.util.Locale;

/**
 * Opens a URI with whatever the system has registered for it: a web page in the browser,
 * a {@code launcher://} link in the launcher that owns the scheme.
 */
public final class UriOpener {
	private UriOpener() {
	}

	public static void open(String uri) {
		// Desktop.browse is meant for web pages and hands anything else to the browser,
		// which then asks what to do with it. Only use it for http(s).
		if (isWebPage(uri) && browseWithDesktop(uri)) {
			return;
		}

		try {
			switch (Os.current()) {
				// "start" hands the URI to the program registered for its scheme. The empty
				// string is the window title; without it, start would use the URI as title.
				case WINDOWS -> new ProcessBuilder("cmd.exe", "/c", "start", "", uri).start();
				case MAC -> new ProcessBuilder("open", uri).start();
				case LINUX -> new ProcessBuilder("xdg-open", uri).start();
			}
		} catch (Exception ignored) {
			// Nothing else we can do at this point.
		}
	}

	private static boolean browseWithDesktop(String uri) {
		try {
			if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
				Desktop.getDesktop().browse(URI.create(uri));
				return true;
			}
		} catch (Exception ignored) {
			// Fall through to the shell.
		}

		return false;
	}

	private static boolean isWebPage(String uri) {
		String lower = uri.toLowerCase(Locale.ROOT);
		return lower.startsWith("http://") || lower.startsWith("https://");
	}
}
