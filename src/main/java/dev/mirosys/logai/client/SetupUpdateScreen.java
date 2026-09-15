package dev.mirosys.logai.client;

import java.util.List;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import dev.mirosys.logai.LogAIRuntime;

/**
 * Shown after an update instead of the full setup: keep the existing settings, or go
 * through everything again.
 */
public class SetupUpdateScreen extends SetupScreen {
	public SetupUpdateScreen(Screen parent) {
		super(parent, Texts.brand().append(Texts.dim(" - updated")));
	}

	@Override
	protected List<Component> body() {
		return List.of(
				Texts.dim("LogAI is now at version ").append(Texts.emphasis(LogAIRuntime.modVersion())),
				Texts.blank(),
				Texts.emphasis("Your previous setup"),
				Texts.dim("AI: ").append(Texts.emphasis(this.config.provider().displayName())),
				Texts.dim("Copy and open automatically: ").append(onOff(this.config.autoOpen)),
				Texts.dim("Restart after a crash: ").append(onOff(this.config.autoRestart)),
				Texts.blank(),
				Texts.warn("Keep it, or go through the setup again to see what changed?"));
	}

	private static Component onOff(boolean value) {
		return Texts.emphasis(value ? "on" : "off");
	}

	@Override
	protected void init() {
		int left = this.width / 2 - 110;
		int top = this.buttonTop();

		this.addRenderableWidget(Button
				.builder(Component.literal("Keep my settings"), button -> this.finishSetup())
				.bounds(left, top, 220, 20)
				.build());

		this.addRenderableWidget(Button
				.builder(Component.literal("Set it up again"),
						button -> this.minecraft.setScreenAndShow(SetupIntroScreen.create(this.parent)))
				.bounds(left, top + 30, 220, 20)
				.build());
	}

	@Override
	public void onClose() {
		// Closing means: everything stays as it was.
		this.finishSetup();
	}
}
