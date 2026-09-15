package dev.mirosys.logai.client;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import dev.mirosys.logai.config.AiProvider;

/**
 * The open list of AI services. Clicking one picks it and moves on - the same screen in
 * the setup as in the settings.
 */
public class AiChooserScreen extends SetupScreen {
	private static final int ROW_HEIGHT = 24;

	private final List<Component> body;
	private final Consumer<AiProvider> onPick;

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
		int top = this.buttonTop();
		AiProvider current = this.config.provider();

		AiProvider[] providers = AiProvider.values();

		for (int i = 0; i < providers.length; i++) {
			AiProvider provider = providers[i];
			// Mark the current choice, so the settings show where you stand.
			Component label = provider == current
					? Texts.emphasis(provider.displayName()).append(Texts.dim("  (current)"))
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
