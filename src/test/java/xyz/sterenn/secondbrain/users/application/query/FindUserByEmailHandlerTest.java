package xyz.sterenn.secondbrain.users.application.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.shared.bus.QueryBus;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration;
import xyz.sterenn.secondbrain.users.application.command.RegisterUser;
import xyz.sterenn.secondbrain.users.domain.exception.InvalidEmailException;

@Import({TestcontainersConfiguration.class, RecordingNotificationSenderConfiguration.class})
@SpringBootTest
@Transactional
class FindUserByEmailHandlerTest {

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private QueryBus queryBus;

    @Test
    void returns_the_view_of_an_existing_account() {
        commandBus.dispatch(new RegisterUser("grace@example.com", "chevalpile42"));

        Optional<UserView> view = queryBus.ask(new FindUserByEmail("GRACE@Example.com"));

        assertThat(view).isPresent();
        assertThat(view.get().email()).isEqualTo("grace@example.com");
        assertThat(view.get().verified()).isFalse();
        assertThat(view.get().id()).isNotNull();
        assertThat(view.get().createdAt()).isNotNull();
    }

    @Test
    void returns_empty_for_an_unknown_email() {
        assertThat(queryBus.ask(new FindUserByEmail("inconnu@example.com"))).isEmpty();
    }

    @Test
    void rejects_a_malformed_email() {
        assertThatThrownBy(() -> queryBus.ask(new FindUserByEmail("pas-un-email")))
                .isInstanceOf(InvalidEmailException.class);
    }
}
