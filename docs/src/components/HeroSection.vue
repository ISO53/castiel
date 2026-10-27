<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { withBase } from "vitepress";
import { BookOpen, Check, Copy, Download } from "@lucide/vue";
import MeshGradient from "@/components/MeshGradient.vue";
import heroPreset from "@/studio/hero-preset.json";

// The MeshDoc designed in zoxilsi studio ({ app, version, doc } project file).
const heroDoc = heroPreset.doc;

// Install one-liner per detected OS. The installer scripts live in docs/public/
// and are served from the site root: /castiel/install.sh and /castiel/install.ps1.
const INSTALL_BASE = "https://iso53.github.io/castiel";

const COMMANDS = {
	windows: `irm ${INSTALL_BASE}/install.ps1 | iex`,
	linux: `curl -fsSL ${INSTALL_BASE}/install.sh | bash`,
	macos: `curl -fsSL ${INSTALL_BASE}/install.sh | bash`,
};

function detectOs() {
	const ua = navigator.userAgent;
	if (/Mac/i.test(ua)) return "macos";
	if (/Linux/i.test(ua) && !/Android/i.test(ua)) return "linux";
	return "windows";
}

const activeOs = ref(detectOs());
const installCommand = computed(() => COMMANDS[activeOs.value]);

// Hero download button: version-less release asset, mirroring the
// install-section download map (keep the file names in sync).
const DOWNLOAD_BASE = "https://github.com/ISO53/castiel/releases/latest/download";
const DOWNLOAD_FILES = {
	windows: "castiel-windows-x64.zip",
	linux: "castiel-linux-x64.tar.gz",
	macos: "castiel-macos-arm64.zip",
};
const OS_LABELS = { windows: "Windows", linux: "Linux", macos: "macOS" };
const heroDownload = computed(() => ({
	label: `Download for ${OS_LABELS[activeOs.value]}`,
	href: `${DOWNLOAD_BASE}/${DOWNLOAD_FILES[activeOs.value]}`,
}));
const shellLabel = computed(() =>
	activeOs.value === "windows" ? "PowerShell" : "bash",
);

// Prompt line mirrors the visitor's platform, like a fresh castiel start.
const promptLine = computed(() =>
	activeOs.value === "windows" ? "PS C:\\> castiel" : "~ $ castiel",
);

