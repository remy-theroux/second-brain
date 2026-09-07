package xyz.sterenn.secondbrain.users.domain.port;

import java.util.Optional;
import java.util.UUID;
import xyz.sterenn.secondbrain.users.domain.entity.User;
import xyz.sterenn.secondbrain.users.domain.exception.EmailAlreadyUsedException;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;

/**
 * Stores and reads back accounts; {@code save} throws {@link EmailAlreadyUsedException} when email
 * uniqueness is violated.
 */
public interface UserRepository {

    boolean existsByEmail(Email email);

    User save(User user);

    Optional<User> findByEmail(Email email);

    Optional<User> findById(UUID id);
}
