/**
 * Mock data for the app-preview demo on the landing page.
 * Everything here is fake — no backend, no model, pure scripted playback.
 */

export const WORKSPACE_NAME = "trial";

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
];

/** Mirrors frontend/src/components/McpPanel.vue. */
export const MCP_SERVERS = [
	{ name: "obscura", tools: 35, enabled: true },
	{ name: "caido", tools: 0, enabled: false },
];

/** Mirrors frontend/src/stores/engagement.js ENGAGEMENT_PHASES. */
export const PHASES = [
	{ value: 1, short: "Planning" },
	{ value: 2, short: "Recon" },
	{ value: 3, short: "Scanning" },
	{ value: 4, short: "Exploitation" },
];

export const INITIAL_PROMPT =
	"Run a full external pentest against meridianfreight.com — the apex domain and every subdomain are in scope. Enumerate the attack surface, map the network and web app, test what you find, and keep the workspace documents up to date as you go. No social engineering, no DoS.";

export const INITIAL_PROMPT_HEADER = INITIAL_PROMPT;

/** Composer footer chips (mirrors ChatView's model/reasoning/loop row). */
export const MODEL_LABEL = "minimax/minimax-m2";
export const CONTEXT_USED = 56.1;

/** Severity bands mirrored from frontend/src/lib/vulns.js. */
export const VULN_BANDS = [
	{ id: "critical", label: "Critical", min: 9, max: 10, color: "#ef4444" },
	{ id: "high", label: "High", min: 7, max: 9, color: "#f97316" },
	{ id: "medium", label: "Medium", min: 4, max: 7, color: "#eab308" },
	{ id: "low", label: "Low", min: 0.1, max: 4, color: "#38bdf8" },
	{ id: "info", label: "Info", min: 0, max: 0.1, color: "#71717a" },
];

/** The mock engagement company: every view draws from this fictional org. */
export const EVIDENCE_ARTIFACTS = [
	{ name: "nmap-full-scan.txt", kind: "scan", title: "Full TCP port scan of the application tier", target: "203.0.113.10-203.0.113.40", vulnerability: "legacy-surface-exposure" },
	{ name: "dns-enumeration.txt", kind: "scan", title: "Passive subdomain and DNS record enumeration", target: "meridianfreight.com", vulnerability: "legacy-surface-exposure" },
	{ name: "nuclei-scan-report.txt", kind: "scan", title: "Nuclei template scan of the api-v1 origin", target: "https://api-v1.meridianfreight.com", vulnerability: "actuator-exposure-v1" },
	{ name: "waf-fingerprint.txt", kind: "log", title: "Cloudflare WAF and edge fingerprinting notes", target: "www.meridianfreight.com", vulnerability: "rate-limit-bypass-origin" },
	{ name: "actuator-env-dump.txt", kind: "capture", title: "Unauthenticated Spring Actuator environment dump", target: "https://api-v1.meridianfreight.com", vulnerability: "secrets-in-actuator" },
	{ name: "ghostcat-ajp-read.txt", kind: "poc", title: "Ghostcat (CVE-2020-1938) AJP file read proof of concept", target: "https://api-v1.meridianfreight.com", vulnerability: "ghostcat-ajp-v1" },
	{ name: "sqli-tracking-response.txt", kind: "capture", title: "Unauthenticated SQL injection in the tracking parameter", target: "https://api-v1.meridianfreight.com", vulnerability: "sqli-tracking-v1" },
	{ name: "portal-jwt-claims.txt", kind: "log", title: "Decoded JWT claims from the portal session cookie", target: "https://portal.meridianfreight.com", vulnerability: "weak-jwt-signing-v1" },
	{ name: "vendor-credential-spray.txt", kind: "log", title: "Credential spray against the vendor admin console", target: "https://vendor.meridianfreight.com", vulnerability: "default-credentials-v1" },
	{ name: "tomcat-manager-probe.txt", kind: "capture", title: "Tomcat manager application probe and auth challenge", target: "http://legacy.meridianfreight.com:8009", vulnerability: "" },
	{ name: "security-headers.txt", kind: "log", title: "Security header matrix across the four vhosts", target: "www.meridianfreight.com", vulnerability: "" },
	{ name: "dir-traversal-poc.txt", kind: "poc", title: "Directory traversal proof of concept on the asset handler", target: "https://www.meridianfreight.com", vulnerability: "path-traversal-assets-v1" },
	{ name: "staging-tls-chain.txt", kind: "scan", title: "TLS certificate chain capture on the staging origin", target: "https://staging.meridianfreight.com", vulnerability: "staging-tls-chain-v1" },
	{ name: "verbose-error-sample.txt", kind: "capture", title: "Verbose stack trace captured from the SSO error page", target: "https://sso.meridianfreight.com", vulnerability: "verbose-errors-v1" },
];

