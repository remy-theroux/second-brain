package xyz.sterenn.secondbrain.users.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.users.domain.entity.User;
import xyz.sterenn.secondbrain.users.domain.exception.EmailAlreadyUsedException;
import xyz.sterenn.secondbrain.users.domain.port.UserRepository;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class JpaUserRepositoryAdapterTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void persists_an_account_in_an_unverified_state() {
        User saved = userRepository.save(User.register(new Email("alice@example.com"), "empreinte"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEmail()).isEqualTo(new Email("alice@example.com"));
        assertThat(saved.getPasswordHash()).isEqualTo("empreinte");
        assertThat(saved.isVerified()).isFalse();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void detects_an_already_taken_email() {
        userRepository.save(User.register(new Email("bob@example.com"), "empreinte"));

        assertThat(userRepository.existsByEmail(new Email("bob@example.com"))).isTrue();
        assertThat(userRepository.existsByEmail(new Email("carol@example.com"))).isFalse();
    }

    @Test
    void finds_an_account_by_its_email() {
        userRepository.save(User.register(new Email("dave@example.com"), "empreinte"));

        assertThat(userRepository.findByEmail(new Email("dave@example.com")))
                .isPresent()
                .hasValueSatisfying(user -> assertThat(user.isVerified()).isFalse());
        assertThat(userRepository.findByEmail(new Email("inconnu@example.com"))).isEmpty();
    }

    @Test
    void projects_the_email_onto_a_text_column() {
        // Only observable proof that EmailAttributeConverter is auto-applied: a unit test
        // of the converter would pass even if Hibernate never applied it.
        userRepository.save(User.register(new Email("  Frank@Example.COM "), "empreinte"));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT email FROM users_users WHERE email = ?", String.class, "frank@example.com"))
                .isEqualTo("frank@example.com");
    }

    @Test
    void translates_the_uniqueness_violation_into_a_business_error() {
        userRepository.save(User.register(new Email("erin@example.com"), "empreinte"));

        assertThatThrownBy(() -> userRepository.save(User.register(new Email("erin@example.com"), "autre")))
                .isInstanceOf(EmailAlreadyUsedException.class);
    }

    @Test
    void no_longer_translates_an_integrity_violation_on_an_update() throws Exception {
        userRepository.save(User.register(new Email("gina@example.com"), "empreinte"));
        User other = userRepository.save(User.register(new Email("henri@example.com"), "empreinte"));

        // The domain exposes no email setter: forcing it by reflection is the only way to
        // trigger an integrity violation on an update rather than on an insert.
        Field email = User.class.getDeclaredField("email");
        email.setAccessible(true);
        email.set(other, new Email("gina@example.com"));

        assertThatThrownBy(() -> userRepository.save(other))
                .isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(EmailAlreadyUsedException.class);
    }
}
