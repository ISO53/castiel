<template>
	<section class="flex h-full min-h-0 flex-col bg-background">
		<header class="flex h-11 shrink-0 items-center justify-between gap-2 border-b px-3">
			<div class="min-w-0">
				<p class="truncate text-xs font-medium text-foreground">{{ headerTitle }}</p>
				<p v-if="selectedProvider" class="truncate text-[11px] text-muted-foreground">
					{{ selectedProvider.id }}
				</p>
			</div>

			<DropdownMenu>
				<DropdownMenuTrigger as-child>
					<Button variant="ghost" size="icon-sm" :disabled="streaming" aria-label="Start a new chat">
						<Plus />
					</Button>
				</DropdownMenuTrigger>
				<DropdownMenuContent align="end" class="!w-64">
					<DropdownMenuLabel>New chat</DropdownMenuLabel>
					<DropdownMenuSeparator />
					<DropdownMenuItem v-for="provider in providers" :key="provider.id" @select="startChat(provider.id)">
						{{ provider.id }}
					</DropdownMenuItem>
					<DropdownMenuItem v-if="!providers.length" disabled>No providers configured</DropdownMenuItem>
				</DropdownMenuContent>
			</DropdownMenu>
		</header>

		<div class="min-h-0 flex-1">
			<MessageScrollerProvider>
				<MessageScroller>
					<MessageScrollerViewport class="px-3 py-4">
						<MessageScrollerContent class="gap-4">
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

							<MessageScrollerItem v-for="message in messages" :key="message.id" :message-id="message.id"
								:scroll-anchor="message.role === 'user'">
								<Message :align="message.role === 'user' ? 'end' : 'start'">
									<MessageContent>
										<Collapsible v-if="message.role === 'assistant' && message.thinking"
											v-model:open="message.thinkingOpen"
											class="w-full self-start">
											<CollapsibleTrigger
												class="flex w-full items-center justify-between gap-2 py-1 text-left text-[11px] text-muted-foreground hover:text-foreground">
												Thinking
												<ChevronDown class="size-3 transition-transform"
													:class="message.thinkingOpen ? 'rotate-180' : ''" />
											</CollapsibleTrigger>
											<CollapsibleContent
											class="py-1 text-xs leading-relaxed text-muted-foreground">
												<p class="whitespace-pre-wrap">{{ message.thinking }}</p>
											</CollapsibleContent>
										</Collapsible>
										<div v-if="message.role === 'user' || message.content || (streaming && message.role === 'assistant')"
											class="max-w-[90%] whitespace-pre-wrap rounded-lg px-3 py-2 text-xs leading-relaxed"
											:class="message.role === 'user' ? 'self-end bg-primary text-primary-foreground' : 'self-start text-foreground'">
											{{ message.content }}<Spinner
												v-if="streaming && !message.content && message.role === 'assistant'"
												class="inline-block size-3" />
										</div>
									</MessageContent>
								</Message>
							</MessageScrollerItem>
						</MessageScrollerContent>
					</MessageScrollerViewport>
					<MessageScrollerButton />
				</MessageScroller>
			</MessageScrollerProvider>
		</div>

		<div class="shrink-0 border-t p-3">
			<p v-if="error" class="mb-2 text-xs text-destructive">{{ error }}</p>
			<Textarea v-model="prompt" class="min-h-18" :disabled="!ready || streaming"
				:placeholder="composerPlaceholder" @keydown="handlePromptKeydown" />
			<div class="mt-2 flex items-center justify-between gap-2">
				<DropdownMenu>
					<DropdownMenuTrigger as-child>
						<Button variant="outline" size="sm"
							:disabled="!providerId || loadingModels || !models.length || streaming">
							<span class="max-w-56 truncate">{{ selectedModel?.name ?? "Select model" }}</span>
							<ChevronDown />
						</Button>
					</DropdownMenuTrigger>
					<DropdownMenuContent align="start" class="!w-72">
						<DropdownMenuItem v-for="model in models" :key="model.name" :text-value="model.name"
							@select="modelName = model.name">
							<span class="min-w-0 truncate">{{ model.name }}</span>
						</DropdownMenuItem>
					</DropdownMenuContent>
				</DropdownMenu>

				<Button size="sm" :disabled="!ready || !prompt.trim() || streaming" @click="sendMessage">
					<Spinner v-if="streaming" />
					<SendHorizontal v-else />
					<span class="sr-only">Send message</span>
				</Button>
			</div>
		</div>
	</section>
</template>

<script>
import { ChevronDown, Plus, SendHorizontal } from "@lucide/vue";
import { Button } from "@/components/ui/button";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import {
	DropdownMenu,
	DropdownMenuContent,
	DropdownMenuItem,
	DropdownMenuLabel,
	DropdownMenuSeparator,
	DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Message, MessageContent } from "@/components/ui/message";
import {
	MessageScroller,
	MessageScrollerButton,
	MessageScrollerContent,
	MessageScrollerItem,
	MessageScrollerProvider,
	MessageScrollerViewport,
} from "@/components/ui/message-scroller";
import { Textarea } from "@/components/ui/textarea";
import { Spinner } from "@/components/ui/spinner";
import { useSettingsStore } from "@/stores/settings";

