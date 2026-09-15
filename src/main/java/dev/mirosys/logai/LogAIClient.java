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
 * Everything that needs Minecraft classes. The crash reporter itself is already running
 * by the time this is reached, see {@link LogAIPreLaunch}.
 */
public final class LogAIClient implements ClientModInitializer {
	/**
	 * How long a seen Alt+F4 counts as the explanation for a close. Between the key press
	 * and the end of shutdown, a big modpack easily takes a second or two.
	 */
	private static final long ALT_F4_MEMORY_MILLIS = 4000L;

	private static long altF4SeenAt;
	/** Keeps the setup from reappearing right after it was closed. */
	private static boolean setupShown;

	@Override
	public void onInitializeClient() {
		// Normally long done, but if preLaunch was skipped a late start still beats none.
		LogAIRuntime.bootstrap();

		registerShutdownMarker();
		registerAltF4Watch();
		registerSetupScreen();
	}

	/**
	 * Written on every orderly shutdown, with the kind of shutdown as its content. Alt+F4
	 * counts as orderly too: Minecraft handles it like a normal quit.
	 */
	private static void registerShutdownMarker() {
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
	 * Tells the in-game quit button apart from Alt+F4 and friends.
	 *
	 * <p>{@code Minecraft.stop()} only sets a field of its own, while the window's close
	 * flag is set exclusively by the window system - Alt+F4, the X button, or a close
	 * request from the task manager. Read-only; Minecraft's own window callback is left
	 * alone.
	 */
	private static ShutdownKind detectShutdownKind(Minecraft client) {
		try {
			if (client == null || client.getWindow() == null || !client.getWindow().shouldClose()) {
				return ShutdownKind.QUIT;
			}
		} catch (Throwable ignored) {
			// When in doubt, report rather than stay silent.
			return ShutdownKind.WINDOW_CLOSE;
		}

		// This is as far as it goes: the X button and "End task" send the same message.
		// Only Alt+F4 gave itself away earlier - provided the game was still responding.
		boolean recentAltF4 = altF4SeenAt > 0
				&& System.currentTimeMillis() - altF4SeenAt <= ALT_F4_MEMORY_MILLIS;

		return recentAltF4 ? ShutdownKind.ALT_F4 : ShutdownKind.WINDOW_CLOSE;
	}

	/**
	 * Remembers when Alt+F4 was last held down. The key press is the only thing that
	 * separates Alt+F4 from the X button or the task manager. A frozen game no longer
	 * ticks, so there it stays at the general "closed from outside" - which is also more
	 * honest.
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

	/**
	 * Shows the setup on the first start and after every version change - then only as a
	 * short question whether to keep the existing settings.
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