// The startup banner, verbatim from harness/src/main/resources/banner.txt
// (ANSI colors mapped to the a1–a4 shade classes below).
const BANNER_HTML = `<span class="a2">▄</span><span class="a3">▓</span><span class="a2">▀▀▀▀▀</span><span class="a3">▓</span><span class="a2">  </span><span class="a3">▓</span><span class="a2">▀▀▀▀▀</span><span class="a3">▓</span><span class="a2">▄    ▄</span><span class="a3">▓</span><span class="a2">▀▀▀▀</span><span class="a3">▓▓</span><span class="a2"> </span><span class="a3">▓</span><span class="a2">▀▀▀</span><span class="a3">▓▓</span><span class="a2">▀▀▀▀</span><span class="a3">▓</span><span class="a2"> ▀</span><span class="a3">▓▓</span><span class="a2">▀  ▄</span><span class="a3">▓</span><span class="a2">▀▀▀▀▀</span><span class="a3">▓</span><span class="a2"> </span><span class="a3">▓▓</span>
<span class="a2">▒▒                ▒▒  ▒▒     ▒▒     ▒▒       ▒▒  ▒▒        ▒▒</span>
<span class="a2">▄▄          ▄▄▄▄▄▄▄▄   ▄▄▄▄▄▄       ▄▄       ▄▄  ▄▄▄▄▄     ▄▄</span>
<span class="a3">▓▓</span><span class="a2">        ▄</span><span class="a3">▓</span><span class="a2">▀     </span><span class="a3">▓▓</span><span class="a2">        ▀</span><span class="a3">▓</span><span class="a2">▄     </span><span class="a3">▓▓</span><span class="a2">       </span><span class="a3">▓▓</span><span class="a2">  </span><span class="a3">▓▓</span><span class="a2">        </span><span class="a3">▓▓</span>
<span class="a2">▒▒        ▒▒      ▒▒  ▒▒     ▒▒     ▒▒       ▒▒  ▒▒        ▒▒</span>
<span class="a1">░░</span><span class="a2">        </span><span class="a1">░░</span><span class="a2">      </span><span class="a1">░░</span><span class="a2">  </span><span class="a1">░░</span><span class="a2">     </span><span class="a1">░░</span><span class="a2">     </span><span class="a1">░░</span><span class="a2">       </span><span class="a1">░░</span><span class="a2">  </span><span class="a1">░░</span><span class="a2">        </span><span class="a1">░░</span>
<span class="a4">██</span><span class="a2">        </span><span class="a4">██</span><span class="a2">      </span><span class="a4">██</span><span class="a2">  </span><span class="a4">██</span><span class="a2">     </span><span class="a4">██</span><span class="a2">     </span><span class="a4">██</span><span class="a2">       </span><span class="a4">██</span><span class="a2">  </span><span class="a4">██</span><span class="a2">        </span><span class="a4">██</span>
<span class="a1">░</span><span class="a4">█</span><span class="a2">        </span><span class="a1">░</span><span class="a4">█</span><span class="a2">      </span><span class="a1">░</span><span class="a4">█</span><span class="a2">  </span><span class="a1">░</span><span class="a4">█</span><span class="a2">     </span><span class="a1">░</span><span class="a4">█</span><span class="a2">     </span><span class="a1">░</span><span class="a4">█</span><span class="a2">       </span><span class="a1">░</span><span class="a4">█</span><span class="a2">  </span><span class="a1">░</span><span class="a4">█</span><span class="a2">        </span><span class="a1">░</span><span class="a4">█</span>
<span class="a1">░</span><span class="a4">█</span><span class="a2">        </span><span class="a1">░</span><span class="a4">█</span><span class="a2">      </span><span class="a1">░</span><span class="a4">█</span><span class="a2">  </span><span class="a1">░</span><span class="a4">█</span><span class="a2">     </span><span class="a1">░</span><span class="a4">█</span><span class="a2">     </span><span class="a1">░</span><span class="a4">█</span><span class="a2">       </span><span class="a1">░</span><span class="a4">█</span><span class="a2">  </span><span class="a1">░</span><span class="a4">█</span><span class="a2">        </span><span class="a1">░</span><span class="a4">█</span>
<span class="a2"> ▀</span><span class="a4">█</span><span class="a2">▄▄▄▄</span><span class="a1">░</span><span class="a4">█</span><span class="a2">  ▀</span><span class="a4">█</span><span class="a2">▄▄▄▄▄</span><span class="a1">░</span><span class="a4">█</span><span class="a2">▄ </span><span class="a4">██</span><span class="a2">▄▄▄▄</span><span class="a4">█</span><span class="a2">▀      </span><span class="a4">██</span><span class="a2">      ▄</span><span class="a1">░</span><span class="a4">█</span><span class="a2">▄  ▀</span><span class="a4">█</span><span class="a2">▄▄▄▄</span><span class="a1">░</span><span class="a4">█</span><span class="a2">  ▀</span><span class="a4">█</span><span class="a2">▄▄▄▄</span><span class="a1">░</span><span class="a2">▒</span>`;

// Startup log lines, in the Spring Boot console format the harness prints
// (levels colored like the real console: INFO green, PID violet, loggers blue).
const LOG_LINES = [
	{
		time: "21:31:21.246",
		logger: "i.g.iso53.castiel.CastielApplication",
		msg: "Starting CastielApplication v1.15.0 using Java 25.0.4.1",
	},
	{
		time: "21:31:21.271",
		logger: "i.g.iso53.castiel.CastielApplication",
		msg: 'No active profile set, falling back to 1 default profile: "default"',
	},
	{
		time: "21:31:25.534",
		logger: "i.g.iso53.castiel.service.AttackCatalog",
		msg: "Loaded 706 ATT&CK techniques (15 tactics) in 165 ms",
	},
	{
		time: "21:31:25.898",
		logger: "io.github.iso53.castiel.mcp.McpManager",
		msg: "Connected to MCP server 'obscura' (36 tools)",
	},
	{
		time: "21:31:27.913",
		logger: "i.g.i.castiel.service.KaliToolCatalog",
		msg: "Loaded 754 Kali tools and indexed in 172 ms",
	},
	{
		time: "21:31:29.214",
		logger: "i.g.iso53.castiel.config.WebConfig",
		msg: "UI accessible at http://localhost:8081",
	},
	{
		time: "21:31:29.222",
		logger: "i.g.iso53.castiel.CastielApplication",
		msg: "Started CastielApplication in 1.445 seconds (process running for 2.994)",
	},
];

