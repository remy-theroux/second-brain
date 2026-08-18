package xyz.sterenn.secondbrain.users.application.query;

import xyz.sterenn.secondbrain.shared.bus.Query;

/**
 * Connexion d'un utilisateur : délivre un jeton d'accès contre un email et un mot de passe.
 *
 * <p>C'est une <strong>query</strong> et non une commande : elle doit retourner le jeton,
 * et elle n'écrit rien — ni session, ni trace, ni compteur. Le socle CQRS du projet
 * interdit à une commande de retourner quoi que ce soit.
 *
 * <p>Conséquence assumée : un refus est une exception, pas un {@code Optional} vide. Cette
 * query ne demande pas « existe-t-il un compte ? » mais « délivre-moi un jeton » ; le refus
 * porte un message affichable, comme les refus de la route de vérification.
 *
 * <p>{@link #toString()} est redéfini pour ne jamais exposer {@code rawPassword}.
 *
 * @param email       email saisi, non normalisé
 * @param rawPassword mot de passe en clair
 */
public record AuthenticateUser(String email, String rawPassword) implements Query<AccessTokenView> {

    @Override
    public String toString() {
        return "AuthenticateUser[email=" + email + ", rawPassword=***]";
    }
}
