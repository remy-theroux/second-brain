package xyz.sterenn.secondbrain.users.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.users.domain.entity.User;
import xyz.sterenn.secondbrain.users.domain.entity.VerificationToken;
import xyz.sterenn.secondbrain.users.domain.port.UserRepository;
import xyz.sterenn.secondbrain.users.domain.port.VerificationTokenRepository;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class JpaVerificationTokenRepositoryAdapterTest {

    private static final Instant ISSUED_AT = Instant.parse("2026-08-06T10:00:00Z");

    @Autowired
    private VerificationTokenRepository verificationTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID existingAccount(String email) {
        return userRepository.save(User.register(new Email(email), "empreinte")).getId();
    }

    @Test
    void persists_a_token_for_an_account() {
        UUID accountId = existingAccount("alice@example.com");

        VerificationToken saved =
                verificationTokenRepository.save(VerificationToken.issue(accountId, "empreinte", ISSUED_AT));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUserId()).isEqualTo(accountId);
        assertThat(saved.getExpiresAt()).isEqualTo(ISSUED_AT.plus(VerificationToken.VALIDITY));
        assertThat(saved.getConsumedAt()).isNull();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void finds_the_token_of_an_account() {
        UUID accountId = existingAccount("bob@example.com");
        verificationTokenRepository.save(VerificationToken.issue(accountId, "empreinte", ISSUED_AT));

        assertThat(verificationTokenRepository.findByUserId(accountId))
                .isPresent()
                .hasValueSatisfying(token -> assertThat(token.getTokenHash()).isEqualTo("empreinte"));
    }

    @Test
    void finds_nothing_for_an_account_without_a_token() {
        assertThat(verificationTokenRepository.findByUserId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void records_the_consumption_of_the_token() {
        UUID accountId = existingAccount("carol@example.com");
        VerificationToken token =
                verificationTokenRepository.save(VerificationToken.issue(accountId, "empreinte", ISSUED_AT));
        Instant click = ISSUED_AT.plusSeconds(60);

        token.consume(click);
        verificationTokenRepository.save(token);

        assertThat(verificationTokenRepository.findByUserId(accountId))
                .hasValueSatisfying(reread -> assertThat(reread.isConsumed()).isTrue());
    }

    @Test
    void persists_the_hash_without_truncating_it() {
        UUID accountId = existingAccount("dave@example.com");
        verificationTokenRepository.save(VerificationToken.issue(accountId, "{bcrypt}$2a$10$empreinte", ISSUED_AT));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT token_hash FROM users_verification_tokens WHERE user_id = ?", String.class, accountId))
                .isEqualTo("{bcrypt}$2a$10$empreinte");
    }
}
