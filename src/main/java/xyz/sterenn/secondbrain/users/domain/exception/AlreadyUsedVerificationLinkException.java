package xyz.sterenn.secondbrain.users.domain.exception;

/**
 * Le lien de vérification a déjà servi. Un jeton est à usage unique.
 */
public class AlreadyUsedVerificationLinkException extends RuntimeException {

    public AlreadyUsedVerificationLinkException() {
        super("Ce lien de vérification a déjà été utilisé.");
    }
}
