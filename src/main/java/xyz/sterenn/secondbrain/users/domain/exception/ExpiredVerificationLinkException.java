package xyz.sterenn.secondbrain.users.domain.exception;

/**
 * Le lien de vérification a dépassé sa durée de validité.
 */
public class ExpiredVerificationLinkException extends RuntimeException {

    public ExpiredVerificationLinkException() {
        super("Ce lien de vérification a expiré.");
    }
}
