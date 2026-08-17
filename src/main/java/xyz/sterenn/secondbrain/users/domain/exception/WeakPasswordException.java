package xyz.sterenn.secondbrain.users.domain.exception;

import xyz.sterenn.secondbrain.users.domain.PasswordPolicy;

/**
 * Le mot de passe soumis ne satisfait pas {@link PasswordPolicy}. Le message énonce
 * la règle pour que l'utilisateur puisse corriger sans deviner.
 */
public class WeakPasswordException extends RuntimeException {

    public WeakPasswordException() {
        super("Le mot de passe doit contenir entre " + PasswordPolicy.MIN_LENGTH
            + " et " + PasswordPolicy.MAX_LENGTH
            + " caractères et ne pas figurer parmi les mots de passe les plus courants");
    }
}