const copied = ref(false);
let copiedTimer = null;

async function copyInstall() {
	try {
		await navigator.clipboard.writeText(installCommand.value);
		copied.value = true;
		clearTimeout(copiedTimer);
		copiedTimer = setTimeout(() => (copied.value = false), 1600);
	} catch {
		// Clipboard unavailable (permissions/insecure context) — ignore.
	}
}

// Startup animation — the session replays on page load: the prompt is typed
// out character by character, the banner prints in one shot and the startup
// logs stream in line by line, like a real terminal booting the app.
const typedPrompt = ref("");
const promptDone = ref(false);
const bannerVisible = ref(false);
const visibleLogCount = ref(0);

const animTimers = [];
const REDUCED_MOTION =
	typeof window !== "undefined" &&
	window.matchMedia("(prefers-reduced-motion: reduce)").matches;

onMounted(() => {
	if (REDUCED_MOTION) {
		typedPrompt.value = promptLine.value;
		promptDone.value = true;
		bannerVisible.value = true;
		visibleLogCount.value = LOG_LINES.length;
		return;
	}

	let elapsed = 0;
	const at = (fn, delay) => animTimers.push(setTimeout(fn, delay));

	// Type the prompt, one character every ~40 ms.
	for (let i = 1; i <= promptLine.value.length; i += 1) {
		const slice = promptLine.value.slice(0, i);
		at(() => (typedPrompt.value = slice), elapsed);
		elapsed += 40;
	}
	at(() => (promptDone.value = true), elapsed + 150);

	// The banner drops in whole, the way ASCII-art banners print.
	elapsed += 450;
	at(() => (bannerVisible.value = true), elapsed);

	// Startup logs stream in, one line per tick.
	elapsed += 450;
	LOG_LINES.forEach((_, index) => {
		at(() => (visibleLogCount.value = index + 1), elapsed);
		elapsed += 320;
	});
});

onBeforeUnmount(() => {
	clearTimeout(copiedTimer);
	animTimers.forEach(clearTimeout);
});
</script>

<template>
	<section class="hero">
		<div class="hero-glow">
			<MeshGradient :doc="heroDoc" />
		</div>

		<div class="hero-inner container">
			<div class="hero-left">
				<h1 class="hero-title">recon<em>.</em> exploit<em>.</em> report<em>.</em></h1>
				<p class="hero-sub">
					castiel is an AI-assisted penetration testing harness that pairs
					your workspace with the language model of your choice.
				</p>

				<div class="hero-actions">
					<a
						class="hero-button primary"
						:href="heroDownload.href"
						target="_blank"
						rel="noopener"
					>
						<Download class="hero-button-icon" aria-hidden="true" />
						<span>{{ heroDownload.label }}</span>
					</a>
					<a
						class="hero-button ghost"
						href="https://github.com/ISO53/castiel#readme"
						target="_blank"
						rel="noopener"
					>
						<BookOpen class="hero-button-icon" aria-hidden="true" />
						<span>Docs</span>
					</a>
				</div>

				<div class="hero-install">
					<div class="install-cmd">
						<span class="cmd-prompt">$</span>
						<code class="cmd-text">{{ installCommand }}</code>
						<button
							class="cmd-copy"
							type="button"
							aria-label="Copy install command"
							@click="copyInstall"
						>
							<Check v-if="copied" class="cmd-copy-icon" />
							<Copy v-else class="cmd-copy-icon" />
						</button>
					</div>
					<p class="install-hint">
						installs the latest release ·
						<a :href="withBase('/#install')">all install options</a>
					</p>
				</div>
			</div>

			<div class="hero-right">
				<div class="term">
					<div class="term-bar">
						<span class="term-dots"><i /><i /><i /></span>
						<span class="term-title">castiel</span>
						<span class="term-os">{{ shellLabel }}</span>
					</div>
					<div class="term-body">
						<p class="term-line">
							<span class="t-prompt">{{ typedPrompt }}</span><span
								v-if="!promptDone"
								class="term-caret"
							>▌</span>
						</p>
						<pre
							class="term-art"
							:class="{ 'term-enter': bannerVisible }"
							:style="{ visibility: bannerVisible ? 'visible' : 'hidden' }"
							aria-hidden="true"
							v-html="BANNER_HTML"
						/>
						<!--
							Every log line is mounted from the start but hidden, so the terminal
							keeps its final height from the first paint; lines appear in place.
						-->
						<p
							v-for="(log, index) in LOG_LINES"
							:key="log.time + log.msg"
							class="term-log"
							:style="{ visibility: index < visibleLogCount ? 'visible' : 'hidden' }"
						>
							<span class="t-time">{{ log.time }}</span>
							<span class="t-info"> INFO </span>
							<span class="t-pid">14140</span>
							<span class="t-dim"> --- [castiel] [  main] </span>
							<span class="t-logger">{{ log.logger }}</span>
							<span class="t-dim"> : </span>
							<span class="t-msg">{{ log.msg }}</span>
						</p>
						<p
							class="term-line term-caret"
							:style="{ visibility: visibleLogCount === LOG_LINES.length ? 'visible' : 'hidden' }"
						>▌</p>
					</div>
				</div>
			</div>
		</div>
	</section>
