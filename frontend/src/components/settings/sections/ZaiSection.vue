<template>
	<ProviderSection
		:connecting="connecting"
		:health="health"
		:error="error"
		:disabled="!form.apiKey.trim()"
		@connect="connect"
	>
		<template #prose>
			<p>Z.ai serves Zhipu's GLM model family. Pick how you pay — the two modes use different endpoints and different keys.</p>
			<ul class="list-disc space-y-1 pl-4">
				<li>
					Pay-as-you-go: create a
					<a class="underline underline-offset-2 hover:text-foreground"
						href="https://z.ai/manage-apikey/apikey-list" target="_blank" rel="noreferrer">Standard API key</a>
					and top up your balance.
				</li>
				<li>
					GLM Coding Plan: subscribe at
					<a class="underline underline-offset-2 hover:text-foreground"
						href="https://z.ai/subscribe" target="_blank" rel="noreferrer">z.ai/subscribe</a>,
					then create the key under
					<a class="underline underline-offset-2 hover:text-foreground"
						href="https://z.ai/manage-apikey/apikey-list" target="_blank" rel="noreferrer">API Keys → Personal Coding Plan</a>
					(Team Plan members: Team Coding Plan → My Plan).
				</li>
				<li>
					The key type — not the endpoint — decides billing. A Standard key sent to the Coding Plan endpoint still bills pay-as-you-go.
				</li>
			</ul>
		</template>

		<SettingsField
			id="zai_usage"
			label="Usage"
			hint="Chooses the endpoint castiel talks to."
		>
			<RadioGroup v-model="form.mode" class="flex gap-4">
				<div class="flex items-center gap-2">
					<RadioGroupItem id="zai_usage_standard" value="standard" />
					<Label for="zai_usage_standard" class="text-sm font-normal">Pay-as-you-go API</Label>
				</div>
				<div class="flex items-center gap-2">
					<RadioGroupItem id="zai_usage_coding" value="coding" />
					<Label for="zai_usage_coding" class="text-sm font-normal">GLM Coding Plan</Label>
				</div>
			</RadioGroup>
		</SettingsField>

		<SettingsField
			id="zai_api_key"
			label="API key"
			:hint="keyHint"
			control-class="w-72"
		>
			<Input id="zai_api_key" v-model="form.apiKey" type="password" placeholder="your z.ai key"
				autocomplete="off" spellcheck="false" />
		</SettingsField>
	</ProviderSection>
</template>

<script setup>
import { computed, reactive, ref } from "vue";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import ProviderSection from "@/components/settings/ProviderSection.vue";
import SettingsField from "@/components/settings/SettingsField.vue";
import { useSettingsStore } from "@/stores/settings";

const settings = useSettingsStore();
const form = reactive({
	apiKey: settings.zai.apiKey ?? "",
	mode: settings.zai.apiUrl?.includes("/coding/paas/v4") ? "coding" : "standard",
});
const connecting = ref(false);
const health = ref(null);
const error = ref("");

const keyHint = computed(() =>
	form.mode === "coding"
		? "Create the key under API Keys → Personal Coding Plan (or Team Coding Plan → My Plan)."
		: "Create a Standard API key at z.ai/manage-apikey/apikey-list.",
);

async function connect() {
	connecting.value = true;
	error.value = "";
	health.value = null;
	try {
		health.value = await settings.connectZai(form);
	} catch (err) {
		error.value = err instanceof Error ? err.message : String(err);
	} finally {
		connecting.value = false;
	}
}
</script>