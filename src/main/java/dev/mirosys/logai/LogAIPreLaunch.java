package dev.mirosys.logai;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/**
 * Der früheste Einstiegspunkt, den Fabric anbietet: er läuft, sobald die Mods gefunden
 * sind und noch bevor Minecraft startet.
 *
 * <p>Dadurch überwacht der Watcher auch den Ladevorgang selbst - also den Abschnitt, in
 * dem Abstürze und Hänger am häufigsten passieren.
 */
public final class LogAIPreLaunch implements PreLaunchEntrypoint {
	@Override
	public void onPreLaunch() {
		LogAIRuntime.bootstrap();
	}
}
