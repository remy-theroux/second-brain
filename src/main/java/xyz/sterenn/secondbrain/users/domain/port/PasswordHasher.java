package xyz.sterenn.secondbrain.users.domain.port;

/** Hache un mot de passe et compare une saisie à une empreinte, par un algorithme que le domaine ignore. */
public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String hash);
}