/** 15 findings: 3 critical · 7 high · 3 medium · 1 low · 1 info — 14 proved · 1 potential. */
export const VULNERABILITIES = [
	{ proof: "potential", score: 9.8, title: "Remote code execution via AJP file write into the writable deploy target", target: "https://api-v1.meridianfreight.com", evidence: 2 },
	{ proof: "proved", score: 9.4, title: "Default and weak credentials accepted by the multi-tenant admin console", target: "https://vendor.meridianfreight.com", evidence: 2 },
	{ proof: "proved", score: 9.1, title: "Guessable HS256 JWT signing secret on the portal subdomain", target: "https://portal.meridianfreight.com", evidence: 2 },
	{ proof: "proved", score: 8.7, title: "Unauthenticated Spring Boot Actuator exposes environment secrets", target: "https://api-v1.meridianfreight.com", evidence: 3 },
	{ proof: "proved", score: 8.6, title: "Unauthenticated SQL injection in the tracking identifier parameter", target: "https://api-v1.meridianfreight.com", evidence: 2 },
	{ proof: "proved", score: 8.2, title: "Legacy surface exposes unauthenticated administration endpoints", target: "http://legacy.meridianfreight.com", evidence: 2 },
	{ proof: "proved", score: 7.8, title: "Actuator v3 exposure permits internal service and route enumeration", target: "https://api-v1.meridianfreight.com", evidence: 2 },
	{ proof: "proved", score: 7.6, title: "Ghostcat AJP file read discloses web.xml and application secrets", target: "https://api-v1.meridianfreight.com", evidence: 2 },
	{ proof: "proved", score: 7.5, title: "Stored XSS in the vendor contact form survives client-side sanitisation", target: "https://vendor.meridianfreight.com", evidence: 1 },
	{ proof: "proved", score: 7.4, title: "Directory traversal in the static asset handler reads arbitrary files", target: "https://www.meridianfreight.com", evidence: 2 },
	{ proof: "proved", score: 6.5, title: "Verbose error responses disclose framework versions and internal routes", target: "https://sso.meridianfreight.com", evidence: 1 },
	{ proof: "proved", score: 5.8, title: "TLS certificate chain presents an expired intermediate on the staging origin", target: "https://staging.meridianfreight.com", evidence: 1 },
	{ proof: "proved", score: 5.3, title: "Missing security headers on the legacy vhost responses", target: "http://legacy.meridianfreight.com", evidence: 1 },
	{ proof: "proved", score: 2.1, title: "Session cookie lacks the Secure flag on an internal-only endpoint", target: "https://portal.meridianfreight.com", evidence: 1 },
	{ proof: "proved", score: 0.0, title: "Rate limit bypass at the origin permits unrestricted login attempts", target: "https://www.meridianfreight.com", evidence: 2 },
];

/** Mirrors frontend/src/components/views/panels/TechStackPanel.vue sections. */
export const TECH_STACK_SECTIONS = [
	{
		name: "Frontend",
		color: "#ef4444",
		techs: [{ name: "Statuspage", desc: "monitoring · not stated" }],
	},
	{
		name: "Backend",
		color: "#a78bfa",
		techs: [
			{ name: "Fastly", desc: "cdn/waf · not stated" },
			{ name: "Tomcat AJP 1.3", desc: "web-server · AJP/1.3 on port 8009" },
			{ name: "Spring Boot", desc: "framework · Actuator v3 (content type application/vnd.spring-boot.actuator.v3+json)" },
			{ name: "Java 17.0.9", desc: "language · 17.0.9" },
			{ name: "PostgreSQL 14.8", desc: "database · 14.8" },
			{ name: "Redis 7.2.4", desc: "message-queue · 7.2.4" },
		],
	},
];

/**
 * The four sub-agent runs the demo spawns, keyed by the document they own.
 * `display` is the pre-truncated id the dock shows; `seed` offsets the runtime
 * clock so live rows read like a session a few minutes in.
 */
