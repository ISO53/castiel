<template>
	<section class="flex h-full min-h-0 flex-col bg-background">
		<header class="flex h-8 shrink-0 items-center justify-between gap-2 border-b px-3">
			<div class="min-w-0">
				<p class="truncate text-xs font-medium text-foreground">{{ headerTitle }}</p>
			</div>

			<DropdownMenu>
				<DropdownMenuTrigger as-child>
					<Button variant="ghost" size="icon-sm" :disabled="streaming" aria-label="Start a new chat">
						<Plus />
					</Button>
				</DropdownMenuTrigger>
				<DropdownMenuContent align="end" class="w-64!">
					<DropdownMenuLabel>Start a new chat with the providers below</DropdownMenuLabel>
					<DropdownMenuSeparator />
					<DropdownMenuItem v-for="provider in providers" :key="provider.id"
						@select="startChat(provider.id)">
						<img v-if="providerLogo(provider.id)" :src="providerLogo(provider.id)"
							class="size-3.5 shrink-0" alt="" aria-hidden="true" />
						{{ provider.id }}
					</DropdownMenuItem>
					<DropdownMenuItem v-if="!providers.length" disabled>No providers configured</DropdownMenuItem>
				</DropdownMenuContent>
			</DropdownMenu>
		</header>

		<Conversation class="min-h-0" aria-label="Chat messages">
			<ConversationContent class="gap-4 px-3 py-4">
							<div v-if="!providerId"
								class="m-auto max-w-56 text-center text-xs leading-relaxed text-muted-foreground">
								Start a new chat and choose one of your configured providers.
							</div>
							<div v-else-if="loadingModels"
								class="m-auto flex items-center gap-2 text-xs text-muted-foreground">
								<Spinner class="size-3" /> Loading models…
							</div>
							<div v-else-if="!models.length && !error"
								class="m-auto max-w-56 text-center text-xs leading-relaxed text-muted-foreground">
								This provider did not report any available models.
							</div>
							<div v-else-if="!modelName"
								class="m-auto max-w-56 text-center text-xs leading-relaxed text-muted-foreground">
								Choose a model below to start chatting.
							</div>

							<Message v-for="message in messages" :key="message.id"
								:align="message.role === 'user' ? 'end' : 'start'">
								<MessageContent>
									<div v-if="message.role === 'user'"
										class="max-w-[90%] self-end whitespace-pre-wrap rounded-lg bg-primary px-3 py-2 text-xs leading-relaxed text-primary-foreground">
										{{ messageText(message) }}
									</div>
									<template v-for="(part, partIndex) in message.parts" v-else :key="partIndex">
										<!-- Thinking/reasoning segment -->
										<Reasoning v-if="part.type === 'thinking'" class="w-full self-start"
											:is-streaming="streaming && message.isThinking
												&& partIndex === message.parts.length - 1">
											<ReasoningTrigger />
											<ReasoningContent :content="part.text"
												class="text-xs leading-relaxed opacity-60" />
										</Reasoning>

										<!-- Tool call segment -->
										<template v-else-if="part.type === 'tool'">
											<Tool v-if="!isPendingQuestion(part)" v-model:open="part.open"
												class="w-full self-start">
												<ToolHeader :state="toolState(part)" :title="part.name"
													:type="`tool-${part.name}`" />
												<ToolContent>
													<ToolInput :input="toolInput(part)" />
													<ToolOutput v-if="part.result !== null" :output="part.result" />
												</ToolContent>
											</Tool>

											<Sources v-if="!isPendingQuestion(part) && sourcesFor(part).length"
												class="w-full self-start">
												<SourcesTrigger :count="sourcesFor(part).length" />
												<SourcesContent>
													<Source v-for="source in sourcesFor(part)" :key="source.href"
														:href="source.href" :title="source.title" class="text-xs" />
												</SourcesContent>
											</Sources>

											<Questionnaire v-if="isPendingQuestion(part)"
												class="w-full max-w-md self-start py-1" default-item="q"
												:items="questionnaireItems(part)" shortcuts="letters"
												@submit="submitAnswer($event, part)">
												<QuestionnaireProgress />
												<QuestionnaireItem name="q" required :multiple="part.multiSelect">
													<QuestionnaireTitle>{{ part.question }}</QuestionnaireTitle>
													<QuestionnaireDescription>
														Choose an answer{{ part.multiSelect ? " (multiple allowed)" : "" }},
														or type your own under Other.
													</QuestionnaireDescription>
													<QuestionnaireChoices>
														<QuestionnaireChoice v-for="option in part.options" :key="option"
															:value="option">
															<span class="font-medium">{{ option }}</span>
														</QuestionnaireChoice>
													</QuestionnaireChoices>
													<div class="flex flex-col gap-1.5 pt-1">
														<p class="text-[11px] font-medium">Other</p>
														<QuestionnaireInput placeholder="Type a custom answer�" />
													</div>
													<QuestionnaireError />
												</QuestionnaireItem>
												<QuestionnaireActions>
													<Button variant="outline" size="sm" @click="dismissQuestion(part)">
														Dismiss
													</Button>
													<QuestionnaireSubmit>Send answer</QuestionnaireSubmit>
												</QuestionnaireActions>
											</Questionnaire>
										</template>

										<!-- Assistant text segment (markdown + code blocks) -->
										<div v-else
											class="flex min-w-0 max-w-[90%] flex-col items-start gap-2 self-start text-foreground">
											<template v-for="(segment, segmentIndex) in contentSegments(part.text)"
												:key="segmentIndex">
												<div v-if="segment.type === 'text'" class="typeset typeset-docs w-full"
													v-html="segment.html" />
												<CodeBlock v-else class="w-full" :code="segment.code"
													:language="segment.language">
													<CodeBlockHeader>
														<CodeBlockTitle>
															<CodeBlockFilename>{{ segment.filename }}</CodeBlockFilename>
														</CodeBlockTitle>
														<CodeBlockActions>
															<CodeBlockCopyButton />
														</CodeBlockActions>
													</CodeBlockHeader>
												</CodeBlock>
											</template>
										</div>
									</template>
								</MessageContent>
							</Message>

							<Loader v-if="assistantIdle" class="mx-auto" />
							</ConversationContent>
							<ConversationScrollButton />
						</Conversation>

		<div class="shrink-0 border-t p-3">
			<p v-if="error" class="mb-2 text-xs text-destructive">{{ error }}</p>
			<PromptInput class="w-full" @submit="handlePromptSubmit">
				<PromptInputTextarea :disabled="!ready || streaming" :placeholder="composerPlaceholder"
					class="min-h-16" />
				<PromptInputFooter class="mt-2 items-center justify-between gap-2 border-none">
					<PromptInputTools>
						<div class="flex items-center gap-2">
							<Context v-if="lastUsage" :used-tokens="lastUsage.totalTokens ?? 0" :max-tokens="contextWindow"
								:usage="lastUsage">
								<ContextTrigger />
								<ContextContent class="w-64">
									<ContextContentHeader />
									<ContextContentBody>
										<ContextInputUsage />
										<ContextOutputUsage />
										<ContextReasoningUsage />
										<ContextCacheUsage />
									</ContextContentBody>
									<ContextContentFooter>
										<span class="text-muted-foreground">Total tokens</span>
										<span>{{ (lastUsage.totalTokens ?? 0).toLocaleString() }}</span>
									</ContextContentFooter>
								</ContextContent>
							</Context>

							<DropdownMenu>
								<DropdownMenuTrigger as-child>
									<Button variant="outline" size="sm" :disabled="!ready || streaming">
										<Brain class="size-3.5" />
										<span>{{ reasoningLabel }}</span>
									</Button>
								</DropdownMenuTrigger>
								<DropdownMenuContent align="start">
									<DropdownMenuItem v-for="option in reasoningOptions"
										:key="option.value ?? 'default'" @select="reasoningEffort = option.value">
										<Check v-if="(reasoningEffort ?? null) === option.value" class="size-3.5 shrink-0" />
										<span v-else class="size-3.5 shrink-0" />
										{{ option.label }}
									</DropdownMenuItem>
								</DropdownMenuContent>
							</DropdownMenu>

							<ModelSelector v-model:open="modelSelectorOpen">
								<ModelSelectorTrigger as-child>
									<Button variant="outline" size="sm"
										:disabled="!providerId || loadingModels || !models.length || streaming">
										<img v-if="providerLogo(providerId)" :src="providerLogo(providerId)"
											class="size-3.5 shrink-0" alt="" aria-hidden="true" />
										<span class="max-w-56 truncate">{{ selectedModel?.name ?? "Select model" }}</span>
									</Button>
								</ModelSelectorTrigger>
								<ModelSelectorContent title="Select model" class="sm:max-w-xl">
									<ModelSelectorInput v-model="modelSearch" placeholder="Search models…" />
									<ModelSelectorList>
										<ModelSelectorEmpty v-if="!visibleModels.length">No models found.</ModelSelectorEmpty>
										<ModelSelectorGroup :heading="providerId ?? 'Models'">
											<ModelSelectorItem v-for="model in visibleModels" :key="model.name"
												:value="model.name" @select="selectModel(model.name)">
												<img v-if="providerLogo(providerId)" :src="providerLogo(providerId)"
													class="size-3.5 shrink-0" alt="" aria-hidden="true" />
												<ModelSelectorName>{{ model.name }}</ModelSelectorName>
											</ModelSelectorItem>
										</ModelSelectorGroup>
									</ModelSelectorList>
								</ModelSelectorContent>
							</ModelSelector>
						</div>
					</PromptInputTools>
					<PromptInputSubmit :status="streaming ? 'streaming' : 'ready'" :disabled="!ready"
						@click="handleSubmitClick" />
				</PromptInputFooter>
			</PromptInput>
		</div>
	</section>
