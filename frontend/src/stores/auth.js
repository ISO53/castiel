import { defineStore } from "pinia";

const API_BASE_URL = `${window.location.origin}/api/auth`;

async function readError(response) {
	const body = await response.text();
	try {
		const json = JSON.parse(body);
		return (
			json.message ||
			json.detail ||
			json.error ||
			body ||
			`Request failed with status ${response.status}`
		);
	} catch {
		return body || `Request failed with status ${response.status}`;
	}
}

/**
 * Mirrors the harness's opt-in --auth gate. The harness decides everything; this
 * store only asks /api/auth/status (once at startup, then on window focus, which
 * also catches server-side session expiry) and exchanges the token for the
 * harness's session cookie on login. With auth disabled the app boots unchanged.
 */
export const useAuthStore = defineStore("auth", {
	state: () => ({
		/** Whether the backend runs with --auth. */
		enabled: false,
		/** Whether the backend currently accepts our session. */
		authenticated: false,
		/** True once /api/auth/status has answered at least once. */
		ready: false,
		/** Guards the login request. */
		busy: false,
		/** Error text of the last failed login; shown on the login card. */
		error: "",
	}),
	getters: {
		/** The login overlay shows only when the gate is on and we are not in. */
		needsLogin(state) {
			return state.ready && state.enabled && !state.authenticated;
		},
	},
	actions: {
		async refresh() {
			try {
				const response = await fetch(`${API_BASE_URL}/status`);
				if (!response.ok) {
					throw new Error(await readError(response));
				}
				const status = await response.json();
				this.enabled = Boolean(status.enabled);
				this.authenticated = Boolean(status.authenticated);
				this.ready = true;
				if (this.authenticated) {
					this.error = "";
				}
			} catch {
				// An unreachable backend must not lock the UI behind a login screen;
				// the app renders and its own error handling takes over.
				this.ready = true;
			}
		},
		async login(token) {
			this.busy = true;
			this.error = "";
			try {
				const response = await fetch(`${API_BASE_URL}/login`, {
					method: "POST",
					headers: { "Content-Type": "application/json" },
					body: JSON.stringify({ token }),
				});
				if (!response.ok) {
					throw new Error(await readError(response));
				}
				this.authenticated = true;
				return true;
			} catch (ex) {
				this.error = ex.message;
				return false;
			} finally {
				this.busy = false;
			}
		},
		async logout() {
			try {
				await fetch(`${API_BASE_URL}/logout`, { method: "POST" });
			} finally {
				// Drop the session locally even if the request failed: the next
				// status check tells the truth either way.
				this.authenticated = false;
				this.ready = true;
			}
		},
		/** Starts the status check and the focus listener; resolves once status is in. */
		start() {
			window.addEventListener("focus", this.onFocus);
			return this.refresh();
		},
		stop() {
			window.removeEventListener("focus", this.onFocus);
		},
		onFocus() {
			this.refresh();
		},
	},
});