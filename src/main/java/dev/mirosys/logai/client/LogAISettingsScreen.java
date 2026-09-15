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
import dev.mirosys.logai.watchdog.UriOpener;

/**
 * The settings screen, reached through Mod Menu.
 *
 * <p>The two choices with more than two options - the AI and the triggers - live on
 * sub-screens, so nothing here has to be clicked through.
 */
public class LogAISettingsScreen extends Screen {
	private static final int ROW_HEIGHT = 24;
	private static final int WIDTH = 250;
	private static final int TOP = 60;

	private static final List<Component> AI_CHOOSER_BODY = List.of(
			Texts.dim("The AI that opens when Minecraft crashes."),
			Texts.blank(),
			Texts.dim("You need to be signed in to it in your browser -"),
			Texts.dim("LogAI never handles your account itself."));

	private final Screen parent;
	private final LogAIConfig config = LogAIRuntime.config();

	public LogAISettingsScreen(Screen parent) {
		super(Component.literal("LogAI"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int left = this.width / 2 - WIDTH / 2;

		this.addRenderableWidget(Button
				.builder(Texts.dim("AI of choice:  ").append(Texts.emphasis(this.config.provider().displayName())),
						button -> this.minecraft.setScreenAndShow(new AiChooserScreen(this,
								Component.literal("AI of choice"), AI_CHOOSER_BODY,
								provider -> this.minecraft.setScreenAndShow(this))))
				.bounds(left, TOP, WIDTH, 20)
				.build());

		this.addRenderableWidget(Button
				.builder(Texts.dim("What gets you a report:  ").append(Texts.emphasis(enabledTriggers() + " of 4")),
						button -> this.minecraft.setScreenAndShow(TriggerChooserScreen.forSettings(this)))
				.bounds(left, TOP + ROW_HEIGHT, WIDTH, 20)
				.build());

		this.addRenderableWidget(CycleButton.onOffBuilder(this.config.autoOpen)
				.create(left, TOP + 2 * ROW_HEIGHT, WIDTH, 20,
						Component.literal("Copy and open automatically"),
						(button, value) -> this.config.autoOpen = value));

		this.addRenderableWidget(CycleButton.onOffBuilder(this.config.autoRestart)
				.create(left, TOP + 3 * ROW_HEIGHT, WIDTH, 20,
						Component.literal("Restart after a crash"),
						(button, value) -> this.config.autoRestart = value));

		this.addRenderableWidget(Button
				.builder(Component.literal("Make sure I am signed in"),
						button -> UriOpener.open(this.config.provider().loginUrl()))
				.bounds(left, TOP + 4 * ROW_HEIGHT, WIDTH, 20)
				.build());

		this.addRenderableWidget(Button
				.builder(Component.literal("Run setup again"), button -> {
					this.config.save();
					this.minecraft.setScreenAndShow(SetupIntroScreen.create(this.parent));
				})
				.bounds(left, TOP + 5 * ROW_HEIGHT, WIDTH, 20)
				.build());

		boolean linkSet = this.config.launchLink != null && !this.config.launchLink.isBlank();
		this.addRenderableWidget(Button
				.builder(Texts.dim("Experimental").append(linkSet ? Texts.emphasis(":  1 in use") : Component.empty()),
						button -> {
							this.config.save();
							this.minecraft.setScreenAndShow(new ExperimentalScreen(this));
						})
				.bounds(left, TOP + 6 * ROW_HEIGHT, WIDTH, 20)
				.build());

		this.addRenderableWidget(Button
				.builder(Texts.warn("Test: crash this game now"), button -> {
					// halt() skips the orderly shutdown, so to the watcher this looks exactly
					// like a real hard crash.
					this.config.save();
					LogAIRuntime.markTestCrash();
					Runtime.getRuntime().halt(1);
				})
				.bounds(left, TOP + 8 * ROW_HEIGHT, WIDTH, 20)
				.build());

		this.addRenderableWidget(Button
				.builder(CommonComponents.GUI_DONE, button -> this.onClose())
				.bounds(left, this.height - 34, WIDTH, 20)
				.build());
	}

	private int enabledTriggers() {
		int count = 0;

		for (boolean on : new boolean[] { this.config.triggerOnCrash, this.config.triggerOnAltF4,
				this.config.triggerOnWindowClose, this.config.triggerOnQuit }) {
			if (on) {
				count++;
			}
		}

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
				centerX, 40, 0xFFA0A0A0);
		graphics.centeredText(this.font,
				Texts.warn("The test button closes Minecraft on purpose. Save your world first."),
				centerX, TOP + 7 * ROW_HEIGHT + 8, 0xFFA0A0A0);
	}
}
