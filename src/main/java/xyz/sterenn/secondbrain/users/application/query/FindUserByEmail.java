package xyz.sterenn.secondbrain.users.application.query;

import java.util.Optional;
import xyz.sterenn.secondbrain.shared.bus.Query;

/**
 * Recherche d'un compte par son email. Renvoie un {@link Optional} vide plutôt qu'une
 * exception : l'absence de compte est un résultat, pas une erreur.
 *
 * @param email email saisi, non normalisé
 */
public record FindUserByEmail(String email) implements Query<Optional<UserView>> {}