</template>

<style scoped>
.hero {
	position: relative;
	display: grid;
	place-items: center;
	min-height: 100svh;
	padding: 7rem 0 4rem;
	overflow: hidden;
}

.hero-glow {
	position: absolute;
	inset: 0;
}

.hero-inner {
	position: relative;
	display: grid;
	grid-template-columns: minmax(0, 1.1fr) minmax(0, 1fr);
	gap: 4rem;
	align-items: center;
}

.hero-title {
	margin: 0 0 1.25rem;
	font-size: clamp(2.75rem, 6vw, 4.75rem);
	font-weight: 500;
	line-height: 1.05;
	letter-spacing: -0.03em;
}

.hero-title em {
	font-style: normal;
	color: var(--accent);
}

.hero-sub {
	margin: 0 0 2rem;
	max-width: 34rem;
	color: var(--muted);
	font-size: clamp(1rem, 2vw, 1.15rem);
	font-weight: 400;
	line-height: 1.6;
}

.hero-actions {
	display: flex;
	flex-wrap: wrap;
	gap: 0.75rem;
	margin: 0 0 2rem;
}

/* Mirrors the CTA buttons: solid primary, bordered ghost. */
.hero-button {
	display: inline-flex;
	align-items: center;
	gap: 0.45rem;
	padding: 0.7rem 1.5rem;
	border-radius: 8px;
	font-size: 0.95rem;
	font-weight: 600;
	transition:
		background-color 0.15s ease,
		border-color 0.15s ease,
		color 0.15s ease;
}

.hero-button-icon {
	width: 1rem;
	height: 1rem;
}

.hero-button.primary {
	background: var(--fg);
	color: var(--bg);
}

.hero-button.primary:hover {
	background: #ffffff;
}

.hero-button.ghost {
	border: 1px solid var(--border);
	color: var(--fg);
}

.hero-button.ghost:hover {
	border-color: var(--fg);
}

.hero-install {
	display: flex;
	flex-direction: column;
	gap: 0.6rem;
}

