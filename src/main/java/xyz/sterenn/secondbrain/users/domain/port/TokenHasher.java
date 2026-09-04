package xyz.sterenn.secondbrain.users.domain.port;

/** Même contrat que {@link PasswordHasher}, pour les jetons de vérification : rien n'impose le même algorithme. */
public interface TokenHasher {

    String hash(String rawToken);

    boolean matches(String rawToken, String hash);
}
