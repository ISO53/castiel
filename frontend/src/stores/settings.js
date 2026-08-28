import { defineStore } from "pinia";

const API_BASE_URL = `${window.location.origin}/api/settings`;

const LLAMA_CPP_ID = "llama.cpp";
const OLLAMA_ID = "ollama";
const OPENROUTER_ID = "openrouter";
/** OpenRouter's endpoint is fixed — users only provide their API key. */
const OPENROUTER_API_URL = "https://openrouter.ai/api/v1";

async function readError(response) {
	const body = await response.text();
	try {
		const json = JSON.parse(body);
		return (
			json.message ||
			json.detail ||
			json.error ||
			body ||
			`Request failed with status ${response.status}`
		);
	} catch {
		return body || `Request failed with status ${response.status}`;
	}
}

/** Default form values for the llama.cpp settings section (UI-only). */
export function defaultLlamaCppProvider() {
	return {
		type: "OPENAI_COMPATIBLE",
		apiUrl: "http://localhost:8080",
		contextWindow: 8192,
		apiKey: "",
	};
}

/** Default form values for the Ollama settings section (UI-only). */
export function defaultOllamaProvider() {
	return {
		type: "OLLAMA",
		apiUrl: "http://localhost:11434",
		contextWindow: 8192,
	};
}

/** Default form values for the OpenRouter settings section (UI-only). */
export function defaultOpenRouterProvider() {
	return {
		type: "OPENAI_COMPATIBLE",
		apiUrl: OPENROUTER_API_URL,
		apiKey: "",
	};
}

export const useSettingsStore = defineStore("settings", {
	state: () => ({
		providers: {},
		activeProviderId: null,
		loaded: false,
	}),
	getters: {
		llamaCpp(state) {
			return {
				...defaultLlamaCppProvider(),
				...(state.providers[LLAMA_CPP_ID] ?? {}),
			};
		},
		ollama(state) {
			return {
				...defaultOllamaProvider(),
				...(state.providers[OLLAMA_ID] ?? {}),
			};
		},
		openrouter(state) {
			return {
				...defaultOpenRouterProvider(),
				...(state.providers[OPENROUTER_ID] ?? {}),
			};
		},
	},
	actions: {
		async fetch() {
			const response = await fetch(API_BASE_URL);
			if (!response.ok) {
				throw new Error(await readError(response));
			}
			const data = await response.json();
			this.providers = data.providers ?? {};
			this.activeProviderId = data.activeProviderId ?? null;
			this.loaded = true;
			return data;
		},
		/**
		 * Upserts a provider and health-checks it.
		 * @returns {{ ok: boolean, message: string }}
		 */
		async connectProvider(id, config) {
			const response = await fetch(
				`${API_BASE_URL}/providers/${encodeURIComponent(id)}/connect`,
				{
					method: "POST",
					headers: { "Content-Type": "application/json" },
					body: JSON.stringify(config),
				},
			);
			if (!response.ok) {
				throw new Error(await readError(response));
			}
			const data = await response.json();
			this.providers = data.settings?.providers ?? this.providers;
			this.activeProviderId = data.settings?.activeProviderId ?? this.activeProviderId;
			this.loaded = true;
			return data.health;
		},
		connectLlamaCpp(form) {
			return this.connectProvider(LLAMA_CPP_ID, {
				type: "OPENAI_COMPATIBLE",
				apiUrl: form.apiUrl,
				contextWindow: Number(form.contextWindow) || 8192,
				apiKey: form.apiKey ?? "",
			});
		},
		connectOllama(form) {
			return this.connectProvider(OLLAMA_ID, {
				type: "OLLAMA",
				apiUrl: form.apiUrl,
				contextWindow: Number(form.contextWindow) || 8192,
			});
		},
		connectOpenRouter(form) {
			return this.connectProvider(OPENROUTER_ID, {
				type: "OPENAI_COMPATIBLE",
				apiUrl: OPENROUTER_API_URL,
				apiKey: form.apiKey ?? "",
			});
		},
	},
});
