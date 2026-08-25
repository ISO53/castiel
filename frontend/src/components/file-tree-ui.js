import { reactive } from "vue";

/**
 * UI state for the workspace file tree (a single instance app-wide):
 * which item is being renamed, and where an inline creation input is open.
 * FileTreeView assigns the action implementations in created(); FileTreeEntry
 * reads the state and invokes the actions from its context menus.
 */
export const fileTreeUi = reactive({
	renamingPath: "",
	creatingIn: "",
	creatingDirectory: false,
});

export const fileTreeActions = {
	startCreate: () => {},
	startRename: () => {},
	remove: () => {},
	commitCreate: () => {},
	cancelCreate: () => {},
	commitRename: () => {},
	cancelRename: () => {},
};
