package xyz.sterenn.secondbrain.shared.bus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class SpringCommandBusTest {

    record Greet(String name) implements Command {}

    record Leave() implements Command {}

    static class GreetHandler implements CommandHandler<Greet> {
        String received;

        @Override
        public void handle(Greet command) {
            this.received = command.name();
        }
    }

    static class LeaveHandler implements CommandHandler<Leave> {
        boolean called;

        @Override
        public void handle(Leave command) {
            this.called = true;
        }
    }

    @Test
    void routes_the_command_to_its_single_handler() {
        GreetHandler greet = new GreetHandler();
        LeaveHandler leave = new LeaveHandler();
        CommandBus bus = new SpringCommandBus(List.of(greet, leave));

        bus.dispatch(new Greet("Rémy"));

        assertThat(greet.received).isEqualTo("Rémy");
        assertThat(leave.called).isFalse();
    }

    @Test
    void fails_when_no_handler_handles_the_command() {
        CommandBus bus = new SpringCommandBus(List.of());

        assertThatThrownBy(() -> bus.dispatch(new Greet("Rémy")))
                .isInstanceOf(HandlerNotFoundException.class)
                .hasMessageContaining("Greet");
    }

    @Test
    void fails_at_startup_when_two_handlers_target_the_same_command() {
        assertThatThrownBy(() -> new SpringCommandBus(List.of(new GreetHandler(), new GreetHandler())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Greet");
    }
}
