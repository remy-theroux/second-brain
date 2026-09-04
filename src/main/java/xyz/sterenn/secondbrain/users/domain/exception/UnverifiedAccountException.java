package xyz.sterenn.secondbrain.users.domain.exception;

public class UnverifiedAccountException extends RuntimeException {

    public UnverifiedAccountException() {
        super("Votre compte n'est pas encore vérifié : suivez le lien reçu par email " + "avant de vous connecter.");
    }
}
