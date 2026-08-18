package xyz.sterenn.secondbrain.users.application.query;

import java.util.Optional;
import java.util.UUID;
import xyz.sterenn.secondbrain.shared.bus.Query;

/**
 * Recherche d'un compte par son identifiant — celui que le jeton d'accès porte en
 * {@code sub}. Renvoie un {@link Optional} vide plutôt qu'une exception : l'absence de
 * compte est un résultat, pas une erreur ; c'est l'appelant qui décide ce qu'elle signifie.
 *
 * @param id identifiant du compte
 */
public record FindUserById(UUID id) implements Query<Optional<UserView>> {}
