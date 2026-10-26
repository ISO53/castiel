package io.github.iso53.castiel.config;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.convert.DurationUnit;

/**
 * Settings for the opt-in {@code --auth} gate. The flag sets {@code castiel.auth.enabled};
 * the timeouts exist so a server deployment can tighten them without a code change.
 *
 * @param enabled         whether every {@code /api/**} call needs a credential
 * @param sessionIdle     idle lifetime of a browser session (sliding)
 * @param sessionAbsolute absolute lifetime of a browser session; also the login cookie's Max-Age
 */
@ConfigurationProperties("castiel.auth")
public record AuthProperties(
	@DefaultValue("false") boolean enabled,
	@DefaultValue("24h") @DurationUnit(ChronoUnit.MINUTES) Duration sessionIdle,
	@DefaultValue("30d") @DurationUnit(ChronoUnit.DAYS) Duration sessionAbsolute
) {}
