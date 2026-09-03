<template>
	<ScrollArea class="h-full">
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
											rel="noreferrer">OpenRouter BYOK settings</a> —
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
			</section>
		</div>
	</ScrollArea>
</template>

<script>
import { Button } from "@/components/ui/button";
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
import { providerLogo } from "@/lib/provider-logos";
import { ChevronDown } from "@lucide/vue";

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
		};
	},
	async mounted() {
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
		} catch (err) {
			this.error = err instanceof Error ? err.message : String(err);
		}
	},
	methods: { providerLogo,
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
	},
};
</script>
