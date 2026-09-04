package xyz.sterenn.secondbrain.users.domain.exception;

public class InvalidVerificationLinkException extends RuntimeException {

    public InvalidVerificationLinkException() {
        super("Ce lien de vérification n'est pas valide.");
    }
}
