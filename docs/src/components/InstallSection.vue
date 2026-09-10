<script setup>
import { computed, onBeforeUnmount, ref } from "vue";
import { Check, Copy } from "@lucide/vue";

// Installation methods. Each step maps to the terminal command shown for the
// selected OS; hovering a step switches the terminal to that step's command.
const OS_LIST = [
	{ id: "windows", label: "Windows", shell: "PowerShell" },
	{ id: "linux", label: "Linux", shell: "bash" },
];

const TABS = [
	{
		id: "download",
		label: "Download & run",
		requirements: "Requires Java 25+. No Maven, no npm.",
		steps: [
			{
				title: "Grab the latest JAR",
				text: "Download castiel.jar from",
				linkText: "GitHub Releases",
				trail: ".",
				href: "https://github.com/ISO53/castiel/releases/latest",
				commands: {
					windows: "curl.exe -L -o castiel.jar https://github.com/ISO53/castiel/releases/latest/download/castiel.jar",
					linux: "wget https://github.com/ISO53/castiel/releases/latest/download/castiel.jar",
				},
			},
			{
				title: "Run it",
				text: "Start the harness and open http://localhost:8081 in your browser.",
				commands: {
					windows: "java -jar castiel.jar",
					linux: "java -jar castiel.jar",
				},
			},
		],
	},
	{
		id: "build",
		label: "Build from source",
		requirements: "Requires Java 25+, Maven and Node.js 22+.",
		steps: [
			{
				title: "Clone the repository",
				text: "The whole project. Harness, UI and website lives in one repo on",
				linkText: "GitHub",
				trail: ".",
				href: "https://github.com/ISO53/castiel",
				commands: {
					windows: "git clone https://github.com/ISO53/castiel.git",
					linux: "git clone https://github.com/ISO53/castiel.git",
				},
			},
			{
				title: "Build the Vue UI",
				text: "Bundle the frontend into the static assets the harness serves.",
				commands: {
					windows: "cd castiel/frontend; npm install; npm run build",
					linux: "cd castiel/frontend && npm install && npm run build",
				},
			},
			{
				title: "Package the harness",
				text: "Maven wraps the built UI and the backend into one runnable JAR.",
				commands: {
					windows: "cd ../harness; mvn package",
					linux: "cd ../harness && mvn package",
				},
			},
			{
				title: "Run your build",
				text: "Same entry point as the release JAR. Open http://localhost:8081 and start working.",
				commands: {
					windows: "java -jar target/castiel-{version}.jar",
					linux: "java -jar target/castiel-{version}.jar",
				},
			},
		],
	},
];

const activeId = ref("download");
const activeStep = ref(0);
const activeOs = ref("windows");
const copied = ref(false);

const activeTab = computed(() => TABS.find((tab) => tab.id === activeId.value));
const currentCommand = computed(
	() => activeTab.value.steps[activeStep.value]?.commands[activeOs.value] ?? "",
);
const currentShell = computed(
	() => OS_LIST.find((entry) => entry.id === activeOs.value)?.shell ?? "",
);

let copiedTimer = null;

async function copyCommands() {
	try {
		await navigator.clipboard.writeText(currentCommand.value);
		copied.value = true;
		clearTimeout(copiedTimer);
		copiedTimer = setTimeout(() => (copied.value = false), 1600);
	} catch {
		// Clipboard unavailable (permissions/insecure context) — ignore.
	}
}

function selectTab(id) {
	activeId.value = id;
	activeStep.value = 0;
}

onBeforeUnmount(() => clearTimeout(copiedTimer));
</script>

