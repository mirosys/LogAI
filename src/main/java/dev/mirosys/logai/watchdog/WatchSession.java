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
 * Die Datenübergabe vom Mod an den Watcher-Prozess.
 *
 * <p>Bewusst eine schlichte {@link Properties}-Datei statt JSON: der Watcher läuft
 * mit dem Mod-Jar als einzigem Classpath und hat damit weder Gson noch sonst eine
 * Bibliothek zur Verfügung.
 */
public final class WatchSession {
	public long pid;
	public Path gameDir;
	public Path logFile;
	public Path markerFile;
	/** Wird vom Testknopf angelegt, damit der Bericht nicht als echter Absturz gelesen wird. */
	public Path testMarkerFile;
	public Path reportDir;
	public AiProvider provider = AiProvider.CLAUDE;
	public boolean autoOpen;
	/** Nach dem Absturz ungefragt neu starten. */
	public boolean autoRestart;
	/** Welche Arten des Beendens gemeldet werden sollen. */
	public boolean triggerOnCrash = true;
	public boolean triggerOnAltF4 = true;
	public boolean triggerOnWindowClose = true;
	public boolean triggerOnQuit;
	/** Datei mit der Startzeile des Spiels. Enthaelt den Access-Token. */
	public Path restartCommandFile;
	public String launcher = "unknown launcher";
	public String minecraftVersion = "unknown";
	public String loaderVersion = "unknown";
	public String modVersion = "unknown";
	/** Zeitpunkt des Spielstarts in Millisekunden, um alte hs_err-Dateien zu ignorieren. */
	public long startedAt;

	public void write(Path file) throws IOException {
		Properties properties = new Properties();
		properties.setProperty("pid", Long.toString(pid));
		properties.setProperty("gameDir", gameDir.toAbsolutePath().toString());
		properties.setProperty("logFile", logFile.toAbsolutePath().toString());
		properties.setProperty("markerFile", markerFile.toAbsolutePath().toString());
		properties.setProperty("testMarkerFile", testMarkerFile.toAbsolutePath().toString());
		properties.setProperty("reportDir", reportDir.toAbsolutePath().toString());
		properties.setProperty("provider", provider.name());
		properties.setProperty("autoOpen", Boolean.toString(autoOpen));
		properties.setProperty("autoRestart", Boolean.toString(autoRestart));
		properties.setProperty("triggerOnCrash", Boolean.toString(triggerOnCrash));
		properties.setProperty("triggerOnAltF4", Boolean.toString(triggerOnAltF4));
		properties.setProperty("triggerOnWindowClose", Boolean.toString(triggerOnWindowClose));
		properties.setProperty("triggerOnQuit", Boolean.toString(triggerOnQuit));
		properties.setProperty("restartCommandFile", restartCommandFile.toAbsolutePath().toString());
		properties.setProperty("launcher", launcher);
		properties.setProperty("minecraftVersion", minecraftVersion);
		properties.setProperty("loaderVersion", loaderVersion);
		properties.setProperty("modVersion", modVersion);
		properties.setProperty("startedAt", Long.toString(startedAt));

		Files.createDirectories(file.getParent());

		try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			properties.store(writer, "LogAI watch session - wird nach dem Spielende automatisch entfernt");
		}
	}

	public static WatchSession read(Path file) throws IOException {
		Properties properties = new Properties();

		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			properties.load(reader);
		}

		WatchSession session = new WatchSession();
		session.pid = Long.parseLong(properties.getProperty("pid", "-1"));
		session.gameDir = Path.of(properties.getProperty("gameDir", "."));
		session.logFile = Path.of(properties.getProperty("logFile", "."));
		session.markerFile = Path.of(properties.getProperty("markerFile", "."));
		session.testMarkerFile = Path.of(properties.getProperty("testMarkerFile", "."));
		session.reportDir = Path.of(properties.getProperty("reportDir", "."));
		session.provider = AiProvider.byName(properties.getProperty("provider"), AiProvider.CLAUDE);
		session.autoOpen = Boolean.parseBoolean(properties.getProperty("autoOpen", "false"));
		session.autoRestart = Boolean.parseBoolean(properties.getProperty("autoRestart", "false"));
		session.triggerOnCrash = Boolean.parseBoolean(properties.getProperty("triggerOnCrash", "true"));
		session.triggerOnAltF4 = Boolean.parseBoolean(properties.getProperty("triggerOnAltF4", "true"));
		session.triggerOnWindowClose =
				Boolean.parseBoolean(properties.getProperty("triggerOnWindowClose", "true"));
		session.triggerOnQuit = Boolean.parseBoolean(properties.getProperty("triggerOnQuit", "false"));
		session.restartCommandFile = Path.of(properties.getProperty("restartCommandFile", "."));
		session.launcher = properties.getProperty("launcher", "unknown launcher");
		session.minecraftVersion = properties.getProperty("minecraftVersion", "unknown");
		session.loaderVersion = properties.getProperty("loaderVersion", "unknown");
		session.modVersion = properties.getProperty("modVersion", "unknown");
		session.startedAt = Long.parseLong(properties.getProperty("startedAt", "0"));
		return session;
	}
}
