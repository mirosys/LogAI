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
				Texts.dim("link here and LogAI will try it first. If nothing starts within twenty"),
				Texts.dim("seconds, LogAI restarts the game itself, so you are never left waiting."),
				Texts.blank(),
				Texts.accent("Modrinth App:  modrinth://launch/instance/<instance id>"),
				Texts.warn("Not yet confirmed to work - the app accepts the link but may ignore it."));
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
		String value = this.config.launchLink == null ? "" : this.config.launchLink.strip();

		if (value.isEmpty()) {
			return List.of(Texts.dim("Empty means LogAI restarts the game on its own."));
		}

		// Nur eine Kennung ohne Schema ist der häufigste Fehler: das Betriebssystem
		// hielte sie für einen Dateinamen und der Neustart fiele stillschweigend aus.
		if (!value.matches("(?i)[a-z][a-z0-9+.-]*://.+")) {
			return List.of(
					Texts.warn("That is not a link - it needs a scheme, like  name://something"),
					Texts.dim("LogAI will ignore it and restart the game on its own."));
		}

		return List.of(Texts.dim("Looks like a link. LogAI will hand the restart to your launcher."));
	}

	@Override
	public void onClose() {
		this.config.save();
		this.minecraft.setScreenAndShow(this.parent);
	}
}
