package io.github.iso53.castiel.tool.process;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.iso53.castiel.service.NudgeScheduler;
import io.github.iso53.castiel.service.WorkspaceSession;
import io.github.iso53.castiel.tool.process.BoundedOutputBuffer.Page;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * Real-process tests on Windows (the primary dev platform). They exercise spawning,
 * output capture, completion detection, and tree-style termination through PowerShell.
 */
@EnabledOnOs(OS.WINDOWS)
class ProcessManagerTest {

	private static ProcessManager newManager() {
		// A sessionless workspace keeps spawns rooted at the harness working directory.
		WorkspaceSession session = new WorkspaceSession(null) {
			@Override
			public Optional<java.nio.file.Path> root() {
				return Optional.empty();
			}
		};
		return new ProcessManager(session, new NudgeScheduler());
	}

	private static void awaitExit(ManagedProcess entry, long seconds) throws Exception {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
		while (entry.isRunning() && System.nanoTime() < deadline) {
			Thread.sleep(50);
		}
	}

	@Test
	void capturesOutputAndDetectsCompletion() throws Exception {
		ProcessManager manager = newManager();
		try {
			ManagedProcess entry = manager.start("Write-Output hello-from-bg; Start-Sleep -Milliseconds 200", "test echo");
			awaitExit(entry, 10);
			assertEquals(ManagedProcess.State.EXITED, entry.state());
			List<ManagedProcess> all = manager.list();
			assertFalse(all.isEmpty());
			Page page = entry.output().read(0, 8_192);
			assertTrue(page.text().contains("hello-from-bg"), "got: " + page.text());
			Integer code = entry.exitCode();
			assertTrue(code != null && code == 0, "exit code: " + code);
		} finally {
			manager.killAllOnShutdown();
		}
	}

	@Test
	void killTerminatesLongRunningProcessTree() throws Exception {
		ProcessManager manager = newManager();
		try {
			// The wrapper shell plus its sleeping grandchild must both die.
			ManagedProcess entry = manager.start(
					"cmd /c \"ping -n 60 127.0.0.1 >nul\"", "long sleeper for kill test");
			Thread.sleep(500); // give the tree time to form
			assertTrue(entry.isRunning());

			String feedback = manager.kill(entry.id());
			assertTrue(feedback.contains("Terminated"), feedback);
			awaitExit(entry, 10);
			assertEquals(ManagedProcess.State.KILLED, entry.state());
			assertFalse(manager.get(entry.id()).isRunning());
		} finally {
			manager.killAllOnShutdown();
		}
	}

	@Test
	void sendInputAfterExitsReportsClosedStdin() throws Exception {
		ProcessManager manager = newManager();
		try {
			ManagedProcess entry = manager.start("Write-Output done", "instant process");
			awaitExit(entry, 10);
			assertFalse(manager.sendInput(entry.id(), "more\n"), "stdin should be closed after exit");
		} finally {
			manager.killAllOnShutdown();
		}
	}
}
