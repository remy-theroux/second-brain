package xyz.sterenn.secondbrain.users.application.query;

import java.time.Instant;
import java.util.UUID;

/**
 * Projection de lecture d'un compte. Distincte de l'agrégat {@code User} : elle
 * n'expose jamais l'empreinte du mot de passe et peut évoluer au rythme des écrans,
 * sans toucher au domaine.
 */
public record UserView(UUID id, String email, boolean verified, Instant createdAt) {
}
