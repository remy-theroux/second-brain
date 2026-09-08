package xyz.sterenn.secondbrain.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@Profile("!worker")
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers("/api/token")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/registrations")
                        .permitAll()
                        // Google does not authenticate itself towards us: the channel token, read
                        // by the handler from a header, is the whole of this route's security.
                        // Left undeclared it answers 401, which Google reads as a failing channel
                        // and closes after a few tries, without a word.
                        .requestMatchers(HttpMethod.POST, "/api/drive/notifications")
                        .permitAll()
                        // Deny by default under /api: a public route must declare itself
                        // above, otherwise it answers 401.
                        .requestMatchers("/api/**")
                        .authenticated()
                        .anyRequest()
                        .permitAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

        return http.build();
    }
}
