package io.github.iso53.castiel.service;

import io.github.iso53.castiel.model.ApplicationResource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * Loads bundled application resources identified by {@link ApplicationResource}.
 */
@Service
public class ResourceLoader {

	/**
	 * Returns a classpath resource described by the application resource catalog.
	 */
	public Resource get(ApplicationResource resource) {
		return new ClassPathResource(resource.path());
	}

	/**
	 * Reads a UTF-8 text resource.
	 */
	public String readText(ApplicationResource resource) {
		try {
			return get(resource).getContentAsString(StandardCharsets.UTF_8).trim();
		} catch (IOException ex) {
			throw new IllegalStateException("Could not read resource: " + resource.path(), ex);
		}
	}

	/**
	 * Reads a resource without imposing a text encoding.
	 */
	public byte[] readBytes(ApplicationResource resource) {
		try {
			return get(resource).getContentAsByteArray();
		} catch (IOException ex) {
			throw new IllegalStateException("Could not read resource: " + resource.path(), ex);
		}
	}
}