export const SUB_AGENT_RUNS = {
	network: {
		id: "agent-mvgx42k1a-d",
		display: "agent-mvg…-d…",
		seed: 262,
		task: "Enumerate the meridianfreight.com attack surface: resolve every subdomain you discover, group live hosts into network segments, record domains and DNS records in network.json, then validate the topology before your DONE answer.",
	},
	web: {
		id: "agent-mvgx42k1b-6",
		display: "agent-mvg…-6…",
		seed: 261,
		task: "Crawl every web site in scope: record the site map, entry points, API endpoints and the fingerprinted tech stack in web.json, validating each entry against a live response before your DONE answer.",
	},
	vulnerabilities: {
		id: "agent-mvgx42k1c-6",
		display: "agent-mvg…-6…",
		seed: 259,
		task: "Test the discovered surface for exploitable weaknesses. Record every finding in vulnerabilities.json with a CVSS 4.0 score, proof status and evidence reference, then validate the register before your DONE answer.",
	},
	evidence: {
		id: "agent-mvgx42k1e-f",
		display: "agent-mvg…-f…",
		seed: 250,
		task: "Capture evidence for every action taken: scans, request/response captures, logs and PoC scripts. Register each artifact in evidence.json with its target and linked vulnerability, then validate before your DONE answer.",
	},
};

/**
 * The scripted chat session. Beat kinds:
 *   user      — a red user bubble
 *   reasoning — "Thought for N seconds" row (streams, then auto-collapses)
 *   text      — assistant text, typed out (**bold** and `code` render inline)
 *   tool      — tool call card; `input` renders as params, `output` as result,
 *               `stayOpen` keeps the card expanded. effect.agent spawns a
 *               sub-agent row, effect.agentDone settles one, effect.doc /
 *               effect.subtab fill a document view and switch the center to it
 *   pause     — delay only
 */
