package io.github.iso53.castiel.tool;

/**
 * Marker implemented by every Spring bean that contributes LangChain4j {@code @Tool} methods.
 *
 * <p>{@link io.github.iso53.castiel.service.HarnessService} discovers all implementations
 * automatically, so adding a tool never requires touching the chat pipeline: create a class,
 * implement this interface, annotate the methods with {@code @Tool}, done.
 */
public interface ToolProvider {}
