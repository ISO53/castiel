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
}
