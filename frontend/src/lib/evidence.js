/**
 * Evidence artifact helpers shared by the evidence view and its detail dialog.
 *
 * An artifact's key is its `name`: the exact file name under `evidence/` for saved
 * files (other documents reference artifacts by that name), or a short slug for
 * inline loot such as credentials that carry a `secret` instead of a file.
 */

export const ARTIFACT_KINDS = ["screenshot", "scan", "capture", "poc", "log", "credential", "other"];

const IMAGE_EXTENSIONS = new Set(["png", "jpg", "jpeg", "gif", "webp", "svg", "bmp", "avif"]);

/** Maps every legacy artifact shape onto the `name` contract. */
export function normalizeArtifact(artifact) {
	if (!artifact || typeof artifact !== "object") return null;
	const name = firstPresent(artifact, ["name", "path", "file", "location"]);
	if (!name) return null;
	return {
		...artifact,
		name,
		kind: String(artifact.kind ?? "other").toLowerCase(),
		title: artifact.title ?? "",
		description: firstPresent(artifact, ["description", "note"]) ?? "",
		target: firstPresent(artifact, ["target", "related_to", "entity"]) ?? "",
		vulnerability: artifact.vulnerability ?? "",
		username: artifact.username ?? "",
		secret: artifact.secret ?? "",
		discovered_by: artifact.discovered_by ?? "",
		discovered_at: artifact.discovered_at ?? "",
	};
}

function firstPresent(object, keys) {
	for (const key of keys) {
		if (object[key] !== undefined && object[key] !== null && object[key] !== "") return object[key];
	}
	return null;
}

/** Extracts the bare file name from an artifact's `name`. */
export function artifactFileName(artifact) {
	return String(artifact?.name ?? "").split(/[\\/]/).pop() ?? String(artifact?.name ?? "");
}

/** Resolves a file's extension, or "" when it has none. */
export function artifactExtension(artifact) {
	const fileName = artifactFileName(artifact);
	const dot = fileName.lastIndexOf(".");
	if (dot <= 0) return "";
	return fileName.slice(dot + 1).toLowerCase();
}

/** Whether the artifact points at an image file that the gallery can render. */
export function isImageArtifact(artifact) {
	return IMAGE_EXTENSIONS.has(artifactExtension(artifact));
}

/** Whether the artifact is a directory marker instead of a file. */
export function isDirectoryArtifact(artifact) {
	const name = String(artifact?.name ?? "");
	return name.endsWith("/") || name.endsWith("\\");
}

/** Whether the artifact carries inline loot instead of pointing at a file. */
export function isCredentialArtifact(artifact) {
	return artifact?.kind === "credential";
}

/** Whether clicking the artifact can open something: a preview, editor tab or lightbox. */
export function isViewableArtifact(artifact) {
	return !isCredentialArtifact(artifact) && !isDirectoryArtifact(artifact);
}

/** Masks a secret for table display, keeping at most a short head and tail. */
export function maskSecret(secret) {
	const value = String(secret ?? "");
	if (!value) return "";
	if (value.length <= 6) return "•".repeat(value.length);
	return `${value.slice(0, 3)}${"•".repeat(Math.max(value.length - 5, 3))}${value.slice(-2)}`;
}
