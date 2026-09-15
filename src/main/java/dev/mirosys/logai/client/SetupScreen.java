package dev.mirosys.logai.client;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import dev.mirosys.logai.LogAIRuntime;
import dev.mirosys.logai.config.LogAIConfig;

/**
 * Common ground for the setup steps and the settings sub-screens.
 *
 * <p>The text sits on a dark band like the lists in Minecraft's own menus, rather than
 * floating over the world: over a bright landscape or a shader sky, grey text on nothing
 * is hard to read.
 */
public abstract class SetupScreen extends Screen {
	private static final int BAND_FILL = 0xA0000000;
	private static final int BAND_EDGE = 0xFF000000;
	private static final int TITLE_COLOR = 0xFFFFFFFF;
	private static final int BODY_COLOR = 0xFFA0A0A0;
	private static final int FOOTER_COLOR = 0xFF808080;

	private static final int BAND_HALF_WIDTH = 200;
	private static final int LINE_HEIGHT = 12;

	protected final Screen parent;
	protected final LogAIConfig config = LogAIRuntime.config();

	protected SetupScreen(Screen parent, String title) {
		this(parent, Component.literal(title));
	}

	protected SetupScreen(Screen parent, Component title) {
		super(title);
		this.parent = parent;
	}

	/** The lines shown on the band. */
	protected abstract List<Component> body();

	/** Optional lines right above the buttons, set a little quieter. */
	protected List<Component> footer() {
		return List.of();
	}

	/** Leaves the setup and remembers that it is done for this version. */
	protected void finishSetup() {
		this.config.markSetupDone(LogAIRuntime.modVersion());
		this.minecraft.setScreenAndShow(this.parent);
	}

	/** Where the buttons start. */
	protected int buttonTop() {
		return this.height - 92;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);

		int centerX = this.width / 2;
		List<Component> body = this.body();

		int bandTop = 46;
		int bandBottom = bandTop + 14 + body.size() * LINE_HEIGHT;
		drawBand(graphics, centerX - BAND_HALF_WIDTH, bandTop, centerX + BAND_HALF_WIDTH, bandBottom);

		graphics.centeredText(this.font, this.title, centerX, 26, TITLE_COLOR);

		int y = bandTop + 9;

		for (Component line : body) {
			graphics.centeredText(this.font, line, centerX, y, BODY_COLOR);
			y += LINE_HEIGHT;
		}

		List<Component> footer = this.footer();

		if (!footer.isEmpty()) {
			int footerY = this.buttonTop() - 12 - footer.size() * LINE_HEIGHT;

			for (Component line : footer) {
				graphics.centeredText(this.font, line, centerX, footerY, FOOTER_COLOR);
				footerY += LINE_HEIGHT;
			}
		}
	}

	private static void drawBand(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2) {
		graphics.fill(x1, y1, x2, y2, BAND_FILL);
		graphics.fill(x1, y1, x2, y1 + 1, BAND_EDGE);
		graphics.fill(x1, y2 - 1, x2, y2, BAND_EDGE);
	}
}