</template>

<script>
import {
	CodeBlock,
	CodeBlockActions,
	CodeBlockCopyButton,
	CodeBlockFilename,
	CodeBlockHeader,
	CodeBlockTitle,
} from "@/components/ai-elements/code-block";
import { Conversation, ConversationContent, ConversationScrollButton } from "@/components/ai-elements/conversation";
import { Context, ContextCacheUsage, ContextContent, ContextContentBody, ContextContentFooter, ContextContentHeader, ContextInputUsage, ContextOutputUsage, ContextReasoningUsage, ContextTrigger } from "@/components/ai-elements/context";
import { Loader } from "@/components/ai-elements/loader";
import { ModelSelector, ModelSelectorContent, ModelSelectorEmpty, ModelSelectorGroup, ModelSelectorInput, ModelSelectorItem, ModelSelectorList, ModelSelectorName, ModelSelectorTrigger } from "@/components/ai-elements/model-selector";
import {
	PromptInput,
	PromptInputFooter,
	PromptInputSubmit,
	PromptInputTextarea,
	PromptInputTools,
} from "@/components/ai-elements/prompt-input";
import { Reasoning, ReasoningContent, ReasoningTrigger } from "@/components/ai-elements/reasoning";
import { Source, Sources, SourcesContent, SourcesTrigger } from "@/components/ai-elements/sources";
import { Tool, ToolContent, ToolHeader, ToolInput, ToolOutput } from "@/components/ai-elements/tool";
import { Brain, Check, ChevronDown, Plus } from "@lucide/vue";
import { Button } from "@/components/ui/button";
import {
	DropdownMenu,
	DropdownMenuContent,
	DropdownMenuItem,
	DropdownMenuLabel,
	DropdownMenuSeparator,
	DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Message, MessageContent } from "@/components/ui/message";
