package xyz.sterenn.secondbrain.users.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.users.domain.entity.VerificationToken;
import xyz.sterenn.secondbrain.users.domain.port.VerificationTokenRepository;

@Component
public class JpaVerificationTokenRepositoryAdapter implements VerificationTokenRepository {

    private final SpringDataVerificationTokenRepository springDataVerificationTokenRepository;

    JpaVerificationTokenRepositoryAdapter(SpringDataVerificationTokenRepository springDataVerificationTokenRepository) {
        this.springDataVerificationTokenRepository = springDataVerificationTokenRepository;
    }

    @Override
    public VerificationToken save(VerificationToken token) {
        // Without an explicit flush, the foreign key violation towards the user would only
        // surface at commit.
        return springDataVerificationTokenRepository.saveAndFlush(token);
    }

    @Override
    public Optional<VerificationToken> findByUserId(UUID userId) {
        return springDataVerificationTokenRepository.findByUserId(userId);
    }
}
