package xyz.sterenn.secondbrain.users.domain.port;

/** Same contract as {@link PasswordHasher}, for verification tokens: nothing mandates the same algorithm. */
public interface TokenHasher {

    String hash(String rawToken);

    boolean matches(String rawToken, String hash);
}
