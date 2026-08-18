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

/**
 * Le test injecte le <em>port</em>, pas l'adapter : c'est le contrat du domaine qui est
 * vérifié. {@code @Transactional} fait rouler chaque test en arrière.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class JpaVerificationTokenRepositoryAdapterTest {

    private static final Instant EMISSION = Instant.parse("2026-08-06T10:00:00Z");

    @Autowired
    private VerificationTokenRepository verificationTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID compteExistant(String email) {
        return userRepository.save(User.register(new Email(email), "empreinte")).getId();
    }

    @Test
    void persiste_un_jeton_pour_un_compte() {
        UUID compte = compteExistant("alice@example.com");

        VerificationToken saved =
                verificationTokenRepository.save(VerificationToken.issue(compte, "empreinte", EMISSION));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUserId()).isEqualTo(compte);
        assertThat(saved.getExpiresAt()).isEqualTo(EMISSION.plus(VerificationToken.VALIDITY));
        assertThat(saved.getConsumedAt()).isNull();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void retrouve_le_jeton_d_un_compte() {
        UUID compte = compteExistant("bob@example.com");
        verificationTokenRepository.save(VerificationToken.issue(compte, "empreinte", EMISSION));

        assertThat(verificationTokenRepository.findByUserId(compte))
                .isPresent()
                .hasValueSatisfying(jeton -> assertThat(jeton.getTokenHash()).isEqualTo("empreinte"));
    }

    @Test
    void ne_retrouve_rien_pour_un_compte_sans_jeton() {
        assertThat(verificationTokenRepository.findByUserId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void enregistre_la_consommation_du_jeton() {
        UUID compte = compteExistant("carol@example.com");
        VerificationToken jeton =
                verificationTokenRepository.save(VerificationToken.issue(compte, "empreinte", EMISSION));
        Instant clic = EMISSION.plusSeconds(60);

        jeton.consume(clic);
        verificationTokenRepository.save(jeton);

        assertThat(verificationTokenRepository.findByUserId(compte))
                .hasValueSatisfying(relu -> assertThat(relu.isConsumed()).isTrue());
    }

    @Test
    void persiste_l_empreinte_sans_la_tronquer() {
        // Contrôle direct en SQL : la colonne doit contenir l'empreinte reçue telle quelle,
        // sans troncature ni conversion. La garantie que le jeton en clair, lui, n'est
        // jamais stocké est apportée ailleurs, par
        // RegisterUserHandlerTest.emet_un_jeton_dont_seule_l_empreinte_est_stockee.
        UUID compte = compteExistant("dave@example.com");
        verificationTokenRepository.save(VerificationToken.issue(compte, "{bcrypt}$2a$10$empreinte", EMISSION));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT token_hash FROM users_verification_tokens WHERE user_id = ?", String.class, compte))
                .isEqualTo("{bcrypt}$2a$10$empreinte");
    }
}
