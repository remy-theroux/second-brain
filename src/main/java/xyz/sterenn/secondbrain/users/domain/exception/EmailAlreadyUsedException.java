package xyz.sterenn.secondbrain.users.domain.exception;

import xyz.sterenn.secondbrain.users.domain.valueobject.Email;

/**
 * L'email soumis correspond déjà à un compte existant.
 */
public class EmailAlreadyUsedException extends RuntimeException {

    public EmailAlreadyUsedException(Email email) {
        super("Un compte existe déjà pour l'email " + email.value());
    }
}
