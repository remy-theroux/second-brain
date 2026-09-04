package xyz.sterenn.secondbrain.users.domain.exception;

public class AlreadyUsedVerificationLinkException extends RuntimeException {

    public AlreadyUsedVerificationLinkException() {
        super("Ce lien de vérification a déjà été utilisé.");
    }
}
