package xyz.sterenn.secondbrain.shared.bus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class SpringQueryBusTest {

    record CountLetters(String word) implements Query<Integer> {}

    static class CountLettersHandler implements QueryHandler<CountLetters, Integer> {
        @Override
        public Integer handle(CountLetters query) {
            return query.word().length();
        }
    }

    @Test
    void routes_the_query_and_returns_its_result() {
        QueryBus bus = new SpringQueryBus(List.of(new CountLettersHandler()));

        int result = bus.ask(new CountLetters("bonjour"));

        assertThat(result).isEqualTo(7);
    }

    @Test
    void fails_when_no_handler_handles_the_query() {
        QueryBus bus = new SpringQueryBus(List.of());

        assertThatThrownBy(() -> bus.ask(new CountLetters("bonjour")))
                .isInstanceOf(HandlerNotFoundException.class)
                .hasMessageContaining("CountLetters");
    }
}
