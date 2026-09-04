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

// Volontairement sans @Transactional : une transaction de test englobante masquerait le
// rollback qu'on observe ici. D'où le nettoyage explicite en @AfterEach.
@Import({TestcontainersConfiguration.class, CommandBusTransactionTest.HandlerDeTest.class})
@SpringBootTest
class CommandBusTransactionTest {

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void nettoyer() {
        jdbcTemplate.update("DELETE FROM users_users WHERE email = ?", "frank@example.com");
    }

    @Test
    void annule_les_ecritures_quand_le_handler_echoue() {
        assertThatThrownBy(() -> commandBus.dispatch(new EchouerApresEcriture("frank@example.com")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("échec volontaire");

        assertThat(userRepository.existsByEmail(new Email("frank@example.com"))).isFalse();
    }

    record EchouerApresEcriture(String email) implements Command {}

    // L'exception levée n'est pas checked : seule une RuntimeException déclenche un
    // rollback avec les réglages Spring par défaut.
    static class EchouerApresEcritureHandler implements CommandHandler<EchouerApresEcriture> {

        private final UserRepository userRepository;

        EchouerApresEcritureHandler(UserRepository userRepository) {
            this.userRepository = userRepository;
        }

        @Override
        public void handle(EchouerApresEcriture command) {
            userRepository.save(User.register(new Email(command.email()), "empreinte"));
            throw new IllegalStateException("échec volontaire");
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class HandlerDeTest {

        @Bean
        EchouerApresEcritureHandler echouerApresEcritureHandler(UserRepository userRepository) {
            return new EchouerApresEcritureHandler(userRepository);
        }
    }
}
