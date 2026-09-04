package xyz.sterenn.secondbrain.users.domain.exception;

public class ExpiredVerificationLinkException extends RuntimeException {

    public ExpiredVerificationLinkException() {
        super("Ce lien de vérification a expiré.");
    }
}
