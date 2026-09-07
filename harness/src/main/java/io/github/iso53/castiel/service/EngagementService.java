package io.github.iso53.castiel.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.iso53.castiel.model.EngagementPhase;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
/**
 * Reads and updates {@code engagement.json}, the document holding the engagement's
 * scope, rules of engagement, and current phase.
 *
 * <p>The phase advances when the user moves it from the UI or the model edits the
 * document directly; this service is the shared read/write path for both.
 */
@Service
public class EngagementService {

	public static final String ENGAGEMENT_FILE = "engagement.json";
	public static final int FIRST_PHASE = 1;
	public static final int LAST_PHASE = 5;

	private static final List<String> PHASE_NAMES = List.of(
		"Planning & Pre-Engagement",
		"Reconnaissance",
		"Scanning & Vulnerability Assessment",
		"Exploitation",
		"Wrap-Up"
	);

	private static final List<String> PHASE_SUMMARIES = List.of(
		"No active testing. Discuss the target, scope, objectives, and rules with the user;"
			+ " passive OSINT from third-party sources only, no traffic to the target.",
		"Information gathering is open. Interact with in-scope targets freely and fill in"
			+ " network.json, web.json, and identity.json.",
		"Probe in-scope targets for weaknesses using the recon data; record each issue in"
			+ " vulnerabilities.json with a CVSS score, proof state, and evidence.",
		"Attempt to exploit confirmed vulnerabilities. Report every attempt and result to"
			+ " the user; stop when told to.",
		"The engagement is finished. Summarize results and follow the user's direction."
	);

	private static final ObjectMapper JSON = new ObjectMapper();

	private final WorkspaceSession workspace;

	public EngagementService(WorkspaceSession workspace) {
		this.workspace = workspace;
	}

	/**
	 * Returns the current phase. Defaults to phase 1 when no workspace is open or the
	 * document is missing/corrupt, so the tool and UI always get a usable answer.
	 */
	public EngagementPhase currentPhase() {
		Optional<Integer> stored = readPhaseFromDocument();
		return describe(stored.map(EngagementService::clampPhase).orElse(FIRST_PHASE));
	}

	/**
	 * Sets the phase in {@code engagement.json}, preserving the rest of the document.
	 *
	 * @throws IllegalStateException When no workspace is open or the document cannot be written.
	 */
	public EngagementPhase setPhase(int requested) {
		int phase = clampPhase(requested);
		Path file = workspaceRoot().resolve(ENGAGEMENT_FILE);
		try {
			ObjectNode document = Files.exists(file) && JSON.readTree(file.toFile()) instanceof ObjectNode node
				? node
				: JSON.createObjectNode();
			document.put("phase", phase);
			JSON.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), document);
		} catch (IOException ex) {
			throw new IllegalStateException("Could not update " + ENGAGEMENT_FILE + ": " + ex.getMessage(), ex);
		}
		return describe(phase);
	}

	/** Builds the phase snapshot served everywhere; single source of naming truth. */
	public static EngagementPhase describe(int phase) {
		return new EngagementPhase(phase, PHASE_NAMES.get(phase - 1), PHASE_SUMMARIES.get(phase - 1), LAST_PHASE);
	}

	private Optional<Integer> readPhaseFromDocument() {
		Path file = workspaceRoot().resolve(ENGAGEMENT_FILE);
		if (!Files.isRegularFile(file)) {
			return Optional.empty();
		}
		try {
			JsonNode phase = JSON.readTree(file.toFile()).get("phase");
			return phase != null && phase.canConvertToInt() ? Optional.of(phase.asInt()) : Optional.empty();
		} catch (IOException | RuntimeException ex) {
			return Optional.empty();
		}
	}

	private Path workspaceRoot() {
		return workspace.root().orElseThrow(() ->
			new IllegalStateException("No workspace is open")
		);
	}

	private static int clampPhase(int value) {
		return Math.clamp(value, FIRST_PHASE, LAST_PHASE);
	}
}