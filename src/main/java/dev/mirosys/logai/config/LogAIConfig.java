package dev.mirosys.logai.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import dev.mirosys.logai.LogAI;

/**
 * Die Mod-Einstellungen, abgelegt als {@code config/logai.json}.
 */
public final class LogAIConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Die Mod-Version, mit der die Einrichtung zuletzt durchlaufen wurde. */
	public String setupVersion = "";
	/** Die gewählte KI. */
	public String provider = AiProvider.CLAUDE.name();
	/** Ohne Nachfrage kopieren und den Browser öffnen. */
	public boolean autoOpen = false;
	/** Nach einem Absturz Minecraft neu starten. */
	public boolean autoRestart = false;

	// Welche Arten des Beendens LogAI ueberhaupt melden soll.
	/** Absturz, Freeze, harter Kill - der eigentliche Zweck des Mods. */
	public boolean triggerOnCrash = true;
	/** Alt+F4, erkannt am Tastendruck. */
	public boolean triggerOnAltF4 = true;
	/** Das X am Fenster oder der Task-Manager. */
	public boolean triggerOnWindowClose = true;
	/** Der Beenden-Knopf im Spiel. Normalerweise nicht gewuenscht. */
	public boolean triggerOnQuit = false;
	/** Überschreibt die automatische Launcher-Erkennung, wenn gesetzt. */
	public String launcherOverride = "";
	/** Experimentell: Link, mit dem der Launcher gebeten wird, die Instanz zu starten. */
	public String launchLink = "";

	private transient Path file;
	private transient boolean existed;
	private transient Runnable onSaved;

	/**
	 * Wird nach jedem Speichern aufgerufen. Der Watcher lebt in einem eigenen Prozess und
	 * merkt von einer Änderung sonst nichts.
	 */
	public void onSaved(Runnable listener) {
		this.onSaved = listener;
	}

	public AiProvider provider() {
		return AiProvider.byName(provider, AiProvider.CLAUDE);
	}

	public void setProvider(AiProvider value) {
		this.provider = value.name();
	}

	/** Ob ueberhaupt schon einmal eine Konfiguration auf der Platte lag. */
	public boolean existed() {
		return existed;
	}

	/** Ob die Einrichtung schon einmal vollstaendig durchlaufen wurde. */
	public boolean setupCompletedBefore() {
		return setupVersion != null && !setupVersion.isBlank();
	}

	/**
	 * Ob die Einrichtung gezeigt werden soll: beim allerersten Start und nach jedem
	 * Versionswechsel, damit neue Optionen nicht unbemerkt bleiben.
	 */
	public boolean needsSetup(String currentVersion) {
		return !currentVersion.equals(setupVersion);
	}

	public void markSetupDone(String currentVersion) {
		this.setupVersion = currentVersion;
		save();
	}

	public static LogAIConfig load(Path file) {
		LogAIConfig config = new LogAIConfig();
		boolean existed = false;

		if (Files.isReadable(file)) {
			try {
				String json = Files.readString(file, StandardCharsets.UTF_8);
				LogAIConfig loaded = GSON.fromJson(json, LogAIConfig.class);

				if (loaded != null) {
					config = loaded;
					existed = true;
				}
			} catch (IOException | JsonSyntaxException e) {
				LogAI.LOGGER.warn("Could not read {}, falling back to defaults", file, e);
			}
		}

		config.file = file;
		config.existed = existed;
		return config;
	}

	public void save() {
		if (file == null) {
			return;
		}

		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(this), StandardCharsets.UTF_8);
		} catch (IOException e) {
			LogAI.LOGGER.warn("Could not save {}", file, e);
		}

		if (onSaved != null) {
			onSaved.run();
		}
	}
}