<template>
	<section id="install" class="install container">
		<div class="install-header">
			<h2 class="install-title">Install</h2>
			<p class="install-sub">One JAR, one command, or build it yourself.</p>
		</div>

		<div class="install-tabs" role="tablist" aria-label="Installation method">
			<button
				v-for="tab in TABS"
				:key="tab.id"
				type="button"
				role="tab"
				class="install-tab"
				:class="{ active: tab.id === activeId }"
				:aria-selected="tab.id === activeId"
				@click="selectTab(tab.id)"
			>
				{{ tab.label }}
			</button>
		</div>

		<div class="install-grid">
			<div class="install-steps">
				<ol>
					<li
						v-for="(step, index) in activeTab.steps"
						:key="step.title"
						class="install-step"
						:class="{ active: index === activeStep }"
						@mouseenter="activeStep = index"
						@click="activeStep = index"
					>
						<span class="install-step-num">{{ String(index + 1).padStart(2, "0") }}</span>
						<div>
							<h3 class="install-step-title">{{ step.title }}</h3>
							<p class="install-step-text">
								{{ step.text }}
								<a v-if="step.href" :href="step.href" target="_blank" rel="noopener">{{ step.linkText }}</a>{{ step.trail }}
							</p>
						</div>
					</li>
				</ol>
				<p class="install-requirements">{{ activeTab.requirements }}</p>
			</div>

			<div class="install-terminal">
				<div class="terminal-bar">
					<span class="terminal-dots" aria-hidden="true"><i></i><i></i><i></i></span>
					<span class="terminal-title">{{ currentShell }}</span>
					<div class="terminal-os" role="group" aria-label="Target operating system">
						<button
							v-for="entry in OS_LIST"
							:key="entry.id"
							type="button"
							class="terminal-os-btn"
							:class="{ active: entry.id === activeOs }"
							:aria-pressed="entry.id === activeOs"
							@click="activeOs = entry.id"
						>
							{{ entry.label }}
						</button>
					</div>
					<button class="terminal-copy" type="button" @click="copyCommands">
						<Check v-if="copied" class="terminal-copy-icon" aria-hidden="true" />
						<Copy v-else class="terminal-copy-icon" aria-hidden="true" />
						<span>{{ copied ? "Copied" : "Copy" }}</span>
					</button>
				</div>
				<pre class="terminal-body"><code>{{ currentCommand }}</code></pre>
			</div>
		</div>
	</section>
</template>

<style scoped>
.install {
	margin-block: 0 6rem;
	scroll-margin-top: 5rem;
}

.install-header {
	margin-bottom: 2rem;
}

.install-title {
	margin: 0;
	font-size: clamp(2rem, 5vw, 3rem);
	font-weight: 500;
	letter-spacing: -0.03em;
}

.install-sub {
	margin: 0.5rem 0 0;
	font-size: 0.95rem;
	color: var(--muted);
}

.install-tabs {
	display: inline-flex;
	gap: 0.25rem;
	padding: 0.25rem;
	border: 1px solid var(--border);
	border-radius: 8px;
	background: var(--surface);
	margin-bottom: 1.5rem;
}

.install-tab {
	padding: 0.45rem 1rem;
	border: 1px solid transparent;
	border-radius: 6px;
	background: transparent;
	color: var(--muted);
	font-size: 0.85rem;
	font-weight: 500;
	font-family: inherit;
	cursor: pointer;
	transition:
		background-color 0.15s ease,
		border-color 0.15s ease,
		color 0.15s ease;
}

.install-tab:hover {
	color: var(--fg);
}

.install-tab.active {
	border-color: var(--border);
	background: var(--bg);
	color: var(--fg);
}

.install-grid {
	display: grid;
	grid-template-columns: minmax(0, 5fr) minmax(0, 6fr);
	gap: 2rem;
	align-items: start;
}

/* Left column — numbered steps */
.install-steps ol {
	display: flex;
	flex-direction: column;
	gap: 1.25rem;
	margin: 0;
	padding: 0;
	list-style: none;
}

.install-steps li {
	display: flex;
	gap: 0.875rem;
	cursor: default;
}

.install-step-num {
	padding-top: 0.1rem;
	font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
	font-size: 0.75rem;
	color: var(--faint);
	transition: color 0.15s ease;
}

.install-step.active .install-step-num {
	color: var(--fg);
}