/* One-liner box — visual language matches the install-section terminal. */
.install-cmd {
	display: flex;
	align-items: center;
	gap: 0.75rem;
	padding: 0.8rem 1rem;
	border: 1px solid var(--border);
	border-radius: 8px;
	/* Glass, like the navbar: translucent dark over the blurred mesh. */
	background: rgba(0, 0, 0, 0.55);
	backdrop-filter: blur(14px) saturate(1.2);
	-webkit-backdrop-filter: blur(14px) saturate(1.2);
	font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

.cmd-prompt {
	color: var(--faint);
	user-select: none;
}

.cmd-text {
	flex: 1;
	min-width: 0;
	overflow-x: auto;
	white-space: nowrap;
	font-size: 0.85rem;
	color: var(--fg);
	scrollbar-width: none;
}

.cmd-copy {
	display: inline-flex;
	align-items: center;
	padding: 0.15rem;
	border: none;
	border-radius: 4px;
	background: none;
	color: var(--muted);
	cursor: pointer;
	transition: color 0.15s ease;
}

.cmd-copy:hover {
	color: var(--fg);
}

.cmd-copy-icon {
	width: 1rem;
	height: 1rem;
}

.install-hint {
	margin: 0;
	font-size: 0.8rem;
	color: var(--faint);
}

.install-hint a {
	color: var(--muted);
	text-decoration: underline;
	text-decoration-color: var(--border);
	text-underline-offset: 3px;
	transition:
		color 0.15s ease,
		text-decoration-color 0.15s ease;
}

.install-hint a:hover {
	color: var(--fg);
	text-decoration-color: var(--fg);
}

/* Terminal mock — tokens echo the install-section terminal. */
.term {
	--t-background: oklch(0.145 0 0);
	--t-foreground: oklch(0.985 0 0);
	--t-card: oklch(0.205 0 0);
	--t-muted-foreground: oklch(0.708 0 0);
	--t-border: oklch(1 0 0 / 10%);
	border: 1px solid var(--t-border);
	border-radius: 0.75rem;
	/* Glass, like the navbar: the mesh gradient shows through, blurred. */
	background: rgba(0, 0, 0, 0.55);
	backdrop-filter: blur(14px) saturate(1.2);
	-webkit-backdrop-filter: blur(14px) saturate(1.2);
	overflow: hidden;
	box-shadow:
		0 1px 2px oklch(0 0 0 / 40%),
		0 24px 64px oklch(0 0 0 / 55%);
}

.term-bar {
	display: flex;
	align-items: center;
	gap: 0.75rem;
	padding: 0.6rem 0.875rem;
	border-bottom: 1px solid var(--t-border);
	background: rgba(255, 255, 255, 0.04);
}

.term-dots {
	display: flex;
	gap: 0.4rem;
}

.term-dots i {
	width: 0.6rem;
	height: 0.6rem;
	border-radius: 999px;
	background: color-mix(in srgb, var(--t-foreground) 14%, transparent);
}

.term-title {
	font-size: 0.72rem;
	font-weight: 500;
	color: var(--t-muted-foreground);
}

.term-os {
	margin-left: auto;
	font-size: 0.68rem;
	font-weight: 500;
	color: var(--t-muted-foreground);
}

.term-body {
	padding: 1rem 1.1rem;
	font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
	font-size: 0.7rem;
	line-height: 1.7;
	color: var(--t-foreground);
}

.term-line,
.term-log {
	margin: 0;
	white-space: pre-wrap;
}

.term-log {
	padding-left: 2.5em;
	text-indent: -2.5em; /* hanging indent: wrapped lines align under the message */
}

.t-prompt {
	color: var(--t-foreground);
	font-weight: 600;
}

.t-time {
	color: oklch(0.55 0 0);
}

.t-info {
	color: #3fb950;
}

.t-pid {
	color: #b48eff;
}

.t-dim {
	color: oklch(0.55 0 0);
}

.t-logger {
	color: #4da6d1;
}

.t-msg {
	color: var(--t-foreground);
}

.term-art {
	margin: 0.75rem 0 1rem;
	font-size: 0.62rem;
	line-height: 1;
	user-select: none;
}

/* Banner shades, mapped from the ANSI colors in banner.txt. */
.term-art :deep(.a1) {
	color: #58181c;
}

.term-art :deep(.a2) {
	color: #c33841;
}

.term-art :deep(.a3) {
	color: #e0444d;
}

.term-art :deep(.a4) {
	color: #ff525c;
}

.term-caret {
	color: #ff525c;
	animation: caret-blink 1.1s steps(1) infinite;
}

@keyframes caret-blink {
	50% {
		opacity: 0;
	}
}

/* Entrance for the banner and each streamed startup-log line. */
.term-enter {
	animation: term-enter 0.28s ease both;
}

@keyframes term-enter {
	from {
		opacity: 0;
		transform: translateY(0.25rem);
	}

	to {
		opacity: 1;
		transform: none;
	}
}

@media (prefers-reduced-motion: reduce) {
	.term-caret,
	.term-enter {
		animation: none;
	}
}

@media (max-width: 860px) {
	.hero {
		padding-top: 6rem;
	}

	.hero-inner {
		grid-template-columns: 1fr;
		gap: 2.5rem;
	}
}

</style>
