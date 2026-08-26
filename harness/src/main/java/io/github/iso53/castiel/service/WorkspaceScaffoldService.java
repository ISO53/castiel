package io.github.iso53.castiel.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Creates the blank engagement documents (the "scaffolds") a pentest workspace starts with.
 *
 * <p>Each scaffold is a valid but empty JSON document whose keys define the shape agents are
 * expected to fill in during an engagement; the frontend renders those documents as live
 * views. Existing files are never overwritten, so opening an older workspace simply backfills
 * whatever scaffolds are missing.
 */
@Service
public class WorkspaceScaffoldService {

	private static final Logger LOG = LoggerFactory.getLogger(WorkspaceScaffoldService.class);

	private static final String EVIDENCE_DIRECTORY = "evidence";

	private static final Map<String, String> SCAFFOLDS = buildScaffolds();

	/**
	 * Creates the evidence folder and every missing scaffold document under {@code root}.
	 */
	public void ensureScaffolds(Path root) throws IOException {
		Files.createDirectories(root.resolve(EVIDENCE_DIRECTORY));
		for (Map.Entry<String, String> scaffold : SCAFFOLDS.entrySet()) {
			Path file = root.resolve(scaffold.getKey());
			if (Files.exists(file)) {
				continue;
			}
			Files.writeString(file, scaffold.getValue());
			LOG.info("Created engagement scaffold {}", file);
		}
	}

	private static Map<String, String> buildScaffolds() {
		Map<String, String> scaffolds = new LinkedHashMap<>();

		scaffolds.put("engagement.json", """
			{
			  "name": "",
			  "phase": 1,
			  "target": {
			    "scope": [],
			    "out_of_scope": [],
			    "objectives": ""
			  },
			  "rules_of_engagement": {
			    "allowed_tools": [],
			    "forbidden_actions": [],
			    "testing_window": "",
			    "contacts": []
			  }
			}
			""");

		scaffolds.put("network.json", """
			{
			  "segments": [],
			  "hosts": [],
			  "domains": [],
			  "relationships": []
			}
			""");

		scaffolds.put("web.json", """
			{
			  "sites": [],
			  "pages": [],
			  "directories": [],
			  "parameters": [],
			  "technologies": [],
			  "relationships": []
			}
			""");

		scaffolds.put("findings.json", """
			{
			  "findings": [],
			  "relationships": []
			}
			""");

		scaffolds.put("credentials.json", """
			{
			  "credentials": [],
			  "relationships": []
			}
			""");

		scaffolds.put("identity.json", """
			{
			  "organizations": [],
			  "people": [],
			  "emails": [],
			  "social_profiles": [],
			  "leaks": [],
			  "relationships": []
			}
			""");

		scaffolds.put("evidence.json", """
			{
			  "artifacts": []
			}
			""");

		scaffolds.put("tasks.json", """
			{
			  "tasks": []
			}
			""");

		return Map.copyOf(scaffolds);
	}
}