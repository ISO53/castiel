import { EditorView } from "@codemirror/view";
import { HighlightStyle, syntaxHighlighting } from "@codemirror/language";
import { tags as t } from "@lezer/highlight";

/**
 * Minimal CodeMirror theme wired to the app's CSS variables so light/dark follow
 * the UI automatically. The highlight palette uses fixed hues that read well on
 * both themes. Deliberately tiny compared to full theme packages.
 */
export const castielEditorTheme = EditorView.theme({
	"&": {
		height: "100%",
		color: "var(--foreground)",
		backgroundColor: "var(--card)",
		fontSize: "12px",
	},
	"&.cm-focused": { outline: "none" },
	".cm-scroller": {
		overflow: "auto",
		fontFamily: "var(--font-mono, ui-monospace, monospace)",
		lineHeight: "1.6",
	},
	".cm-content": { caretColor: "var(--primary)" },
	".cm-cursor, .cm-dropCursor": { borderLeftColor: "var(--primary)" },
	"&.cm-focused .cm-selectionBackground, .cm-selectionBackground, .cm-content ::selection":
		{ backgroundColor: "color-mix(in srgb, var(--primary) 22%, transparent)" },
	".cm-activeLine": { backgroundColor: "color-mix(in srgb, var(--muted) 45%, transparent)" },
	".cm-gutters": {
		backgroundColor: "transparent",
		color: "var(--muted-foreground)",
		border: "none",
		borderRight: "1px solid var(--border)",
	},
	".cm-activeLineGutter": { backgroundColor: "color-mix(in srgb, var(--muted) 60%, transparent)" },
}, { dark: document.documentElement.classList.contains("dark") });

export const castielHighlightStyle = HighlightStyle.define([
	{ tag: t.comment, color: "#7d8590", fontStyle: "italic" },
	{ tag: [t.keyword, t.moduleKeyword], color: "#c678dd" },
	{ tag: [t.controlKeyword, t.moduleKeyword], color: "#c678dd" },
	{ tag: [t.definitionKeyword, t.modifier], color: "#e5c07b" },
	{ tag: [t.string, t.special(t.string)], color: "#98c379" },
	{ tag: [t.number, t.bool, t.null, t.atom], color: "#d19a66" },
	{ tag: [t.function(t.variableName), t.function(t.propertyName)], color: "#61afef" },
	{ tag: [t.typeName, t.className, t.namespace], color: "#e5c07b" },
	{ tag: [t.variableName, t.propertyName], color: "#e06c75" },
	{ tag: [t.operator, t.punctuation, t.separator], color: "#56b6c2" },
	{ tag: [t.regexp, t.escape], color: "#56b6c2" },
	{ tag: t.heading, color: "#61afef", fontWeight: "bold" },
	{ tag: t.link, color: "#98c379", textDecoration: "underline" },
	{ tag: t.invalid, color: "#e06c75" },
]);

/** Static base extensions every editor instance gets (no autocomplete/search). */
export const leanEditorExtensions = [
	castielEditorTheme,
	syntaxHighlighting(castielHighlightStyle),
];
