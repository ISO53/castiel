<template>
	<!-- Pinned to the viewport: #app has no height of its own, so percentage
		heights would collapse and the card would hug the top-left corner. -->
	<div class="fixed inset-0 grid place-items-center bg-background">
		<div class="w-full max-w-sm rounded-lg border bg-card p-6 text-card-foreground shadow-sm">
			<div class="mb-4 flex items-center gap-2">
				<ShieldLock class="size-5 text-primary" />
				<span class="text-base font-semibold">castiel</span>
			</div>
			<p class="mb-4 text-sm text-muted-foreground">
				This harness requires the access token to work. Please enter below.
			</p>
			<form @submit.prevent="submit">
				<Input
					v-model="token"
					type="password"
					placeholder="Access token"
					autocomplete="off"
					autofocus
					:disabled="auth.busy"
				/>
				<p v-if="auth.error" class="mt-2 text-sm text-destructive">{{ auth.error }}</p>
				<Button type="submit" class="mt-4 w-full" :disabled="auth.busy || !token.trim()">
					{{ auth.busy ? "Checking..." : "Unlock" }}
				</Button>
			</form>
		</div>
	</div>
</template>

<script>
import { ShieldLock } from "@lucide/vue";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useAuthStore } from "@/stores/auth";

/**
 * Full-screen login card for the harness's --auth gate. Rendered by App.vue in
 * place of the whole app while the session is missing; every view mounts fresh
 * after a successful login, so no store has to know about authentication.
 */
export default {
	name: "AuthGate",
	components: {
		Button,
		Input,
		ShieldLock,
	},
	data() {
		return {
			auth: useAuthStore(),
			token: "",
		};
	},
	methods: {
		async submit() {
			if (!this.token.trim() || this.auth.busy) {
				return;
			}
			const ok = await this.auth.login(this.token);
			if (ok) {
				// Let App.vue remount the app; keep the token out of component state.
				this.token = "";
			}
		},
	},
};
</script>
