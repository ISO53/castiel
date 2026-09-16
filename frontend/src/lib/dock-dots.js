// Dot indicator styling for the bottom-dock tables (processes and agents).
const DOT_CLASSES = {
	RUNNING: "bg-emerald-400",
	QUEUED: "bg-amber-400",
	EXITED: "bg-zinc-500",
	DONE: "bg-zinc-500",
	KILLED: "bg-red-400",
	FAILED: "bg-red-400",
	CANCELLED: "bg-orange-400",
};

export function dotClass(state) {
	return DOT_CLASSES[state] ?? "bg-zinc-600";
}

export function dotTitle(state) {
	return (state ?? "").toLowerCase();
}
