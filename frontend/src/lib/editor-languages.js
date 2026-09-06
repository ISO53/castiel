// Language support is loaded lazily per file extension. Each import becomes its
// own Vite chunk, so opening the app pays nothing until a matching file opens.
const LANGUAGE_LOADERS = {
	js: () => import("@codemirror/lang-javascript").then((m) => m.javascript()),
	jsx: () => import("@codemirror/lang-javascript").then((m) => m.javascript({ jsx: true })),
	ts: () => import("@codemirror/lang-javascript").then((m) => m.javascript({ typescript: true })),
	tsx: () => import("@codemirror/lang-javascript").then((m) => m.javascript({ typescript: true, jsx: true })),
	mjs: () => import("@codemirror/lang-javascript").then((m) => m.javascript()),
	cjs: () => import("@codemirror/lang-javascript").then((m) => m.javascript()),

	py: () => import("@codemirror/lang-python").then((m) => m.python()),

	html: () => import("@codemirror/lang-html").then((m) => m.html()),
	htm: () => import("@codemirror/lang-html").then((m) => m.html()),
	vue: () => import("@codemirror/lang-html").then((m) => m.html()),
	xml: () => import("@codemirror/lang-html").then((m) => m.html()),
	svg: () => import("@codemirror/lang-html").then((m) => m.html()),

	css: () => import("@codemirror/lang-css").then((m) => m.css()),
	scss: () => import("@codemirror/lang-css").then((m) => m.css()),
	less: () => import("@codemirror/lang-css").then((m) => m.css()),

	json: () => import("@codemirror/lang-json").then((m) => m.json()),
	jsonc: () => import("@codemirror/lang-json").then((m) => m.json()),

	md: () => import("@codemirror/lang-markdown").then((m) => m.markdown()),
	mdx: () => import("@codemirror/lang-markdown").then((m) => m.markdown()),

	c: () => import("@codemirror/lang-cpp").then((m) => m.cpp()),
	h: () => import("@codemirror/lang-cpp").then((m) => m.cpp()),
	cpp: () => import("@codemirror/lang-cpp").then((m) => m.cpp()),
	cc: () => import("@codemirror/lang-cpp").then((m) => m.cpp()),
	hpp: () => import("@codemirror/lang-cpp").then((m) => m.cpp()),
	hh: () => import("@codemirror/lang-cpp").then((m) => m.cpp()),

	java: () => import("@codemirror/lang-cpp").then((m) => m.cpp()),
	cs: () => import("@codemirror/lang-cpp").then((m) => m.cpp()),

	// Legacy stream modes. Small and good enough for config/script files.
	sh: () => import("@codemirror/legacy-modes/mode/shell").then((m) => streamLanguage(m.shell)),
	bash: () => import("@codemirror/legacy-modes/mode/shell").then((m) => streamLanguage(m.shell)),
	zsh: () => import("@codemirror/legacy-modes/mode/shell").then((m) => streamLanguage(m.shell)),
	ps1: () => import("@codemirror/legacy-modes/mode/powershell").then((m) => streamLanguage(m.powerShell)),
	yml: () => import("@codemirror/legacy-modes/mode/yaml").then((m) => streamLanguage(m.yaml)),
	yaml: () => import("@codemirror/legacy-modes/mode/yaml").then((m) => streamLanguage(m.yaml)),
	toml: () => import("@codemirror/legacy-modes/mode/toml").then((m) => streamLanguage(m.toml)),
	conf: () => import("@codemirror/legacy-modes/mode/toml").then((m) => streamLanguage(m.toml)),
	ini: () => import("@codemirror/legacy-modes/mode/toml").then((m) => streamLanguage(m.toml)),
	properties: () => import("@codemirror/legacy-modes/mode/properties").then((m) => streamLanguage(m.properties)),
	sql: () => import("@codemirror/legacy-modes/mode/sql").then((m) => streamLanguage(m.standardSQL)),
	lua: () => import("@codemirror/legacy-modes/mode/lua").then((m) => streamLanguage(m.lua)),
	ruby: () => import("@codemirror/legacy-modes/mode/ruby").then((m) => streamLanguage(m.ruby)),
	go: () => import("@codemirror/legacy-modes/mode/go").then((m) => streamLanguage(m.go)),
	rust: () => import("@codemirror/legacy-modes/mode/rust").then((m) => streamLanguage(m.rust)),
};

import { StreamLanguage } from "@codemirror/language";

function streamLanguage(mode) {
	return StreamLanguage.define(mode);
}

const LANGUAGE_LABELS = {
	js: "JavaScript", jsx: "JavaScript", ts: "TypeScript", tsx: "TypeScript",
	mjs: "JavaScript", cjs: "JavaScript", py: "Python", html: "HTML", htm: "HTML",
	vue: "Vue", xml: "XML", svg: "SVG", css: "CSS", scss: "SCSS", less: "Less",
	json: "JSON", jsonc: "JSON", md: "Markdown", mdx: "Markdown", c: "C",
	h: "C", cpp: "C++", cc: "C++", hpp: "C++", hh: "C++", java: "Java",
	cs: "C#", sh: "Shell", bash: "Shell", zsh: "Shell", ps1: "PowerShell",
	yml: "YAML", yaml: "YAML", toml: "TOML", conf: "Config", ini: "INI",
	properties: "Properties", sql: "SQL", lua: "Lua", ruby: "Ruby",
	go: "Go", rust: "Rust", php: "PHP",
};

export function languageLabel(filename) {
	const ext = String(filename ?? "").split(".").pop().toLowerCase();
	return LANGUAGE_LABELS[ext] ?? "Plain text";
}

/**
 * Resolves the CodeMirror language extension for a filename.
 * @returns {Promise<Extension[]>} empty for unknown extensions (plain text).
 */
export function loadLanguageExtension(filename) {
	const ext = String(filename ?? "").split(".").pop().toLowerCase();
	const loader = LANGUAGE_LOADERS[ext];
	if (!loader) return Promise.resolve([]);
	return loader().then((extension) => [extension]).catch(() => []);
}
