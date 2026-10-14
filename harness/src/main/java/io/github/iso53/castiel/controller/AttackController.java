package io.github.iso53.castiel.controller;

import io.github.iso53.castiel.model.AttackTactic;
import io.github.iso53.castiel.model.AttackTechnique;
import io.github.iso53.castiel.service.AttackCatalog;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * Read-only MITRE ATT&CK catalog access for the explore dialog. The agent-facing tools
 * live in {@link io.github.iso53.castiel.tool.AttackToolsTool}; this endpoint serves the
 * same catalog to the UI.
 */
@RestController
@RequestMapping("/api/attack")
public class AttackController {

	private final AttackCatalog catalog;

	public AttackController(AttackCatalog catalog) {
		this.catalog = catalog;
	}

	@GetMapping
	public Mono<List<AttackTechnique>> search(
		@RequestParam(name = "query", required = false) String query,
		@RequestParam(name = "tactic", required = false) String tactic,
		@RequestParam(name = "platform", required = false) String platform
	) {
		// The explore dialog browses the full catalog; only the agent tools carry a cap.
		return Mono.just(catalog.browse(query, tactic, platform));
	}

	@GetMapping("/tactics")
	public Mono<List<AttackTactic>> tactics() {
		return Mono.just(catalog.tactics());
	}

	@GetMapping("/techniques/{id}")
	public Mono<AttackTechnique> technique(@PathVariable("id") String id) {
		Optional<AttackTechnique> found = catalog.findById(id);
		if (found.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown technique: " + id);
		}
		return Mono.just(found.get());
	}
}
