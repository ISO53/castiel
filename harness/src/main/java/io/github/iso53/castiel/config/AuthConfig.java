package io.github.iso53.castiel.config;

import io.github.iso53.castiel.service.AuthService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;

/**
 * Registers the API gate filter. CORS must run first so browser preflights are
 * answered before this filter is evaluated.
 */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class AuthConfig {

	@Bean
	@Order(-90)
	public AuthWebFilter authWebFilter(AuthService auth, Environment env) {
		return new AuthWebFilter(auth, env);
	}
}
