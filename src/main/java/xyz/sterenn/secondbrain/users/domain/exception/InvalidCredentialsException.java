package xyz.sterenn.secondbrain.users.domain.exception;

/**
 * Refus de connexion : l'email ne correspond à aucun compte, ou le mot de passe est faux,
 * ou l'email est mal formé.
 *
 * <p>Les trois cas partagent volontairement un seul message. Les distinguer dirait à
 * l'appelant si un compte existe, et le message serait de toute façon inutile à
 * l'utilisateur légitime, qui n'a qu'une chose à faire : ressaisir.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Email ou mot de passe incorrect.");
    }
}
