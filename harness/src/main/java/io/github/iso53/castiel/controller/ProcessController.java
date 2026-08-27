package io.github.iso53.castiel.controller;

import io.github.iso53.castiel.tool.process.BoundedOutputBuffer.Page;
import io.github.iso53.castiel.tool.process.ManagedProcess;
import io.github.iso53.castiel.tool.process.ProcessManager;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

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
		List<Map<String, Object>> rows = manager
			.list()
			.stream()
			.map(entry -> {
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
			})
			.toList();
		return Mono.just(rows);
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
}
