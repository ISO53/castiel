const FILES_API = "http://localhost:8081/api/files";

/**
 * The engagement documents backing the pentest views. Each document is one JSON file in
 * the workspace root that agents fill in and these views render live.
 */
export const ENGAGEMENT_DOCUMENTS = [
	{
		id: "network",
		file: "network.json",
		label: "Network",
		component: "NetworkView",
		description: "Segments, hosts, services & DNS",
	},
	{
		id: "web",
		file: "web.json",
		label: "Web App",
		component: "WebAppView",
		description: "Sites, pages, tech stack & headers",
	},
	{
		id: "findings",
		file: "findings.json",
		label: "Findings",
		component: "FindingsView",
		description: "Vulnerabilities & severity",
	},
	{
		id: "evidence",
		file: "evidence.json",
		label: "Evidence",
		component: "EvidenceView",
		description: "Captured artifacts gallery",
	},
	{
		id: "tasks",
		file: "tasks.json",
		label: "Tasks",
		component: "TasksView",
		description: "Engagement plan & progress",
	},
];

/** Resolves a registry entry by document file name. */
export function documentByFile(file) {
	return ENGAGEMENT_DOCUMENTS.find((entry) => entry.file === file) ?? null;
}

/** Extracts the harness error message from a failed response. */
export async function readErrorBody(response) {
	const body = await response.text();
	try {
		const json = JSON.parse(body);
		return json.message || json.detail || json.error || body || `Request failed with status ${response.status}`;
	} catch {
		return body || `Request failed with status ${response.status}`;
	}
}

/** Joins a workspace directory and a relative document name with the right separator. */
export function joinWorkspacePath(cwd, name) {
	if (!cwd) return name;
	const separator = cwd.includes("\\") ? "\\" : "/";
	return cwd.endsWith(separator) ? cwd + name : cwd + separator + name;
}

/**
 * Fetches an engagement document and parses it as JSON.
 * Missing files surface as errors — scaffolding should have created them.
 */
export async function fetchDocument(file, cwd) {
	const query = new URLSearchParams({ path: joinWorkspacePath(cwd, file) });
	const response = await fetch(`${FILES_API}/content?${query.toString()}`);
	if (!response.ok) throw new Error(await readErrorBody(response));
	const payload = await response.json();
	return JSON.parse(payload.content);
}

/** Serializes and writes an engagement document back to disk. */
export async function saveDocument(file, cwd, data) {
	const response = await fetch(`${FILES_API}/content`, {
		method: "PUT",
		headers: { "Content-Type": "application/json" },
		body: JSON.stringify({ path: joinWorkspacePath(cwd, file), content: JSON.stringify(data, null, 2) + "\n" }),
	});
	if (!response.ok) throw new Error(await readErrorBody(response));
}

/** Returns the first present property among {@param keys}, or {@param fallback}. */
export function firstOf(object, keys, fallback = undefined) {
	for (const key of keys) {
		if (object && object[key] !== undefined && object[key] !== null && object[key] !== "") return object[key];
	}
	return fallback;
}