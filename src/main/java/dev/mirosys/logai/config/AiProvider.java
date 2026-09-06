package dev.mirosys.logai.config;

/**
 * Die auswählbaren KI-Anbieter.
 *
 * <p>Diese Klasse wird auch vom Watcher-Prozess geladen und darf deshalb weder
 * Minecraft- noch sonstige Fremdklassen referenzieren.
 */
public enum AiProvider {
	CLAUDE("Claude", "https://claude.ai/new", "https://claude.ai/login"),
	CHATGPT("ChatGPT", "https://chatgpt.com/", "https://chatgpt.com/auth/login"),
	COPILOT("Copilot", "https://copilot.microsoft.com/", "https://copilot.microsoft.com/"),
	DEEPSEEK("DeepSeek", "https://chat.deepseek.com/", "https://chat.deepseek.com/sign_in"),
	GEMINI("Gemini", "https://gemini.google.com/app", "https://gemini.google.com/app");

	private final String displayName;
	private final String newChatUrl;
	private final String loginUrl;

	AiProvider(String displayName, String newChatUrl, String loginUrl) {
		this.displayName = displayName;
		this.newChatUrl = newChatUrl;
		this.loginUrl = loginUrl;
	}

	public String displayName() {
		return displayName;
	}

	/** Startet einen frischen Chat, keine bestimmte Konversation. */
	public String newChatUrl() {
		return newChatUrl;
	}

	/**
	 * Die Anmeldeseite. Manche Anbieter haben keine eigene, dort führt der Chat selbst
	 * zur Anmeldung.
	 */
	public String loginUrl() {
		return loginUrl;
	}

	public static AiProvider byName(String name, AiProvider fallback) {
		if (name == null) {
			return fallback;
		}

		for (AiProvider provider : values()) {
			if (provider.name().equalsIgnoreCase(name)) {
				return provider;
			}
		}

		return fallback;
	}
}
