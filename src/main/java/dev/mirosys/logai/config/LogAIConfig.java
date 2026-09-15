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
 * The mod's settings, stored as {@code config/logai.json}.
 */
public final class LogAIConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** The mod version the setup was last completed with. */
	public String setupVersion = "";
	/** The chosen AI, by enum name. */
	public String provider = AiProvider.CLAUDE.name();
	/** Copy and open the browser without asking. */
	public boolean autoOpen = false;
	/** Restart Minecraft after a crash. */
	public boolean autoRestart = false;

	// Which kinds of shutdown should produce a report.
	public boolean triggerOnCrash = true;
	public boolean triggerOnAltF4 = true;
	public boolean triggerOnWindowClose = true;
	public boolean triggerOnQuit = false;

	/** Overrides the launcher detection when set. */
	public String launcherOverride = "";
	/** Experimental: a link that asks the launcher to start this instance. */
	public String launchLink = "";

	private transient Path file;
	private transient Runnable onSaved;

	/**
	 * Called after every save. The watcher lives in its own process and would not notice
	 * a change otherwise.
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

	/** Whether the setup has ever been completed. */
	public boolean setupCompletedBefore() {
		return setupVersion != null && !setupVersion.isBlank();
	}

	/**
	 * The setup runs on the very first start and again after every version change, so
	 * new options do not go unnoticed.
	 */
	public boolean needsSetup(String currentVersion) {
		return !currentVersion.equals(setupVersion);
	}

	public void markSetupDone(String currentVersion) {
		this.setupVersion = currentVersion;
		save();
	}

	public static LogAIConfig load(Path file) {
		LogAIConfig config = null;

		if (Files.isReadable(file)) {
			try {
				config = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), LogAIConfig.class);
			} catch (IOException | JsonSyntaxException e) {
				LogAI.LOGGER.warn("Could not read {}, falling back to defaults", file, e);
			}
		}

		if (config == null) {
			config = new LogAIConfig();
		}

		config.file = file;
		return config;
	}

	public void save() {
		if (file != null) {
			try {
				Files.createDirectories(file.getParent());
				Files.writeString(file, GSON.toJson(this), StandardCharsets.UTF_8);
			} catch (IOException e) {
				LogAI.LOGGER.warn("Could not save {}", file, e);
			}
		}

		if (onSaved != null) {
			onSaved.run();
		}
	}
}