export const DEMO_SCRIPT = [
	{ t: "user", text: INITIAL_PROMPT },
	{ t: "pause", ms: 500 },
	{
		t: "reasoning",
		seconds: 3,
		text: "Scope confirmed: apex domain plus every subdomain, no social engineering. I'll fan out four sub-agents — network, web, vulnerabilities and evidence — and validate every cross-reference before reporting back.",
	},
	{
		t: "text",
		text: "Starting the engagement. Evidence first — every scan, capture and PoC gets registered as we go, so findings can reference real artifacts. Spawning four sub-agents; I'll validate each document as it lands.",
		effect: { agent: "evidence" },
	},
	{
		t: "tool",
		name: "sub_agent",
		seconds: 1.2,
		input: { profile: "worker", task: SUB_AGENT_RUNS.vulnerabilities.task },
		effect: { agent: "vulnerabilities" },
	},
	{
		t: "tool",
		name: "sub_agent",
		seconds: 1.2,
		input: { profile: "worker", task: SUB_AGENT_RUNS.web.task },
		effect: { agent: "web" },
	},
	{
		t: "tool",
		name: "sub_agent",
		seconds: 1.2,
		input: { profile: "worker", task: SUB_AGENT_RUNS.network.task },
		effect: { agent: "network" },
	},
	{ t: "pause", ms: 700 },
	{
		t: "tool",
		name: "bash",
		seconds: 1.6,
		input: { command: 'cd "C:\\Users\\termi\\Desktop\\trial"; Get-ChildItem *.json | Select-Object Name, Length' },
		output:
			"evidence.json          41208\nvulnerabilities.json   ----\nweb.json               ----\nnetwork.json           ----",
	},
	{
		t: "text",
		text: "Evidence file is written; that agent is still in its verification pass. The other three haven't written yet — they're reading all 14 evidence files first. Waiting.",
		effect: { doc: "evidence" },
	},
	{
		t: "tool",
		name: "agent_list",
		seconds: 1.0,
		input: {},
		output:
			"agent-mvgx42k1e-f  evidence         DONE      registry verified\nagent-mvgx42k1c-6  vulnerabilities  RUNNING   reading evidence refs\nagent-mvgx42k1b-6  web              RUNNING   mapping sites\nagent-mvgx42k1a-d  network          RUNNING   mapping topology",
		effect: { agentDone: "evidence" },
	},
	{
		t: "text",
		text: "vulnerabilities.json just landed at 66KB. Let me validate it while network and web continue.",
		effect: { doc: "vulnerabilities", agentDone: "vulnerabilities" },
	},
	{
		t: "tool",
		name: "bash",
		seconds: 1.8,
		input: {
			command:
				'cd "C:\\Users\\termi\\Desktop\\trial"; python -c "import json; d=json.load(open(\'vulnerabilities.json\', encoding=\'utf-8\')); print(len(d[\'vulnerabilities\']), \'findings\')"',
		},
		output: "15 findings",
	},
	{
		t: "reasoning",
		seconds: 2,
		text: "Register validates: 15 findings, differentiated scores, all evidence refs valid. One consistency check left on the zero-score entry.",
	},
	{
		t: "text",
		text: "Register validates: **15 findings**, differentiated scores, all evidence refs valid. One consistency check on the zero-score entry.",
	},
	{
		t: "tool",
		name: "bash",
		seconds: 1.6,
		input: {
			command:
				'cd "C:\\Users\\termi\\Desktop\\trial"; python -c "import json; d=json.load(open(\'vulnerabilities.json\', encoding=\'utf-8\')); print([v[\'name\'] for v in d[\'vulnerabilities\'] if v[\'cvss\'][\'4.0\'][\'score\'] == 0])"',
		},
		output: "['rate-limit-bypass-origin']",
	},
	{
		t: "text",
		text: "All correct — `rate-limit-bypass-origin` correctly carries a 0.0 informational vector, and the RCE variant is honestly marked `potential` with a hypothesis while the file-read path is `proved`. Waiting on network.json and web.json.",
	},
	{
		t: "tool",
		name: "bash",
		seconds: 1.8,
		input: { command: 'Start-Sleep -Seconds 55; Write-Output "waited"', timeoutMs: 60000 },
		output: "waited",
		stayOpen: true,
	},
	{
		t: "text",
		text: "web.json landed at 115KB. Two agents done, one still writing. Let me collect the finished ones and validate web.json.",
		effect: { doc: "web", subtab: "tech", agentDone: "web" },
	},
	{
		t: "tool",
		name: "agent_read",
		seconds: 1.2,
		input: { runId: "agent-mvgx4k21-6066" },
		output: "web.json written — 4 sites, 23 endpoints, 7 entry points, tech stack recorded",
	},
	{
		t: "tool",
		name: "agent_read",
		seconds: 1.2,
		input: { runId: "agent-mvgx4k2v-f113" },
		output: "vulnerabilities.json written — 15 findings, 14 proved · 1 potential",
	},
	{
		t: "tool",
		name: "bash",
		seconds: 1.6,
		input: {
			command:
				'cd "C:\\Users\\termi\\Desktop\\trial"; python -c "import json; d=json.load(open(\'web.json\', encoding=\'utf-8\')); print(len(d[\'sites\']), \'sites\')"',
		},
		output: "4 sites",
	},
	{
		t: "text",
		text: "Both agents completed cleanly with cross-validation. Note: the agents left scratch scripts in the workspace root — I'll clean those up. Let me check the network agent and the workspace state.",
	},
	{
		t: "tool",
		name: "bash",
		seconds: 1.4,
		input: { command: 'cd "C:\\Users\\termi\\Desktop\\trial"; Remove-Item .scratch-*.py -ErrorAction SilentlyContinue; Get-ChildItem *.json | Select-Object Name, Length' },
		output:
			"evidence.json          41208\nvulnerabilities.json   66055\nweb.json               115412\nnetwork.json           ----",
	},
	{
		t: "tool",
		name: "agent_list",
		seconds: 1.0,
		input: {},
		output:
			"agent-mvgx42k1e-f  evidence         DONE      registry verified\nagent-mvgx42k1c-6  vulnerabilities  DONE      register verified\nagent-mvgx42k1b-6  web              DONE      map verified\nagent-mvgx42k1a-d  network          RUNNING   resolving DNS records",
	},
	{
		t: "text",
		text: "network.json is the last one in flight. The topology agent is the slowest — it resolves every DNS record it found before writing. Give it a moment.",
		effect: { doc: "network", subtab: "topology", agentDone: "network" },
	},
	{
		t: "text",
		text: "All four documents are in and cross-validated — **network.json** (topology), **web.json** (map + tech stack), **vulnerabilities.json** (15 findings, 14 proved · 1 potential) and **evidence.json** (14 artifacts). Ready to move into exploitation — say the word and I'll start chaining the proved findings.",
	},
];

/** Canned answers when visitors type into the mock input after the demo. */
export const CANNED_REPLIES = [
	"Next up is exploitation: I'd chain the Actuator exposure into the AJP write path for RCE, keeping every step captured as evidence.",
	"Pick a finding and I'll pull up its evidence chain, or say the word and I'll start working the proved ones in CVSS order.",
	"I can also re-run the scan with the credentialed access you just provided — the register and evidence registry would update live.",
];
