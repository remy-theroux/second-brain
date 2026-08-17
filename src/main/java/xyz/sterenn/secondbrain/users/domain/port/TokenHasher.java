package xyz.sterenn.secondbrain.users.domain.port;

/**
 * Port sortant vers le hachage des jetons de vérification. Jumeau de
 * {@link PasswordHasher}, et distinct de lui : les deux secrets n'ont ni la même durée de
 * vie ni la même exposition, rien n'impose qu'ils partagent un jour le même algorithme.
 */
public interface TokenHasher {

    String hash(String rawToken);

    boolean matches(String rawToken, String hash);
}
