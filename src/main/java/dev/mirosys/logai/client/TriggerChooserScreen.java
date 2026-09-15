package dev.mirosys.logai.client;

import java.util.List;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * The four kinds of shutdown, each with its own on/off switch.
 *
 * <p>Used twice: as the last setup step and as a settings sub-screen. The only difference
 * is what the final button says and does.
 */
public class TriggerChooserScreen extends SetupScreen {
	private static final int ROW_HEIGHT = 24;
	private static final int ROWS = 4;
	private static final int WIDTH = 250;

	private final Component doneLabel;
	private final boolean finishesSetup;

	private TriggerChooserScreen(Screen parent, Component doneLabel, boolean finishesSetup) {
		super(parent, "What should get you a report");
		this.doneLabel = doneLabel;
		this.finishesSetup = finishesSetup;
	}

	/** Last setup step: the button leads into the game. */
	public static TriggerChooserScreen forSetup(Screen parent) {
		return new TriggerChooserScreen(parent, Component.literal("Finish and play"), true);
	}

	/** Settings sub-screen: the button leads back. */
	public static TriggerChooserScreen forSettings(Screen parent) {
		return new TriggerChooserScreen(parent, CommonComponents.GUI_DONE, false);
	}

	@Override
	protected List<Component> body() {
		return List.of(
				Texts.emphasis("Minecraft can end in four ways, and LogAI can tell them apart."),
				Texts.blank(),
				Texts.term("Crash", " - the game died on its own, or was killed while frozen."),
				Texts.term("Alt+F4", " - recognised by the key press, as long as the game still"),
				Texts.dim("            responded to input at that moment."),
				Texts.term("Window closed", " - the X on the window, or the task manager. Those two"),
				Texts.dim("            send the exact same signal, so nothing can tell them apart."),
				Texts.term("Quit", " - the quit button inside the game. Almost always harmless."));
	}

	@Override
	protected int buttonTop() {
		return this.height - 40 - ROWS * ROW_HEIGHT;
	}

	@Override
	protected void init() {
		int left = this.width / 2 - WIDTH / 2;
		int top = this.buttonTop();

		this.addRenderableWidget(CycleButton.onOffBuilder(this.config.triggerOnCrash)
				.create(left, top, WIDTH, 20,
						Texts.emphasis("Crashes").append(Texts.dim(" (recommended)")),
						(button, value) -> this.config.triggerOnCrash = value));

		this.addRenderableWidget(CycleButton.onOffBuilder(this.config.triggerOnAltF4)
				.create(left, top + ROW_HEIGHT, WIDTH, 20, Component.literal("Alt+F4"),
						(button, value) -> this.config.triggerOnAltF4 = value));

		this.addRenderableWidget(CycleButton.onOffBuilder(this.config.triggerOnWindowClose)
				.create(left, top + 2 * ROW_HEIGHT, WIDTH, 20,
						Component.literal("Window closed / task manager"),
						(button, value) -> this.config.triggerOnWindowClose = value));

		this.addRenderableWidget(CycleButton.onOffBuilder(this.config.triggerOnQuit)
				.create(left, top + 3 * ROW_HEIGHT, WIDTH, 20,
						Texts.emphasis("Normal quits").append(Texts.warn(" (not recommended)")),
						(button, value) -> this.config.triggerOnQuit = value));

		this.addRenderableWidget(Button
				.builder(this.doneLabel, button -> this.leave())
				.bounds(left, this.height - 34, WIDTH, 20)
				.build());
	}

	@Override
	protected List<Component> footer() {
		if (!this.finishesSetup) {
			return List.of();
		}

		return List.of(
				Texts.dim("You can change all of this later, and pick a different AI:"),
				Texts.emphasis("Mods -> LogAI -> the settings button")
						.append(Texts.dim("  (needs the Mod Menu mod)")));
	}

	private void leave() {
		if (this.finishesSetup) {
			this.finishSetup();
			return;
		}

		this.config.save();
		this.minecraft.setScreenAndShow(this.parent);
	}

	@Override
	public void onClose() {
		// The defaults are sensible, so closing may pass here.
		this.leave();
	}
}
