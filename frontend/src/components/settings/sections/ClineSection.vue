<template>
	<ProviderSection
		:connecting="connecting"
		:disabled="!form.apiKey.trim()"
		:health="health"
		:error="error"
		@connect="connect"
	>
		<template #prose>
			<p>
				Access models from Anthropic, OpenAI, Google, and more through Cline's
				OpenAI-compatible endpoint.
			</p>
			<ul class="list-disc space-y-1 pl-4">
				<li>
					Create an API key at
					<a class="underline underline-offset-2 hover:text-foreground" href="https://app.cline.bot"
						target="_blank" rel="noreferrer">app.cline.bot</a>
					under Settings &gt; API Keys
				</li>
				<li>Click Connect below to start using Cline in castiel</li>
			</ul>
		</template>

		<SettingsField id="cline_api_key" label="API key">
			<Input id="cline_api_key" v-model="form.apiKey" type="password" placeholder="Your Cline API key"
				autocomplete="off" spellcheck="false" />
		</SettingsField>

		<template #extra>
			<Separator />

			<div class="space-y-2 text-xs leading-relaxed text-muted-foreground">
				<p>
					Or skip the API key and sign in with your Cline account instead. A browser window
					opens to authorize castiel, and tokens refresh automatically afterwards.
				</p>
			</div>

			<div v-if="account" class="flex items-center gap-2 text-xs">
				<span class="size-2 rounded-full bg-emerald-500" aria-hidden="true" />
				<span class="text-muted-foreground">Signed in as {{ account }}</span>
			</div>
			<Button
				v-if="account"
				size="sm"
				variant="outline"
				:disabled="disconnecting"
				@click="disconnect"
			>
				{{ disconnecting ? "Disconnecting..." : "Disconnect" }}
			</Button>

			<div v-else-if="signIn" class="space-y-2">
				<div class="flex flex-col gap-1.5">
					<Label>Device code</Label>
					<div class="flex items-center gap-2">
						<code class="rounded bg-muted px-2 py-1 text-sm tracking-widest">{{ signIn.userCode }}</code>
						<Button size="sm" variant="outline" @click="openVerification">Open browser</Button>
					</div>
				</div>
				<div class="flex items-center gap-2 text-xs text-muted-foreground">
					<Spinner class="size-3" />
					<span>{{ signInStatus || "Waiting for authorization..." }}</span>
				</div>
			</div>

			<div v-else>
				<Button size="sm" :disabled="starting" @click="startSignIn">
					{{ starting ? "Starting..." : "Sign in with Cline" }}
				</Button>
			</div>
		</template>
	</ProviderSection>
</template>

<script setup>
import { onUnmounted, reactive, ref } from "vue";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Separator } from "@/components/ui/separator";
import { Spinner } from "@/components/ui/spinner";
import ProviderSection from "@/components/settings/ProviderSection.vue";
import SettingsField from "@/components/settings/SettingsField.vue";
import { useSettingsStore } from "@/stores/settings";

const settings = useSettingsStore();
const form = reactive({ apiKey: settings.cline.apiKey ?? "" });
const account = ref(settings.cline.auth?.email ?? null);
const connecting = ref(false);
const disconnecting = ref(false);
const starting = ref(false);
const health = ref(null);
const error = ref("");
const signIn = ref(null);
const signInStatus = ref("");
let pollTimer = null;

onUnmounted(stopPolling);

function stopPolling() {
	if (pollTimer) clearTimeout(pollTimer);
	pollTimer = null;
}

async function connect() {
	connecting.value = true;
	error.value = "";
	health.value = null;
	try {
		health.value = await settings.connectCline(form);
	} catch (err) {
		error.value = err instanceof Error ? err.message : String(err);
	} finally {
		connecting.value = false;
	}
}

async function startSignIn() {
	starting.value = true;
	error.value = "";
	try {
		signIn.value = await settings.startClineSignIn();
		signInStatus.value = "Waiting for authorization...";
		openVerification();
		poll();
	} catch (err) {
		error.value = err instanceof Error ? err.message : String(err);
	} finally {
		starting.value = false;
	}
}

function openVerification() {
	if (signIn.value?.verificationUrl) {
		window.open(signIn.value.verificationUrl, "_blank", "noopener");
	}
}

async function poll() {
	try {
		const result = await settings.pollClineSignIn();
		if (result.status === "PENDING") {
			signInStatus.value = result.message || "Waiting for authorization...";
			pollTimer = setTimeout(poll, 4000);
		} else if (result.status === "AUTHORIZED") {
			account.value = result.email ?? null;
			health.value = result.health ?? null;
			signIn.value = null;
			signInStatus.value = "";
		} else if (result.status === "EXPIRED") {
			signIn.value = null;
			signInStatus.value = "";
			error.value = "Sign-in code expired. Start again.";
		} else if (result.status === "DENIED") {
			signIn.value = null;
			signInStatus.value = "";
			error.value = "Authorization was denied.";
		} else {
			// IDLE: the flow was restarted server-side.
			signIn.value = null;
			signInStatus.value = "";
		}
	} catch (err) {
		error.value = err instanceof Error ? err.message : String(err);
		signIn.value = null;
		signInStatus.value = "";
	}
}

async function disconnect() {
	disconnecting.value = true;
	error.value = "";
	try {
		await settings.disconnectCline();
		account.value = null;
		health.value = null;
	} catch (err) {
		error.value = err instanceof Error ? err.message : String(err);
	} finally {
		disconnecting.value = false;
	}
}
</script>
