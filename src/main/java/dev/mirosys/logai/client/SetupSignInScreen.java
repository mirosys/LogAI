package dev.mirosys.logai.client;

import java.util.List;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import dev.mirosys.logai.watchdog.CrashDialog;

/**
 * Schritt 2: darauf bestehen, dass der Nutzer angemeldet ist.
 *
 * <p>Wer im Absturz-Moment auf einer Anmeldeseite landet, hat vom Mod nichts - deshalb
 * ist dieser Schritt eine bewusste Entscheidung und kein beiläufiger Hinweis.
 */
public class SetupSignInScreen extends SetupScreen {
	/** Ob die Anmeldeseite bereits geöffnet wurde. Danach ändert sich die Auswahl. */
	private boolean openedLoginPage;

	public SetupSignInScreen(Screen parent) {
		super(parent, "Sign in first");
	}

	@Override
	protected List<Component> body() {
		String ai = this.config.provider().displayName();

		if (this.openedLoginPage) {
			return List.of(
					Texts.accent(ai + " should now be open in your browser."),
					Texts.blank(),
					Texts.dim("Sign in there, then come back and finish the setup."),
					Texts.dim("You will not be asked again until the next update."));
		}

		return List.of(
				Texts.term("You picked ", "").append(Texts.accent(ai)),
				Texts.blank(),
				Texts.warn("LogAI is useless in the moment it matters if you are not signed in."),
				Texts.dim("The crash would send you to a login page instead of an answer."),
				Texts.blank(),
				Texts.dim("Take twenty seconds now and make sure you are signed in."));
	}

	@Override
	protected void init() {
		int centerX = this.width / 2;
		int top = this.buttonTop();

		if (this.openedLoginPage) {
			this.addRenderableWidget(Button
					.builder(Component.literal("Open it again"),
							button -> CrashDialog.openUri(this.config.provider().loginUrl()))
					.bounds(centerX - 130, top, 260, 20)
					.build());

			this.addRenderableWidget(Button
					.builder(Component.literal("Done, I am signed in"), button -> this.next())
					.bounds(centerX - 130, top + 30, 260, 20)
					.build());
			return;
		}

		this.addRenderableWidget(Button
				.builder(Component.literal("No, let me sign in"), button -> {
					CrashDialog.openUri(this.config.provider().loginUrl());
					this.openedLoginPage = true;
					this.rebuildWidgets();
				})
				.bounds(centerX - 130, top, 260, 20)
				.build());

		this.addRenderableWidget(Button
				.builder(Component.literal("Yes, I am already signed in (not recommended)"),
						button -> this.next())
				.bounds(centerX - 130, top + 24, 260, 20)
				.build());

		this.addRenderableWidget(Button
				.builder(Component.literal("Back"),
						button -> this.minecraft.setScreenAndShow(SetupIntroScreen.create(this.parent)))
				.bounds(centerX - 130, top + 54, 260, 20)
				.build());
	}

	private void next() {
		this.config.save();
		this.minecraft.setScreenAndShow(TriggerChooserScreen.forSetup(this.parent));
	}

	@Override
	public void onClose() {
		// Wegklicken zaehlt nicht als erledigt, beim naechsten Start kommt die Frage wieder.
		this.config.save();
		this.minecraft.setScreenAndShow(this.parent);
	}
}
