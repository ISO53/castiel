<template>
	<div class="space-y-4">
		<p class="text-xs leading-relaxed text-muted-foreground">
			How hard the harness pushes in one message. These govern the main agent, not the
			sub-agents, which have their own budget under Sub-agents.
		</p>

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
	error.value = "";
	try {
		await settings.saveLimits({
			maxToolRounds: maxToolRounds.value,
			checkpointIntervalRounds: checkpointIntervalRounds.value,
			verbatimToolCalls: verbatimToolCalls.value,
		});
		// Echo back what the backend actually stored (it floors the values too).
		maxToolRounds.value = settings.maxToolRounds;
		checkpointIntervalRounds.value = settings.checkpointIntervalRounds;
		verbatimToolCalls.value = settings.verbatimToolCalls;
		settingsUi.flashSaved();
	} catch (err) {
		error.value = err instanceof Error ? err.message : String(err);
	}
}
</script>