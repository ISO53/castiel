package io.github.iso53.castiel.model;

import java.util.List;

/**
 * One MITRE ATT&CK technique from the bundled Enterprise catalog.
 *
 * <p>Records are extracted from MITRE's official STIX bundle by
 * {@code scripts/extract_attack_techniques.py}, with every text field capped at
 * extraction time — the catalog is deliberately lean because models already know
 * what the techniques are; they need the menu, the exact IDs and the playbooks.
 *
 * @param id                  ATT&CK technique id (e.g. {@code T1021.002}).
 * @param name                Technique name.
 * @param parent              Parent technique id for sub-techniques, or {@code null}.
 * @param tactics             ATT&CK tactic shortnames the technique serves (e.g. {@code lateral-movement}).
 * @param platforms           Platforms the technique applies to (e.g. {@code Windows}).
 * @param summary             One capped sentence describing the technique.
 * @param detection           Capped detection note, or empty.
 * @param permissionsRequired Privileges the technique demands (e.g. {@code Administrator}).
 * @param remoteSupport       Whether the technique can be executed remotely, or {@code null} when unknown.
 * @param procedures          Real-world adversary usage examples, attributed to groups or software.
 * @param software            Names of tools/malware associated with the technique.
 * @param url                 attack.mitre.org reference.
 */
public record AttackTechnique(
	String id,
	String name,
	String parent,
	List<String> tactics,
	List<String> platforms,
	String summary,
	String detection,
	List<String> permissionsRequired,
	Boolean remoteSupport,
	List<Procedure> procedures,
	List<String> software,
	String url
) {
	public record Procedure(String source, String text) {}

	public AttackTechnique {
		tactics = tactics == null ? List.of() : List.copyOf(tactics);
		platforms = platforms == null ? List.of() : List.copyOf(platforms);
		permissionsRequired = permissionsRequired == null ? List.of() : List.copyOf(permissionsRequired);
		procedures = procedures == null ? List.of() : List.copyOf(procedures);
		software = software == null ? List.of() : List.copyOf(software);
		summary = summary == null ? "" : summary;
		detection = detection == null ? "" : detection;
	}
}