package xyz.sterenn.secondbrain.users.domain.port;

import java.util.Optional;
import java.util.UUID;
import xyz.sterenn.secondbrain.users.domain.entity.User;
import xyz.sterenn.secondbrain.users.domain.exception.EmailAlreadyUsedException;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;

/**
 * Stocke et relit les comptes ; {@code save} lève {@link EmailAlreadyUsedException} si l'unicité
 * de l'email est violée.
 */
public interface UserRepository {

    boolean existsByEmail(Email email);

    User save(User user);

    Optional<User> findByEmail(Email email);

    Optional<User> findById(UUID id);
}
