package xyz.sterenn.secondbrain.users.infrastructure.web;

import jakarta.validation.constraints.NotBlank;

/**
 * Support de liaison du formulaire d'inscription.
 *
 * <p>Classe mutable à accesseurs JavaBean, et non un record : {@code th:field} lit la
 * valeur via {@code BeanWrapper}, qui exige {@code getEmail()} et non {@code email()}.
 *
 * <p>La validation portée ici se limite à « le champ est rempli ». Le format de l'email
 * est l'affaire du value object {@code Email}, la robustesse du mot de passe celle de
 * {@code PasswordPolicy} : dupliquer ces règles ici les ferait diverger.
 */
public class RegistrationForm {

    @NotBlank(message = "L'email est obligatoire")
    private String email;

    @NotBlank(message = "Le mot de passe est obligatoire")
    private String password;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
