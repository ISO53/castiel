<template>
	<div class="space-y-4">
		<p class="text-xs leading-relaxed text-muted-foreground">
			The tools the agent can call. Switching one off hides it from the model everywhere,
			including from sub-agents. The greyed-out ones keep castiel working and cannot be
			switched off.
		</p>
		<!-- Only the groups whose absence costs correctness warn; losing Web or Kali
		     discovery costs convenience and is deliberately silent. -->
		<div
			v-if="criticalOff.length"
			class="rounded-md border border-destructive/30 bg-destructive/5 px-3 py-2.5 text-[11px] leading-relaxed"
		>
			<p class="font-medium text-destructive">
				{{ criticalOff.length === 1 ? "1 tool is" : criticalOff.length + " tools are" }}
				vital to castiel switched off:
				{{ criticalOff.map((group) => group.label).join(", ") }}.
			</p>
			<p class="mt-1.5 text-muted-foreground">
				The agent's instructions still tell it to use {{ criticalOff.length === 1 ? "it" : "them" }},
				so it will keep calling {{ criticalOff.length === 1 ? "it" : "them" }} and fail. It will
				press on with a worse route and tell you nothing, so expect results that are
				incomplete or unscored, and you will only find out when you read them.
			</p>
		</div>

		<p v-if="loadError" class="wrap-break-word text-xs text-destructive">{{ loadError }}</p>

		<div v-else-if="settings.toolGroups.length" class="divide-y divide-border">
			<SettingsField
				v-for="group in settings.toolGroups"
				:key="group.id"
				:label="group.label"
				:hint="group.description"
			>
				<!-- Required tools show as on, but the switch itself is inert so they
				     cannot be switched off. -->
				<Switch
					size="sm"
					:model-value="group.enabled"
					:disabled="group.locked"
					:aria-label="'Toggle ' + group.label"
					@update:model-value="(value) => toggle(group, value)"
				/>
			</SettingsField>
		</div>
	</div>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import SettingsField from "@/components/settings/SettingsField.vue";
import { Switch } from "@/components/ui/switch";
import { useSettingsStore } from "@/stores/settings";
import { useSettingsUiStore } from "@/stores/settingsUi";

const settings = useSettingsStore();
const settingsUi = useSettingsUiStore();
const loadError = ref("");

// Only groups flagged critical in the catalogue drive the warning; the rest are
// genuine conveniences, and warning about those would just train the user to ignore it.
const criticalOff = computed(() => settings.toolGroups.filter((group) => group.critical && !group.enabled));

onMounted(async () => {
	try {
		await settings.fetchToolGroups();
	} catch (err) {
		loadError.value = err instanceof Error ? err.message : String(err);
	}
});

async function toggle(group, enabled) {
	loadError.value = "";
	try {
		await settings.setToolGroupEnabled(group.id, enabled);
		settingsUi.flashSaved();
	} catch (err) {
		loadError.value = err instanceof Error ? err.message : String(err);
	}
}
</script>