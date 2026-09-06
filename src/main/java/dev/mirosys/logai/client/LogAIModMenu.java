package dev.mirosys.logai.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Hängt die Einstellungen in Mod Menu ein. Wird nur geladen, wenn Mod Menu installiert ist.
 */
public class LogAIModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return LogAISettingsScreen::new;
	}
}
