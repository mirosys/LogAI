package dev.mirosys.logai.client;

import java.util.List;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import dev.mirosys.logai.LogAIRuntime;

/**
 * Wird nach einem Update statt der vollen Einrichtung gezeigt: entweder die bisherigen
 * Einstellungen behalten oder alles noch einmal durchgehen.
 */
public class SetupUpdateScreen extends SetupScreen {
	public SetupUpdateScreen(Screen parent) {
		super(parent, Texts.brand().append(Texts.dim(" - updated")));
	}

	@Override
	protected List<Component> body() {
		return List.of(
				Texts.dim("LogAI is now at version ").append(
						Texts.accent(LogAIRuntime.modVersion())),
				Texts.blank(),
				Texts.term("Your previous setup", ""),
				Texts.dim("AI: ").append(Texts.accent(this.config.provider().displayName())),
				Texts.dim("Copy and open automatically: ").append(
						Texts.accent(this.config.autoOpen ? "on" : "off")),
				Texts.dim("Restart after a crash: ").append(
						Texts.accent(this.config.autoRestart ? "on" : "off")),
				Texts.blank(),
				Texts.warn("Keep it, or go through the setup again to see what changed?"));
	}

	@Override
	protected void init() {
		int centerX = this.width / 2;
		int top = this.buttonTop();

		this.addRenderableWidget(Button
				.builder(Component.literal("Keep my settings"), button -> this.finishSetup())
				.bounds(centerX - 110, top, 220, 20)
				.build());

		this.addRenderableWidget(Button
				.builder(Component.literal("Set it up again"),
						button -> this.minecraft.setScreenAndShow(SetupIntroScreen.create(this.parent)))
				.bounds(centerX - 110, top + 30, 220, 20)
				.build());
	}

	@Override
	public void onClose() {
		// Wegklicken heisst: alles bleibt, wie es war.
		this.finishSetup();
	}
}
