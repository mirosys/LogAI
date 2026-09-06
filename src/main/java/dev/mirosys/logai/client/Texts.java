package dev.mirosys.logai.client;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Kleine Helfer für den Fließtext der Bildschirme.
 *
 * <p>Minecraft kann Text nicht umbrechen lassen und nicht fett und normal in einem
 * Aufruf mischen, ohne dass es umständlich wird - das hier nimmt die Umständlichkeit ab.
 */
public final class Texts {
	private Texts() {
	}

	public static Component line(String text) {
		return Component.literal(text);
	}

	/** Leerzeile. */
	public static Component blank() {
		return Component.empty();
	}

	/** Hervorgehobener Anfang, normaler Rest - etwa "Crash - the game died on its own." */
	public static MutableComponent term(String highlighted, String rest) {
		return Component.literal(highlighted).withStyle(ChatFormatting.WHITE)
				.append(Component.literal(rest).withStyle(ChatFormatting.GRAY));
	}

	public static MutableComponent accent(String text) {
		return Component.literal(text).withStyle(ChatFormatting.AQUA);
	}

	public static MutableComponent warn(String text) {
		return Component.literal(text).withStyle(ChatFormatting.YELLOW);
	}

	/** Der Titel der Einrichtungs-Bildschirme: Name kraeftig, Signatur dezent. */
	public static MutableComponent brand() {
		return Component.literal("LogAI").withStyle(ChatFormatting.WHITE)
				.append(Component.literal(" - by mirosys").withStyle(ChatFormatting.GRAY));
	}

	public static MutableComponent dim(String text) {
		return Component.literal(text).withStyle(ChatFormatting.GRAY);
	}
}
