package xyz.sterenn.secondbrain.users.domain.port;

/**
 * Port sortant vers l'algorithme de hachage. Le domaine ignore lequel est utilisé.
 */
public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String hash);
}
