import DOMPurify from "dompurify";

// Chat and agent output come from the model and, through it, from target content.
// Render only basic markdown formatting: forms would let target content phish the
// operator inside the harness UI, and remote images would phone home to the target
// as tracking beacons. Everything dynamic or embeddable is out as well.
const FORBIDDEN_TAGS = [
	"form",
	"input",
	"button",
	"select",
	"textarea",
	"img",
	"picture",
	"source",
	"iframe",
	"object",
	"embed",
	"video",
	"audio",
	"svg",
	"math",
	"base",
];

/** Strips everything beyond basic markdown formatting from rendered HTML. */
export function sanitizeMarkdown(html) {
	return DOMPurify.sanitize(html, { FORBID_TAGS: FORBIDDEN_TAGS });
}