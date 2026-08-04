package xyz.sterenn.secondbrain.users.application.command;

import xyz.sterenn.secondbrain.shared.bus.Command;

/**
 * Inscription d'un nouveau compte.
 *
 * <p>Les champs sont des {@code String} bruts, tels que saisis : c'est le handler qui
 * les convertit en value objects du domaine. Une commande transporte l'intention, elle
 * ne la valide pas.
 *
 * @param email       email saisi, non normalisé
 * @param rawPassword mot de passe en clair — ne jamais logguer une instance de cette commande
 */
public record RegisterUser(String email, String rawPassword) implements Command {
}
