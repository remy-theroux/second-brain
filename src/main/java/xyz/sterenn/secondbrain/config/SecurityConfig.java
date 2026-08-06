package xyz.sterenn.secondbrain.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuration de sécurité.
 *
 * <p>L'authentification HTTP Basic de départ (utilisateur admin en dur) a été retirée :
 * la création de compte doit être accessible à un visiteur anonyme et il n'existe pas
 * encore de mécanisme de remplacement. Tout est donc public.
 *
 * <p>TODO : le ticket « login » introduira l'authentification par session, et avec elle
 * la réactivation de CSRF et une vraie politique d'autorisation. CSRF reste désactivé et
 * la session STATELESS d'ici là, faute de session HTTP à protéger.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        return http.build();
    }
}
