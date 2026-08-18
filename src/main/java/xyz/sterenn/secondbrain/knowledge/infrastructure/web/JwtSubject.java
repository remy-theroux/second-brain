package xyz.sterenn.secondbrain.knowledge.infrastructure.web;

import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Lit le compte porté par un jeton d'accès déjà validé.
 *
 * <p>Package-private, et local à ce contexte borné : recréer un {@code shared/web} pour
 * quatre lignes rouvrirait une couture que ce projet a fermée. Le jour où un troisième
 * contexte en aura besoin, ce sera le moment de la rouvrir — pas avant.
 *
 * <p>Le jeton est déjà validé par le filtre resource server quand ces méthodes s'exécutent :
 * signature, expiration et forme ont été contrôlées en amont. Un {@code sub} illisible est
 * hors de portée avec nos propres jetons ; il vaut tout de même un refus plutôt qu'une
 * erreur serveur, parce qu'un jeton qui n'identifie personne n'autorise rien.
 */
final class JwtSubject {

    private JwtSubject() {
        // classe utilitaire
    }

    static UUID accountId(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new UnreadableSubjectException();
        }
    }

    /** Le porteur du jeton n'est identifiable par aucun compte. */
    static final class UnreadableSubjectException extends RuntimeException {}
}
