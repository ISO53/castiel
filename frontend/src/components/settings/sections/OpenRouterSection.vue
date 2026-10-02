<template>
	<ProviderSection
		:connecting="connecting"
		:health="health"
		:error="error"
		:disabled="!form.apiKey.trim()"
		@connect="connect"
	>
		<template #prose>
			<p>One key unlocks models from many upstream providers through OpenRouter.</p>
			<ul class="list-disc space-y-1 pl-4">
				<li>
					Create an API key at
					<a class="underline underline-offset-2 hover:text-foreground"
						href="https://openrouter.ai/settings/keys" target="_blank" rel="noreferrer">openrouter.ai/settings/keys</a>
				</li>
				<li>
					To use your own upstream provider keys (BYOK), add them in the
					<a class="underline underline-offset-2 hover:text-foreground"
						href="https://openrouter.ai/settings/byok" target="_blank" rel="noreferrer">OpenRouter BYOK settings</a>.
					castiel only ever needs the single OpenRouter key.
				</li>
			</ul>
		</template>

		<SettingsField
			id="openrouter_api_key"
			label="API key"
			hint="Create one at openrouter.ai/settings/keys."
			control-class="w-72"
		>
			<Input id="openrouter_api_key" v-model="form.apiKey" type="password" placeholder="sk-or-..."
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
const form = reactive({ apiKey: settings.openrouter.apiKey ?? "" });
const connecting = ref(false);
const health = ref(null);
const error = ref("");

async function connect() {
	connecting.value = true;
	error.value = "";
	health.value = null;
	try {
		health.value = await settings.connectOpenRouter(form);
	} catch (err) {
		error.value = err instanceof Error ? err.message : String(err);
	} finally {
		connecting.value = false;
	}
}
</script>
