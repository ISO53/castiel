import { ref, reactive } from "vue";

export function useAiChat() {
	const backendUrl = ref("http://localhost:8081");
	const isStreaming = ref(false);
	const error = ref(null);
	const messages = ref([]);

	const providerConfig = reactive({
		baseUrl: "http://localhost:8080/v1",
		modelName: "DevQuasar/huihui-ai.Qwen3-0.6B-abliterated-GGUF:Q6_K",
		apiKey: "no-key-required",
		systemPrompt: "You are a helpful and concise AI assistant.",
	});

	let abortController = null;

	async function sendMessage(text) {
		if (!text || !text.trim() || isStreaming.value) return;

		const userText = text.trim();
		error.value = null;

		// 1. Add User Message
		messages.value.push({
			id: Date.now() + "-user",
			role: "user",
			content: userText,
			timestamp: new Date(),
		});

		// 2. Add empty Assistant Message placeholder
		const assistantMsgId = Date.now() + "-assistant";
		const assistantMsg = reactive({
			id: assistantMsgId,
			role: "assistant",
			content: "",
			timestamp: new Date(),
			isThinking: false,
		});
		messages.value.push(assistantMsg);

		isStreaming.value = true;
		abortController = new AbortController();

		try {
			const res = await fetch(`${backendUrl.value}/api/chat/stream`, {
				method: "POST",
				headers: {
					"Content-Type": "application/json",
				},
				body: JSON.stringify({
					userMessage: userText,
					systemPrompt: providerConfig.systemPrompt,
					provider: {
						baseUrl: providerConfig.baseUrl,
						modelName: providerConfig.modelName,
						apiKey: providerConfig.apiKey,
					},
				}),
				signal: abortController.signal,
			});

			if (!res.ok) {
				throw new Error(`Server returned HTTP ${res.status}: ${res.statusText}`);
			}

			const reader = res.body.getReader();
			const decoder = new TextDecoder("utf-8");
			let buffer = "";

			while (true) {
				const { done, value } = await reader.read();
				if (done) break;

				buffer += decoder.decode(value, { stream: true });
				const lines = buffer.split("\n\n");
				buffer = lines.pop(); // keep remainder

				for (const chunk of lines) {
					if (!chunk.trim()) continue;
					const chunkLines = chunk.split("\n");
					let eventType = "message";
					let dataStr = "";

					for (const line of chunkLines) {
						if (line.startsWith("event:")) {
							eventType = line.slice(6).trim();
						} else if (line.startsWith("data:")) {
							// SSE data chunk
							const val = line.slice(5);
							dataStr += val.startsWith(" ") ? val.slice(1) : val;
						}
					}

					if (eventType === "error") {
						throw new Error(dataStr || "Unknown backend error");
					}

					if (dataStr) {
						assistantMsg.content += dataStr;
					}
				}
			}
		} catch (err) {
			if (err.name === "AbortError") {
				assistantMsg.content += " \n\n*[Generation stopped by user]*";
			} else {
				error.value = err.message || "Failed to communicate with AI harness backend";
				assistantMsg.content += `\n\n⚠️ Error: ${error.value}`;
			}
		} finally {
			isStreaming.value = false;
			abortController = null;
		}
	}

	function stopStreaming() {
		if (abortController) {
			abortController.abort();
		}
	}

	function clearMessages() {
		if (isStreaming.value) {
			stopStreaming();
		}
		messages.value = [];
		error.value = null;
	}

	return {
		backendUrl,
		isStreaming,
		error,
		messages,
		providerConfig,
		sendMessage,
		stopStreaming,
		clearMessages,
	};
}
