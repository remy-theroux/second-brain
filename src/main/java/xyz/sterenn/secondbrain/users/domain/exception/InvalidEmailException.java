package xyz.sterenn.secondbrain.users.domain.exception;

/**
 * L'email soumis n'a pas une forme exploitable. Le message est destiné à être
 * affiché tel quel sous le champ email du formulaire.
 */
public class InvalidEmailException extends RuntimeException {

    public InvalidEmailException(String message) {
        super(message);
    }
}
