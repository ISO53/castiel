package io.github.iso53.castiel.model;

/**
 * LangChain4j transport / client family used to talk to a provider.
 *
 * <p>UI brands (llama.cpp, LM Studio, …) map to a type. Usually
 * {@link #OPENAI_COMPATIBLE}. New protocols add a new enum constant and one
 * factory branch, not a new settings stack.
 */
public enum ProviderType {
	/**
	 * OpenAI Chat Completions + Models API (base URL ends with {@code /v1}).
	 * Covers llama.cpp server, vLLM, LM Studio, and OpenAI itself.
	 */
	OPENAI_COMPATIBLE,

	/**
	 * Native Ollama HTTP API ({@code /api/chat}, {@code /api/tags}) served at the
	 * Ollama root URL (default {@code http://localhost:11434}). No API key required.
	 */
	OLLAMA,

	/**
	 * Z.ai (Zhipu GLM) cloud API. OpenAI Chat Completions dialect at a fixed root URL
	 * that already carries the version segment ({@code /api/paas/v4} or the Coding
	 * Plan's {@code /api/coding/paas/v4}); served from a static catalog since z.ai
	 * exposes no model-listing endpoint usable for key validation.
	 */
	ZAI,
}
