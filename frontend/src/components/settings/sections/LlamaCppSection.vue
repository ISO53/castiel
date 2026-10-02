<template>
	<ProviderSection
		:connecting="connecting"
		:health="health"
		:error="error"
		@connect="connect"
	>
		<template #prose>
			<p>
				Run open models locally with llama.cpp's built-in server, or connect to a remote
				llama.cpp server.
			</p>
			<p>To use a local llama.cpp server:</p>
			<ul class="list-disc space-y-1 pl-4">
				<li>
					Install llama.cpp from
					<a class="underline underline-offset-2 hover:text-foreground" href="https://llama.cpp"
						target="_blank" rel="noreferrer">llama.cpp</a>
				</li>
				<li>Start the server in router mode: <code class="text-foreground">llama-server</code></li>
				<li>Click Connect below to start using llama.cpp in castiel</li>
			</ul>
			<p>
				Alternatively, connect to a remote llama.cpp server by specifying its URL and API key
				(set with <code class="text-foreground">--api-key</code>; may not be required):
			</p>
		</template>

		<SettingsField
			id="llamacpp_api_url"
			label="API URL"
			hint="Where castiel reaches llama.cpp."
			control-class="w-72"
		>
			<Input id="llamacpp_api_url" v-model="form.apiUrl" type="url" placeholder="http://localhost:8080"
				autocomplete="off" spellcheck="false" />
		</SettingsField>

		<SettingsField
			id="llamacpp_context"
			label="Context Window"
			hint="Tokens the model reads at once."
			control-class="w-72"
		>
			<Input id="llamacpp_context" v-model.number="form.contextWindow" type="number" min="1"
				placeholder="8192" />
		</SettingsField>

		<SettingsField
			id="llamacpp_api_key"
			label="API key"
			hint="Only if the server sets --api-key."
			control-class="w-72"
		>
			<Input id="llamacpp_api_key" v-model="form.apiKey" type="password" placeholder="sk-..."
				autocomplete="off" spellcheck="false" />
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
const form = reactive({ ...settings.llamaCpp, apiKey: settings.llamaCpp.apiKey ?? "" });
const connecting = ref(false);
const health = ref(null);
const error = ref("");

async function connect() {
	connecting.value = true;
	error.value = "";
	health.value = null;
	try {
		health.value = await settings.connectLlamaCpp(form);
	} catch (err) {
		error.value = err instanceof Error ? err.message : String(err);
	} finally {
		connecting.value = false;
	}
}
</script>
