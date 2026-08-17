package xyz.sterenn.secondbrain.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuration de sécurité. L'authentification se fait par <strong>jeton porteur</strong>
 * (JWT signé en HS256, voir {@link JwtConfiguration}) : le filtre resource server valide le
 * jeton présenté en {@code Authorization: Bearer …}, et rien d'autre n'identifie l'appelant.
 *
 * <p>Sous {@code /api/**}, le refus est le défaut : une route protégée n'a rien à déclarer,
 * une route publique doit se nommer explicitement. Seule {@code /api/token} y déroge — créer
 * un compte, vérifier une adresse et demander un jeton doivent rester accessibles à un
 * visiteur anonyme, un {@code 401} sur la route de délivrance de jeton serait une boucle sans
 * issue. En dehors de {@code /api/**}, tout reste public.
 *
 * <p>{@code STATELESS} et CSRF désactivé ne sont plus une dette mais un choix cohérent :
 * un navigateur n'envoie jamais spontanément un en-tête {@code Authorization}, donc rien
 * n'est contrefaisable depuis un site tiers, et il n'existe aucun cookie
 * d'authentification à protéger. Le jour où l'authentification passera par un cookie,
 * CSRF redeviendra obligatoire.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // La route de délivrance de jeton doit rester anonyme : un 401 ici serait
                // une boucle sans issue.
                .requestMatchers("/api/token").permitAll()
                // Refus par défaut sous /api : une future route protégée l'est sans que
                // personne ait à y penser, et une route publique doit se déclarer ici.
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll())
            // Prend le bean JwtDecoder de JwtConfiguration.
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

        return http.build();
    }
}
