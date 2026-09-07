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

// Deliberately without @Transactional: an enclosing test transaction would hide the
// rollback observed here. Hence the explicit cleanup in @AfterEach.
@Import({TestcontainersConfiguration.class, CommandBusTransactionTest.TestHandler.class})
@SpringBootTest
class CommandBusTransactionTest {

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM users_users WHERE email = ?", "frank@example.com");
    }

    @Test
    void rolls_back_the_writes_when_the_handler_fails() {
        assertThatThrownBy(() -> commandBus.dispatch(new FailAfterWrite("frank@example.com")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("échec volontaire");

        assertThat(userRepository.existsByEmail(new Email("frank@example.com"))).isFalse();
    }

    record FailAfterWrite(String email) implements Command {}

    // The exception thrown is unchecked: only a RuntimeException triggers a rollback
    // with Spring's default settings.
    static class FailAfterWriteHandler implements CommandHandler<FailAfterWrite> {

        private final UserRepository userRepository;

        FailAfterWriteHandler(UserRepository userRepository) {
            this.userRepository = userRepository;
        }

        @Override
        public void handle(FailAfterWrite command) {
            userRepository.save(User.register(new Email(command.email()), "empreinte"));
            throw new IllegalStateException("échec volontaire");
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestHandler {

        @Bean
        FailAfterWriteHandler failAfterWriteHandler(UserRepository userRepository) {
            return new FailAfterWriteHandler(userRepository);
        }
    }
}
