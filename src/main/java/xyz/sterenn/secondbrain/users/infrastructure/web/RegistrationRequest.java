package xyz.sterenn.secondbrain.users.infrastructure.web;

import jakarta.validation.constraints.NotBlank;

/**
 * Corps JSON de la demande d'inscription.
 *
 * <p>Record et non JavaBean : l'ancien {@code RegistrationForm} n'avait d'accesseurs que
 * parce que {@code th:field} lit la valeur via {@code BeanWrapper}. Thymeleaf disparu,
 * la raison disparaît avec lui.
 *
 * <p>La validation portée ici se limite à « le champ est rempli ». Le format de l'email
 * est l'affaire du value object {@code Email}, la robustesse du mot de passe celle de
 * {@code PasswordPolicy} : dupliquer ces règles ici les ferait diverger.
 *
 * <p>{@link #toString()} est redéfini pour ne jamais exposer le mot de passe en clair,
 * comme sur la commande {@code RegisterUser}.
 *
 * @param email    email saisi, non normalisé
 * @param password mot de passe en clair
 */
public record RegistrationRequest(
        @NotBlank(message = "L'email est obligatoire") String email,

        @NotBlank(message = "Le mot de passe est obligatoire")
        String password) {

    @Override
    public String toString() {
        return "RegistrationRequest[email=" + email + ", password=***]";
    }
}
