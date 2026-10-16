package io.github.iso53.castiel.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * WebFlux and CORS configuration allowing local frontend development servers.
 * Also logs the UI URL once the web server has bound its port.
 */
@Configuration
public class WebConfig {

    private static final Logger log = LoggerFactory.getLogger(WebConfig.class);

    private static final String UI_DISABLED_PATH_PATTERN = "/unavailable/**";

    private final Environment env;

    public WebConfig(Environment env) {
        this.env = env;
    }

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration corsConfig = new CorsConfiguration();
        corsConfig.setAllowedOriginPatterns(List.of("*"));
        corsConfig.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD"));
        corsConfig.setAllowedHeaders(List.of("*"));
        corsConfig.setAllowCredentials(true);
        corsConfig.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);

        return new CorsWebFilter(source);
    }

    @EventListener
    public void logUiUrl(WebServerInitializedEvent event) {
        // In API-only mode (dev backend, mvn spring-boot:run) the UI is not served.
        if (UI_DISABLED_PATH_PATTERN.equals(env.getProperty("spring.webflux.static-path-pattern", "/**"))) {
            return;
        }
        log.info("UI accessible at http://localhost:{}", event.getWebServer().getPort());
    }
}
