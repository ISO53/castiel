<template>
	<aside class="flex h-full min-h-0 w-full flex-col bg-background">
		<header class="flex h-8 shrink-0 items-center justify-between gap-2 border-b px-3">
			<div class="flex items-center gap-1.5 min-w-0">
				<History class="size-3.5 shrink-0 text-muted-foreground" />
				<span class="truncate text-xs font-medium text-foreground">Chat History</span>
			</div>
			<Button
				variant="ghost"
				size="icon-sm"
				aria-label="Close history"
				title="Close history"
				@click="chats.historyOpen = false"
			>
				<X class="size-3.5" />
			</Button>
		</header>

		<div class="min-h-0 flex-1 overflow-y-auto p-2">
			<div v-if="!cwd" class="px-2 py-4 text-center text-xs text-muted-foreground">
				Open a workspace to view and save chat history.
			</div>
			<div v-else-if="chats.loading" class="flex items-center justify-center gap-2 px-2 py-4 text-xs text-muted-foreground">
				<Spinner class="size-3 shrink-0" /> Loading history…
			</div>
			<div v-else-if="!chats.chats.length" class="px-2 py-4 text-center text-xs text-muted-foreground">
				No saved chats in this workspace.
			</div>
			<div v-else class="flex flex-col gap-1">
				<div
					v-for="chat in chats.chats"
					:key="chat.id"
					class="group relative flex flex-col gap-1 rounded-md p-2 text-left text-xs transition-colors hover:bg-accent cursor-pointer"
					:class="{ 'bg-accent text-accent-foreground font-medium': chats.activeChatId === chat.id }"
					@click="onSelectChat(chat.id)"
				>
					<div class="flex items-center justify-between gap-1">
						<span class="truncate font-medium flex-1 text-foreground" :title="chat.title">{{ chat.title || 'Untitled chat' }}</span>
						<Button
							variant="ghost"
							size="icon-xs"
							class="opacity-0 group-hover:opacity-100 transition-opacity hover:text-destructive hover:bg-destructive/10 -mr-1"
							title="Delete chat"
							@click.stop="handleDeleteChat(chat.id)"
						>
							<Trash2 class="size-3" />
						</Button>
					</div>

					<div class="flex items-center justify-between gap-2 text-[10px] text-muted-foreground">
						<div class="flex items-center gap-1 truncate">
							<img
								v-if="providerLogo(chat.providerId)"
								:src="providerLogo(chat.providerId)"
								class="size-2.5 shrink-0"
								alt=""
								aria-hidden="true"
							/>
							<span class="truncate">{{ chat.modelName || chat.providerId }}</span>
						</div>
						<span class="shrink-0">{{ formatChatDate(chat.updatedAt || chat.createdAt) }}</span>
					</div>
				</div>
			</div>
		</div>
	</aside>
</template>

<script>
import { History, Trash2, X } from "@lucide/vue";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { useChatsStore } from "@/stores/chats";
import { useWorkspaceStore } from "@/stores/workspace";
import { providerLogo } from "@/lib/provider-logos";

export default {
	name: "ChatHistoryPanel",
	components: {
		Button,
		History,
		Spinner,
		Trash2,
		X,
	},
	data() {
		return {
			chats: useChatsStore(),
			workspace: useWorkspaceStore(),
		};
	},
	computed: {
		cwd() {
			return this.workspace.cwd;
		},
	},
	mounted() {
		if (this.cwd) {
			this.chats.fetchList().catch(() => {});
		}
	},
	methods: {
		providerLogo,
		formatChatDate(isoString) {
			if (!isoString) return "";
			const date = new Date(isoString);
			if (isNaN(date.getTime())) return "";
			const now = new Date();
			const isToday = date.toDateString() === now.toDateString();
			if (isToday) {
				return date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
			}
			const isThisYear = date.getFullYear() === now.getFullYear();
			if (isThisYear) {
				return date.toLocaleDateString([], { month: "short", day: "numeric" });
			}
			return date.toLocaleDateString([], { year: "numeric", month: "short", day: "numeric" });
		},
		async onSelectChat(chatId) {
			await this.chats.selectChat(chatId);
		},
		async handleDeleteChat(chatId) {
			await this.chats.deleteChat(chatId);
		},
	},
};
</script>
