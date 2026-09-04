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

			<section class="space-y-4">
				<div class="space-y-1">
					<h2 class="text-sm font-semibold text-foreground">MCP Servers</h2>
					<p class="text-xs text-muted-foreground">
						Register Model Context Protocol servers to give the agent extra tools.
					</p>
				</div>
				<Separator />

				<div v-if="mcp.servers.length" class="space-y-2">
					<div
						v-for="server in mcp.servers"
						:key="server.id"
						class="flex items-center justify-between gap-3 rounded-lg border border-border px-4 py-2.5"
					>
						<div class="min-w-0">
							<p class="truncate text-sm font-medium text-foreground">{{ server.name }}</p>
							<p class="truncate font-mono text-[11px] text-muted-foreground">
								{{ server.target }} · {{ server.tools.length }} tool(s)
							</p>
						</div>
						<div class="flex shrink-0 items-center gap-3">
							<span
								class="flex items-center gap-1.5 text-xs"
								:class="server.connected ? 'text-muted-foreground' : 'text-destructive'"
							>
								<span
									class="size-2 rounded-full"
									:class="server.connected ? 'bg-emerald-500' : 'bg-destructive'"
									aria-hidden="true"
								/>
								{{ server.connected ? "Running" : "Down" }}
							</span>
							<Button
								variant="ghost"
								size="icon-sm"
								class="text-muted-foreground hover:text-foreground"
								:aria-label="`Reconnect to MCP server ${server.name}`"
								:title="`Reconnect to ${server.name}`"
								:disabled="mcpReconnecting[server.id]"
								@click="reconnectServer(server)"
							>
								<RefreshCw :class="mcpReconnecting[server.id] ? 'animate-spin' : ''" />
							</Button>
							<Button
								variant="ghost"
								size="icon-sm"
								class="text-muted-foreground hover:text-destructive"
								aria-label="Remove MCP server"
								@click="removeServer(server)"
							>
								<Trash2 />
							</Button>
						</div>
					</div>
				</div>
				<p v-else class="text-xs text-muted-foreground">No MCP servers registered yet.</p>
				<p v-if="mcpError" class="text-xs text-destructive wrap-break-word">{{ mcpError }}</p>

				<Collapsible v-model:open="mcpAddOpen" class="rounded-lg border border-border">
					<CollapsibleTrigger
						class="flex w-full items-center justify-between gap-3 px-4 py-3 text-left hover:bg-muted/40">
						<span class="flex items-center gap-2 text-sm font-medium text-foreground">
							<Plug class="size-4 shrink-0" />
							Add MCP Server
						</span>
						<ChevronDown class="size-4 shrink-0 text-muted-foreground transition-transform duration-200"
							:class="mcpAddOpen ? 'rotate-180' : ''" />
					</CollapsibleTrigger>

					<CollapsibleContent class="border-t border-border px-4 py-4">
						<div class="flex flex-col gap-4">
							<div class="space-y-2 text-xs text-muted-foreground leading-relaxed">
								<p>
									Start the MCP server yourself, then connect Castiel to it. Castiel does not
									install or run servers for you.
								</p>
							</div>

							<div class="grid grid-cols-2 gap-3">
								<div class="flex flex-col gap-1.5">
									<Label for="mcp_name">Display name</Label>
									<Input id="mcp_name" v-model="mcpForm.name" placeholder="My browser server"
										autocomplete="off" spellcheck="false" />
								</div>
								<div class="flex flex-col gap-1.5">
									<Label for="mcp_transport">Transport</Label>
									<Select v-model="mcpForm.transport">
										<SelectTrigger id="mcp_transport">
											<SelectValue placeholder="Choose transport" />
										</SelectTrigger>
										<SelectContent>
											<SelectItem value="HTTP">HTTP</SelectItem>
											<SelectItem value="STDIO">STDIO (subprocess)</SelectItem>
										</SelectContent>
									</Select>
								</div>
							</div>

							<template v-if="mcpForm.transport === 'HTTP'">
								<div class="grid grid-cols-3 gap-3">
									<div class="col-span-2 flex flex-col gap-1.5">
										<Label for="mcp_host">Host</Label>
										<Input id="mcp_host" v-model="mcpForm.host" placeholder="127.0.0.1"
											autocomplete="off" spellcheck="false" />
									</div>
									<div class="flex flex-col gap-1.5">
										<Label for="mcp_port">Port</Label>
										<Input id="mcp_port" v-model.number="mcpForm.port" type="number" min="1"
											max="65535" placeholder="3000" />
									</div>
								</div>
								<div class="flex flex-col gap-1.5">
									<Label for="mcp_path">Endpoint path</Label>
									<Input id="mcp_path" v-model="mcpForm.path" placeholder="mcp"
										autocomplete="off" spellcheck="false" />
									<p class="text-[11px] text-muted-foreground">Default: mcp</p>
								</div>
							</template>

							<template v-else>
								<div class="flex flex-col gap-1.5">
									<Label for="mcp_command">Command</Label>
									<Input id="mcp_command" v-model="mcpForm.command" placeholder="caido-mcp-server.exe"
										autocomplete="off" spellcheck="false" />
								</div>
								<div class="flex flex-col gap-1.5">
									<Label for="mcp_args">Arguments</Label>
									<Input id="mcp_args" v-model="mcpForm.args" placeholder="serve"
										autocomplete="off" spellcheck="false" />
									<p class="text-[11px] text-muted-foreground">
										Space-separated arguments passed to the command.
									</p>
								</div>
								<div class="flex flex-col gap-1.5">
									<Label for="mcp_env">Environment variables</Label>
									<Input id="mcp_env" v-model="mcpForm.env" placeholder="CAIDO_URL=http://127.0.0.1:8080"
										autocomplete="off" spellcheck="false" />
									<p class="text-[11px] text-muted-foreground">
										Optional. KEY=value pairs separated by spaces.
									</p>
								</div>
							</template>

							<div class="flex items-center gap-3 pt-1">
								<Button size="sm" :disabled="mcpProbing" @click="probeServer">
									{{ mcpProbing ? "Testing..." : "Test connection" }}
								</Button>
								<Button v-if="mcpProbeResult" size="sm" :disabled="mcpAdding" @click="addServer">
									{{ mcpAdding ? "Adding..." : "Add" }}
								</Button>
								<div v-if="mcpProbeResult" class="flex min-w-0 items-center gap-2 text-xs">
									<span
										class="size-2 shrink-0 rounded-full"
										:class="mcpProbeResult.connected ? 'bg-emerald-500' : 'bg-destructive'"
										aria-hidden="true"
									/>
									<span class="truncate text-muted-foreground">
										{{ mcpProbeResult.tools.length }} tool(s) offered by the server
									</span>
								</div>
							</div>
							<p v-if="mcpProbeResult && mcpProbeResult.tools.length"
								class="break-all font-mono text-[11px] text-muted-foreground">
								{{ mcpProbeResult.tools.join(", ") }}
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
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Separator } from "@/components/ui/separator";
import { useSettingsStore } from "@/stores/settings";
import { useMcpStore } from "@/stores/mcp";
import { providerLogo } from "@/lib/provider-logos";
import { ChevronDown, Plug, RefreshCw, Trash2 } from "@lucide/vue";

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
		Select,
		SelectContent,
		SelectItem,
		SelectTrigger,
		SelectValue,
		Separator,
		ChevronDown,
		Plug,
		RefreshCw,
		Trash2,
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
			mcp: useMcpStore(),
			mcpAddOpen: false,
			mcpProbing: false,
			mcpAdding: false,
			mcpError: "",
			mcpProbeResult: null,
			mcpReconnecting: {},
			mcpForm: {
				name: "",
				transport: "HTTP",
				host: "127.0.0.1",
				port: 3000,
				path: "mcp",
				command: "",
				args: "",
				env: "",
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
		async probeServer() {
			this.mcpProbing = true;
			this.mcpError = "";
			this.mcpProbeResult = null;
			try {
				this.mcpProbeResult = await this.mcp.probe(this.mcpPayload());
			} catch (err) {
				this.mcpError = err instanceof Error ? err.message : String(err);
			} finally {
				this.mcpProbing = false;
			}
		},
		async addServer() {
			this.mcpAdding = true;
			this.mcpError = "";
			try {
				await this.mcp.add(this.mcpPayload());
				this.mcpProbeResult = null;
				this.mcpAddOpen = false;
			} catch (err) {
				this.mcpError = err instanceof Error ? err.message : String(err);
			} finally {
				this.mcpAdding = false;
			}
		},
		async removeServer(server) {
			this.mcpError = "";
			try {
				await this.mcp.remove(server.id);
			} catch (err) {
				this.mcpError = err instanceof Error ? err.message : String(err);
			}
		},
		// Retries the handshake with a registered server, e.g. after it was started late.
		async reconnectServer(server) {
			this.mcpError = "";
			this.mcpReconnecting[server.id] = true;
			try {
				const status = await this.mcp.reconnect(server.id);
				if (!status.connected) {
					this.mcpError = `Could not reach MCP server '${server.name}'. Make sure it is running, then try again.`;
				}
			} catch (err) {
				this.mcpError = err instanceof Error ? err.message : String(err);
			} finally {
				this.mcpReconnecting[server.id] = false;
			}
		},
		// Collects the form into the config payload the harness expects.
		mcpPayload() {
			const trimmedName = (this.mcpForm.name ?? "").trim();
			const payload = {
				id: trimmedName
					? trimmedName.toLowerCase().replace(/\s+/g, "-")
					: `mcp-server-${Date.now()}`,
				name: trimmedName,
				type: this.mcpForm.transport,
			};
			if (this.mcpForm.transport === "HTTP") {
				payload.host = (this.mcpForm.host ?? "").trim();
				payload.port = Number(this.mcpForm.port);
				payload.path = (this.mcpForm.path ?? "").trim() || "mcp";
			} else {
				payload.command = (this.mcpForm.command ?? "").trim();
				payload.args = (this.mcpForm.args ?? "").trim() ? this.mcpForm.args.trim().split(/\s+/) : [];
				payload.env = parseEnvPairs(this.mcpForm.env);
			}
			return payload;
		},
	},
};

// Parses "KEY=value" pairs separated by whitespace into an object.
function parseEnvPairs(text) {
	const env = {};
	for (const pair of (text ?? "").split(/\s+/)) {
		const separator = pair.indexOf("=");
		if (separator > 0) {
			env[pair.slice(0, separator)] = pair.slice(separator + 1);
		}
	}
	return env;
}
</script>
