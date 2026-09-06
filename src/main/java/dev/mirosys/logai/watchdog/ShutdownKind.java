package dev.mirosys.logai.watchdog;

/**
 * Wie das Spiel zu Ende gegangen ist.
 *
 * <p>Der Mod schreibt die Art beim Herunterfahren in die Marker-Datei; fehlt die Datei
 * ganz, hat der Prozess es nicht mehr geschafft, etwas zu schreiben.
 *
 * <p>Weiter als bis {@link #ALT_F4} lässt sich nicht aufschlüsseln: das X am Fenster und
 * "Task beenden" im Task-Manager senden dieselbe Fenster-Nachricht, die kein Programm
 * unterscheiden kann. Nur Alt+F4 verrät sich vorher über die Tastatur.
 */
public enum ShutdownKind {
	/** Über den Beenden-Knopf im Spiel. Der einzige Fall, der wirklich freiwillig ist. */
	QUIT("the quit button inside the game"),
	/** Alt+F4, erkannt am Tastendruck kurz vor dem Schließen. */
	ALT_F4("Alt+F4"),
	/** Das X am Fenster, der Task-Manager, ein Abmelden - von außen geschlossen. */
	WINDOW_CLOSE("the window's close button or the task manager"),
	/** Gar kein Marker: Absturz, Freeze oder harter Kill. */
	CRASH("no shutdown at all, the process just died");

	private final String description;

	ShutdownKind(String description) {
		this.description = description;
	}

	public String description() {
		return description;
	}

	/** Ob das Fenster von außen geschlossen wurde, egal auf welchem Weg. */
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

		// Marker aus einer älteren Version ohne Art-Angabe: damals hiess das "freiwillig".
		return QUIT;
	}
}
