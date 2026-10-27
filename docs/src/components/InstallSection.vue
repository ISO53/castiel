<script setup>
import { computed, ref } from "vue";

// Installation methods. The OS tabs switch the download asset, hint and
// one-liner command shown below.
const OS_LIST = [
	{ id: "windows", label: "Windows" },
	{ id: "linux", label: "Linux" },
	{ id: "macos", label: "macOS" },
];

// Version-less asset names keep these URLs stable across releases.
const DOWNLOAD_BASE = "https://github.com/ISO53/castiel/releases/latest/download";

const DOWNLOADS = {
	windows: {
		label: "Download for Windows",
		file: "castiel-windows-x64.zip",
		hint: "Expand-Archive castiel.zip, then run .\\castiel\\castiel.exe",
	},
	linux: {
		label: "Download for Linux",
		file: "castiel-linux-x64.tar.gz",
		hint: "tar -xzf castiel-linux-x64.tar.gz, then run ./castiel/castiel",
	},
	macos: {
		label: "Download for macOS",
		file: "castiel-macos-arm64.zip",
		hint: "Unzip, then open castiel.app — right-click → Open on first launch (unsigned build).",
	},
};

function detectOs() {
	const ua = navigator.userAgent;
	if (/Mac/i.test(ua)) return "macos";
	if (/Linux/i.test(ua) && !/Android/i.test(ua)) return "linux";
	return "windows";
}

const activeOs = ref(detectOs());

const activeDownload = computed(() => DOWNLOADS[activeOs.value]);
</script>

<template>
	<section id="install" class="install container">
		<div class="install-header">
			<h2 class="install-title">Install</h2>
			<p class="install-sub">Download, extract, run. No Java, no Maven, no npm.</p>
		</div>

		<div class="install-tabs" role="tablist" aria-label="Operating system">
			<button
				v-for="entry in OS_LIST"
				:key="entry.id"
				type="button"
				role="tab"
				class="install-tab"
				:class="{ active: entry.id === activeOs }"
				:aria-selected="entry.id === activeOs"
				@click="activeOs = entry.id"
			>
				{{ entry.label }}
			</button>
		</div>

		<div class="download-actions">
			<a
				class="download-button"
				:href="`${DOWNLOAD_BASE}/${activeDownload.file}`"
				target="_blank"
				rel="noopener"
			>
				{{ activeDownload.label }}
			</a>
			<p class="download-hint">{{ activeDownload.hint }}</p>
		</div>
	</section>
</template>

<style scoped>
.install {
	margin-block: 0 6rem;
	scroll-margin-top: 5rem;
	text-align: center;
}

.download-actions {
	margin-top: 1.5rem;
}

.download-button {
	display: inline-block;
	padding: 0.85rem 2.25rem;
	border-radius: 8px;
	background: var(--fg);
	color: var(--bg);
	font-size: 0.95rem;
	font-weight: 500;
	font-family: inherit;
	text-decoration: none;
	transition: background-color 0.15s ease;
}

.download-button:hover {
	background: #ffffff;
}

.download-hint {
	margin: 0.75rem 0 0;
	font-size: 0.8rem;
	color: var(--faint);
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
</style>
