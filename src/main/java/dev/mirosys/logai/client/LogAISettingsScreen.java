package dev.mirosys.logai.client;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import dev.mirosys.logai.LogAIRuntime;
import dev.mirosys.logai.config.LogAIConfig;
import dev.mirosys.logai.watchdog.CrashDialog;

/**
 * Die Einstellungen. Erreichbar über Mod Menu.
 *
 * <p>Die beiden Auswahlen mit mehr als zwei Möglichkeiten - KI und Auslöser - liegen in
 * eigenen Untermenüs, damit hier nichts durchgeklickt werden muss.
 */
public class LogAISettingsScreen extends Screen {
	private static final int ROW_HEIGHT = 24;
	private static final int WIDTH = 250;

	private final Screen parent;
	private final LogAIConfig config = LogAIRuntime.config();

	public LogAISettingsScreen(Screen parent) {
		super(Component.literal("LogAI"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int left = this.width / 2 - WIDTH / 2;
		int top = 60;

		this.addRenderableWidget(Button
				.builder(Texts.dim("AI of choice:  ").append(
						Texts.accent(this.config.provider().displayName())),
						button -> this.minecraft.setScreenAndShow(new AiChooserScreen(this,
								"AI of choice", CHOOSER_BODY, provider -> this.minecraft
										.setScreenAndShow(this))))
				.bounds(left, top, WIDTH, 20)
				.build());

		this.addRenderableWidget(Button
				.builder(Texts.dim("What gets you a report:  ").append(
						Texts.accent(this.enabledTriggerCount() + " of 4")),
						button -> this.minecraft.setScreenAndShow(
								TriggerChooserScreen.forSettings(this)))
				.bounds(left, top + ROW_HEIGHT, WIDTH, 20)
				.build());

		this.addRenderableWidget(CycleButton.onOffBuilder(this.config.autoOpen)
				.create(left, top + 2 * ROW_HEIGHT, WIDTH, 20,
						Component.literal("Copy and open automatically"),
						(button, value) -> this.config.autoOpen = value));

		this.addRenderableWidget(CycleButton.onOffBuilder(this.config.autoRestart)
				.create(left, top + 3 * ROW_HEIGHT, WIDTH, 20,
						Component.literal("Restart after a crash"),
						(button, value) -> this.config.autoRestart = value));

		this.addRenderableWidget(Button
				.builder(Component.literal("Make sure I am signed in"),
						button -> CrashDialog.openUri(this.config.provider().loginUrl()))
				.bounds(left, top + 4 * ROW_HEIGHT, WIDTH, 20)
				.build());

		this.addRenderableWidget(Button
				.builder(Component.literal("Run setup again"), button -> {
					this.config.save();
					this.minecraft.setScreenAndShow(SetupIntroScreen.create(this.parent));
				})
				.bounds(left, top + 5 * ROW_HEIGHT, WIDTH, 20)
				.build());

		this.addRenderableWidget(Button
				.builder(Texts.dim("Experimental").append(
						this.config.launchLink == null || this.config.launchLink.isBlank()
								? Component.empty()
								: Texts.accent(":  1 in use")),
						button -> {
							this.config.save();
							this.minecraft.setScreenAndShow(new ExperimentalScreen(this));
						})
				.bounds(left, top + 6 * ROW_HEIGHT, WIDTH, 20)
				.build());

		this.addRenderableWidget(Button
				.builder(Texts.warn("Test: crash this game now"), button -> {
					// halt() umgeht das geordnete Herunterfahren und sieht fuer den Watcher
					// deshalb aus wie ein echter harter Absturz.
					this.config.save();
					LogAIRuntime.markTestCrash();
					Runtime.getRuntime().halt(1);
				})
				.bounds(left, top + 8 * ROW_HEIGHT, WIDTH, 20)
				.build());

		this.addRenderableWidget(Button
				.builder(CommonComponents.GUI_DONE, button -> this.onClose())
				.bounds(left, this.height - 34, WIDTH, 20)
				.build());
	}

	private static final List<Component> CHOOSER_BODY = List.of(
			Texts.dim("The AI that opens when Minecraft crashes."),
			Texts.blank(),
			Texts.dim("You need to be signed in to it in your browser -"),
			Texts.dim("LogAI never handles your account itself."));

	private int enabledTriggerCount() {
		int count = 0;
		count += this.config.triggerOnCrash ? 1 : 0;
		count += this.config.triggerOnAltF4 ? 1 : 0;
		count += this.config.triggerOnWindowClose ? 1 : 0;
		count += this.config.triggerOnQuit ? 1 : 0;
		return count;
	}

	@Override
	public void onClose() {
		this.config.save();
		this.minecraft.setScreenAndShow(this.parent);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);

		int centerX = this.width / 2;
		graphics.centeredText(this.font, this.title, centerX, 26, 0xFFFFFFFF);
		graphics.centeredText(this.font,
				Texts.dim("Settings are stored per instance, in config/logai.json"),
				centerX, 40, 0xFF9A9AA6);
		graphics.centeredText(this.font,
				Texts.warn("The test button closes Minecraft on purpose. Save your world first."),
				centerX, 60 + 7 * ROW_HEIGHT + 8, 0xFFFFAA00);
	}
}
