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
import xyz.sterenn.secondbrain.users.application.command.RegisterUser;
import xyz.sterenn.secondbrain.users.domain.exception.InvalidEmailException;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class FindUserByEmailHandlerTest {

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private QueryBus queryBus;

    @Test
    void renvoie_la_vue_d_un_compte_existant() {
        commandBus.dispatch(new RegisterUser("grace@example.com", "chevalpile42"));

        Optional<UserView> vue = queryBus.ask(new FindUserByEmail("GRACE@Example.com"));

        assertThat(vue).isPresent();
        assertThat(vue.get().email()).isEqualTo("grace@example.com");
        assertThat(vue.get().verified()).isFalse();
        assertThat(vue.get().id()).isNotNull();
        assertThat(vue.get().createdAt()).isNotNull();
    }

    @Test
    void renvoie_vide_pour_un_email_inconnu() {
        assertThat(queryBus.ask(new FindUserByEmail("inconnu@example.com"))).isEmpty();
    }

    @Test
    void refuse_un_email_mal_forme() {
        assertThatThrownBy(() -> queryBus.ask(new FindUserByEmail("pas-un-email")))
            .isInstanceOf(InvalidEmailException.class);
    }
}