.install-step-title {
	margin: 0 0 0.25rem;
	font-size: 0.95rem;
	font-weight: 600;
	color: var(--fg);
	transition: color 0.15s ease;
}

.install-step:not(.active) .install-step-title {
	color: var(--muted);
}

.install-step-text {
	margin: 0;
	font-size: 0.9rem;
	line-height: 1.65;
	color: var(--muted);
}

.install-step-text a {
	color: var(--fg);
	text-decoration: underline;
	text-decoration-color: var(--border);
	text-underline-offset: 3px;
	transition: text-decoration-color 0.15s ease;
}

.install-step-text a::after {
	content: "↗";
	margin-left: 0.2em;
	font-size: 0.8em;
}

.install-step-text a:hover {
	text-decoration-color: var(--fg);
}

.install-requirements {
	margin: 1.75rem 0 0;
	font-size: 0.8rem;
	color: var(--faint);
}

/* Right column — terminal (tokens echo the app-preview mock) */
.install-terminal {
	/* Mock-app tokens copied verbatim from preview.css. */
	--t-background: oklch(0.145 0 0);
	--t-foreground: oklch(0.985 0 0);
	--t-card: oklch(0.205 0 0);
	--t-muted-foreground: oklch(0.708 0 0);
	--t-border: oklch(1 0 0 / 10%);
	border: 1px solid var(--t-border);
	border-radius: 0.75rem;
	background: var(--t-background);
	overflow: hidden;
	box-shadow:
		0 1px 2px oklch(0 0 0 / 40%),
		0 24px 64px oklch(0 0 0 / 55%);
	font-family: "Inter Variable", ui-sans-serif, system-ui, sans-serif;
}

.terminal-bar {
	display: flex;
	align-items: center;
	gap: 0.75rem;
	padding: 0.6rem 0.875rem;
	border-bottom: 1px solid var(--t-border);
	background: var(--t-card);
}

.terminal-dots {
	display: flex;
	gap: 0.4rem;
}

.terminal-dots i {
	width: 0.6rem;
	height: 0.6rem;
	border-radius: 999px;
	background: color-mix(in srgb, var(--t-foreground) 14%, transparent);
}

.terminal-title {
	font-size: 0.72rem;
	font-weight: 500;
	color: var(--t-muted-foreground);
}

.terminal-os {
	display: inline-flex;
	gap: 0.15rem;
	margin-left: auto;
	padding: 0.15rem;
	border: 1px solid var(--t-border);
	border-radius: 6px;
}

.terminal-os-btn {
	padding: 0.15rem 0.5rem;
	border: none;
	border-radius: 4px;
	background: none;
	color: var(--t-muted-foreground);
	font-size: 0.68rem;
	font-weight: 500;
	font-family: inherit;
	cursor: pointer;
	transition:
		background-color 0.15s ease,
		color 0.15s ease;
}

.terminal-os-btn:hover {
	color: var(--t-foreground);
}

.terminal-os-btn.active {
	background: color-mix(in srgb, var(--t-foreground) 12%, transparent);
	color: var(--t-foreground);
}

.terminal-copy {
	display: inline-flex;
	align-items: center;
	gap: 0.3rem;
	border: none;
	background: none;
	padding: 0.1rem 0.2rem;
	color: var(--t-muted-foreground);
	font-size: 0.7rem;
	font-weight: 500;
	font-family: inherit;
	cursor: pointer;
	transition: color 0.15s ease;
}

.terminal-copy:hover {
	color: var(--t-foreground);
}

.terminal-copy-icon {
	width: 0.8rem;
	height: 0.8rem;
}

.terminal-body {
	margin: 0;
	padding: 1rem 1.1rem;
	overflow-x: auto;
	font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
	font-size: 0.78rem;
	line-height: 1.7;
	color: var(--t-foreground);
	white-space: pre;
	scrollbar-width: thin;
	scrollbar-color: var(--t-muted-foreground) transparent;
}

@media (max-width: 860px) {
	.install-grid {
		grid-template-columns: 1fr;
		gap: 1.5rem;
	}
}
</style>
