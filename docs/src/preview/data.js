/**
 * Mock data for the app-preview demo on the landing page.
 * Everything here is fake — no backend, no model, pure scripted playback.
 */

export const WORKSPACE_NAME = "pentest";

/** Mirrors frontend/src/lib/documents.js ENGAGEMENT_DOCUMENTS. */
export const DOCUMENTS = [
	{
		id: "network",
		file: "network.json",
		label: "Network",
		empty: {
			message: "No hosts, segments or domains discovered yet.",
			hint: "As recon fills network.json, the topology will grow here. Click any node for its full details.",
		},
	},
	{
		id: "web",
		file: "web.json",
		label: "Web App",
		empty: {
			message: "No sites or pages discovered yet.",
			hint: "As recon fills web.json, the sitemap and tech stack will grow here.",
		},
	},
	{
		id: "vulnerabilities",
		file: "vulnerabilities.json",
		label: "Vulnerabilities",
		empty: {
			message: "No vulnerabilities recorded yet.",
			hint: "Findings appear here as scanning and exploitation fill vulnerabilities.json.",
		},
	},
	{
		id: "evidence",
		file: "evidence.json",
		label: "Evidence",
		empty: {
			message: "No evidence captured yet.",
			hint: "Screenshots, banners and artifacts land here during the engagement.",
		},
	},
	{
		id: "tasks",
		file: "tasks.json",
		label: "Tasks",
		empty: {
			message: "No tasks planned yet.",
			hint: "The engagement plan and its progress will show up here.",
		},
	},
];

/** Mirrors frontend/src/components/McpPanel.vue. */
export const MCP_SERVERS = [
	{ name: "obscura", tools: 35 },
	{ name: "caido", tools: 66 },
];

/** Mirrors frontend/src/stores/engagement.js ENGAGEMENT_PHASES. */
export const PHASES = [
	{ value: 1, short: "Planning" },
	{ value: 2, short: "Recon" },
	{ value: 3, short: "Scanning" },
	{ value: 4, short: "Exploitation" },
	{ value: 5, short: "Wrap-Up" },
];

export const INITIAL_PROMPT_HEADER =
	"Start a pentest on 10.0.0.0/24. Scope is the internal segment only — n…";

/**
 * The scripted chat session. Beat kinds:
 *   user      — a red user bubble
 *   reasoning — "Thought for N seconds" row (streams, then auto-collapses)
 *   text      — assistant text, typed out
 *   tool      — tool card (running → completed); effect.process adds a
 *               background process, effect.topology fills the network view
 *   effect    — phase / clearProcesses side effects on the other panels
 */
export const DEMO_SCRIPT = [
	{
		t: "user",
		text: "Start a pentest on 10.0.0.0/24. Scope is the internal segment only — no production hosts. Do recon first and keep everything documented.",
	},
	{ t: "pause", ms: 500 },
	{
		t: "reasoning",
		seconds: 3,
		text: "Scope is limited to the internal segment. Check the workspace scaffold, record the scope and rules of engagement in tasks.json, then start host discovery before any enumeration.",
	},
	{
		t: "text",
		text: "Got it. Scope: **10.0.0.0/24**, internal segment only. I'll record the scope and rules of engagement first, then start host discovery.",
	},
	{
		t: "tool",
		name: "write file",
		seconds: 1.2,
		input: "tasks.json",
		output: "tasks.json written — scope, rules of engagement and phase checklist recorded",
	},
	{ t: "pause", ms: 400 },
	{
		t: "text",
		text: "Sweeping the segment for live hosts now — I'll background the scan and report when it's done.",
	},
	{
		t: "tool",
		name: "bash",
		seconds: 2.4,
		input: "nmap -sn -T4 10.0.0.0/24 -oA recon/hosts",
		output:
			"Starting Nmap 7.95 ( https://nmap.org )\nNmap done: 256 IP addresses (3 hosts up) scanned in 2.11 seconds",
		effect: { process: { name: "nmap -sn -T4 10.0.0.0/24" } },
	},
	{
		t: "tool",
		name: "bash",
		seconds: 2.2,
		input: "nmap -sV -p- 10.0.0.1,15,23 -oA recon/services",
		output:
			"10.0.0.1   22/tcp  ssh   OpenSSH 9.6\n           80/tcp  http  nginx 1.27.0\n10.0.0.15  443/tcp https nginx 1.27.0\n           8080/tcp http  Apache Tomcat 9\n10.0.0.23  3306/tcp mysql MySQL 8.0.36",
		effect: { process: { name: "nmap -sV -p- 10.0.0.1,15,23" } },
	},
	{
		t: "tool",
		name: "write file",
		seconds: 1.2,
		input: "network.json",
		output: "network.json written — 1 segment, 3 hosts, 6 services",
		effect: { topology: true, clearProcesses: true },
	},
	{ t: "effect", phase: 2 },
	{
		t: "text",
		text: "Host discovery is done and **network.json** is filled — the topology view is live. Interesting so far: an nginx host on 10.0.0.15 with Tomcat on 8080. Want me to enumerate it?",
	},
	{ t: "user", text: "go ahead" },
	{ t: "pause", ms: 400 },
	{
		t: "reasoning",
		seconds: 2,
		text: "Tomcat manager on 8080 is a common weak point. Probe it, note the response headers, and document the finding in web.json before trying anything louder.",
	},
	{
		t: "tool",
		name: "http request",
		seconds: 1.6,
		input: "GET http://10.0.0.15:8080/manager/html",
		output:
			'HTTP/1.1 401 Unauthorized\nWWW-Authenticate: Basic realm="Tomcat Manager Application"\nServer: Apache Tomcat/9.0.85',
	},
	{
		t: "tool",
		name: "write file",
		seconds: 1.0,
		input: "web.json",
		output: "web.json written — 1 site, 2 endpoints, tech stack recorded",
	},
	{
		t: "text",
		text: "The Tomcat manager app is exposed with basic auth on **10.0.0.15:8080** — documented in web.json. Next I'd try default credentials against it and fingerprint the nginx vhost; say the word and I'll continue.",
	},
];

/** Canned answers when visitors type into the mock input after the demo. */
export const CANNED_REPLIES = [
	"This is where a real session would continue — the model plans, calls tools and keeps every finding in the workspace.",
	"In the real app I'd answer with tool calls against your workspace. Here, everything is a scripted mock.",
	"Try the tabs above the view — Topology and Ports & Services were filled in by the mock run.",
];
