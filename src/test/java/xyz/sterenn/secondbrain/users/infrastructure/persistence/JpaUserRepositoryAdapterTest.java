package xyz.sterenn.secondbrain.users.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.users.domain.Email;
import xyz.sterenn.secondbrain.users.domain.EmailAlreadyUsedException;
import xyz.sterenn.secondbrain.users.domain.User;
import xyz.sterenn.secondbrain.users.domain.UserRepository;

/**
 * {@code @Transactional} fait rouler chaque test en arrière : la PostgreSQL
 * Testcontainers est partagée par toute la suite.
 *
 * <p>Le test injecte le <em>port</em> {@link UserRepository}, pas l'adapter : c'est le
 * contrat du domaine qui est vérifié.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class JpaUserRepositoryAdapterTest {

    @Autowired
    private UserRepository users;

    @Test
    void persiste_un_compte_dans_un_etat_non_verifie() {
        User saved = users.save(User.register(new Email("alice@example.com"), "empreinte"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEmail()).isEqualTo(new Email("alice@example.com"));
        assertThat(saved.getPasswordHash()).isEqualTo("empreinte");
        assertThat(saved.isVerified()).isFalse();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void detecte_un_email_deja_pris() {
        users.save(User.register(new Email("bob@example.com"), "empreinte"));

        assertThat(users.existsByEmail(new Email("bob@example.com"))).isTrue();
        assertThat(users.existsByEmail(new Email("carol@example.com"))).isFalse();
    }

    @Test
    void retrouve_un_compte_par_son_email() {
        users.save(User.register(new Email("dave@example.com"), "empreinte"));

        assertThat(users.findByEmail(new Email("dave@example.com")))
            .isPresent()
            .hasValueSatisfying(user -> assertThat(user.isVerified()).isFalse());
        assertThat(users.findByEmail(new Email("inconnu@example.com"))).isEmpty();
    }

    @Test
    void traduit_la_violation_d_unicite_en_erreur_metier() {
        users.save(User.register(new Email("erin@example.com"), "empreinte"));

        assertThatThrownBy(() -> users.save(User.register(new Email("erin@example.com"), "autre")))
            .isInstanceOf(EmailAlreadyUsedException.class);
    }
}
