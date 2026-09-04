package xyz.sterenn.secondbrain.users.infrastructure.web;

import jakarta.validation.constraints.NotBlank;

public record RegistrationRequest(
        @NotBlank(message = "L'email est obligatoire") String email,

        @NotBlank(message = "Le mot de passe est obligatoire")
        String password) {

    // Le mot de passe en clair ne doit apparaître dans aucun log ni message d'assertion.
    @Override
    public String toString() {
        return "RegistrationRequest[email=" + email + ", password=***]";
    }
}
