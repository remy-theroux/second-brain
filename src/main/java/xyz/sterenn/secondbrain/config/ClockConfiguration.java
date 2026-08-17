package xyz.sterenn.secondbrain.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Le temps est une dépendance comme une autre. Les handlers reçoivent une {@link Clock}
 * et passent l'instant au domaine, qui n'appelle jamais {@code Instant.now()} lui-même :
 * c'est ce qui rend l'expiration d'un jeton testable sans attendre.
 */
@Configuration
public class ClockConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
