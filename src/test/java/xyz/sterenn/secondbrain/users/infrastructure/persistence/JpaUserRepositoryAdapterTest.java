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
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void persiste_un_compte_dans_un_etat_non_verifie() {
        User saved = userRepository.save(User.register(new Email("alice@example.com"), "empreinte"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEmail()).isEqualTo(new Email("alice@example.com"));
        assertThat(saved.getPasswordHash()).isEqualTo("empreinte");
        assertThat(saved.isVerified()).isFalse();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void detecte_un_email_deja_pris() {
        userRepository.save(User.register(new Email("bob@example.com"), "empreinte"));

        assertThat(userRepository.existsByEmail(new Email("bob@example.com"))).isTrue();
        assertThat(userRepository.existsByEmail(new Email("carol@example.com"))).isFalse();
    }

    @Test
    void retrouve_un_compte_par_son_email() {
        userRepository.save(User.register(new Email("dave@example.com"), "empreinte"));

        assertThat(userRepository.findByEmail(new Email("dave@example.com")))
            .isPresent()
            .hasValueSatisfying(user -> assertThat(user.isVerified()).isFalse());
        assertThat(userRepository.findByEmail(new Email("inconnu@example.com"))).isEmpty();
    }

    @Test
    void projette_l_email_sur_une_colonne_texte() {
        // Seule preuve observable qu'EmailAttributeConverter est bien auto-appliqué : il
        // n'est nommé nulle part dans le code, et un test unitaire du converter passerait
        // au vert même si Hibernate ne l'appliquait jamais.
        userRepository.save(User.register(new Email("  Frank@Example.COM "), "empreinte"));

        assertThat(jdbcTemplate.queryForObject(
            "SELECT email FROM users_users WHERE email = ?", String.class, "frank@example.com"))
            .isEqualTo("frank@example.com");
    }

    @Test
    void traduit_la_violation_d_unicite_en_erreur_metier() {
        userRepository.save(User.register(new Email("erin@example.com"), "empreinte"));

        assertThatThrownBy(() -> userRepository.save(User.register(new Email("erin@example.com"), "autre")))
            .isInstanceOf(EmailAlreadyUsedException.class);
    }

    @Test
    void ne_traduit_plus_une_violation_d_integrite_lors_d_une_mise_a_jour() throws Exception {
        userRepository.save(User.register(new Email("gina@example.com"), "empreinte"));
        User autre = userRepository.save(User.register(new Email("henri@example.com"), "empreinte"));

        // Le domaine n'expose aucun mutateur d'email — VerifyAccountHandler ne fait que
        // basculer `verified`, jamais l'email. On force ici le champ de l'entité déjà
        // persistée pour isoler la seule chose sous test : une fois `autre` pourvu d'un
        // identifiant (mise à jour, pas insertion), une violation d'intégrité ne doit plus
        // être traduite en EmailAlreadyUsedException.
        Field email = User.class.getDeclaredField("email");
        email.setAccessible(true);
        email.set(autre, new Email("gina@example.com"));

        assertThatThrownBy(() -> userRepository.save(autre))
            .isInstanceOf(DataIntegrityViolationException.class)
            .isNotInstanceOf(EmailAlreadyUsedException.class);
    }
}
