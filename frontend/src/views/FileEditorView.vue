<template>
	<div class="flex h-full min-h-0 flex-col bg-card">
		<header class="flex h-8 shrink-0 items-center justify-between gap-2 border-b px-3">
			<p class="min-w-0 truncate text-xs text-muted-foreground" :title="path">{{ path ?? "" }}</p>
			<Button variant="outline" size="sm" :disabled="!dirty || saving" @click="save">
				{{ saving ? "Saving…" : "Save" }}
			</Button>
		</header>

		<p v-if="error" class="shrink-0 px-3 py-2 text-xs leading-relaxed text-destructive">{{ error }}</p>
		<div v-if="loading" class="flex flex-1 items-center justify-center gap-2 text-xs text-muted-foreground">
			<Spinner class="size-3 shrink-0" /> Loading…
		</div>

		<!-- CodeMirror mounts itself into this container -->
		<div v-show="!loading && !error" ref="editorContainer" class="min-h-0 flex-1 overflow-hidden" />

		<footer v-if="!loading && !error"
			class="flex h-7 shrink-0 items-center gap-3 border-t px-3 text-[11px] text-muted-foreground">
			<span>{{ languageLabel }}</span>
			<span aria-hidden="true">·</span>
			<span>{{ lineCount }} lines</span>
			<span class="ml-auto flex items-center gap-1.5" :class="dirty ? 'text-amber-500' : ''">
				<span class="size-1.5 rounded-full" :class="dirty ? 'bg-amber-500' : 'bg-emerald-500'" />
				{{ dirty ? "Unsaved changes — Ctrl+S to save" : "Saved" }}
			</span>
		</footer>
	</div>
</template>

<script>
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { Compartment, EditorState } from "@codemirror/state";
import { EditorView, keymap, lineNumbers, drawSelection } from "@codemirror/view";
import { defaultKeymap, history, historyKeymap, indentWithTab } from "@codemirror/commands";
import { loadLanguageExtension, languageLabel } from "@/lib/editor-languages";
import { leanEditorExtensions } from "@/lib/editor-theme";
import { useTabsStore } from "@/stores/tabs";

const FILES_API = `${window.location.origin}/api/files`;

export default {
	name: "FileEditorView",

	props: {
		path: { type: String, required: true },
	},

	data() {
		return {
			code: "",
			savedContent: "",
			languageLabel: "Plain text",
			loading: false,
			saving: false,
			error: "",
		};
	},
	computed: {
		dirty() {
			return this.code !== this.savedContent;
		},
		lineCount() {
			if (!this.code) return 0;
			return this.code.split("\n").length;
		},
	},
	watch: {
		path() {
			this.load();
		},
		dirty(dirty) {
			const name = this.path.split(/[\\/]/).pop() ?? this.path;
			useTabsStore().setTabLabel(`file:${this.path}`, dirty ? `${name} •` : name);
		},
	},
	mounted() {
		window.addEventListener("keydown", this.handleWindowKeydown);
		this.load();
	},
	beforeUnmount() {
		window.removeEventListener("keydown", this.handleWindowKeydown);
		this.destroyView();
	},
	methods: {
		// Lean extension set: no autocomplete, no search, no folding — the AI writes
		// the code; humans mostly read it.
		baseExtensions() {
			return [
				lineNumbers(),
				history(),
				drawSelection(),
				EditorView.lineWrapping,
				keymap.of([...defaultKeymap, ...historyKeymap, indentWithTab]),
				EditorView.updateListener.of((update) => {
					if (update.docChanged) {
						this.code = update.state.doc.toString();
					}
				}),
				...leanEditorExtensions,
			];
		},

		async load() {
			if (!this.path) return;
			this.loading = true;
			this.error = "";
			try {
				const query = new URLSearchParams({ path: this.path });
				const response = await fetch(`${FILES_API}/content?${query.toString()}`);
				if (!response.ok) {
					throw new Error(await response.text() || `Request failed with status ${response.status}`);
				}
				const data = await response.json();
				this.code = data.content ?? "";
				this.savedContent = this.code;
				this.languageLabel = languageLabel(this.path);

				const [language] = await Promise.all([loadLanguageExtension(this.path)]);
				this.createView(language);
			} catch (err) {
				this.error = err instanceof Error ? err.message.replace(/^"|"$/g, "") : String(err);
			} finally {
				this.loading = false;
			}
		},

		createView(languageExtensions) {
			this.destroyView();
			const languageCompartment = new Compartment();
			this._view = new EditorView({
				state: EditorState.create({
					doc: this.code,
					extensions: [...this.baseExtensions(), languageCompartment.of(languageExtensions ?? [])],
				}),
				parent: this.$refs.editorContainer,
			});
		},

		destroyView() {
			this._view?.destroy();
			this._view = null;
		},

		async save() {
			if (!this.dirty || this.saving || !this.path) return;
			this.saving = true;
			this.error = "";
			try {
				const response = await fetch(`${FILES_API}/content`, {
					method: "PUT",
					headers: { "Content-Type": "application/json" },
					body: JSON.stringify({ path: this.path, content: this.code }),
				});
				if (!response.ok) {
					throw new Error(await response.text() || `Request failed with status ${response.status}`);
				}
				this.savedContent = this.code;
			} catch (err) {
				this.error = err instanceof Error ? err.message : String(err);
			} finally {
				this.saving = false;
			}
		},

		handleWindowKeydown(event) {
			if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "s") {
				event.preventDefault();
				this.save();
			}
		},
	},
};
</script>
