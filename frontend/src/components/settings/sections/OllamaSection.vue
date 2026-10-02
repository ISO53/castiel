<template>
	<ProviderSection
		:connecting="connecting"
		:health="health"
		:error="error"
		@connect="connect"
	>
		<template #prose>
			<p>Run open models locally with Ollama, or connect to a remote Ollama server.</p>
			<p>To use a local Ollama server:</p>
			<ul class="list-disc space-y-1 pl-4">
				<li>
					Install Ollama from
					<a class="underline underline-offset-2 hover:text-foreground" href="https://ollama.com"
						target="_blank" rel="noreferrer">ollama.com</a>
				</li>
				<li>Pull a model: <code class="text-foreground">ollama pull llama3.2</code></li>
				<li>The server starts automatically on port 11434</li>
				<li>Click Connect below to start using Ollama in castiel</li>
			</ul>
			<p>Alternatively, connect to a remote Ollama server by specifying its URL:</p>
		</template>

		<SettingsField
			id="ollama_api_url"
			label="API URL"
			hint="Where castiel reaches Ollama."
			control-class="w-72"
		>
			<Input id="ollama_api_url" v-model="form.apiUrl" type="url" placeholder="http://localhost:11434"
				autocomplete="off" spellcheck="false" />
		</SettingsField>

		<SettingsField
			id="ollama_context"
			label="Context Window"
			hint="Tokens the model reads at once."
			control-class="w-72"
		>
			<Input id="ollama_context" v-model.number="form.contextWindow" type="number" min="1"
				placeholder="8192" />
		</SettingsField>
	</ProviderSection>
</template>

<script setup>
import { reactive, ref } from "vue";
import { Input } from "@/components/ui/input";
import ProviderSection from "@/components/settings/ProviderSection.vue";
import SettingsField from "@/components/settings/SettingsField.vue";
import { useSettingsStore } from "@/stores/settings";

const settings = useSettingsStore();
const form = reactive({ ...settings.ollama });
const connecting = ref(false);
const health = ref(null);
const error = ref("");

async function connect() {
	connecting.value = true;
	error.value = "";
	health.value = null;
	try {
		health.value = await settings.connectOllama(form);
	} catch (err) {
		error.value = err instanceof Error ? err.message : String(err);
	} finally {
		connecting.value = false;
	}
}
</script>
