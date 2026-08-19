<template>
	<div class="ide-container">
		<el-splitter class="main-splitter">
			<!-- Left Panel (Collapsible) -->
			<el-splitter-panel collapsible size="20%" min="50px">
				<div class="panel"></div>
			</el-splitter-panel>

			<!-- Middle Panel (Split vertically into Main View and Bottom Panel) -->
			<el-splitter-panel size="55%">
				<el-splitter layout="vertical">
					<!-- Center Top Panel (Main View - Non-collapsible) -->
					<el-splitter-panel size="70%" min="50px">
						<div class="panel"></div>
					</el-splitter-panel>

					<!-- Center Bottom Panel (Collapsible) -->
					<el-splitter-panel collapsible size="30%" min="50px">
						<div class="panel"></div>
					</el-splitter-panel>
				</el-splitter>
			</el-splitter-panel>

			<!-- Right Panel (Collapsible - Hosts AI Chat) -->
			<el-splitter-panel collapsible size="25%" min="180px">
				<div class="panel panel--ai">
					<ChatContainer />
				</div>
			</el-splitter-panel>
		</el-splitter>
	</div>
</template>

<script setup>
import ChatContainer from "@/components/ai/ChatContainer.vue";
</script>

<style scoped>
.ide-container {
	height: 100vh;
	width: 100vw;
	overflow: hidden;
	background-color: var(--bg-root);
}

.main-splitter {
	height: 100%;
	width: 100%;
}

.panel {
	height: 100%;
	width: 100%;
	background-color: var(--bg-panel);
	overflow: auto;
}

.panel--ai {
	display: flex;
	flex-direction: column;
	overflow: hidden;
}
</style>
