package xyz.sterenn.secondbrain.users.infrastructure.web;

import jakarta.validation.constraints.NotBlank;

public record RegistrationRequest(
        @NotBlank(message = "L'email est obligatoire") String email,

        @NotBlank(message = "Le mot de passe est obligatoire")
        String password) {

    // The clear-text password must not appear in any log or assertion message.
    @Override
    public String toString() {
        return "RegistrationRequest[email=" + email + ", password=***]";
    }
}
