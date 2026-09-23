package io.github.iso53.castiel.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import io.github.iso53.castiel.service.WorkspaceEventBus;
import io.github.iso53.castiel.service.WorkspaceSession;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.springframework.stereotype.Service;
import us.springett.cvss.Cvss;

/**
 * Attaches an exact CVSS assessment to a vulnerability in the workspace's
 * vulnerabilities.json. The orchestrator passes a v4.0 and a v3.1 vector; this
 * tool validates them, computes the official scores with the cvss-calculator
 * library, and stores vectors plus scores on the finding.
 */
@Service
public class CvssScoreTool implements ToolProvider {

	private static final String VULNERABILITIES_FILE = "vulnerabilities.json";

	private static final ObjectMapper JSON = new ObjectMapper();

	private final WorkspaceSession workspace;
	private final WorkspaceEventBus workspaceEvents;

	public CvssScoreTool(WorkspaceSession workspace, WorkspaceEventBus workspaceEvents) {
		this.workspace = workspace;
		this.workspaceEvents = workspaceEvents;
	}

	@Tool(
		name = "cvss_score",
		value = {
			"Attaches an exact CVSS assessment to a vulnerability in vulnerabilities.json.",
			"Provide a CVSS v4.0 and a CVSS v3.1 base vector string for the finding; the tool",
			"validates them, computes the official scores, and stores vectors plus scores on",
			"the finding identified by its id. NEVER invent or estimate a CVSS score yourself,",
			"and never write score numbers into vulnerabilities.json. Always call this tool.",
			"v4.0 mandatory metrics: AV (N/A/L/P), AC (L/H), AT (N/P), PR (N/L/H), UI (N/P/A),",
			"VC (H/L/N), VI (H/L/N), VA (H/L/N), SC (H/L/N), SI (S/H/L/N), SA (S/H/L/N).",
			"Optional v4.0 metrics: E (X/A/P/U), CR/IR/AR (X/H/M/L).",
			"v3.1 mandatory metrics: AV (N/A/L/P), AC (L/H), PR (N/L/H), UI (N/R), S (U/C),",
			"C (H/L/N), I (H/L/N), A (H/L/N).",
			"Examples: CVSS:4.0/AV:N/AC:L/AT:N/PR:N/UI:N/VC:H/VI:H/VA:H/SC:N/SI:N/SA:N and",
			"CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H.",
			"If the tool reports an invalid vector, fix the named metric and call it again.",
		}
	)
	public String score(
		@P("The id of the vulnerability in vulnerabilities.json to attach the assessment to") String vulnerabilityId,
		@P(
			"CVSS v4.0 base vector string for the finding, e.g. " +
				"CVSS:4.0/AV:N/AC:L/AT:N/PR:N/UI:N/VC:H/VI:H/VA:H/SC:N/SI:N/SA:N"
		) String vector40,
		@P(
			"CVSS v3.1 base vector string for the finding, e.g. " + "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H"
		) String vector31
	) {
		if (vulnerabilityId == null || vulnerabilityId.strip().isEmpty()) {
			return "Error: vulnerabilityId is required; use the finding's id in vulnerabilities.json";
		}
		if (vector40 == null || vector40.strip().isEmpty() || vector31 == null || vector31.strip().isEmpty()) {
			return "Error: both a CVSS:4.0 and a CVSS:3.1 vector are required";
		}

		double score40;
		double score31;
		try {
			score40 = baseScore(vector40, "4.0");
			score31 = baseScore(vector31, "3.1");
		} catch (RuntimeException ex) {
			return "Error: " + ex.getMessage();
		}

		Path root = workspace.root().orElseThrow(() -> new IllegalArgumentException("No workspace is open"));
		Path file = root.resolve(VULNERABILITIES_FILE);
		if (!Files.isRegularFile(file)) {
			return (
				"Error: " +
				VULNERABILITIES_FILE +
				" does not exist in the workspace; create the vulnerability entry first, then call this tool"
			);
		}

		ObjectNode document;
		try {
			document = (ObjectNode) JSON.readTree(file.toFile());
		} catch (IOException | RuntimeException ex) {
			return "Error: could not read " + VULNERABILITIES_FILE + ": " + ex.getMessage();
		}
		JsonNode vulnerabilities = document.get("vulnerabilities");
		if (vulnerabilities == null || !vulnerabilities.isArray()) {
			return (
				"Error: " + VULNERABILITIES_FILE + " has no vulnerabilities array; create the vulnerability entry first"
			);
		}

		String wantedId = vulnerabilityId.strip();
		ObjectNode finding = null;
		for (JsonNode candidate : vulnerabilities) {
			JsonNode id = candidate.get("id");
			if (id != null && id.asText().strip().equals(wantedId)) {
				finding = (ObjectNode) candidate;
				break;
			}
		}
		if (finding == null) {
			return (
				"Error: no vulnerability with id '" +
				wantedId +
				"' in " +
				VULNERABILITIES_FILE +
				"; read the file and use the exact id of the finding"
			);
		}

		boolean updated = finding.has("cvss");
		ObjectNode cvss = JSON.createObjectNode();
		cvss.set("4.0", entryOf(vector40.strip(), score40));
		cvss.set("3.1", entryOf(vector31.strip(), score31));
		finding.set("cvss", cvss);

		try {
			JSON.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), document);
		} catch (IOException ex) {
			return "Error: could not write " + VULNERABILITIES_FILE + ": " + ex.getMessage();
		}
		workspaceEvents.publish(VULNERABILITIES_FILE);

		return (
			"CVSS assessment " +
			(updated ? "updated" : "stored") +
			" for '" +
			wantedId +
			"': " +
			"v4.0 " +
			score40 +
			" (" +
			severityBand(score40) +
			") " +
			vector40.strip() +
			" | " +
			"v3.1 " +
			score31 +
			" (" +
			severityBand(score31) +
			") " +
			vector31.strip()
		);
	}

	private static ObjectNode entryOf(String vector, double score) {
		ObjectNode entry = JSON.createObjectNode();
		entry.put("vector", vector);
		entry.put("score", score);
		return entry;
	}

	/**
	 * Parses and scores one vector with the cvss-calculator library, enforcing
	 * the expected version prefix. {@link Cvss#fromVector} throws
	 * MalformedVectorException for invalid vectors and returns null for null
	 * input; both become actionable error messages.
	 */
	private static double baseScore(String raw, String expectedVersion) {
		String vector = raw.strip();
		if (!vector.toUpperCase(Locale.ROOT).startsWith("CVSS:" + expectedVersion + "/")) {
			throw new IllegalArgumentException(
				"The " +
					parameterName(expectedVersion) +
					" vector must start with 'CVSS:" +
					expectedVersion +
					"/' - got '" +
					vector +
					"'"
			);
		}
		Cvss cvss = Cvss.fromVector(vector);
		if (cvss == null) {
			throw new IllegalArgumentException(
				"The " + parameterName(expectedVersion) + " vector could not be parsed: '" + vector + "'"
			);
		}
		return cvss.calculateScore().getBaseScore();
	}

	private static String parameterName(String version) {
		return "4.0".equals(version) ? "v4.0" : "v3.1";
	}

	/** FIRST's qualitative severity rating for a 0-10 base score. */
	private static String severityBand(double score) {
		if (score <= 0) {
			return "None";
		} else if (score < 4) {
			return "Low";
		} else if (score < 7) {
			return "Medium";
		} else if (score < 9) {
			return "High";
		}
		return "Critical";
	}
}
