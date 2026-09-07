package xyz.sterenn.secondbrain.users.domain.port;

/** Hashes a password and matches an input against a digest, with an algorithm unknown to the domain. */
public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String hash);
}
