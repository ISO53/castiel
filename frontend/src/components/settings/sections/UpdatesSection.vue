<template>
	<div class="space-y-4">
		<div class="divide-y divide-border">
			<SettingsField label="Current version" hint="The build this app is running.">
				<span class="font-mono text-xs font-medium text-foreground">{{ version }}</span>
			</SettingsField>

			<SettingsField
				label="Check for updates"
				hint="Compares this build with the latest GitHub release."
				control-class="w-96"
			>
				<!-- Busy: the inline progress circle replaces the status text and every button. -->
				<div v-if="update.busy" class="flex items-center gap-3">
					<UpdateProgress />
				</div>
				<div v-else class="flex items-center justify-end gap-3">
					<!-- Failures (rule 6): "See log file" reveals the log directory. -->
					<p v-if="failed" class="text-right text-xs font-medium text-destructive">
						Update failed.
						<button type="button" class="underline underline-offset-2" @click="revealLogs">See log file</button>
					</p>
					<p v-else-if="statusText" class="text-right text-xs" :class="statusDestructive ? 'text-destructive' : 'text-muted-foreground'">
						{{ statusText }}
						<a
							v-if="releaseNotesUrl"
							:href="releaseNotesUrl"
							target="_blank"
							rel="noopener noreferrer"
							class="ml-1 underline underline-offset-2"
						>
							Release notes
						</a>
					</p>
					<Button v-if="update.actionable" size="sm" @click="installNow">Install</Button>
					<Button v-else size="sm" variant="outline" :disabled="checking" @click="checkNow">
						<Spinner v-if="checking" class="size-3" />
						{{ checking ? "Checking..." : "Check now" }}
					</Button>
				</div>
			</SettingsField>

			<SettingsField label="Check on startup" hint="Check for a new version every time castiel opens.">
				<Switch
					size="sm"
					:model-value="checkOnStartup"
					:disabled="savingStartupCheck"
					aria-label="Check for a new version every time castiel opens"
					@update:model-value="toggleStartupCheck"
				/>
			</SettingsField>
		</div>

		<!-- The headless fallback: no file manager opened, so the path is the text. -->
		<p v-if="logPath" class="text-xs text-muted-foreground">Log directory: {{ logPath }}</p>
		<p v-if="error" class="wrap-break-word text-xs text-destructive">{{ error }}</p>
	</div>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import SettingsField from "@/components/settings/SettingsField.vue";
import UpdateProgress from "@/components/update/UpdateProgress.vue";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { Switch } from "@/components/ui/switch";
import { useSettingsUiStore } from "@/stores/settingsUi";
import { useUpdateStore } from "@/stores/update";

const settingsUi = useSettingsUiStore();
const update = useUpdateStore();
const error = ref("");
const checking = ref(false);
const savingStartupCheck = ref(false);
const logPath = ref("");

const version = computed(() => update.status?.installedVersion || "unknown");
const checkOnStartup = computed(() => update.status?.checkOnStartup ?? true);
const releaseNotesUrl = computed(() => (update.status?.updateAvailable ? update.status?.releaseUrl : null));

/** True when the phase or a startup marker says the install failed. */
const failed = computed(() => update.status?.phase === "error" || update.status?.lastResultOutcome === "failed");

/** The one status text next to the button; hidden until the first check completes. */
const statusText = computed(() => {
	const status = update.status;
	if (!status) return "";
	if (status.phase === "staged") return "The app will be updated on next launch.";
	if (update.checkError) return update.checkError;
	if (status.updateAvailable) {
		return status.installable
			? `There is a new version v${status.latestVersion}.`
			: `There is a new version v${status.latestVersion}. ${status.reason}`;
	}
	// A completed check with no newer release is the only "up to date" case.
	return status.latestVersion ? "You are up to date." : "";
});

/** The status text turns destructive when the check request itself failed. */
const statusDestructive = computed(() => Boolean(update.checkError));

onMounted(async () => {
	error.value = "";
	try {
		await update.fetchStatus();
	} catch (err) {
		error.value = err instanceof Error ? err.message : String(err);
	}
});

/** Forced re-check; the backend cache is bypassed on purpose. */
async function checkNow() {
	error.value = "";
	checking.value = true;
	try {
		await update.check(true);
	} catch (err) {
		error.value = err instanceof Error ? err.message : String(err);
	} finally {
		checking.value = false;
	}
}

async function installNow() {
	error.value = "";
	try {
		await update.install();
	} catch (err) {
		error.value = err instanceof Error ? err.message : String(err);
	}
}

async function toggleStartupCheck(value) {
	error.value = "";
	savingStartupCheck.value = true;
	try {
		await update.setCheckOnStartup(value);
		settingsUi.flashSaved();
	} catch (err) {
		error.value = err instanceof Error ? err.message : String(err);
	} finally {
		savingStartupCheck.value = false;
	}
}

/** Opens the log directory in the file manager; a headless backend answers with the path. */
async function revealLogs() {
	error.value = "";
	try {
		const result = await update.openLogs();
		if (!result.opened) logPath.value = result.path;
	} catch (err) {
		error.value = err instanceof Error ? err.message : String(err);
	}
}
</script>
