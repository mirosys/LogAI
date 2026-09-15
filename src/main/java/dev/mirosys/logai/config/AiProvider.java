package dev.mirosys.logai.config;

/**
 * The AI services the user can pick from.
 *
 * <p>The watcher process loads this class too, so it must not reference Minecraft or any
 * other library.
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

	/** Opens a fresh chat, not any particular conversation. */
	public String newChatUrl() {
		return newChatUrl;
	}

	/** The sign-in page. Some services have none; there the chat itself prompts. */
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
