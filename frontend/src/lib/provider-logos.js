import llamaCppLogo from "@/assets/providers/llama-cpp.svg";
import ollamaLogo from "@/assets/providers/ollama.svg";
import openRouterLogo from "@/assets/providers/open-router.svg";

/**
 * Maps a settings-provider id to its brand logo asset, for display in the
 * Settings sections and the chat model picker. Returns null when the provider
 * has no known brand (generic UI is rendered instead).
 */
const LOGOS = {
	"llama.cpp": llamaCppLogo,
	openrouter: openRouterLogo,
	"open-router": openRouterLogo,
	ollama: ollamaLogo,
};

export function providerLogo(providerId) {
	return LOGOS[String(providerId ?? "").trim().toLowerCase()] ?? null;
}
