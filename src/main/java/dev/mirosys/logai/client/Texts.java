package dev.mirosys.logai.client;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Small helpers for the screen text, using only the colours Minecraft's own menus use:
 * white for what matters, grey for the rest, yellow for the occasional hint.
 */
public final class Texts {
	private Texts() {
	}

	public static Component blank() {
		return Component.empty();
	}

	/** Plain explanatory text. */
	public static MutableComponent dim(String text) {
		return Component.literal(text).withStyle(ChatFormatting.GRAY);
	}

	/** Something the reader should not skim past. */
	public static MutableComponent emphasis(String text) {
		return Component.literal(text).withStyle(ChatFormatting.WHITE);
	}

	/** A hint or a warning, in the yellow Minecraft itself uses for those. */
	public static MutableComponent warn(String text) {
		return Component.literal(text).withStyle(ChatFormatting.YELLOW);
	}

	/** A white term followed by its grey explanation, like "Crash - the game died". */
	public static MutableComponent term(String highlighted, String rest) {
		return emphasis(highlighted).append(dim(rest));
	}

	/** The setup title: the name in white, the credit in grey. */
	public static MutableComponent brand() {
		return emphasis("LogAI").append(dim(" - by mirosys"));
	}
}
