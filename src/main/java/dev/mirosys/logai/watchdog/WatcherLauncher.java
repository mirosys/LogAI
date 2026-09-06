package dev.mirosys.logai.watchdog;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import dev.mirosys.logai.LogAI;

/**
 * Startet den Watcher-Prozess. Läuft im Minecraft-Prozess und ist der einzige Ort,
 * an dem Mod-Seite und Watcher-Seite aufeinandertreffen.
 */
public final class WatcherLauncher {
	private WatcherLauncher() {
	}

	public static void start(WatchSession session, Path sessionFile) throws IOException {
		Path javaBinary = findJavaBinary();
		Path classpath = findOwnJar();

		if (classpath == null) {
			throw new IOException("could not locate the LogAI jar to launch the watcher from");
		}

		session.write(sessionFile);

		List<String> command = new ArrayList<>();
		command.add(javaBinary.toString());
		// Der Watcher schläft die meiste Zeit nur, ein kleiner Heap reicht völlig.
		command.add("-Xmx64M");
		command.add("-cp");
		command.add(classpath.toString());
		command.add(CrashWatcher.class.getName());
		command.add(sessionFile.toAbsolutePath().toString());

		Path watcherLog = sessionFile.resolveSibling("watcher.log");

		ProcessBuilder builder = new ProcessBuilder(command);
		builder.directory(session.gameDir.toFile());
		builder.redirectOutput(ProcessBuilder.Redirect.to(watcherLog.toFile()));
		builder.redirectErrorStream(true);

		Process process = builder.start();
		LogAI.LOGGER.info("Watcher process started (pid {}), watching Minecraft pid {}",
				process.pid(), session.pid);
	}

	/**
	 * Nimmt dieselbe JVM, mit der Minecraft läuft - dann passt garantiert auch die
	 * Java-Version. Unter Windows javaw, damit kein Konsolenfenster aufblitzt.
	 */
	private static Path findJavaBinary() {
		boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

		String current = ProcessHandle.current().info().command().orElse(null);

		if (current != null) {
			Path path = Path.of(current);

			if (windows) {
				Path windowless = path.resolveSibling("javaw.exe");

				if (Files.isExecutable(windowless)) {
					return windowless;
				}
			}

			if (Files.isExecutable(path)) {
				return path;
			}
		}

		Path home = Path.of(System.getProperty("java.home"));
		return home.resolve("bin").resolve(windows ? "javaw.exe" : "java");
	}

	/**
	 * Der Pfad zum eigenen Jar. Im Entwicklungsbetrieb ist das stattdessen ein
	 * Klassenverzeichnis, was für {@code -cp} genauso funktioniert.
	 */
	private static Path findOwnJar() {
		ModContainer container = FabricLoader.getInstance().getModContainer(LogAI.MOD_ID).orElse(null);

		if (container != null) {
			List<Path> origins = container.getOrigin().getPaths();

			if (!origins.isEmpty()) {
				return origins.get(0).toAbsolutePath();
			}
		}

		try {
			var source = CrashWatcher.class.getProtectionDomain().getCodeSource();

			if (source != null && source.getLocation() != null) {
				return new File(source.getLocation().toURI()).toPath().toAbsolutePath();
			}
		} catch (Exception ignored) {
			// Dann gibt es unten null zurück und der Aufrufer meldet es.
		}

		return null;
	}
}
