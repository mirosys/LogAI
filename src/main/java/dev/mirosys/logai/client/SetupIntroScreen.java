package dev.mirosys.logai.client;

import java.util.List;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Schritt 1: erklären, was der Mod tut, und die KI auswählen lassen.
 *
 * <p>Die Auswahl ist der Weiter-Knopf: ein Klick auf eine KI übernimmt sie und führt
 * direkt zum nächsten Schritt.
 */
public final class SetupIntroScreen {
	private SetupIntroScreen() {
	}

	private static final List<Component> BODY = List.of(
			Texts.accent("LogAI watches this Minecraft instance from a separate process."),
			Texts.blank(),
			Texts.dim("When the game crashes it collects the log, puts it on your clipboard"),
			Texts.dim("and offers to open the AI of your choice, so it can tell you what broke."),
			Texts.blank(),
			Texts.term("Nothing is uploaded by the mod itself.", " It never asks for a password"),
			Texts.dim("or an API key. You paste the log yourself, into your own account."),
			Texts.blank(),
			Texts.warn("Which AI do you want to use?"));

	/** Baut Schritt 1 als KI-Auswahl, die anschliessend zu Schritt 2 führt. */
	public static Screen create(Screen parent) {
		return new AiChooserScreen(parent, Texts.brand(), BODY,
				provider -> net.minecraft.client.Minecraft.getInstance()
						.setScreenAndShow(new SetupSignInScreen(parent)));
	}
}
