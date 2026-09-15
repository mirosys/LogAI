package dev.mirosys.logai.watchdog;

/**
 * How the game ended.
 *
 * <p>The mod writes this into the marker file on shutdown. No marker file at all means
 * the process never got as far as writing one.
 *
 * <p>This is as fine-grained as it gets: the window's close button and "End task" in the
 * task manager send the same window message, which no program can tell apart. Only Alt+F4
 * gives itself away, through the key press just before.
 */
public enum ShutdownKind {
	/** The quit button inside the game. The only case that is truly voluntary. */
	QUIT,
	/** Alt+F4, recognised by the key press shortly before the window closed. */
	ALT_F4,
	/** The window's close button, the task manager, logging off - closed from outside. */
	WINDOW_CLOSE,
	/** No marker at all: crash, freeze, or hard kill. */
	CRASH;

	/** Whether the window was closed from outside the game, by whatever means. */
	public boolean isForceClose() {
		return this == ALT_F4 || this == WINDOW_CLOSE;
	}

	public static ShutdownKind fromMarker(String content) {
		if (content == null) {
			return CRASH;
		}

		String value = content.strip();

		for (ShutdownKind kind : values()) {
			if (kind.name().equals(value)) {
				return kind;
			}
		}

		// A marker from an older version that did not record the kind meant "voluntary".
		return QUIT;
	}
}
