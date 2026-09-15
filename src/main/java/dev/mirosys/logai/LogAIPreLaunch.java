package dev.mirosys.logai;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/**
 * The earliest entrypoint Fabric offers: it runs once the mods are discovered and before
 * Minecraft starts. That way the watcher also covers the loading phase, which is where
 * crashes and hangs are most common.
 */
public final class LogAIPreLaunch implements PreLaunchEntrypoint {
	@Override
	public void onPreLaunch() {
		LogAIRuntime.bootstrap();
	}
}
