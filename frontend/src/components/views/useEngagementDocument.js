import { onBeforeUnmount, ref, watch } from "vue";
import { useWorkspaceStore } from "@/stores/workspace";
import { useWorkspaceFeedStore } from "@/stores/workspaceFeed";
import { fetchDocument } from "@/lib/documents";

/**
 * Loads one engagement document for the active workspace and keeps it fresh.
 *
 * Returns reactive { data, loading, error, refresh }. Data refetches when the
 * workspace feed pings a change to this file, or to an unknown file.
 */
export function useEngagementDocument(file) {
	const workspace = useWorkspaceStore();
	const feed = useWorkspaceFeedStore();
	const data = ref(null);
	const loading = ref(false);
	const error = ref("");
	let debounce = null;

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

	watch(
		() => workspace.cwd,
		() => refresh(),
		{ immediate: true },
	);

	// Coalesce bursts of change pings into a single refetch.
	const unsubscribe = feed.subscribe((path) => {
		if (path !== "*" && !path.endsWith(file)) return;
		if (debounce) clearTimeout(debounce);
		debounce = setTimeout(() => {
			debounce = null;
			refresh(true);
		}, 400);
	});

	onBeforeUnmount(() => {
		unsubscribe();
		if (debounce) clearTimeout(debounce);
	});

	return { data, loading, error, refresh };
}