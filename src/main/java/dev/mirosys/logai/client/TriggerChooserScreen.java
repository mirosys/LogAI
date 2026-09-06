package dev.mirosys.logai.client;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Die Liste der Beendigungsarten, jede einzeln an- und abschaltbar.
 *
 * <p>Wird an zwei Stellen benutzt: als letzter Einrichtungsschritt und als Untermenü der
 * Einstellungen. Der einzige Unterschied ist die Beschriftung des Abschluss-Knopfs.
 */
public class TriggerChooserScreen extends SetupScreen {
	private static final int ROW_HEIGHT = 24;
	private static final int ROWS = 4;

	private final Component doneLabel;
	private final boolean finishesSetup;

	private TriggerChooserScreen(Screen parent, Component doneLabel, boolean finishesSetup) {
		super(parent, "What should get you a report");
		this.doneLabel = doneLabel;
		this.finishesSetup = finishesSetup;
	}

	/** Letzter Einrichtungsschritt: der Knopf führt ins Spiel. */
	public static TriggerChooserScreen forSetup(Screen parent) {
		return new TriggerChooserScreen(parent, Component.literal("Finish and play"), true);
	}

	/** Untermenü der Einstellungen: der Knopf führt zurück. */
	public static TriggerChooserScreen forSettings(Screen parent) {
		return new TriggerChooserScreen(parent, CommonComponents.GUI_DONE, false);
	}

	@Override
	protected List<Component> body() {
		return List.of(
				Texts.accent("Minecraft can end in four ways, and LogAI can tell them apart."),
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
		int centerX = this.width / 2;
		int top = this.buttonTop();
		int width = 250;

		this.addRenderableWidget(CycleButton.onOffBuilder(this.config.triggerOnCrash)
				.create(centerX - width / 2, top, width, 20,
						label("Crashes", " (recommended)", ChatFormatting.GREEN),
						(button, value) -> this.config.triggerOnCrash = value));

		this.addRenderableWidget(CycleButton.onOffBuilder(this.config.triggerOnAltF4)
				.create(centerX - width / 2, top + ROW_HEIGHT, width, 20,
						Component.literal("Alt+F4"),
						(button, value) -> this.config.triggerOnAltF4 = value));

		this.addRenderableWidget(CycleButton.onOffBuilder(this.config.triggerOnWindowClose)
				.create(centerX - width / 2, top + 2 * ROW_HEIGHT, width, 20,
						Component.literal("Window closed / task manager"),
						(button, value) -> this.config.triggerOnWindowClose = value));

		this.addRenderableWidget(CycleButton.onOffBuilder(this.config.triggerOnQuit)
				.create(centerX - width / 2, top + 3 * ROW_HEIGHT, width, 20,
						label("Normal quits", " (not recommended)", ChatFormatting.YELLOW),
						(button, value) -> this.config.triggerOnQuit = value));

		this.addRenderableWidget(Button
				.builder(this.doneLabel, button -> this.leave())
				.bounds(centerX - width / 2, this.height - 34, width, 20)
				.build());
	}

	private static Component label(String name, String hint, ChatFormatting hintColour) {
		return Component.literal(name).append(Component.literal(hint).withStyle(hintColour));
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
		// Die Vorgaben sind brauchbar, Wegklicken darf hier also durchgehen.
		this.leave();
	}

	@Override
	protected List<Component> footer() {
		if (!this.finishesSetup) {
			return List.of();
		}

		return List.of(
				Texts.dim("You can change all of this later, and pick a different AI:"),
				Texts.accent("Mods -> LogAI -> the settings button").append(
						Texts.dim("  (needs the Mod Menu mod)")));
	}
}
