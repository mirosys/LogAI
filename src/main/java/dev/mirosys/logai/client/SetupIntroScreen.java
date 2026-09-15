package dev.mirosys.logai.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Step 1: explain what the mod does and let the user pick an AI. Picking one is the
 * "continue" button.
 */
public final class SetupIntroScreen {
	private static final List<Component> BODY = List.of(
			Texts.emphasis("LogAI watches this Minecraft instance from a separate process."),
			Texts.blank(),
			Texts.dim("When the game crashes it collects the log, puts it on your clipboard"),
			Texts.dim("and offers to open the AI of your choice, so it can tell you what broke."),
			Texts.blank(),
			Texts.term("Nothing is uploaded by the mod itself.", " It never asks for a password"),
			Texts.dim("or an API key. You paste the log yourself, into your own account."),
			Texts.blank(),
			Texts.warn("Which AI do you want to use?"));

	private SetupIntroScreen() {
	}

	public static Screen create(Screen parent) {
		return new AiChooserScreen(parent, Texts.brand(), BODY,
				provider -> Minecraft.getInstance().setScreenAndShow(new SetupSignInScreen(parent)));
	}
}
