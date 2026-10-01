/**
 * Settings search. The tree is small (<50 tokens), so this is a plain linear
 * scan: no index, no debounce, no dependency. What matters is the scoring, so
 * a title hit always outranks a hit buried in a paragraph of help text.
 */

/** Per-field weights. A title hit must beat a description hit. */
const WEIGHTS = { title: 10, keywords: 8, header: 5, labels: 4, description: 2 };

/** Lowercase, strip diacritics and punctuation. */
function normalize(value) {
	return String(value ?? "")
		.toLowerCase()
		.normalize("NFD")
		.replace(/\p{Diacritic}/gu, "")
		.replace(/[^a-z0-9.]+/g, " ")
		.trim();
}

/**
 * Words of a string. A dotted word also yields its parts and its squashed
 * form, so "llama.cpp" answers to a "llamacpp" query.
 */
function tokenize(value) {
	const flat = normalize(value);
	if (!flat) return [];
	const tokens = [];
	for (const word of flat.split(/\s+/)) {
		tokens.push(word);
		if (word.includes(".")) tokens.push(word.replace(/\./g, ""), ...word.split("."));
	}
	return tokens;
}

/** Best score one query token can get across every field of an item. */
function scoreToken(token, fields) {
	let best = 0;
	for (const [name, text] of fields) {
		const weight = WEIGHTS[name] ?? 1;
		for (const word of tokenize(text)) {
			const points = word === token ? 3 : word.startsWith(token) ? 2 : word.includes(token) ? 1 : 0;
			if (points * weight > best) best = points * weight;
		}
	}
	return best;
}

function fieldsOf(item) {
	return [
		["title", item.title],
		["keywords", (item.keywords ?? []).join(" ")],
		["header", item.parent?.title ?? ""],
		["labels", (item.fields ?? []).map((field) => field.label).join(" ")],
		["description", item.description ?? ""],
	];
}

/**
 * Ranks items against a query. Every query token has to hit somewhere (AND
 * across tokens, OR across fields), so "worker model" lands on Sub-agents
 * while "worker api" finds nothing.
 *
 * @returns {{ item: object, score: number }[]} best match first
 */
export function searchSettings(items, query) {
	const tokens = [...new Set(tokenize(query))];
	if (!tokens.length) return [];

	return items
		.map((item) => {
			const fields = fieldsOf(item);
			let score = 0;
			for (const token of tokens) {
				const points = scoreToken(token, fields);
				if (!points) return null;
				score += points;
			}
			// Shorter titles win ties, so "API key" outranks a longer match.
			return { item, score: score - item.title.length * 0.01 };
		})
		.filter(Boolean)
		.sort((a, b) => b.score - a.score);
}

/**
 * Splits text into `{ text, hit }` runs so a match can be marked without
 * `v-html`. Longest tokens first, then merged, so overlapping hits stay clean.
 */
export function highlight(text, query) {
	const source = String(text ?? "");
	const tokens = [...new Set(tokenize(query))].sort((a, b) => b.length - a.length);
	if (!tokens.length || !source) return [{ text: source, hit: false }];

	const haystack = source.toLowerCase();
	const ranges = [];
	for (const token of tokens) {
		for (let from = 0; ; ) {
			const at = haystack.indexOf(token, from);
			if (at === -1) break;
			ranges.push([at, at + token.length]);
			from = at + token.length;
		}
	}
	if (!ranges.length) return [{ text: source, hit: false }];

	ranges.sort((a, b) => a[0] - b[0]);
	const merged = [ranges[0]];
	for (const range of ranges.slice(1)) {
		const last = merged[merged.length - 1];
		if (range[0] <= last[1]) last[1] = Math.max(last[1], range[1]);
		else merged.push(range);
	}

	const parts = [];
	let cursor = 0;
	for (const [start, end] of merged) {
		if (start > cursor) parts.push({ text: source.slice(cursor, start), hit: false });
		parts.push({ text: source.slice(start, end), hit: true });
		cursor = end;
	}
	if (cursor < source.length) parts.push({ text: source.slice(cursor), hit: false });
	return parts;
}
