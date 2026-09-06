package dev.mirosys.logai.client;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import dev.mirosys.logai.LogAIRuntime;
import dev.mirosys.logai.config.LogAIConfig;

/**
 * Gemeinsame Grundlage der Einrichtungs-Schritte.
 *
 * <p>Der Text steht in einem abgesetzten Kasten statt frei über dem Spielhintergrund -
 * über einer hellen Landschaft oder einem Shader-Himmel ist graue Schrift sonst kaum
 * lesbar.
 */
public abstract class SetupScreen extends Screen {
	/** Füllung des Textkastens: fast deckend, damit die Welt dahinter nicht stört. */
	private static final int PANEL_FILL = 0xE0101014;
	private static final int PANEL_BORDER = 0xFF4C4C55;
	/** Schmaler Farbstreifen oben, damit der Kasten nicht wie ein grauer Block wirkt. */
	private static final int PANEL_ACCENT = 0xFF6EA8FF;

	private static final int PANEL_HALF_WIDTH = 200;
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

	/** Die Zeilen im Textkasten. */
	protected abstract List<Component> body();

	/** Optionale Zeilen direkt über den Schaltflächen, dezenter gesetzt. */
	protected List<Component> footer() {
		return List.of();
	}

	/** Verlässt die Einrichtung und merkt sich, dass sie für diese Version erledigt ist. */
	protected void finishSetup() {
		this.config.markSetupDone(LogAIRuntime.modVersion());
		this.minecraft.setScreenAndShow(this.parent);
	}

	/** Die y-Position, ab der die Schaltflächen stehen. */
	protected int buttonTop() {
		return this.height - 92;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);

		int centerX = this.width / 2;
		List<Component> body = this.body();

		int panelTop = 46;
		int panelBottom = panelTop + 14 + body.size() * LINE_HEIGHT;
		drawPanel(graphics, centerX - PANEL_HALF_WIDTH, panelTop, centerX + PANEL_HALF_WIDTH,
				panelBottom);

		graphics.centeredText(this.font, this.title, centerX, 26, 0xFFFFFFFF);

		int y = panelTop + 9;

		for (Component line : body) {
			graphics.centeredText(this.font, line, centerX, y, 0xFFC6C6D0);
			y += LINE_HEIGHT;
		}

		List<Component> footer = this.footer();

		if (!footer.isEmpty()) {
			int footerY = this.buttonTop() - 12 - footer.size() * LINE_HEIGHT;

			for (Component line : footer) {
				graphics.centeredText(this.font, line, centerX, footerY, 0xFF9A9AA6);
				footerY += LINE_HEIGHT;
			}
		}
	}

	private static void drawPanel(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2) {
		graphics.fill(x1, y1, x2, y2, PANEL_FILL);
		graphics.fill(x1, y1, x2, y1 + 1, PANEL_ACCENT);
		graphics.fill(x1, y2 - 1, x2, y2, PANEL_BORDER);
		graphics.fill(x1, y1, x1 + 1, y2, PANEL_BORDER);
		graphics.fill(x2 - 1, y1, x2, y2, PANEL_BORDER);
	}
}
