<script setup>
// "What is castiel" manifesto: a short, centered explanation of the product,
// placed between the app preview and the feature bento. Copy stays consistent
// with README.md and the hero — no claims beyond those texts.
</script>

<template>
	<section class="manifesto container">
		<h2 class="manifesto-title">The whole engagement, run through a single chat.</h2>

		<p class="manifesto-copy">
			castiel is an <strong>AI-assisted penetration testing harness</strong>. Open a
			project folder and connect a language model of your choice. From there, everything
			happens in a chat: the model runs commands, reads and edits files, launches
			background processes and connects to MCP servers, organizing what it finds into
			structured documents you can review as graphs, boards and tables.
			<strong>You stay in charge.</strong> You define the target and scope, decide when
			the engagement moves to the next phase, and approve how far things go, while the
			model does the hands-on technical work and keeps every finding recorded in the workspace.
		</p>
	</section>
</template>

<style scoped>
.manifesto {
	margin-block: 0 6rem;
	scroll-margin-top: 5rem;
	text-align: center;
}

.manifesto-title {
	max-width: 24ch;
	margin: 0 auto 2rem;
	font-size: clamp(1.75rem, 4vw, 2.75rem);
	font-weight: 500;
	line-height: 1.15;
	letter-spacing: -0.03em;
}

.manifesto-copy {
	max-width: 44rem;
	margin: 0 auto 1.25rem;
	font-size: clamp(0.95rem, 1.6vw, 1.05rem);
	line-height: 1.7;
	color: var(--muted);
}

.manifesto-copy strong {
	color: var(--fg);
	font-weight: 600;
}
</style>
