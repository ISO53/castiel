/**
 * CVSS scoring helpers shared by the vulnerabilities view.
 *
 * Findings recorded through the harness's cvss_score tool carry per-version
 * assessments: `cvss: { "4.0": { vector, score }, "3.1": { vector, score } }`.
 * Severity is never stored on a finding — it is derived from the score, so
 * agent and UI can never disagree about it. Info (0) is a real band, not an
 * exclusion.
 */
export const VULN_BANDS = [
	{ id: "critical", label: "Critical", min: 9, max: 10, color: "#ef4444" },
	{ id: "high", label: "High", min: 7, max: 9, color: "#f97316" },
	{ id: "medium", label: "Medium", min: 4, max: 7, color: "#eab308" },
	{ id: "low", label: "Low", min: 0.1, max: 4, color: "#38bdf8" },
	{ id: "info", label: "Info", min: 0, max: 0.1, color: "#71717a" },
];

/**
 * Returns a finding's stored assessment for a version ({ vector, score }) or
 * null when the finding has none for it.
 */
function cvssEntryOf(finding, version) {
	const entry = finding?.cvss?.[version];
	return entry && Number.isFinite(Number(entry.score)) ? entry : null;
}

/**
 * Numerically scores a finding for the requested CVSS version. Falls back to
 * the legacy shapes (a bare `cvss` number, or `cvss.score`) written before the
 * per-version assessments existed; missing values count as informational (0).
 */
export function scoreOf(finding, version = "4.0") {
	const entry = cvssEntryOf(finding, version);
	if (entry) {
		return clamp(Number(entry.score));
	}
	const raw = typeof finding?.cvss === "number" ? finding.cvss : Number(finding?.cvss?.score);
	return clamp(Number.isFinite(raw) ? raw : 0);
}

/** Resolves a finding's severity band from its CVSS score for a version. */
export function bandOf(finding, version = "4.0") {
	const score = scoreOf(finding, version);
	return (
		VULN_BANDS.find((band) => score >= band.min && (score < band.max || (band.id === "critical" && score <= band.max))) ??
		VULN_BANDS[VULN_BANDS.length - 1]
	);
}

function clamp(raw) {
	return Math.min(10, Math.max(0, raw));
}

/** Tailwind classes for the POTENTIAL/PROVED proof badge. */
export function proofBadgeClass(proof) {
	return proof === "proved"
		? "bg-emerald-500/15 text-emerald-400"
		: "bg-amber-500/15 text-amber-400";
}
