package xyz.sterenn.secondbrain.users.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.users.domain.entity.User;
import xyz.sterenn.secondbrain.users.domain.exception.EmailAlreadyUsedException;
import xyz.sterenn.secondbrain.users.domain.port.UserRepository;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;

@Component
public class JpaUserRepositoryAdapter implements UserRepository {

    private final SpringDataUserRepository springDataUserRepository;

    JpaUserRepositoryAdapter(SpringDataUserRepository springDataUserRepository) {
        this.springDataUserRepository = springDataUserRepository;
    }

    @Override
    public boolean existsByEmail(Email email) {
        return springDataUserRepository.existsByEmail(email);
    }

    @Override
    public User save(User user) {
        boolean insertion = user.getId() == null;
        try {
            // Sans flush explicite, la violation d'unicité ne surviendrait qu'au commit,
            // hors de portée de ce try/catch.
            return springDataUserRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            if (!insertion) {
                throw e;
            }
            throw new EmailAlreadyUsedException(user.getEmail());
        }
    }

    @Override
    public Optional<User> findByEmail(Email email) {
        return springDataUserRepository.findByEmail(email);
    }

    @Override
    public Optional<User> findById(UUID id) {
        return springDataUserRepository.findById(id);
    }
}
