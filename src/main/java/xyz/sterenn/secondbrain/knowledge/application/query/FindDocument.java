package xyz.sterenn.secondbrain.knowledge.application.query;

import java.util.Optional;
import java.util.UUID;
import xyz.sterenn.secondbrain.shared.bus.Query;

/**
 * Lit un document de la base de connaissance d'un compte, avec ce qui en a été extrait.
 *
 * <p>{@code ownerId} n'est pas un filtre de confort : il cloisonne. Un document qui n'est pas
 * au demandeur est rendu introuvable, jamais interdit.
 *
 * <p>Rend un {@link Optional} vide quand il n'y a rien : une query ne lève pas. C'est le
 * contrôleur qui traduit ce vide en {@code 404}.
 */
public record FindDocument(UUID documentId, UUID ownerId) implements Query<Optional<DocumentDetailView>> {}
