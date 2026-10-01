<template>
	<div class="space-y-4">
		<div class="grid grid-cols-2 gap-3">
			<div class="flex flex-col gap-1.5">
				<Label>Worker provider</Label>
				<Popover v-model:open="providerOpen">
					<PopoverTrigger as-child>
						<Button variant="outline" role="combobox" :aria-expanded="providerOpen"
							class="w-full justify-between font-normal">
							<span :class="worker.providerId ? '' : 'text-muted-foreground'">
								{{ worker.providerId || "Select provider..." }}
							</span>
							<ChevronsUpDown class="opacity-50" />
						</Button>
					</PopoverTrigger>
					<PopoverContent class="w-(--reka-popover-trigger-width) p-0">
						<Command>
							<CommandInput class="h-9" placeholder="Search provider..." />
							<CommandList>
								<CommandEmpty>No provider found.</CommandEmpty>
								<CommandGroup>
									<CommandItem v-for="id in providerIds" :key="id" :value="id"
										@select="(ev) => selectProvider(String(ev.detail.value))">
										{{ id }}
										<Check :class="['ml-auto', worker.providerId === id ? 'opacity-100' : 'opacity-0']" />
									</CommandItem>
								</CommandGroup>
							</CommandList>
						</Command>
					</PopoverContent>
				</Popover>
			</div>
			<div class="flex flex-col gap-1.5">
				<Label>Worker model</Label>
				<Popover v-model:open="modelOpen">
					<PopoverTrigger as-child>
						<Button variant="outline" role="combobox" :aria-expanded="modelOpen"
							:disabled="!worker.providerId" class="w-full justify-between font-normal">
							<span :class="worker.modelName ? '' : 'text-muted-foreground'">
								{{ worker.modelName || "Select model..." }}
							</span>
							<ChevronsUpDown class="opacity-50" />
						</Button>
					</PopoverTrigger>
					<PopoverContent class="w-(--reka-popover-trigger-width) p-0">
						<Command>
							<CommandInput class="h-9" placeholder="Search model..." />
							<CommandList>
								<CommandEmpty>No model found.</CommandEmpty>
								<CommandGroup>
									<CommandItem v-for="model in models" :key="model.name" :value="model.name"
										@select="(ev) => selectModel(String(ev.detail.value))">
										{{ model.name }}
										<Check :class="['ml-auto', worker.modelName === model ? 'opacity-100' : 'opacity-0']" />
									</CommandItem>
								</CommandGroup>
							</CommandList>
						</Command>
					</PopoverContent>
				</Popover>
			</div>
		</div>

		<SettingsField
			id="agent_max_rounds"
			label="Default tool-call rounds"
			hint="Budget for one sub-agent run (2-32). The agent can request more per call, up to 32."
		>
			<Input id="agent_max_rounds" v-model.number="maxRounds" type="number" min="2" max="32" placeholder="16" />
		</SettingsField>

		<div class="flex items-center gap-3">
			<Button size="sm" :disabled="saving" @click="save">
				{{ saving ? "Saving..." : "Save sub-agent settings" }}
			</Button>
			<span v-if="saved" class="text-xs text-emerald-500">Saved</span>
			<span v-if="error" class="wrap-break-word text-xs text-destructive">{{ error }}</span>
		</div>
	</div>
</template>

<script setup>
import { computed, reactive, ref, watch } from "vue";
import { Button } from "@/components/ui/button";
import {
	Command,
	CommandEmpty,
	CommandGroup,
	CommandInput,
	CommandItem,
	CommandList,
} from "@/components/ui/command";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import SettingsField from "@/components/settings/SettingsField.vue";
import { useSettingsStore } from "@/stores/settings";
import { Check, ChevronsUpDown } from "@lucide/vue";

const settings = useSettingsStore();
const providerIds = computed(() => settings.providerIds);
const worker = reactive({ ...settings.workerModel });
const maxRounds = ref(settings.defaultMaxRounds);
const models = ref([]);
const providerOpen = ref(false);
const modelOpen = ref(false);
const saving = ref(false);
const saved = ref(false);
const error = ref("");

// The catalog is per provider, so it is refetched whenever the pick changes.
watch(() => worker.providerId, loadModels, { immediate: true });

async function loadModels(providerId) {
	worker.modelName = "";
	models.value = [];
	if (!providerId) return;
	try {
		models.value = await settings.listModels(providerId);
	} catch {
		// Transient; the picker simply stays empty.
	}
}

function selectProvider(id) {
	worker.providerId = id;
	providerOpen.value = false;
}

function selectModel(name) {
	worker.modelName = name;
	modelOpen.value = false;
}

async function save() {
	saving.value = true;
	error.value = "";
	saved.value = false;
	try {
		await settings.saveAgents(worker, Math.trunc(Number(maxRounds.value)));
		saved.value = true;
		setTimeout(() => {
			saved.value = false;
		}, 2500);
	} catch (err) {
		error.value = err instanceof Error ? err.message : String(err);
	} finally {
		saving.value = false;
	}
}
</script>
