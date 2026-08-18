package xyz.sterenn.secondbrain.users.domain.exception;

/**
 * Refus de connexion : le compte existe, le mot de passe est le bon, mais l'adresse email
 * n'a jamais été vérifiée.
 *
 * <p>Message distinct de {@link InvalidCredentialsException} : l'utilisateur légitime doit
 * savoir qu'il lui reste un lien à cliquer, sinon le produit est cassé sans explication.
 * Ce refus n'arrive qu'à qui a déjà donné le bon mot de passe.
 */
public class UnverifiedAccountException extends RuntimeException {

    public UnverifiedAccountException() {
        super("Votre compte n'est pas encore vérifié : suivez le lien reçu par email "
            + "avant de vous connecter.");
    }
}
