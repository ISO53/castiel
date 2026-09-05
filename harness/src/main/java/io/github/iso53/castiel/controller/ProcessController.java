package io.github.iso53.castiel.controller;

import io.github.iso53.castiel.tool.process.BoundedOutputBuffer.Page;
import io.github.iso53.castiel.tool.process.ManagedProcess;
import io.github.iso53.castiel.tool.process.ProcessManager;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read side of the process registry for the bottom-dock UI. */
@RestController
@RequestMapping("/api/processes")
public class ProcessController {

	/** Annotation values must be compile-time constants, so the default lives as its wire format. */
	private static final String DEFAULT_OUTPUT_CHARS = "8192";
	private static final int MAX_OUTPUT_CHARS = 65_536;

	private final ProcessManager manager;

	public ProcessController(ProcessManager manager) {
		this.manager = manager;
	}

	/** All tracked processes, oldest first; drives the dock's table rows. */
	@GetMapping
	public Mono<List<Map<String, Object>>> list() {
		List<Map<String, Object>> rows = manager.list().stream().map(ProcessController::toRow).toList();
		return Mono.just(rows);
	}

	/** SSE change feed; each ping tells the dock to refetch the process list. */
	@GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<ServerSentEvent<String>> events() {
		return manager.events();
	}

	/**
	 * Starts a background process on the user's behalf from the bottom-dock dialog.
	 * The command runs like any agent-started process — same tracking, output, stdin
	 * and wake-up rules; the purpose is silently prefixed so the agent can tell who
	 * started it.
	 */
	@PostMapping
	public Mono<Map<String, Object>> start(@RequestBody StartProcessRequest request) {
		if (request == null || request.command() == null || request.command().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "command is required");
		}
		String description = request.description() == null || request.description().isBlank()
				? "No description provided"
				: request.description().strip();
		try {
			ManagedProcess entry = manager.start(
					request.command().strip(),
					ProcessManager.USER_START_PREFIX + description);
			return Mono.just(toRow(entry));
		} catch (IOException ex) {
			return Mono.error(new ResponseStatusException(
					HttpStatus.INTERNAL_SERVER_ERROR, "could not start the command: " + ex.getMessage()));
		}
	}

	/** Request body for {@link #start}; the description is optional. */
	public record StartProcessRequest(String command, String description) {}

	/** Wire shape of one registry row; shared by {@link #list()} and {@link #start}. */
	private static Map<String, Object> toRow(ManagedProcess entry) {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("id", entry.id());
		row.put("pid", entry.osPid());
		row.put("command", entry.command());
		row.put("purpose", entry.purpose());
		row.put("state", entry.state().name());
		row.put("exitCode", entry.exitCode());
		row.put("runtimeSeconds", entry.runtime().toSeconds());
		row.put("unread", !entry.isRunning() && !entry.isSeenByAgent());
		return row;
	}

	/**
	 * Output appended after {@code cursor}; the UI tracks one cursor per selected row.
	 */
	@GetMapping("/{id}/output")
	public Mono<Map<String, Object>> output(
		@PathVariable("id") String id,
		@RequestParam(name = "cursor", defaultValue = "0") long cursor,
		@RequestParam(name = "max", defaultValue = DEFAULT_OUTPUT_CHARS) int max
	) {
		ManagedProcess entry = require(id);
		int cap = (int) Math.clamp((long) Math.max(max, 100), 100L, (long) MAX_OUTPUT_CHARS);
		Page page = entry.output().read(cursor, cap);
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("id", entry.id());
		body.put("cursor", page.cursor());
		body.put("text", page.text());
		body.put("state", entry.state().name());
		body.put("exitCode", entry.exitCode());
		return Mono.just(body);
	}

	/** Removes a finished entry; running entries must be killed first. */
	@DeleteMapping("/{id}")
	public Mono<Map<String, Object>> remove(@PathVariable("id") String id) {
		boolean removed = manager.remove(id);
		if (!removed && manager.get(id) != null) {
			throw new IllegalStateException(id + " is still running; terminate it before removing");
		}
		return Mono.just(Map.of("removed", removed));
	}

	private ManagedProcess require(String id) {
		ManagedProcess entry = manager.get(id);
		if (entry == null) {
			throw new IllegalArgumentException("No tracked process with id " + id);
		}
		return entry;
	}

	@ExceptionHandler(IllegalArgumentException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public Map<String, String> notFound(IllegalArgumentException ex) {
		return Map.of("error", ex.getMessage());
	}

	@ExceptionHandler(IllegalStateException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	public Map<String, String> conflict(IllegalStateException ex) {
		return Map.of("error", ex.getMessage());
	}

	/** Preserves the status of {@link ResponseStatusException} while exposing its reason as JSON. */
	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<Map<String, String>> statusError(ResponseStatusException ex) {
		String reason = ex.getReason() != null ? ex.getReason() : ex.getStatusCode().toString();
		return ResponseEntity.status(ex.getStatusCode()).body(Map.of("error", reason));
	}
}
