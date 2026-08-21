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
						<span class="text-sm font-medium text-foreground">llama.cpp</span>
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
			llamaOpen: true,
			connecting: false,
			error: "",
			health: null,
			form: {
				apiUrl: "http://localhost:8080",
				contextWindow: 8192,
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
		} catch (err) {
			this.error = err instanceof Error ? err.message : String(err);
		}
	},
	methods: {
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
	},
};
</script>
