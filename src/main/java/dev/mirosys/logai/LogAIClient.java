package dev.mirosys.logai;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;

import com.mojang.blaze3d.platform.InputConstants;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;

import dev.mirosys.logai.client.SetupIntroScreen;
import dev.mirosys.logai.client.SetupUpdateScreen;
import dev.mirosys.logai.config.LogAIConfig;
import dev.mirosys.logai.watchdog.ShutdownKind;

/**
 * Alles, was Minecraft-Klassen braucht. Der eigentliche Absturz-Melder läuft schon
 * vorher, siehe {@link LogAIPreLaunch}.
 */
public final class LogAIClient implements ClientModInitializer {
	/** Verhindert, dass die Einrichtung nach dem Schließen sofort wiederkommt. */
	private static boolean setupShown;

	/**
	 * Wie lange ein gesehener Alt+F4-Griff als Erklärung fürs Schließen gilt. Zwischen
	 * Tastendruck und Ende des Herunterfahrens vergeht bei vielen Mods gut eine Sekunde.
	 */
	private static final long ALT_F4_MEMORY_MILLIS = 4000L;

	private static long altF4SeenAt;

	@Override
	public void onInitializeClient() {
		// Normalerweise ist das längst passiert, aber falls preLaunch übersprungen wurde,
		// ist ein später Start immer noch besser als gar keiner.
		LogAIRuntime.bootstrap();

		registerCleanExitMarker();
		registerAltF4Watch();
		registerSetupScreen();
	}

	/**
	 * Merkt sich, wann Alt+F4 zuletzt gedrückt war.
	 *
	 * <p>Der Tastendruck ist das einzige Signal, das Alt+F4 von einem Klick aufs X oder
	 * vom Task-Manager unterscheidet - danach sind alle drei dieselbe Fenster-Nachricht.
	 * Bei einem eingefrorenen Spiel tickt hier nichts mehr, dann bleibt es beim
	 * allgemeinen "von außen geschlossen", was auch ehrlicher ist.
	 */
	private static void registerAltF4Watch() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.getWindow() == null) {
				return;
			}

			boolean alt = InputConstants.isKeyDown(client.getWindow(), GLFW.GLFW_KEY_LEFT_ALT)
					|| InputConstants.isKeyDown(client.getWindow(), GLFW.GLFW_KEY_RIGHT_ALT);

			if (alt && InputConstants.isKeyDown(client.getWindow(), GLFW.GLFW_KEY_F4)) {
				altF4SeenAt = System.currentTimeMillis();
			}
		});
	}

	public static LogAIConfig config() {
		return LogAIRuntime.config();
	}

	/**
	 * Wird bei jedem geordneten Herunterfahren geschrieben - also auch bei Alt+F4, weil
	 * das in Minecraft ebenfalls ein normales Beenden auslöst.
	 */
	private static void registerCleanExitMarker() {
		Path markerFile = LogAIRuntime.markerFile();

		if (markerFile == null) {
			return;
		}

		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			ShutdownKind kind = detectShutdownKind(client);

			try {
				Files.createDirectories(markerFile.getParent());
				Files.writeString(markerFile, kind.name());
				LogAI.LOGGER.info("Shutting down, LogAI recorded this as {}", kind);
			} catch (IOException e) {
				LogAI.LOGGER.warn("Could not write the shutdown marker", e);
			}
		});
	}

	/**
	 * Unterscheidet den Beenden-Knopf im Spiel von Alt+F4 und Konsorten.
	 *
	 * <p>{@code Minecraft.stop()} setzt nur ein eigenes Feld, während das Schließen-Flag
	 * des Fensters ausschließlich vom Fenstersystem gesetzt wird - also von Alt+F4, dem X
	 * am Fenster oder einem Schließen-Wunsch des Task-Managers. Reines Auslesen, Minecrafts
	 * eigener Fenster-Callback bleibt unangetastet.
	 */
	private static ShutdownKind detectShutdownKind(Minecraft client) {
		try {
			if (client == null || client.getWindow() == null || !client.getWindow().shouldClose()) {
				return ShutdownKind.QUIT;
			}
		} catch (Throwable ignored) {
			// Im Zweifel lieber melden als schweigen.
			return ShutdownKind.WINDOW_CLOSE;
		}

		// Weiter als bis hier lässt sich nicht aufschlüsseln: das X am Fenster und
		// "Task beenden" senden dieselbe Nachricht. Nur Alt+F4 hat sich vorher über die
		// Tastatur verraten - sofern das Spiel da noch auf Eingaben reagiert hat.
		boolean recentAltF4 = altF4SeenAt > 0
				&& System.currentTimeMillis() - altF4SeenAt <= ALT_F4_MEMORY_MILLIS;

		return recentAltF4 ? ShutdownKind.ALT_F4 : ShutdownKind.WINDOW_CLOSE;
	}

	/**
	 * Zeigt die Einrichtung beim ersten Start und nach jedem Versionswechsel - dann
	 * allerdings nur als kurze Rückfrage, ob die bisherigen Einstellungen bleiben sollen.
	 */
	private static void registerSetupScreen() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			LogAIConfig config = LogAIRuntime.config();

			if (setupShown || !config.needsSetup(LogAIRuntime.modVersion())) {
				return;
			}

			if (screen instanceof TitleScreen) {
				setupShown = true;
				client.setScreenAndShow(config.setupCompletedBefore()
						? new SetupUpdateScreen(screen)
						: SetupIntroScreen.create(screen));
			}
		});
	}
}