import { Spinner } from "@/components/ui/spinner";
import {
	Questionnaire,
	QuestionnaireActions,
	QuestionnaireChoice,
	QuestionnaireChoices,
	QuestionnaireDescription,
	QuestionnaireError,
	QuestionnaireInput,
	QuestionnaireItem,
	QuestionnaireProgress,
	QuestionnaireSubmit,
	QuestionnaireTitle,
} from "@/components/ui/questionnaire";
import { useSettingsStore } from "@/stores/settings";
import { providerLogo } from "@/lib/provider-logos";
import DOMPurify from "dompurify";
import { marked } from "marked";

const SETTINGS_API = "http://localhost:8081/api/settings";
const CHAT_API = "http://localhost:8081/api/chat/stream";
const CHAT_BASE = "http://localhost:8081/api/chat";

// The harness normalizes provider-side thinking to <think> tags, but some models emit
// raw <think>/<thinking> tags inline in the content stream — accept every spelling.
const THINK_OPEN_TAGS = ["<think>", "<thinking>"];
const THINK_CLOSE_TAGS = ["</think>", "</thinking>"];

// Fence tags models emit most often that shiki names differently.
const LANGUAGE_ALIASES = {
	js: "javascript",
	ts: "typescript",
	sh: "bash",
	shell: "bash",
	zsh: "bash",
	py: "python",
	rb: "ruby",
	rs: "rust",
	ps1: "powershell",
	yml: "yaml",
	md: "markdown",
};

