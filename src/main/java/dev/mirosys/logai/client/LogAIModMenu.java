package dev.mirosys.logai.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Hooks the settings into Mod Menu. Only loaded when Mod Menu is installed. */
public class LogAIModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return LogAISettingsScreen::new;
	}
}
