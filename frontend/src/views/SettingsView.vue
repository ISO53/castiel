<template>
	<ScrollArea class="h-full bg-card">
		<div class="mx-auto flex max-w-2xl flex-col gap-8 p-6">
			<div class="space-y-1">
				<h1 class="text-xl font-semibold tracking-tight text-foreground">Settings</h1>
				<p class="text-xs text-muted-foreground">
					Configure Castiel for your machine. Changes are stored on this PC.
				</p>
			</div>

			<section class="space-y-4">
				<div class="space-y-1">
					<h2 class="text-sm font-semibold text-foreground">LLM Providers</h2>
					<p class="text-xs text-muted-foreground">
						Connect language model backends used by the harness.
					</p>
				</div>
				<Separator />

				<Collapsible v-model:open="llamaOpen" class="rounded-lg border border-border">
					<CollapsibleTrigger
						class="flex w-full items-center justify-between gap-3 px-4 py-3 text-left hover:bg-muted/40">
						<span class="flex items-center gap-2 text-sm font-medium text-foreground">
							<img v-if="providerLogo('llama.cpp')" :src="providerLogo('llama.cpp')"
								class="size-4 shrink-0" alt="" aria-hidden="true" />
							llama.cpp
						</span>
						<ChevronDown class="size-4 shrink-0 text-muted-foreground transition-transform duration-200"
							:class="llamaOpen ? 'rotate-180' : ''" />
					</CollapsibleTrigger>

					<CollapsibleContent class="border-t border-border px-4 py-4">
						<div class="flex flex-col gap-4">
							<div class="space-y-2 text-xs text-muted-foreground leading-relaxed">
								<p>
									Run open models locally with llama.cpp's built-in server, or connect to a
									remote llama.cpp server.
								</p>
								<p>To use a local llama.cpp server:</p>
								<ul class="list-disc space-y-1 pl-4">
									<li>
										Install llama.cpp from
										<a class="underline underline-offset-2 hover:text-foreground"
											href="https://llama.cpp" target="_blank" rel="noreferrer">llama.cpp</a>
									</li>
									<li>Start the server in router mode: <code
											class="text-foreground">llama-server</code></li>
									<li>Click Connect below to start using llama.cpp in Castiel</li>
								</ul>
								<p>
									Alternatively, connect to a remote llama.cpp server by specifying its URL and
									API key (set with <code class="text-foreground">--api-key</code>; may not be
									required):
								</p>
							</div>

							<div class="flex flex-col gap-3">
								<div class="flex flex-col gap-1.5">
									<Label for="llamacpp_api_url">API URL</Label>
									<Input id="llamacpp_api_url" v-model="form.apiUrl" type="url"
										placeholder="http://localhost:8080" autocomplete="off" spellcheck="false" />
								</div>

								<div class="flex flex-col gap-1.5">
									<Label for="llamacpp_context">Context Window</Label>
									<Input id="llamacpp_context" v-model.number="form.contextWindow" type="number"
										min="1" placeholder="8192" />
									<p class="text-[11px] text-muted-foreground">
										Default: Discovered from the server
									</p>
								</div>

								<div class="flex flex-col gap-1.5">
									<Label for="llamacpp_api_key">API key</Label>
									<Input id="llamacpp_api_key" v-model="form.apiKey" type="password"
										placeholder="sk-..." autocomplete="off" spellcheck="false" />
								</div>
							</div>

							<div class="flex items-center gap-3 pt-1">
								<Button size="sm" :disabled="connecting" @click="connect">
									{{ connecting ? "Connecting..." : "Connect" }}
								</Button>
								<div v-if="health" class="flex items-center gap-2 text-xs">
									<span class="size-2 rounded-full"
										:class="health.ok ? 'bg-emerald-500' : 'bg-destructive'" aria-hidden="true" />
									<span :class="health.ok ? 'text-muted-foreground' : 'text-destructive'">
										{{ health.message }}
									</span>
								</div>
							</div>

							<p v-if="error" class="text-xs text-destructive wrap-break-word">{{ error }}</p>
						</div>
					</CollapsibleContent>
				</Collapsible>

				<Collapsible v-model:open="ollamaOpen" class="rounded-lg border border-border">
					<CollapsibleTrigger
						class="flex w-full items-center justify-between gap-3 px-4 py-3 text-left hover:bg-muted/40">
						<span class="flex items-center gap-2 text-sm font-medium text-foreground">
							<img v-if="providerLogo('ollama')" :src="providerLogo('ollama')"
								class="size-4 shrink-0" alt="" aria-hidden="true" />
							Ollama
						</span>
						<ChevronDown class="size-4 shrink-0 text-muted-foreground transition-transform duration-200"
							:class="ollamaOpen ? 'rotate-180' : ''" />
					</CollapsibleTrigger>

					<CollapsibleContent class="border-t border-border px-4 py-4">
						<div class="flex flex-col gap-4">
							<div class="space-y-2 text-xs text-muted-foreground leading-relaxed">
								<p>
									Run open models locally with Ollama, or connect to a remote Ollama server.
								</p>
								<p>To use a local Ollama server:</p>
								<ul class="list-disc space-y-1 pl-4">
									<li>
										Install Ollama from
										<a class="underline underline-offset-2 hover:text-foreground"
											href="https://ollama.com" target="_blank" rel="noreferrer">ollama.com</a>
									</li>
									<li>Pull a model: <code class="text-foreground">ollama pull llama3.2</code></li>
									<li>The server starts automatically on port 11434</li>
									<li>Click Connect below to start using Ollama in Castiel</li>
								</ul>
								<p>
									Alternatively, connect to a remote Ollama server by specifying its URL:
								</p>
							</div>

							<div class="flex flex-col gap-3">
								<div class="flex flex-col gap-1.5">
									<Label for="ollama_api_url">API URL</Label>
									<Input id="ollama_api_url" v-model="ollamaForm.apiUrl" type="url"
										placeholder="http://localhost:11434" autocomplete="off" spellcheck="false" />
								</div>

								<div class="flex flex-col gap-1.5">
									<Label for="ollama_context">Context Window</Label>
									<Input id="ollama_context" v-model.number="ollamaForm.contextWindow" type="number"
										min="1" placeholder="8192" />
									<p class="text-[11px] text-muted-foreground">
										Default: Discovered from the server
									</p>
								</div>
							</div>

							<div class="flex items-center gap-3 pt-1">
								<Button size="sm" :disabled="connectingOllama" @click="connectOllama">
									{{ connectingOllama ? "Connecting..." : "Connect" }}
								</Button>
								<div v-if="ollamaHealth" class="flex items-center gap-2 text-xs">
									<span class="size-2 rounded-full"
										:class="ollamaHealth.ok ? 'bg-emerald-500' : 'bg-destructive'" aria-hidden="true" />
									<span :class="ollamaHealth.ok ? 'text-muted-foreground' : 'text-destructive'">
										{{ ollamaHealth.message }}
									</span>
								</div>
							</div>

							<p v-if="ollamaError" class="text-xs text-destructive wrap-break-word">{{ ollamaError }}</p>
						</div>
					</CollapsibleContent>
				</Collapsible>

				<Collapsible v-model:open="openrouterOpen" class="rounded-lg border border-border">
					<CollapsibleTrigger
						class="flex w-full items-center justify-between gap-3 px-4 py-3 text-left hover:bg-muted/40">
						<span class="flex items-center gap-2 text-sm font-medium text-foreground">
							<img v-if="providerLogo('openrouter')" :src="providerLogo('openrouter')"
								class="size-4 shrink-0" alt="" aria-hidden="true" />
							OpenRouter
						</span>
						<ChevronDown class="size-4 shrink-0 text-muted-foreground transition-transform duration-200"
							:class="openrouterOpen ? 'rotate-180' : ''" />
					</CollapsibleTrigger>

					<CollapsibleContent class="border-t border-border px-4 py-4">
						<div class="flex flex-col gap-4">
							<div class="space-y-2 text-xs text-muted-foreground leading-relaxed">
								<p>
									Access hundreds of cloud models through one OpenAI-compatible endpoint.
								</p>
								<ul class="list-disc space-y-1 pl-4">
									<li>
										Create an API key at
										<a class="underline underline-offset-2 hover:text-foreground"
											href="https://openrouter.ai/settings/keys" target="_blank"
											rel="noreferrer">openrouter.ai/settings/keys</a>
									</li>
									<li>
										To use your own upstream provider keys (BYOK), add them in the
										<a class="underline underline-offset-2 hover:text-foreground"
											href="https://openrouter.ai/settings/byok" target="_blank"
											rel="noreferrer">OpenRouter BYOK settings</a>
										Castiel only ever needs the single OpenRouter key.
									</li>
									<li>Click Connect below to start using OpenRouter in Castiel</li>
								</ul>
							</div>

							<div class="flex flex-col gap-3">
								<div class="flex flex-col gap-1.5">
									<Label for="openrouter_api_key">API key</Label>
									<Input id="openrouter_api_key" v-model="openrouterForm.apiKey" type="password"
										placeholder="sk-or-..." autocomplete="off" spellcheck="false" />
								</div>
							</div>

							<div class="flex items-center gap-3 pt-1">
								<Button size="sm" :disabled="connectingOpenRouter || !openrouterForm.apiKey.trim()"
									@click="connectOpenRouterRequest">
									{{ connectingOpenRouter ? "Connecting..." : "Connect" }}
								</Button>
								<div v-if="openrouterHealth" class="flex items-center gap-2 text-xs">
									<span class="size-2 rounded-full"
										:class="openrouterHealth.ok ? 'bg-emerald-500' : 'bg-destructive'"
										aria-hidden="true" />
									<span :class="openrouterHealth.ok ? 'text-muted-foreground' : 'text-destructive'">
										{{ openrouterHealth.message }}
									</span>
								</div>
							</div>

							<p v-if="openrouterError" class="text-xs text-destructive wrap-break-word">
								{{ openrouterError }}
							</p>
						</div>
					</CollapsibleContent>
				</Collapsible>
				<Collapsible v-model:open="clineOpen" class="rounded-lg border border-border">
					<CollapsibleTrigger
						class="flex w-full items-center justify-between gap-3 px-4 py-3 text-left hover:bg-muted/40">
						<span class="flex items-center gap-2 text-sm font-medium text-foreground">
							<img v-if="providerLogo('cline')" :src="providerLogo('cline')"
								class="size-4 shrink-0" alt="" aria-hidden="true" />
							Cline
						</span>
						<ChevronDown class="size-4 shrink-0 text-muted-foreground transition-transform duration-200"
							:class="clineOpen ? 'rotate-180' : ''" />
					</CollapsibleTrigger>

					<CollapsibleContent class="border-t border-border px-4 py-4">
						<div class="flex flex-col gap-4">
							<div class="space-y-2 text-xs text-muted-foreground leading-relaxed">
								<p>
									Access models from Anthropic, OpenAI, Google, and more through Cline's
									OpenAI-compatible endpoint.
								</p>
								<ul class="list-disc space-y-1 pl-4">
									<li>
										Create an API key at
										<a class="underline underline-offset-2 hover:text-foreground"
											href="https://app.cline.bot" target="_blank" rel="noreferrer">app.cline.bot</a>
										under Settings &gt; API Keys
									</li>
									<li>Click Connect below to start using Cline in Castiel</li>
								</ul>
							</div>

							<div class="flex flex-col gap-3">
								<div class="flex flex-col gap-1.5">
									<Label for="cline_api_key">API key</Label>
									<Input id="cline_api_key" v-model="clineForm.apiKey" type="password"
										placeholder="Your Cline API key" autocomplete="off" spellcheck="false" />
								</div>
							</div>

							<div class="flex items-center gap-3 pt-1">
								<Button size="sm" :disabled="connectingCline || !clineForm.apiKey.trim()"
									@click="connectClineRequest">
									{{ connectingCline ? "Connecting..." : "Connect" }}
								</Button>
								<div v-if="clineHealth" class="flex items-center gap-2 text-xs">
									<span class="size-2 rounded-full"
										:class="clineHealth.ok ? 'bg-emerald-500' : 'bg-destructive'"
										aria-hidden="true" />
									<span :class="clineHealth.ok ? 'text-muted-foreground' : 'text-destructive'">
										{{ clineHealth.message }}
									</span>
								</div>
							</div>

							<p v-if="clineError" class="text-xs text-destructive wrap-break-word">
								{{ clineError }}
							</p>
						</div>
					</CollapsibleContent>
				</Collapsible>

			</section>

			<section class="space-y-4">
				<div class="space-y-1">
					<h2 class="text-sm font-semibold text-foreground">MCP Servers</h2>
					<p class="text-xs text-muted-foreground">
						MCP servers are configured in the mcp.json settings file. Edit and save it.
						Castiel reloads the servers automatically.
					</p>
				</div>
				<Separator />

				<div class="flex flex-wrap items-center gap-3">
					<Button size="sm" @click="openConfigFile">Open settings file</Button>
					<p v-if="mcpConfig" class="min-w-0 truncate font-mono text-[11px] text-muted-foreground">
						{{ mcpConfig.path }}
					</p>
				</div>
				<p v-if="mcpFileError" class="text-xs text-destructive wrap-break-word">{{ mcpFileError }}</p>
				<p v-if="mcpConfig && mcpConfig.error" class="text-xs text-destructive wrap-break-word">
					Invalid mcp.json: {{ mcpConfig.error }}
				</p>
			</section>

			<section class="space-y-4">
				<div class="space-y-1">
					<h2 class="text-sm font-semibold text-foreground">Sub-agents</h2>
					<p class="text-xs text-muted-foreground">
						Sub-agents are smaller models the main agent delegates mechanical tasks to
						(recon, scanning, OSINT, document formatting); it writes each one's role and
						toolset per task. Pick the model they run on here.
					</p>
				</div>
				<Separator />

				<div class="space-y-4 rounded-lg border border-border p-4">
					<div class="grid grid-cols-2 gap-3">
						<div class="flex flex-col gap-1.5">
							<Label>Worker provider</Label>
							<Popover v-model:open="workerProviderOpen">
								<PopoverTrigger as-child>
									<Button variant="outline" role="combobox" :aria-expanded="workerProviderOpen"
										class="w-full justify-between font-normal">
										<span :class="agentWorker.providerId ? '' : 'text-muted-foreground'">
											{{ agentWorker.providerId || "Select provider..." }}
										</span>
										<ChevronsUpDown class="opacity-50" />
									</Button>
								</PopoverTrigger>
								<PopoverContent class="w-(--reka-popover-trigger-width) p-0">
									<Command>
										<CommandInput class="h-9" placeholder="Search provider..." />
										<CommandList>
											<CommandEmpty>No provider found.</CommandEmpty>
											<CommandGroup>
												<CommandItem v-for="id in providerIds" :key="id" :value="id"
													@select="(ev) => selectWorkerProvider(String(ev.detail.value))">
													{{ id }}
													<Check :class="['ml-auto', agentWorker.providerId === id ? 'opacity-100' : 'opacity-0']" />
												</CommandItem>
											</CommandGroup>
										</CommandList>
									</Command>
								</PopoverContent>
							</Popover>
						</div>
						<div class="flex flex-col gap-1.5">
							<Label>Worker model</Label>
							<Popover v-model:open="workerModelOpen">
								<PopoverTrigger as-child>
									<Button variant="outline" role="combobox" :aria-expanded="workerModelOpen"
										:disabled="!agentWorker.providerId" class="w-full justify-between font-normal">
										<span :class="agentWorker.modelName ? '' : 'text-muted-foreground'">
											{{ agentWorker.modelName || "Select model..." }}
										</span>
										<ChevronsUpDown class="opacity-50" />
									</Button>
								</PopoverTrigger>
								<PopoverContent class="w-(--reka-popover-trigger-width) p-0">
									<Command>
										<CommandInput class="h-9" placeholder="Search model..." />
										<CommandList>
											<CommandEmpty>No model found.</CommandEmpty>
											<CommandGroup>
												<CommandItem v-for="model in agentModels[agentWorker.providerId] ?? []"
													:key="model.name" :value="model.name"
													@select="(ev) => selectWorkerModel(String(ev.detail.value))">
													{{ model.name }}
													<Check :class="['ml-auto', agentWorker.modelName === model.name ? 'opacity-100' : 'opacity-0']" />
												</CommandItem>
											</CommandGroup>
										</CommandList>
									</Command>
								</PopoverContent>
							</Popover>
						</div>
					</div>

					<div class="flex flex-col gap-1.5">
						<Label for="agent_max_rounds">Default tool-call rounds</Label>
						<Input id="agent_max_rounds" v-model.number="agentMaxRounds" type="number" min="2" max="32"
							placeholder="16" />
						<p class="text-[11px] text-muted-foreground">
							Budget for one sub-agent run (2–32). The agent can request more per call, up to 32.
						</p>
					</div>

					<div class="flex items-center gap-3">
						<Button size="sm" :disabled="agentSaving" @click="saveAgents">
							{{ agentSaving ? "Saving..." : "Save sub-agent settings" }}
						</Button>
						<span v-if="agentSaved" class="text-xs text-emerald-500">Saved</span>
						<span v-if="agentError" class="text-xs text-destructive wrap-break-word">{{ agentError }}</span>
					</div>
				</div>
			</section>
		</div>
	</ScrollArea>
