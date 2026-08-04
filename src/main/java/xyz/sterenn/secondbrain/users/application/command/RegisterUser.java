package xyz.sterenn.secondbrain.users.application.command;

import xyz.sterenn.secondbrain.shared.bus.Command;

/**
 * Inscription d'un nouveau compte.
 *
 * <p>Les champs sont des {@code String} bruts, tels que saisis : c'est le handler qui
 * les convertit en value objects du domaine. Une commande transporte l'intention, elle
 * ne la valide pas.
 *
 * <p>{@link #toString()} est redéfini pour ne jamais exposer {@code rawPassword} : le
 * mot de passe en clair ne doit apparaître ni dans un log, ni dans un message d'échec
 * d'assertion qui rendrait la commande.
 *
 * @param email       email saisi, non normalisé
 * @param rawPassword mot de passe en clair
 */
public record RegisterUser(String email, String rawPassword) implements Command {

    @Override
    public String toString() {
        return "RegisterUser[email=" + email + ", rawPassword=***]";
    }
}
