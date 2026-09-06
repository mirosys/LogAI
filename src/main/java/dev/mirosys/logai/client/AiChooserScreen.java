package dev.mirosys.logai.client;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import dev.mirosys.logai.config.AiProvider;

/**
 * Die offene Liste der KI-Anbieter. Ein Klick wählt aus und geht weiter - dieselbe
 * Darstellung im Einrichtungsschritt wie in den Einstellungen.
 */
public class AiChooserScreen extends SetupScreen {
	private static final int ROW_HEIGHT = 24;

	private final Consumer<AiProvider> onPick;
	private final List<Component> body;

	public AiChooserScreen(Screen parent, String title, List<Component> body,
			Consumer<AiProvider> onPick) {
		this(parent, Component.literal(title), body, onPick);
	}

	public AiChooserScreen(Screen parent, Component title, List<Component> body,
			Consumer<AiProvider> onPick) {
		super(parent, title);
		this.body = body;
		this.onPick = onPick;
	}

	@Override
	protected List<Component> body() {
		return this.body;
	}

	@Override
	protected int buttonTop() {
		return this.height - 26 - AiProvider.values().length * ROW_HEIGHT;
	}

	@Override
	protected void init() {
		int centerX = this.width / 2;
		AiProvider[] providers = AiProvider.values();
		AiProvider current = this.config.provider();
		int top = this.buttonTop();

		for (int i = 0; i < providers.length; i++) {
			AiProvider provider = providers[i];
			// Die aktuelle Wahl wird markiert, damit man in den Einstellungen sieht,
			// wo man gerade steht.
			Component label = provider == current
					? Component.literal(provider.displayName()).withStyle(ChatFormatting.GREEN)
							.append(Texts.dim("  (current)"))
					: Component.literal(provider.displayName());

			this.addRenderableWidget(Button
					.builder(label, button -> {
						this.config.setProvider(provider);
						this.config.save();
						this.onPick.accept(provider);
					})
					.bounds(centerX - 100, top + i * ROW_HEIGHT, 200, 20)
					.build());
		}
	}

	@Override
	public void onClose() {
		this.config.save();
		this.minecraft.setScreenAndShow(this.parent);
	}
}
