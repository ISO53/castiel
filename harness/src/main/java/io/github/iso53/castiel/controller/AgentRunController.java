package io.github.iso53.castiel.controller;

import io.github.iso53.castiel.agent.AgentRunManager;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read side of the sub-agent run registry for the bottom-dock Agents view.
 * Mirrors the process registry API: list, SSE change feed, cancel and remove. There is no
 * transcript endpoint, by design; a run exposes its state, its activity, and its summary.
 */
@RestController
@RequestMapping("/api/agents")
public class AgentRunController {

	private final AgentRunManager manager;

	public AgentRunController(AgentRunManager manager) {
		this.manager = manager;
	}

	/** All tracked sub-agent runs, oldest first; drives the dock's table rows. */
	@GetMapping
	public Mono<List<Map<String, Object>>> list() {
		return Mono.just(manager.list().stream().map(AgentRunController::toRow).toList());
	}

	/** SSE change feed; each ping tells the dock to refetch the run list. */
	@GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<ServerSentEvent<String>> events() {
		return manager.events();
	}

	/** Cancels a live sub-agent run; finished runs are unaffected. */
	@PostMapping("/{id}/cancel")
	public Mono<Map<String, Object>> cancel(@PathVariable("id") String id) {
		AgentRunManager.AgentRun run = require(id);
		boolean cancelled = manager.cancel(id);
		if (!cancelled) {
			throw new ResponseStatusException(
				HttpStatus.CONFLICT, run.id() + " is not running (state: " + run.state().name() + ")");
		}
		return Mono.just(Map.of("cancelled", true));
	}

	/** Removes a finished run from the registry; live runs must be cancelled first. */
	@DeleteMapping("/{id}")
	public Mono<Map<String, Object>> remove(@PathVariable("id") String id) {
		if (!manager.remove(id)) {
			throw new ResponseStatusException(
				HttpStatus.CONFLICT, id + " is still running; cancel it before removing");
		}
		return Mono.just(Map.of("removed", true));
	}

	/** Wire shape of one registry row; shared by {@link #list()}. */
	private static Map<String, Object> toRow(AgentRunManager.AgentRun run) {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("id", run.id());
		row.put("profile", run.profile());
		row.put("task", run.task());
		row.put("state", run.state().name());
		row.put("activity", run.activity().name());
		row.put("runtimeSeconds", Duration.between(run.startedAt(), java.time.Instant.now()).toSeconds());
		row.put("startedAt", run.startedAt().toEpochMilli());
		row.put("resultSummary", run.resultSummary());
		row.put("error", run.error());
		return row;
	}

	private AgentRunManager.AgentRun require(String id) {
		AgentRunManager.AgentRun run = manager.get(id);
		if (run == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No tracked sub-agent run with id " + id);
		}
		return run;
	}
}
