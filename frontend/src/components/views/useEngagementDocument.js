import { onBeforeUnmount, ref, watch } from "vue";
import { useWorkspaceStore } from "@/stores/workspace";
import { fetchDocument } from "@/lib/documents";

/**
 * Loads one engagement document for the active workspace and keeps it fresh.
 *
 * Returns reactive { data, loading, error, refresh }. Data polls silently every
 * {@param pollMs} milliseconds so views track agent edits to the underlying file.
 */
export function useEngagementDocument(file, { pollMs = 15000 } = {}) {
	const workspace = useWorkspaceStore();
	const data = ref(null);
	const loading = ref(false);
	const error = ref("");
	let timer = null;

	async function refresh(silent = false) {
		if (!workspace.cwd) {
			data.value = null;
			error.value = "";
			return;
		}
		if (!silent) loading.value = true;
		try {
			data.value = await fetchDocument(file, workspace.cwd);
			error.value = "";
		} catch (err) {
			error.value = err instanceof Error ? err.message : String(err);
		} finally {
			loading.value = false;
		}
	}

	function stopPolling() {
		if (timer) {
			clearInterval(timer);
			timer = null;
		}
	}

	function startPolling() {
		stopPolling();
		if (!workspace.cwd) return;
		refresh();
		timer = setInterval(() => refresh(true), pollMs);
	}

	watch(
		() => workspace.cwd,
		() => startPolling(),
		{ immediate: true },
	);
	onBeforeUnmount(stopPolling);

	return { data, loading, error, refresh };
}