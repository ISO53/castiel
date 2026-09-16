package io.github.iso53.castiel.controller;

import io.github.iso53.castiel.agent.AgentRunManager;
import io.github.iso53.castiel.tool.process.BoundedOutputBuffer.Page;
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
 * Mirrors the process registry API: list, SSE change feed, cursor-based transcript reads.
 */
@RestController
@RequestMapping("/api/agents")
public class AgentRunController {

	/** Annotation values must be compile-time constants, so the default lives as its wire format. */
	private static final String DEFAULT_OUTPUT_CHARS = "8192";
	private static final int MAX_OUTPUT_CHARS = 65_536;

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

	/**
	 * Transcript appended after {@code cursor}; the UI tracks one cursor per selected run.
	 */
	@GetMapping("/{id}/output")
	public Mono<Map<String, Object>> output(
		@PathVariable("id") String id,
		@RequestParam(name = "cursor", defaultValue = "0") long cursor,
		@RequestParam(name = "max", defaultValue = DEFAULT_OUTPUT_CHARS) int max
	) {
		AgentRunManager.AgentRun run = require(id);
		int cap = (int) Math.clamp((long) Math.max(max, 100), 100L, (long) MAX_OUTPUT_CHARS);
		Page page = run.transcript().read(cursor, cap);
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("id", run.id());
		body.put("cursor", page.cursor());
		body.put("text", page.text());
		body.put("state", run.state().name());
		return Mono.just(body);
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
		row.put("runtimeSeconds", Duration.between(run.startedAt(), java.time.Instant.now()).toSeconds());
		row.put("startedAt", run.startedAt().toEpochMilli());
		row.put("tokens", run.totalUsage().totalTokenCount());
		row.put("transcriptPath", run.transcriptPath().toString());
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
