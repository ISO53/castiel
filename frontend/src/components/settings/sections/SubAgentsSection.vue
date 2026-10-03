<template>
	<div class="space-y-6">
		<!-- Shared by every sub-agent kind below. -->
		<div class="divide-y divide-border">
			<SettingsField
				label="Tool-call rounds"
				hint="Default budget for one sub-agent run (2-64). The agent may request more on a specific call."
			>
				<Input
					id="agent_max_rounds"
					v-model.number="maxRounds"
					type="number"
					min="2"
					max="64"
					class="w-20 text-right"
					placeholder="32"
				/>
			</SettingsField>

			<SettingsField
				label="Run timeout"
				hint="Wall-clock minutes one sub-agent run may take (1-240). Runs work in the background, so this is a safety net against a model that never answers, not a normal limit."
			>
				<Input
					id="agent_timeout_minutes"
					v-model.number="timeoutMinutes"
					type="number"
					min="1"
					max="240"
					class="w-20 text-right"
					placeholder="30"
				/>
			</SettingsField>
		</div>

		<!-- Heading and description sit flush left; only the Provider/Model rows are indented. -->
		<div class="space-y-6">
			<!-- Worker sub-agent -->
			<div>
				<p class="mb-1 text-xs font-semibold text-foreground">
					Worker sub-agent
				</p>
				<p class="mb-1.5 text-[11px] leading-relaxed text-muted-foreground">
					The general helper: recon, scanning, OSINT, document formatting.
				</p>
				<div class="divide-y divide-border pl-5">
					<SettingsField label="Provider" hint="Which provider serves this sub-agent.">
						<SubAgentModelPicker
							v-model="worker.providerId"
							:options="providerIds"
							search-placeholder="Search provider..."
						/>
					</SettingsField>
					<SettingsField label="Model" hint="Left empty until a provider is picked.">
						<SubAgentModelPicker
							v-model="worker.modelName"
							:options="workerModels"
							:disabled="!worker.providerId"
							search-placeholder="Search model..."
						/>
					</SettingsField>
				</div>
			</div>

			<!-- Kali sub-agent -->
			<div>
				<p class="mb-1 text-xs font-semibold text-foreground">
					Kali sub-agent
				</p>
				<p class="mb-1.5 text-[11px] leading-relaxed text-muted-foreground">
					Starts the tools you pick in the Kali dialog, then terminates.
				</p>
				<div class="divide-y divide-border pl-5">
					<SettingsField label="Provider" hint="Which provider serves this sub-agent.">
						<SubAgentModelPicker
							v-model="kali.providerId"
							:options="providerIds"
							search-placeholder="Search provider..."
						/>
					</SettingsField>
					<SettingsField label="Model" hint="Left empty until a provider is picked.">
						<SubAgentModelPicker
							v-model="kali.modelName"
							:options="kaliModels"
							:disabled="!kali.providerId"
							search-placeholder="Search model..."
						/>
					</SettingsField>
				</div>
			</div>
		</div>

		<p v-if="error" class="wrap-break-word text-xs text-destructive">{{ error }}</p>
	</div>
</template>

<script setup>
import { computed, reactive, ref, watch } from "vue";
import { Input } from "@/components/ui/input";
import SettingsField from "@/components/settings/SettingsField.vue";
import SubAgentModelPicker from "@/components/settings/sections/SubAgentModelPicker.vue";
import { useSettingsStore } from "@/stores/settings";
import { useSettingsUiStore } from "@/stores/settingsUi";

const settings = useSettingsStore();
const settingsUi = useSettingsUiStore();
const providerIds = computed(() => settings.providerIds);

const worker = reactive({ ...settings.workerModel });
const kali = reactive({ ...settings.kaliModel });
const maxRounds = ref(settings.defaultMaxRounds);
const timeoutMinutes = ref(settings.subAgentTimeoutMinutes);
// Each picker refetches its own catalog; the two never share one.
const workerModels = ref([]);
const kaliModels = ref([]);
const error = ref("");

watch(() => worker.providerId, loadWorkerModels, { immediate: true });
watch(() => kali.providerId, loadKaliModels, { immediate: true });

async function loadModels(providerId, into) {
	into.value = [];
	if (!providerId) return;
	try {
		into.value = (await settings.listModels(providerId)).map((entry) => entry.name);
	} catch {
		// Transient; the picker simply stays empty.
	}
}

/**
 * Changing provider invalidates the model chosen for the old one. On the very
 * first run the provider is already the persisted one, so the stored model is
 * still valid and must survive.
 */
function loadWorkerModels(providerId, previous) {
	if (previous !== undefined) worker.modelName = "";
	return loadModels(providerId, workerModels);
}

function loadKaliModels(providerId, previous) {
	if (previous !== undefined) kali.modelName = "";
	return loadModels(providerId, kaliModels);
}

/**
 * Persists these settings as soon as any of them changes. Debounced so
 * typing a number or arrow-keying through rounds is one request, not one per
 * keystroke.
 */
watch(
	() => [
		worker.providerId,
		worker.modelName,
		kali.providerId,
		kali.modelName,
		maxRounds.value,
		timeoutMinutes.value,
	],
	(_value, previous) => {
		// The first run mirrors the loaded settings; nothing has changed yet.
		if (!previous) return;
		persist();
	},
);

let persistTimer = null;
function persist() {
	clearTimeout(persistTimer);
	persistTimer = setTimeout(async () => {
		error.value = "";
		try {
			await settings.saveAgents(
				worker,
				kali,
				Math.trunc(Number(maxRounds.value)),
				Math.trunc(Number(timeoutMinutes.value)),
			);
			settingsUi.flashSaved();
		} catch (err) {
			error.value = err instanceof Error ? err.message : String(err);
		}
	}, 400);
}
</script>
