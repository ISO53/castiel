/**
 * CVSS v3.1 severity bands shared by the vulnerabilities view. Severity is never
 * stored on a finding — it is derived from the CVSS score, so agent and UI can
 * never disagree about it. Info (0) is a real band, not an exclusion.
 */
export const VULN_BANDS = [
	{ id: "critical", label: "Critical", min: 9, max: 10, color: "#ef4444" },
	{ id: "high", label: "High", min: 7, max: 9, color: "#f97316" },
	{ id: "medium", label: "Medium", min: 4, max: 7, color: "#eab308" },
	{ id: "low", label: "Low", min: 0.1, max: 4, color: "#38bdf8" },
	{ id: "info", label: "Info", min: 0, max: 0.1, color: "#71717a" },
];

/**
 * Numerically scores a finding. Accepts `cvss` as a bare number or `{score}`;
 * missing or unparseable values count as informational (0).
 */
export function scoreOf(finding) {
	const raw = typeof finding?.cvss === "number" ? finding.cvss : Number(finding?.cvss?.score);
	return Number.isFinite(raw) ? Math.min(10, Math.max(0, raw)) : 0;
}

/** Resolves a finding's severity band from its CVSS score. */
export function bandOf(finding) {
	const score = scoreOf(finding);
	return (
		VULN_BANDS.find((band) => score >= band.min && (score < band.max || (band.id === "critical" && score <= band.max))) ??
		VULN_BANDS[VULN_BANDS.length - 1]
	);
}

/** Tailwind classes for the POTENTIAL/PROVED proof badge. */
export function proofBadgeClass(proof) {
	return proof === "proved"
		? "bg-emerald-500/15 text-emerald-400"
		: "bg-amber-500/15 text-amber-400";
}
