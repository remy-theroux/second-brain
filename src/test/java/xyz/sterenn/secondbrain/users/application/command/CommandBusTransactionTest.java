package xyz.sterenn.secondbrain.users.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.shared.bus.Command;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.shared.bus.CommandHandler;
import xyz.sterenn.secondbrain.users.domain.entity.User;
import xyz.sterenn.secondbrain.users.domain.port.UserRepository;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;

/**
 * Vérifie l'exigence centrale du CommandBus : tout le handler s'exécute dans une seule
 * transaction SQL, et un échec en cours de route annule ce qui a déjà été écrit.
 *
 * <p>Ce test ne porte volontairement <strong>pas</strong> {@code @Transactional} : une
 * transaction de test englobante masquerait le rollback qu'on cherche à observer. Le
 * nettoyage est donc explicite.
 */
@Import({TestcontainersConfiguration.class, CommandBusTransactionTest.HandlerDeTest.class})
@SpringBootTest
class CommandBusTransactionTest {

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private UserRepository users;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Filet de sécurité : si le rollback ne fonctionnait pas, la ligne survivrait et
     * ferait échouer les autres tests de la suite. Le nettoyage est explicite puisque
     * ce test refuse la transaction englobante de {@code @Transactional}.
     */
    @AfterEach
    void nettoyer() {
        jdbcTemplate.update("DELETE FROM users_users WHERE email = ?", "frank@example.com");
    }

    @Test
    void annule_les_ecritures_quand_le_handler_echoue() {
        assertThatThrownBy(() -> commandBus.dispatch(new EchouerApresEcriture("frank@example.com")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("échec volontaire");

        assertThat(users.existsByEmail(new Email("frank@example.com"))).isFalse();
    }

    record EchouerApresEcriture(String email) implements Command {
    }

    /**
     * Écrit puis lève une {@link RuntimeException} : seules les exceptions non checked
     * déclenchent un rollback avec les réglages Spring par défaut.
     */
    static class EchouerApresEcritureHandler implements CommandHandler<EchouerApresEcriture> {

        private final UserRepository users;

        EchouerApresEcritureHandler(UserRepository users) {
            this.users = users;
        }

        @Override
        public void handle(EchouerApresEcriture command) {
            users.save(User.register(new Email(command.email()), "empreinte"));
            throw new IllegalStateException("échec volontaire");
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class HandlerDeTest {

        @Bean
        EchouerApresEcritureHandler echouerApresEcritureHandler(UserRepository users) {
            return new EchouerApresEcritureHandler(users);
        }
    }
}