const SETTINGS_API = "http://localhost:8081/api/settings";
const CHAT_API = "http://localhost:8081/api/chat/stream";

export default {
	name: "ChatView",
	components: {
		Button,
		ChevronDown,
		Collapsible,
		CollapsibleContent,
		CollapsibleTrigger,
		DropdownMenu,
		DropdownMenuContent,
		DropdownMenuItem,
		DropdownMenuLabel,
		DropdownMenuSeparator,
		DropdownMenuTrigger,
		Message,
		MessageContent,
		MessageScroller,
		MessageScrollerButton,
		MessageScrollerContent,
		MessageScrollerItem,
		MessageScrollerProvider,
		MessageScrollerViewport,
		Plus,
		SendHorizontal,
		Spinner,
		Textarea,
	},
	data() {
		return {
			settings: useSettingsStore(),
			providerId: null,
			modelName: null,
			models: [],
			messages: [],
			prompt: "",
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
			return this.selectedModel?.name ?? "New chat";
		},
		composerPlaceholder() {
			if (!this.providerId) return "Choose a provider to start a chat";
			if (!this.modelName) return "Choose a model to start chatting";
			return "Message Castiel…";
		},
	},
	async mounted() {
		if (this.settings.loaded) return;
		try {
			await this.settings.fetch();
		} catch (error) {
			this.error = this.messageFor(error, "Could not load configured providers.");
		}
	},
	methods: {
		createMessage(role, content) {
			return {
				id: crypto.randomUUID(),
				role,
				content,
				thinking: "",
				thinkingOpen: false,
				isThinking: false,
				streamBuffer: "",
				trimResponseLeadingNewlines: false,
			};
		},
		async startChat(nextProviderId) {
			if (this.streaming) return;
			this.providerId = nextProviderId;
			this.modelName = null;
			this.models = [];
			this.messages = [];
			this.prompt = "";
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
		async sendMessage() {
			const content = this.prompt.trim();
			if (!content || !this.ready || this.streaming) return;

			this.error = "";
			this.prompt = "";
			this.messages.push(this.createMessage("user", content));
			this.messages.push(this.createMessage("assistant", ""));
			// Read the item back from Vue's reactive array so each incoming token repaints immediately.
			const assistantMessage = this.messages[this.messages.length - 1];
			this.streaming = true;

			try {
				const response = await fetch(CHAT_API, {
					method: "POST",
					headers: { "Content-Type": "application/json", Accept: "text/event-stream" },
					body: JSON.stringify({
						providerId: this.providerId,
						modelName: this.modelName,
						messages: this.messages
							.slice(0, -1)
							.map(({ role, content: messageContent }) => ({ role, content: messageContent })),
					}),
				});
				if (!response.ok) throw new Error(await this.readError(response));
				if (!response.body) throw new Error("The chat service did not return a response stream.");

				await this.readEventStream(response.body, (event, data) => {
					if (event === "error") throw new Error(data || "The model could not complete the response.");
					this.appendStreamChunk(assistantMessage, data);
				});
				this.flushStreamBuffer(assistantMessage);
			} catch (error) {
				if (!assistantMessage.content && !assistantMessage.thinking) this.messages.pop();
				this.error = this.messageFor(error, "The message could not be sent.");
			} finally {
				assistantMessage.isThinking = false;
				assistantMessage.thinkingOpen = false;
				this.streaming = false;
			}
		},
		appendStreamChunk(message, chunk) {
			message.streamBuffer += chunk;
			while (message.streamBuffer) {
				const tag = message.isThinking ? "</think>" : "<think>";
				const tagIndex = message.streamBuffer.indexOf(tag);
				if (tagIndex >= 0) {
					this.appendToActivePart(message, message.streamBuffer.slice(0, tagIndex));
					message.streamBuffer = message.streamBuffer.slice(tagIndex + tag.length);
					message.isThinking = !message.isThinking;
					if (message.isThinking) message.thinkingOpen = true;
					else {
						message.thinkingOpen = false;
						message.trimResponseLeadingNewlines = true;
					}
					continue;
				}

				const retainedLength = this.trailingTagPrefixLength(message.streamBuffer, tag);
				this.appendToActivePart(message, message.streamBuffer.slice(0, -retainedLength || undefined));
				message.streamBuffer = retainedLength ? message.streamBuffer.slice(-retainedLength) : "";
				return;
			}
		},
		appendToActivePart(message, text) {
			if (!text) return;
			if (message.isThinking) message.thinking += text;
			else {
				if (message.trimResponseLeadingNewlines) {
					text = text.replace(/^[\r\n]+/, "");
					if (!text) return;
					message.trimResponseLeadingNewlines = false;
				}
				message.content += text;
			}
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
		handlePromptKeydown(event) {
			if (event.key === "Enter" && !event.shiftKey) {
				event.preventDefault();
				this.sendMessage();
			}
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
