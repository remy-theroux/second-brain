package xyz.sterenn.secondbrain.users.domain.exception;

/**
 * Le lien de vérification ne désigne rien d'exploitable : identifiant de compte
 * illisible, compte inexistant, ou jeton ne correspondant pas.
 *
 * <p>Ces trois situations partagent volontairement un seul message. Les distinguer
 * ferait de la route de vérification un oracle : un visiteur pourrait savoir quels
 * comptes existent en observant la réponse.
 */
public class InvalidVerificationLinkException extends RuntimeException {

    public InvalidVerificationLinkException() {
        super("Ce lien de vérification n'est pas valide.");
    }
}
