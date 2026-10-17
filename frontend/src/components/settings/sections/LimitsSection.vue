<template>
	<div class="space-y-4">
		<div class="divide-y divide-border">
			<SettingsField
				label="Tool-call rounds"
				hint="How many tool rounds one message may use before the harness stops it (8-1024)."
				control-class="w-24"
			>
				<Input
					id="limit_max_rounds"
					v-model.number="maxToolRounds"
					type="number"
					min="8"
					max="1024"
					class="text-right"
					placeholder="256"
					@change="persist"
				/>
			</SettingsField>

			<SettingsField
				label="Checkpoint every"
				hint="Rounds between reminders to sync findings into the workspace (1-128)."
				control-class="w-24"
			>
				<Input
					id="limit_checkpoint"
					v-model.number="checkpointIntervalRounds"
					type="number"
					min="1"
					max="128"
					class="text-right"
					placeholder="32"
					@change="persist"
				/>
			</SettingsField>

			<SettingsField
				label="Verbatim tool calls"
				hint="Recent tool calls kept untruncated in the model's history (0-64). More costs tokens."
				control-class="w-24"
			>
				<Input
					id="limit_verbatim"
					v-model.number="verbatimToolCalls"
					type="number"
					min="0"
					max="64"
					class="text-right"
					placeholder="32"
					@change="persist"
				/>
			</SettingsField>

			<SettingsField
				label="Looper directive cap"
				hint="Character cap for one directive the looper writes to the orchestrator (500-5000)."
				control-class="w-24"
			>
				<Input
					id="limit_looper_directive_cap"
					v-model.number="looperDirectiveCap"
					type="number"
					min="500"
					max="5000"
					class="text-right"
					placeholder="2000"
					@change="persist"
				/>
			</SettingsField>

			<SettingsField
				label="Looper tool-call rounds"
				hint="Tool-round budget for one looper run (8-64). The looper only reads workspace state."
				control-class="w-24"
			>
				<Input
					id="limit_looper_rounds"
					v-model.number="looperMaxRounds"
					type="number"
					min="8"
					max="64"
					class="text-right"
					placeholder="24"
					@change="persist"
				/>
			</SettingsField>

			<SettingsField
				label="PoC star threshold"
				hint="Minimum GitHub stars a proof-of-concept repo needs before exploit_search shows it (0-1000). Low-star PoCs are frequently noise or outright malware; the agent can still list them on explicit request."
				control-class="w-24"
			>
				<Input
					id="limit_poc_min_stars"
					v-model.number="pocMinStars"
					type="number"
					min="0"
					max="1000"
					class="text-right"
					placeholder="25"
					@change="persist"
				/>
			</SettingsField>
		</div>

		<p v-if="error" class="wrap-break-word text-xs text-destructive">{{ error }}</p>
	</div>
</template>

<script setup>
import { ref } from "vue";
import SettingsField from "@/components/settings/SettingsField.vue";
import { Input } from "@/components/ui/input";
import { useSettingsStore } from "@/stores/settings";
import { useSettingsUiStore } from "@/stores/settingsUi";

const settings = useSettingsStore();
const settingsUi = useSettingsUiStore();
const error = ref("");

// Local copies so a half-typed number never overwrites the stored value mid-edit;
// @change (on blur or Enter) is what commits.
const maxToolRounds = ref(settings.maxToolRounds);
const checkpointIntervalRounds = ref(settings.checkpointIntervalRounds);
const verbatimToolCalls = ref(settings.verbatimToolCalls);
const looperDirectiveCap = ref(settings.looperDirectiveCap);
const looperMaxRounds = ref(settings.looperMaxRounds);
const pocMinStars = ref(settings.pocMinStars);

/** Clamps to the field's range and clears anything that is not a number. */
function clampTo(value, min, max, fallback) {
	const parsed = Math.trunc(Number(value));
	if (!Number.isFinite(parsed)) return fallback;
	return Math.min(max, Math.max(min, parsed));
}

async function persist() {
	maxToolRounds.value = clampTo(maxToolRounds.value, 8, 1024, 256);
	checkpointIntervalRounds.value = clampTo(checkpointIntervalRounds.value, 1, 128, 32);
	verbatimToolCalls.value = clampTo(verbatimToolCalls.value, 0, 64, 32);
	looperDirectiveCap.value = clampTo(looperDirectiveCap.value, 500, 5000, 2000);
	looperMaxRounds.value = clampTo(looperMaxRounds.value, 8, 64, 24);
	pocMinStars.value = clampTo(pocMinStars.value, 0, 1000, 25);
	error.value = "";
	try {
		await settings.saveLimits({
			maxToolRounds: maxToolRounds.value,
			checkpointIntervalRounds: checkpointIntervalRounds.value,
			verbatimToolCalls: verbatimToolCalls.value,
			looperDirectiveCap: looperDirectiveCap.value,
			looperMaxRounds: looperMaxRounds.value,
		});
		// Echo back what the backend actually stored (it floors the values too).
		maxToolRounds.value = settings.maxToolRounds;
		checkpointIntervalRounds.value = settings.checkpointIntervalRounds;
		verbatimToolCalls.value = settings.verbatimToolCalls;
		looperDirectiveCap.value = settings.looperDirectiveCap;
		looperMaxRounds.value = settings.looperMaxRounds;
		// The PoC threshold lives on its own settings endpoint.
		await settings.setPocMinStars(pocMinStars.value);
		pocMinStars.value = settings.pocMinStars;
		settingsUi.flashSaved();
	} catch (err) {
		error.value = err instanceof Error ? err.message : String(err);
	}
}
</script>