export default {
	name: "ChatView",
	components: {
		Brain,
		Button,
		Check,
		ChevronDown,
		CodeBlock,
		CodeBlockActions,
		CodeBlockCopyButton,
		CodeBlockFilename,
		CodeBlockHeader,
		CodeBlockTitle,
		Conversation,
		ConversationContent,
		ConversationScrollButton,
		Context,
		ContextCacheUsage,
		ContextContent,
		ContextContentBody,
		ContextContentFooter,
		ContextContentHeader,
		ContextInputUsage,
		ContextOutputUsage,
		ContextReasoningUsage,
		ContextTrigger,
		DropdownMenu,
		DropdownMenuContent,
		DropdownMenuItem,
		DropdownMenuLabel,
		DropdownMenuSeparator,
		DropdownMenuTrigger,
		Loader,
		Message,
		MessageContent,
		ModelSelector,
		ModelSelectorContent,
		ModelSelectorEmpty,
		ModelSelectorGroup,
		ModelSelectorInput,
		ModelSelectorItem,
		ModelSelectorList,
		ModelSelectorName,
		ModelSelectorTrigger,
		Plus,
		PromptInput,
		PromptInputFooter,
		PromptInputSubmit,
		PromptInputTextarea,
		PromptInputTools,
		Questionnaire,
		QuestionnaireActions,
		QuestionnaireChoice,
		QuestionnaireChoices,
		QuestionnaireDescription,
		QuestionnaireError,
		QuestionnaireInput,
		QuestionnaireItem,
		QuestionnaireProgress,
		QuestionnaireSubmit,
		QuestionnaireTitle,
		Reasoning,
		ReasoningContent,
		ReasoningTrigger,
		Source,
		Sources,
		SourcesContent,
		SourcesTrigger,
		Spinner,
		Tool,
		ToolContent,
		ToolHeader,
		ToolInput,
		ToolOutput,
	},

	data() {
		return {
			settings: useSettingsStore(),
			providerId: null,
			modelName: null,
			models: [],
			messages: [],
			generationId: null,
			abortController: null,
			lastUsage: null,
			reasoningEffort: null,
			modelSelectorOpen: false,
			modelSearch: "",
			loadingModels: false,
			streaming: false,
			error: "",
		};
	},
	computed: {
		providers() {
			return Object.entries(this.settings.providers).map(([id, config]) => ({ id, config }));
		},
		selectedProvider() {
			return this.providers.find((provider) => provider.id === this.providerId) ?? null;
		},
		selectedModel() {
			return this.models.find((model) => model.name === this.modelName) ?? null;
		},
		ready() {
			return Boolean(this.providerId && this.modelName);
		},
		headerTitle() {
			return this.selectedProvider?.id ?? "New chat";
		},
		composerPlaceholder() {
			if (!this.providerId) return "Choose a provider to start a chat";
			if (!this.modelName) return "Choose a model to start chatting";
			return "Message Castiel…";
		},
		// Advertised input limit of the selected model; sensible fallback when the
		// provider catalog does not report one (e.g. Ollama).
		contextWindow() {
			return this.selectedModel?.contextWindow ?? 128000;
		},
		reasoningOptions() {
			return [
				{ value: null, label: "Default" },
				{ value: "off", label: "Off" },
				{ value: "low", label: "Low" },
				{ value: "medium", label: "Medium" },
				{ value: "high", label: "High" },
			];
		},
		reasoningLabel() {
			const option = this.reasoningOptions.find((candidate) => candidate.value === this.reasoningEffort);
			return option ? `Reasoning: ${option.label}` : "Reasoning";
		},
		// Rendering hundreds of command items (OpenRouter catalogs 400+ models) makes the
		// dialog mount crawl, so we filter and cap the list ourselves — the search box
		// covers everything beyond the cap.
		visibleModels() {
			const query = this.modelSearch.trim().toLowerCase();
			const filtered = query
				? this.models.filter((model) => model.name.toLowerCase().includes(query))
				: this.models;
			return filtered.slice(0, 100);
		},
			// A response was requested but nothing streamed yet — show the loader.
		assistantIdle() {
			if (!this.streaming) return false;
			const last = this.messages[this.messages.length - 1];
			return Boolean(
				last &&
				last.role === "assistant" &&
				!this.hasRenderableContent(last),
			);
		},
	},
	async mounted() {
		window.addEventListener("keydown", this.handleWindowKeydown);
		if (this.settings.loaded) return;
		try {
			await this.settings.fetch();
		} catch (error) {
			this.error = this.messageFor(error, "Could not load configured providers.");
		}
	},
	beforeUnmount() {
		window.removeEventListener("keydown", this.handleWindowKeydown);
	},
	watch: {
		// Reset the search so reopening starts from the full (capped) list.
		modelSelectorOpen(open) {
			if (!open) this.modelSearch = "";
		},
	},
	methods: { providerLogo,
		handleWindowKeydown(event) {
			if (event.key === "Escape" && this.streaming) {
				event.preventDefault();
				this.stopGeneration();
			}
		},
		// Tells the harness to cancel the generation (aborting the provider stream and any
		// pending tool rounds), then drops the SSE connection itself.
		async stopGeneration() {
			if (!this.streaming) return;
			const controller = this.abortController;
			this.abortController = null;
			if (this.generationId) {
				try {
					await fetch(`${CHAT_API}/${encodeURIComponent(this.generationId)}/cancel`, { method: "POST" });
				} catch {
					// Best effort — aborting the local stream is enough to stop the UI.
				}
			}
			controller?.abort();
		},
		// While streaming the submit button acts as a stop button.
		handleSubmitClick(event) {
			if (!this.streaming) return;
			event.preventDefault();
			event.stopPropagation();
			this.stopGeneration();
		},
		renderMarkdown(content) {
			return DOMPurify.sanitize(marked.parse(content, { async: false }));
		},
		// Splits assistant text into prose (markdown HTML) and fenced code segments so
		// code blocks can be rendered with the CodeBlock component while streaming.
		contentSegments(text) {
			const lines = String(text ?? "").split("\n");
			const segments = [];
			let textLines = [];
			let codeLines = null;
			let info = "";

			const flushText = () => {
				if (textLines.length) {
					segments.push({ type: "text", html: this.renderMarkdown(textLines.join("\n")) });
					textLines = [];
				}
			};

			for (const line of lines) {
				if (codeLines === null) {
					const open = line.match(/^```(.*)$/);
					if (open) {
						flushText();
						codeLines = [];
						info = open[1].trim();
					} else {
						textLines.push(line);
					}
				} else if (/^```\s*$/.test(line)) {
					segments.push(this.codeSegment(codeLines.join("\n"), info));
					codeLines = null;
					info = "";
				} else {
					codeLines.push(line);
				}
			}

			if (codeLines !== null) {
				// Unterminated fence — still streaming, render what we have as code.
				segments.push(this.codeSegment(codeLines.join("\n"), info));
			} else {
				flushText();
			}
			return segments;
		},
		codeSegment(code, info) {
			const [tag, ...rest] = info.split(/\s+/).filter(Boolean);
			const language = LANGUAGE_ALIASES[tag?.toLowerCase()] ?? tag?.toLowerCase() ?? "text";
			return { type: "code", code, language, filename: rest.join(" ") || language };
		},
		toolState(call) {
			return call.result === null ? "input-available" : "output-available";
		},
		// Maps the harness `usage` SSE payload onto the AI SDK usage shape the
		// Context component expects; only keeps fields the provider reported.
		applyUsage(usage) {
			if (!usage || typeof usage !== "object") return;
			const mapped = {};
			for (const [key, value] of Object.entries({
				inputTokens: usage.inputTokens,
				outputTokens: usage.outputTokens,
				totalTokens: usage.totalTokens,
				reasoningTokens: usage.reasoningTokens,
				cachedInputTokens: usage.cachedInputTokens,
			})) {
				if (typeof value === "number") mapped[key] = value;
			}
			this.lastUsage = mapped;
		},
		toolInput(call) {
			try {
				return JSON.parse(call.arguments || "{}");
			} catch {
				return call.arguments ? { input: call.arguments } : {};
			}
		},
		// web_search results arrive as "[1] title\nurl\nsnippet" blocks; turn them into
		// link sources for the Sources component. Anything unparsable renders nothing.
		sourcesFor(call) {
			if (call.name !== "web_search" || typeof call.result !== "string") return [];
			const sources = [];
			const pattern = /^\[\d+\]\s+(.+)\n(https?:\/\/\S+)/gm;
			let match;
			while ((match = pattern.exec(call.result)) !== null) {
				sources.push({ title: match[1].trim(), href: match[2].trim() });
			}
			return sources;
		},
		isPendingQuestion(call) {
			return call.name === "ask_user_question" && call.result === null && !call.answered;
		},
		questionnaireItems(call) {
			return [
				{
					name: "q",
					required: true,
					choices: call.options.map((option) => ({ value: option })),
				},
			];
		},

		createMessage(role, content) {
			return {
				id: crypto.randomUUID(),
				role,
				// Ordered stream of segments: {type:"thinking"|"text", text} and
				// {type:"tool", id, name, arguments, result, ...}. Preserves the real
				// interleaving of thinking, tool calls and answers.
				parts: content ? [{ type: "text", text: content }] : [],
				isThinking: false,
				streamBuffer: "",
				trimResponseLeadingNewlines: false,
			};
		},
		// Plain text of a message (used for user bubbles and history serialization).
		messageText(message) {
			return message.parts
				.filter((part) => part.type === "text")
				.map((part) => part.text)
				.join("");
		},
		hasRenderableContent(message) {
			return message.parts.some((part) => part.type === "tool" || (part.text ?? "").length > 0);
		},
		async startChat(nextProviderId) {
			if (this.streaming) return;
			this.providerId = nextProviderId;
			this.modelName = null;
			this.models = [];
			this.messages = [];
			this.generationId = null;
			this.lastUsage = null;
			this.error = "";
			this.loadingModels = true;

			try {
				const response = await fetch(`${SETTINGS_API}/providers/${encodeURIComponent(nextProviderId)}/models`);
				if (!response.ok) throw new Error(await this.readError(response));
				if (this.providerId === nextProviderId) this.models = await response.json();
			} catch (error) {
				if (this.providerId === nextProviderId) {
					this.error = this.messageFor(error, "Could not load models for this provider.");
				}
			} finally {
				if (this.providerId === nextProviderId) this.loadingModels = false;
			}
		},
		async sendMessage(content) {
			const text = String(content ?? "").trim();
			if (!text || !this.ready || this.streaming) return;

			this.error = "";
			this.messages.push(this.createMessage("user", text));
			this.messages.push(this.createMessage("assistant", ""));
			// Read the item back from Vue's reactive array so each incoming token repaints immediately.
			const assistantMessage = this.messages[this.messages.length - 1];
			this.streaming = true;
			this.generationId = null;
			const controller = new AbortController();
			this.abortController = controller;

			try {
				const response = await fetch(CHAT_API, {
					method: "POST",
					headers: { "Content-Type": "application/json", Accept: "text/event-stream" },
					signal: controller.signal,
					body: JSON.stringify({
						providerId: this.providerId,
						modelName: this.modelName,
						reasoningEffort: this.reasoningEffort,
						messages: this.messages
							.slice(0, -1)
							.map((message) => ({
								role: message.role,
								content: this.messageText(message),
								toolCalls:
									message.role === "assistant"
										? message.parts
											.filter((part) => part.type === "tool")
											.map(({ id, name, arguments: args, result }) => ({
												id,
												name,
												arguments: args,
												result: result ?? "",
											}))
										: [],
							})),
					}),
				});
				if (!response.ok) throw new Error(await this.readError(response));
				if (!response.body) throw new Error("The chat service did not return a response stream.");

				await this.readEventStream(response.body, (event, data) => {
					if (event === "error") throw new Error(data || "The model could not complete the response.");
					if (event === "start") {
						this.generationId = JSON.parse(data).id ?? null;
						return;
					}
					if (event === "usage") {
						this.applyUsage(JSON.parse(data));
						return;
					}
					if (event === "tool_call") {
						const call = JSON.parse(data);
						const entry = {
							type: "tool",
							id: call.id,
							name: call.name,
							arguments: call.arguments ?? "",
							result: null,
							// Expanded while running; collapsed automatically when its
							// result arrives or the generation ends.
							open: true,
						};
						if (call.name === "ask_user_question") {
							const args = JSON.parse(call.arguments || "{}");
							entry.question = args.question ?? "(no question)";
							entry.options = Array.isArray(args.options) ? args.options : [];
							entry.multiSelect = Boolean(args.multiSelect);
						}
						// A tool call always starts a new segment — any thinking that follows
						// (next round) must not merge into a previous block.
						assistantMessage.isThinking = false;
						assistantMessage.parts.push(entry);
						return;
					}
					if (event === "tool_result") {
						const payload = JSON.parse(data);
						const part = assistantMessage.parts.find(
							(candidate) => candidate.type === "tool" && candidate.id === payload.id,
						);
						if (part) {
							part.result = payload.result ?? "";
							part.open = false;
						} else {
							assistantMessage.parts.push({
								type: "tool",
								id: payload.id,
								name: payload.name ?? "",
								arguments: "",
								result: payload.result ?? "",
								open: false,
							});
						}
						return;
					}
					this.appendStreamChunk(assistantMessage, data);
				});
				this.flushStreamBuffer(assistantMessage);
			} catch (error) {
				if (error?.name === "AbortError") {
					// User stopped the generation — keep whatever streamed, no error shown.
					this.flushStreamBuffer(assistantMessage);
					return;
				}
				if (!this.hasRenderableContent(assistantMessage)) {
					this.messages.pop();
				}
				this.error = this.messageFor(error, "The message could not be sent.");
			} finally {
				assistantMessage.isThinking = false;
				this.streaming = false;
				this.abortController = null;
				// Collapse any tool calls that never produced a result (stop, error, abort).
				for (const part of assistantMessage.parts) {
					if (part.type === "tool" && part.result === null) part.open = false;
				}
			}
		},
		async submitAnswer(event, call) {
			event.preventDefault();
			const form = new FormData(event.target);
			const answers = form.getAll("q").map((value) => String(value).trim()).filter(Boolean);
			await this.deliverAnswer(call, answers.join("; "));
		},
		async dismissQuestion(call) {
			await this.deliverAnswer(call, "");
		},
		async deliverAnswer(call, answer) {
			call.answered = true;
			try {
				const response = await fetch(
					`${CHAT_BASE}/questions/${encodeURIComponent(call.id)}/answer`,
					{
						method: "POST",
						headers: { "Content-Type": "application/json" },
						body: JSON.stringify({ answer }),
					},
				);
				if (!response.ok) throw new Error(await this.readError(response));
			} catch (error) {
				this.error = this.messageFor(error, "Could not deliver your answer to the harness.");
			}
		},

		appendStreamChunk(message, chunk) {
			message.streamBuffer += chunk;
			while (message.streamBuffer) {
				const tags = message.isThinking ? THINK_CLOSE_TAGS : THINK_OPEN_TAGS;
				let matchedTag = null;
				let matchIndex = -1;
				for (const tag of tags) {
					const index = message.streamBuffer.indexOf(tag);
					if (index >= 0 && (matchIndex < 0 || index < matchIndex)) {
						matchedTag = tag;
						matchIndex = index;
					}
				}
				if (matchedTag !== null) {
					this.appendToActivePart(message, message.streamBuffer.slice(0, matchIndex));
					message.streamBuffer = message.streamBuffer.slice(matchIndex + matchedTag.length);
					message.isThinking = !message.isThinking;
					if (!message.isThinking) message.trimResponseLeadingNewlines = true;
					continue;
				}

				const retainedLength = Math.max(0, ...tags.map((tag) => this.trailingTagPrefixLength(message.streamBuffer, tag)));
				this.appendToActivePart(message, message.streamBuffer.slice(0, -retainedLength || undefined));
				message.streamBuffer = retainedLength ? message.streamBuffer.slice(-retainedLength) : "";
				return;
			}
		},
		// Appends streamed text to the message's last part, opening a new part whenever
		// the thinking/text mode changed or a tool call intervened — this is what keeps
		// interleaved thinking blocks separate instead of merging them at the top.
		appendToActivePart(message, text) {
			if (!text) return;
			const type = message.isThinking ? "thinking" : "text";
			let last = message.parts[message.parts.length - 1];
			if (!last || last.type !== type) {
				last = { type, text: "" };
				message.parts.push(last);
			}
			if (type === "text" && message.trimResponseLeadingNewlines) {
				text = text.replace(/^[\r\n]+/, "");
				if (!text) return;
				message.trimResponseLeadingNewlines = false;
			}
			last.text += text;
		},
		trailingTagPrefixLength(text, tag) {
			const maxLength = Math.min(text.length, tag.length - 1);
			for (let length = maxLength; length > 0; length -= 1) {
				if (text.endsWith(tag.slice(0, length))) return length;
			}
			return 0;
		},
		flushStreamBuffer(message) {
			this.appendToActivePart(message, message.streamBuffer);
			message.streamBuffer = "";
		},
		// PromptInput clears its own input before calling submit and restores it on error.
		handlePromptSubmit(message) {
			this.sendMessage(message?.text);
		},
		selectModel(name) {
			this.modelName = name;
			this.modelSelectorOpen = false;
		},
		async readEventStream(stream, onEvent) {
			const reader = stream.getReader();
			const decoder = new TextDecoder();
			let buffer = "";
			const dispatch = (block) => {
				if (!block.trim()) return;
				let event = "message";
				const data = [];
				for (const line of block.split("\n")) {
					if (line.startsWith("event:")) event = line.slice(6).trim();
					if (line.startsWith("data:")) data.push(line.slice(5));
				}
				if (data.length) onEvent(event, data.join("\n"));
			};

			try {
				while (true) {
					const { done, value } = await reader.read();
					buffer += decoder.decode(value ?? new Uint8Array(), { stream: !done }).replace(/\r/g, "");
					const blocks = buffer.split("\n\n");
					buffer = blocks.pop();
					blocks.forEach(dispatch);
					if (done) break;
				}
				dispatch(buffer);
			} finally {
				reader.releaseLock();
			}
		},
		async readError(response) {
			const body = await response.text();
			try {
				const json = JSON.parse(body);
				return json.message || json.detail || json.error || body || `Request failed (${response.status})`;
			} catch {
				return body || `Request failed (${response.status})`;
			}
		},
		messageFor(error, fallback) {
			return error instanceof Error ? error.message : fallback;
		},
	},
};
</script>
