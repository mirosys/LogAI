package dev.mirosys.logai.watchdog;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import dev.mirosys.logai.LogAI;

/**
 * Spawns the watcher process. Runs inside Minecraft and is the only place where the mod
 * side and the watcher side meet.
 */
public final class WatcherLauncher {
	private WatcherLauncher() {
	}

	public static void start(WatchSession session, Path sessionFile) throws IOException {
		Path classpath = findOwnJar();

		if (classpath == null) {
			throw new IOException("could not locate the LogAI jar to launch the watcher from");
		}

		session.write(sessionFile);

		// The watcher sleeps most of the time; a small heap is plenty.
		List<String> command = List.of(
				findJavaBinary().toString(),
				"-Xmx64M",
				"-cp", classpath.toString(),
				CrashWatcher.class.getName(),
				sessionFile.toAbsolutePath().toString());

		ProcessBuilder builder = new ProcessBuilder(command);
		builder.directory(session.gameDir.toFile());
		builder.redirectOutput(ProcessBuilder.Redirect.to(sessionFile.resolveSibling("watcher.log").toFile()));
		builder.redirectErrorStream(true);

		Process process = builder.start();
		LogAI.LOGGER.info("Watcher process started (pid {}), watching Minecraft pid {}",
				process.pid(), session.pid);
	}

	/**
	 * Use the JVM Minecraft itself runs on, so the Java version is guaranteed to match.
	 * On Windows prefer javaw so no console window flashes up.
	 */
	private static Path findJavaBinary() {
		String current = ProcessHandle.current().info().command().orElse(null);

		if (current != null) {
			Path path = Path.of(current);

			if (Os.isWindows()) {
				Path windowless = path.resolveSibling("javaw.exe");

				if (Files.isExecutable(windowless)) {
					return windowless;
				}
			}

			if (Files.isExecutable(path)) {
				return path;
			}
		}

		return Path.of(System.getProperty("java.home"), "bin", Os.isWindows() ? "javaw.exe" : "java");
	}

	/**
	 * The path to our own jar. In a development environment this is a class directory
	 * instead, which works just as well on the classpath.
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
			// The caller reports the null.
		}

		return null;
	}
}
