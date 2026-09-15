package dev.mirosys.logai.watchdog;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import dev.mirosys.logai.config.AiProvider;

/**
 * Everything the watcher process needs to know, handed over as a file.
 *
 * <p>A plain {@link Properties} file rather than JSON on purpose: the watcher runs with
 * the mod jar as its only classpath, so there is no Gson or anything else to lean on.
 */
public final class WatchSession {
	public long pid;
	public Path gameDir;
	public Path logFile;
	/** Written by the mod on an orderly shutdown; its content says how the game ended. */
	public Path markerFile;
	/** Written by the test button so the report is not read as a real crash. */
	public Path testMarkerFile;
	/** The recorded command line for restarting. Contains the access token. */
	public Path restartCommandFile;
	public Path reportDir;

	public AiProvider provider = AiProvider.CLAUDE;
	public boolean autoOpen;
	public boolean autoRestart;
	/** Experimental: a launcher link to restart through; empty means restart directly. */
	public String launchLink = "";

	public boolean triggerOnCrash = true;
	public boolean triggerOnAltF4 = true;
	public boolean triggerOnWindowClose = true;
	public boolean triggerOnQuit;

	public String launcher = "unknown launcher";
	public String minecraftVersion = "unknown";
	public String loaderVersion = "unknown";
	public String modVersion = "unknown";
	/** Game start time in millis, used to ignore hs_err files from earlier sessions. */
	public long startedAt;

	public void write(Path file) throws IOException {
		Properties p = new Properties();
		p.setProperty("pid", Long.toString(pid));
		p.setProperty("gameDir", absolute(gameDir));
		p.setProperty("logFile", absolute(logFile));
		p.setProperty("markerFile", absolute(markerFile));
		p.setProperty("testMarkerFile", absolute(testMarkerFile));
		p.setProperty("restartCommandFile", absolute(restartCommandFile));
		p.setProperty("reportDir", absolute(reportDir));
		p.setProperty("provider", provider.name());
		p.setProperty("autoOpen", Boolean.toString(autoOpen));
		p.setProperty("autoRestart", Boolean.toString(autoRestart));
		p.setProperty("launchLink", launchLink == null ? "" : launchLink);
		p.setProperty("triggerOnCrash", Boolean.toString(triggerOnCrash));
		p.setProperty("triggerOnAltF4", Boolean.toString(triggerOnAltF4));
		p.setProperty("triggerOnWindowClose", Boolean.toString(triggerOnWindowClose));
		p.setProperty("triggerOnQuit", Boolean.toString(triggerOnQuit));
		p.setProperty("launcher", launcher);
		p.setProperty("minecraftVersion", minecraftVersion);
		p.setProperty("loaderVersion", loaderVersion);
		p.setProperty("modVersion", modVersion);
		p.setProperty("startedAt", Long.toString(startedAt));

		Files.createDirectories(file.getParent());

		try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			p.store(writer, "LogAI watch session - removed automatically when the game ends");
		}
	}

	public static WatchSession read(Path file) throws IOException {
		Properties p = new Properties();

		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			p.load(reader);
		}

		WatchSession s = new WatchSession();
		s.pid = Long.parseLong(p.getProperty("pid", "-1"));
		s.gameDir = path(p, "gameDir");
		s.logFile = path(p, "logFile");
		s.markerFile = path(p, "markerFile");
		s.testMarkerFile = path(p, "testMarkerFile");
		s.restartCommandFile = path(p, "restartCommandFile");
		s.reportDir = path(p, "reportDir");
		s.provider = AiProvider.byName(p.getProperty("provider"), AiProvider.CLAUDE);
		s.autoOpen = flag(p, "autoOpen", false);
		s.autoRestart = flag(p, "autoRestart", false);
		s.launchLink = p.getProperty("launchLink", "");
		s.triggerOnCrash = flag(p, "triggerOnCrash", true);
		s.triggerOnAltF4 = flag(p, "triggerOnAltF4", true);
		s.triggerOnWindowClose = flag(p, "triggerOnWindowClose", true);
		s.triggerOnQuit = flag(p, "triggerOnQuit", false);
		s.launcher = p.getProperty("launcher", "unknown launcher");
		s.minecraftVersion = p.getProperty("minecraftVersion", "unknown");
		s.loaderVersion = p.getProperty("loaderVersion", "unknown");
		s.modVersion = p.getProperty("modVersion", "unknown");
		s.startedAt = Long.parseLong(p.getProperty("startedAt", "0"));
		return s;
	}

	private static String absolute(Path path) {
		return path.toAbsolutePath().toString();
	}

	private static Path path(Properties p, String key) {
		return Path.of(p.getProperty(key, "."));
	}

	private static boolean flag(Properties p, String key, boolean fallback) {
		return Boolean.parseBoolean(p.getProperty(key, Boolean.toString(fallback)));
	}
}
