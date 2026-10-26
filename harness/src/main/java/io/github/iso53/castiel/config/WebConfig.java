package io.github.iso53.castiel.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
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
    @Order(-100) // CORS answers browser preflights before the auth gate sees them.
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration corsConfig = new CorsConfiguration();
        corsConfig.setAllowedOriginPatterns(localUiOrigins());
        corsConfig.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD"));
        corsConfig.setAllowedHeaders(List.of("*"));
        corsConfig.setAllowCredentials(false);
        corsConfig.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);

        return new CorsWebFilter(source);
    }

    /**
     * The origins the local UI is reached from: any localhost port. Browsers send an
     * Origin header on every POST, so the exact port cannot be assumed - the Vite dev
     * server moves to the next free port, and the packaged build is served by this
     * process itself. Loopback-only patterns keep the API unreachable to web pages
     * from other hosts.
     */
    private List<String> localUiOrigins() {
        return List.of("http://localhost:*", "http://127.0.0.1:*", "http://[::1]:*");
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
