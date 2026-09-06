package dev.mirosys.logai.client;

import java.util.List;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Einstellungen, die nur bei bestimmten Launchern funktionieren und deshalb nicht in die
 * regulären gehören.
 */
public class ExperimentalScreen extends SetupScreen {
	private EditBox launchLink;

	public ExperimentalScreen(Screen parent) {
		super(parent, "Experimental");
	}

	@Override
	protected List<Component> body() {
		return List.of(
				Texts.warn("These only work with some launchers. Leave them empty if unsure."),
				Texts.blank(),
				Texts.term("Restart through the launcher.", " Normally LogAI restarts Minecraft"),
				Texts.dim("itself, which works everywhere - but your launcher then no longer"),
				Texts.dim("knows the game is running and shows the instance as stopped."),
				Texts.blank(),
				Texts.dim("If your launcher can be asked to start an instance by link, put that"),
				Texts.dim("link here and LogAI will use it instead. The launcher does the work,"),
				Texts.dim("so its own display stays correct."),
				Texts.blank(),
				Texts.accent("Modrinth App:  modrinth://launch/instance/<your instance id>"));
	}

	@Override
	protected int buttonTop() {
		return this.height - 84;
	}

	@Override
	protected void init() {
		int centerX = this.width / 2;
		int top = this.buttonTop();

		this.launchLink = new EditBox(this.font, centerX - 170, top, 340, 20,
				Component.literal("Launch link"));
		this.launchLink.setMaxLength(512);
		this.launchLink.setValue(this.config.launchLink == null ? "" : this.config.launchLink);
		this.launchLink.setResponder(value -> this.config.launchLink = value.strip());
		this.addRenderableWidget(this.launchLink);

		this.addRenderableWidget(Button
				.builder(Component.literal("Clear"), button -> {
					this.launchLink.setValue("");
					this.config.launchLink = "";
				})
				.bounds(centerX - 170, top + 26, 165, 20)
				.build());

		this.addRenderableWidget(Button
				.builder(CommonComponents.GUI_DONE, button -> this.onClose())
				.bounds(centerX + 5, top + 26, 165, 20)
				.build());
	}

	@Override
	protected List<Component> footer() {
		return List.of(Texts.dim("Empty means LogAI restarts the game on its own."));
	}

	@Override
	public void onClose() {
		this.config.save();
		this.minecraft.setScreenAndShow(this.parent);
	}
}
