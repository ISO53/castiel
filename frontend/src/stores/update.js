import { defineStore } from "pinia";
import { toast } from "vue-sonner";

const API = `${window.location.origin}/api/update`;

/** One toast id for the whole update flow: phases morph it, they never stack. */
const TOAST_ID = "update";

/** How often the staged phase polls for the sideloader's error marker. */
const STAGED_POLL_MS = 5000;

async function readError(response) {
	const body = await response.text();
	try {
		const json = JSON.parse(body);
		return json.message || json.detail || json.error || body || `Request failed with status ${response.status}`;
	} catch {
		return body || `Request failed with status ${response.status}`;
	}
}

/**
 * The update feature's UI state. The backend owns the status; this store only
 * mirrors it (SSE or fetch), runs the once-per-launch startup flow, and keeps
 * the single update toast in step with the phase.
 */
export const useUpdateStore = defineStore("update", {
	state: () => ({
		status: null,
		/** EventSource for the backend's live status feed. */
		source: null,
		/** Error text of the last failed check request; shown in the section only. */
		checkError: "",
		/** Guards the once-per-launch startup flow. */
		started: false,
		/** Whether the offer toast was already shown this launch. */
		offered: false,
		/** Interval id of the staged-phase status poll. */
		pollTimer: null,
		/** Phase the toast currently shows; skips redundant toast calls. */
		toastPhase: "",
	}),
	getters: {
		phase(state) {
			return state.status?.phase ?? "idle";
		},
		/** True while an install is downloading or staging. */
		busy(state) {
			return state.status?.phase === "downloading" || state.status?.phase === "staging";
		},
		/** True when a newer installable release exists and nothing is running yet. */
		actionable(state) {
			return (
				state.status?.updateAvailable &&
				state.status?.installable &&
				(state.status?.phase ?? "idle") === "idle"
			);
		},
	},
	actions: {
		/**
		 * Startup flow, runs once: connect the feed, fetch the status, report the
		 * pending marker result, adopt any in-progress phase, then auto-check.
		 * Failures stay silent; the settings section reports the state.
		 */
		async init() {
			if (this.started) return;
			this.started = true;
			this.startFeed();
			try {
				await this.fetchStatus();
			} catch {
				return;
			}
			// Success markers toast; failure markers stay in the section (rule 6).
			if (this.status.lastResultOutcome === "updated") {
				toast(`castiel was updated to ${this.status.installedVersion}`, { duration: 6000 });
			}
			this.syncToast();
			if (this.status.checkOnStartup && this.phase === "idle") {
				try {
					await this.check(false);
					if (this.actionable && !this.offered) {
						this.offered = true;
						this.offerToast();
					}
				} catch {
					// Silent: a failed startup check must never nag the user.
				}
			}
		},
		startFeed() {
			if (this.source) return;
			const source = new EventSource(`${API}/events`);
			source.onmessage = (event) => {
				this.status = JSON.parse(event.data);
				this.syncToast();
			};
			this.source = source;
		},
		async fetchStatus() {
			const response = await fetch(API);
			if (!response.ok) throw new Error(await readError(response));
			this.status = await response.json();
		},
		/** Checks GitHub for a newer release; force bypasses the backend cache. */
		async check(force) {
			this.checkError = "";
			const response = await fetch(`${API}/check?force=${force ? "true" : "false"}`, { method: "POST" });
			if (!response.ok) {
				this.checkError = await readError(response);
				throw new Error(this.checkError);
			}
			this.status = await response.json();
			return this.status;
		},
		/** Starts the install; progress arrives over the feed. */
		async install() {
			const response = await fetch(`${API}/install`, { method: "POST" });
			if (!response.ok) throw new Error(await readError(response));
			this.status = await response.json();
			this.syncToast();
		},
		/** Replaces the check-on-startup preference. */
		async setCheckOnStartup(value) {
			const response = await fetch(`${API}/prefs`, {
				method: "PUT",
				headers: { "Content-Type": "application/json" },
				body: JSON.stringify({ checkOnStartup: value }),
			});
			if (!response.ok) throw new Error(await readError(response));
			this.status = await response.json();
		},
		/** Opens the log directory; a headless backend answers with the path to show. */
		async openLogs() {
			const response = await fetch(`${API}/logs/open`, { method: "POST" });
			if (!response.ok) throw new Error(await readError(response));
			return await response.json();
		},
		/** Keeps the single update toast in step with the phase. */
		syncToast() {
			if (this.toastPhase === this.phase) return;
			this.toastPhase = this.phase;
			if (this.phase === "staged") {
				// Plain toast, not success: every update message renders the same neutral style.
				// action is cleared on purpose: vue-sonner merges options for an existing id,
				// so the offer's Install button would otherwise survive this update.
				toast("The app will be updated on next launch.", { id: TOAST_ID, duration: 8000, action: undefined });
				this.startPolling();
			} else {
				// Progress lives in the settings section only; failures stay there too (rule 6).
				toast.dismiss(TOAST_ID);
				this.stopPolling();
			}
		},
		/** The offer: a new version is available, one click installs it. */
		offerToast() {
			toast(`castiel ${this.status.latestVersion} is available`, {
				id: TOAST_ID,
				duration: Infinity,
				description: `You have ${this.status.installedVersion} installed.`,
				action: { label: "Install", onClick: () => this.install() },
			});
		},
		startPolling() {
			if (this.pollTimer) return;
			// The sideloader only reports through marker files; poll for them.
			this.pollTimer = setInterval(async () => {
				try {
					await this.fetchStatus();
					this.syncToast();
				} catch {
					// Silent: the next successful poll catches up.
				}
			}, STAGED_POLL_MS);
		},
		stopPolling() {
			if (!this.pollTimer) return;
			clearInterval(this.pollTimer);
			this.pollTimer = null;
		},
	},
});
