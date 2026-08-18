package xyz.sterenn.secondbrain.users.application.query;

import java.time.Instant;
import java.util.UUID;
import xyz.sterenn.secondbrain.users.domain.entity.User;

/**
 * Projection de lecture d'un compte. Distincte de l'agrégat {@code User} : elle
 * n'expose jamais l'empreinte du mot de passe et peut évoluer au rythme des écrans,
 * sans toucher au domaine.
 */
public record UserView(UUID id, String email, boolean verified, Instant createdAt) {

    /** Seule conversion depuis l'agrégat, partagée par les handlers qui lisent un compte. */
    public static UserView of(User user) {
        return new UserView(user.getId(), user.getEmail().value(), user.isVerified(), user.getCreatedAt());
    }
}