</template>

<script>
import { Button } from "@/components/ui/button";
import {
	Command,
	CommandEmpty,
	CommandGroup,
	CommandInput,
	CommandItem,
	CommandList,
} from "@/components/ui/command";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import {
	Collapsible,
	CollapsibleContent,
	CollapsibleTrigger,
} from "@/components/ui/collapsible";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Separator } from "@/components/ui/separator";
import { useSettingsStore } from "@/stores/settings";
import { useMcpStore } from "@/stores/mcp";
import { useTabsStore } from "@/stores/tabs";
import { providerLogo } from "@/lib/provider-logos";
import { Check, ChevronDown, ChevronsUpDown } from "@lucide/vue";

export default {
	name: "SettingsView",
	components: {
		Button,
		Collapsible,
		CollapsibleContent,
		CollapsibleTrigger,
		Input,
		Label,
		ScrollArea,
		Separator,
		ChevronDown,
		Check,
		ChevronsUpDown,
		Popover,
		PopoverContent,
		PopoverTrigger,
		Command,
		CommandEmpty,
		CommandGroup,
		CommandInput,
		CommandItem,
		CommandList,
	},
	data() {
		return {
			llamaOpen: false,
			connecting: false,
			error: "",
			health: null,
			form: {
				apiUrl: "http://localhost:8080",
				contextWindow: 8192,
				apiKey: "",
			},
			ollamaOpen: false,
			connectingOllama: false,
			ollamaError: "",
			ollamaHealth: null,
			ollamaForm: {
				apiUrl: "http://localhost:11434",
				contextWindow: 8192,
			},
			openrouterOpen: false,
			connectingOpenRouter: false,
			openrouterError: "",
			openrouterHealth: null,
			openrouterForm: {
				apiKey: "",
			},
			clineOpen: false,
			connectingCline: false,
			clineError: "",
			clineHealth: null,
			clineForm: {
				apiKey: "",
			},
			mcp: useMcpStore(),
			mcpConfig: null,
			mcpFileError: "",
			agentWorker: { providerId: "", modelName: "" },
			workerProviderOpen: false,
			workerModelOpen: false,
			agentMaxRounds: 16,
			agentModels: {},
			agentSaving: false,
			agentError: "",
			agentSaved: false,
		};
	},
	computed: {
		providerIds() {
			return Object.keys(useSettingsStore().providers ?? {});
		},
	},
	async mounted() {
		this.mcp.startFeed();
		this.fetchMcpConfig();
		const settings = useSettingsStore();
		try {
			await settings.fetch();
			const llama = settings.llamaCpp;
			this.form = {
				apiUrl: llama.apiUrl,
				contextWindow: llama.contextWindow,
				apiKey: llama.apiKey ?? "",
			};
			const ollama = settings.ollama;
			this.ollamaForm = {
				apiUrl: ollama.apiUrl,
				contextWindow: ollama.contextWindow,
			};
			this.ollamaOpen = Boolean(settings.providers.ollama);
			const openrouter = settings.openrouter;
			this.openrouterForm = {
				apiKey: openrouter.apiKey ?? "",
			};
			this.openrouterOpen = Boolean(settings.providers.openrouter);
			const cline = settings.cline;
			this.clineForm = {
				apiKey: cline.apiKey ?? "",
			};
			this.clineOpen = Boolean(settings.providers.cline);
			await this.initAgents();
		} catch (err) {
			this.error = err instanceof Error ? err.message : String(err);
		}
	},
	methods: { providerLogo,
		// ---- Sub-agents ---------------------------------------------------------------------
		async initAgents() {
			try {
				const response = await fetch(`${window.location.origin}/api/settings`);
				if (!response.ok) return;
				const data = await response.json();
				const worker = data.workerModel;
				this.agentWorker = {
					providerId: worker?.providerId ?? "",
					modelName: worker?.modelName ?? "",
				};
				this.agentMaxRounds = data.defaultMaxRounds ?? 16;
				if (this.agentWorker.providerId) this.loadAgentModels(this.agentWorker.providerId);
			} catch {
				// Non-fatal; the section simply shows defaults.
			}
		},
		async loadAgentModels(providerId) {
			if (!providerId || this.agentModels[providerId]) return;
			try {
				const response = await fetch(
					`${window.location.origin}/api/settings/providers/${encodeURIComponent(providerId)}/models`,
				);
				if (!response.ok) return;
				this.agentModels[providerId] = await response.json();
			} catch {
				// Transient; the select simply stays empty.
			}
		},
		onWorkerProviderChange() {
			this.agentWorker.modelName = "";
			if (this.agentWorker.providerId) this.loadAgentModels(this.agentWorker.providerId);
		},
		selectWorkerProvider(id) {
			this.agentWorker.providerId = id;
			this.workerProviderOpen = false;
			this.onWorkerProviderChange();
		},
		selectWorkerModel(name) {
			this.agentWorker.modelName = name;
			this.workerModelOpen = false;
		},
		async saveAgents() {
			this.agentSaving = true;
			this.agentError = "";
			this.agentSaved = false;
			const rounds = Math.trunc(Number(this.agentMaxRounds));
			const body = {
				workerModel:
					this.agentWorker.providerId && this.agentWorker.modelName
						? { providerId: this.agentWorker.providerId, modelName: this.agentWorker.modelName }
						: null,
				defaultMaxRounds: Number.isFinite(rounds) ? rounds : null,
			};
			try {
				const response = await fetch(`${window.location.origin}/api/settings/agents`, {
					method: "PUT",
					headers: { "Content-Type": "application/json" },
					body: JSON.stringify(body),
				});
				if (!response.ok) {
					const text = await response.text();
					throw new Error(text || `HTTP ${response.status}`);
				}
				this.agentSaved = true;
				setTimeout(() => {
					this.agentSaved = false;
				}, 2500);
			} catch (err) {
				this.agentError = err instanceof Error ? err.message : String(err);
			} finally {
				this.agentSaving = false;
			}
		},
		// ---- Providers ----------------------------------------------------------------------
		async connect() {
			this.connecting = true;
			this.error = "";
			this.health = null;
			const settings = useSettingsStore();
			try {
				this.health = await settings.connectLlamaCpp(this.form);
			} catch (err) {
				this.error = err instanceof Error ? err.message : String(err);
			} finally {
				this.connecting = false;
			}
		},
		async connectOllama() {
			this.connectingOllama = true;
			this.ollamaError = "";
			this.ollamaHealth = null;
			const settings = useSettingsStore();
			try {
				this.ollamaHealth = await settings.connectOllama(this.ollamaForm);
			} catch (err) {
				this.ollamaError = err instanceof Error ? err.message : String(err);
			} finally {
				this.connectingOllama = false;
			}
		},
		async connectOpenRouterRequest() {
			this.connectingOpenRouter = true;
			this.openrouterError = "";
			this.openrouterHealth = null;
			const settings = useSettingsStore();
			try {
				this.openrouterHealth = await settings.connectOpenRouter(this.openrouterForm);
			} catch (err) {
				this.openrouterError = err instanceof Error ? err.message : String(err);
			} finally {
				this.connectingOpenRouter = false;
			}
		},
		async connectClineRequest() {
			this.connectingCline = true;
			this.clineError = "";
			this.clineHealth = null;
			const settings = useSettingsStore();
			try {
				this.clineHealth = await settings.connectCline(this.clineForm);
			} catch (err) {
				this.clineError = err instanceof Error ? err.message : String(err);
			} finally {
				this.connectingCline = false;
			}
		},
		// Opens mcp.json in the built-in file editor; saving it triggers a live reload.
		async openConfigFile() {
			this.mcpFileError = "";
			try {
				if (!this.mcpConfig) {
					this.mcpConfig = await this.mcp.fetchConfig();
				}
				useTabsStore().openTab({
					value: `file:${this.mcpConfig.path}`,
					label: "mcp.json",
					component: "FileEditorView",
					path: this.mcpConfig.path,
				});
			} catch (err) {
				this.mcpFileError = err instanceof Error ? err.message : String(err);
			}
		},
		async fetchMcpConfig() {
			try {
				this.mcpConfig = await this.mcp.fetchConfig();
			} catch {
				// Surfaced when the user clicks the open button.
			}
		},
	},
};
</script>
