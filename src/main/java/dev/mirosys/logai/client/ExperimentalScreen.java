package dev.mirosys.logai.client;

import java.util.List;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Settings that only work with some launchers, and therefore do not belong with the
 * regular ones.
 */
public class ExperimentalScreen extends SetupScreen {
	/** Anything that has a scheme, like {@code name://something}. */
	private static final String LINK_PATTERN = "(?i)[a-z][a-z0-9+.-]*://.+";

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
				Texts.emphasis("Modrinth App:  modrinth://launch/instance/<instance id>"),
				Texts.warn("Not yet confirmed to work - the app accepts the link but may ignore it."));
	}

	@Override
	protected int buttonTop() {
		return this.height - 84;
	}

	@Override
	protected void init() {
		int left = this.width / 2 - 170;
		int top = this.buttonTop();

		this.launchLink = new EditBox(this.font, left, top, 340, 20, Component.literal("Launch link"));
		this.launchLink.setMaxLength(512);
		this.launchLink.setValue(this.config.launchLink == null ? "" : this.config.launchLink);
		this.launchLink.setResponder(value -> this.config.launchLink = value.strip());
		this.addRenderableWidget(this.launchLink);

		this.addRenderableWidget(Button
				.builder(Component.literal("Clear"), button -> {
					this.launchLink.setValue("");
					this.config.launchLink = "";
				})
				.bounds(left, top + 26, 165, 20)
				.build());

		this.addRenderableWidget(Button
				.builder(CommonComponents.GUI_DONE, button -> this.onClose())
				.bounds(left + 175, top + 26, 165, 20)
				.build());
	}

	@Override
	protected List<Component> footer() {
		String value = this.config.launchLink == null ? "" : this.config.launchLink.strip();

		if (value.isEmpty()) {
			return List.of(Texts.dim("Empty means LogAI restarts the game on its own."));
		}

		// Just an id without a scheme is the most common mistake: the OS would take it for a
		// file name, and the restart would silently do nothing.
		if (!value.matches(LINK_PATTERN)) {
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
