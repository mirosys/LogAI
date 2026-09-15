package dev.mirosys.logai.client;

import java.util.List;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import dev.mirosys.logai.watchdog.UriOpener;

/**
 * Step 2: insist that the user is signed in.
 *
 * <p>Landing on a login page at the moment of a crash makes the whole mod pointless, so
 * this is a deliberate choice, not a passing hint.
 */
public class SetupSignInScreen extends SetupScreen {
	/** Once the login page has been opened, the choices change. */
	private boolean openedLoginPage;

	public SetupSignInScreen(Screen parent) {
		super(parent, "Sign in first");
	}

	@Override
	protected List<Component> body() {
		String ai = this.config.provider().displayName();

		if (this.openedLoginPage) {
			return List.of(
					Texts.emphasis(ai + " should now be open in your browser."),
					Texts.blank(),
					Texts.dim("Sign in there, then come back and finish the setup."),
					Texts.dim("You will not be asked again until the next update."));
		}

		return List.of(
				Texts.dim("You picked ").append(Texts.emphasis(ai)),
				Texts.blank(),
				Texts.warn("LogAI is useless in the moment it matters if you are not signed in."),
				Texts.dim("The crash would send you to a login page instead of an answer."),
				Texts.blank(),
				Texts.dim("Take twenty seconds now and make sure you are signed in."));
	}

	@Override
	protected void init() {
		int left = this.width / 2 - 130;
		int top = this.buttonTop();

		if (this.openedLoginPage) {
			this.addRenderableWidget(Button
					.builder(Component.literal("Open it again"),
							button -> UriOpener.open(this.config.provider().loginUrl()))
					.bounds(left, top, 260, 20)
					.build());

			this.addRenderableWidget(Button
					.builder(Component.literal("Done, I am signed in"), button -> this.next())
					.bounds(left, top + 30, 260, 20)
					.build());
			return;
		}

		this.addRenderableWidget(Button
				.builder(Component.literal("No, let me sign in"), button -> {
					UriOpener.open(this.config.provider().loginUrl());
					this.openedLoginPage = true;
					this.rebuildWidgets();
				})
				.bounds(left, top, 260, 20)
				.build());

		this.addRenderableWidget(Button
				.builder(Component.literal("Yes, I am already signed in (not recommended)"),
						button -> this.next())
				.bounds(left, top + 24, 260, 20)
				.build());

		this.addRenderableWidget(Button
				.builder(Component.literal("Back"),
						button -> this.minecraft.setScreenAndShow(SetupIntroScreen.create(this.parent)))
				.bounds(left, top + 54, 260, 20)
				.build());
	}

	private void next() {
		this.config.save();
		this.minecraft.setScreenAndShow(TriggerChooserScreen.forSetup(this.parent));
	}

	@Override
	public void onClose() {
		// Closing does not count as done; the question comes back on the next start.
		this.config.save();
		this.minecraft.setScreenAndShow(this.parent);
	}
}
