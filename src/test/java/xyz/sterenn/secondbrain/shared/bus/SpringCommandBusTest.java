package xyz.sterenn.secondbrain.shared.bus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class SpringCommandBusTest {

    record Saluer(String nom) implements Command {}

    record Partir() implements Command {}

    static class SaluerHandler implements CommandHandler<Saluer> {
        String recu;

        @Override
        public void handle(Saluer command) {
            this.recu = command.nom();
        }
    }

    static class PartirHandler implements CommandHandler<Partir> {
        boolean appele;

        @Override
        public void handle(Partir command) {
            this.appele = true;
        }
    }

    @Test
    void route_la_commande_vers_son_seul_handler() {
        SaluerHandler saluer = new SaluerHandler();
        PartirHandler partir = new PartirHandler();
        CommandBus bus = new SpringCommandBus(List.of(saluer, partir));

        bus.dispatch(new Saluer("Rémy"));

        assertThat(saluer.recu).isEqualTo("Rémy");
        assertThat(partir.appele).isFalse();
    }

    @Test
    void echoue_si_aucun_handler_ne_traite_la_commande() {
        CommandBus bus = new SpringCommandBus(List.of());

        assertThatThrownBy(() -> bus.dispatch(new Saluer("Rémy")))
                .isInstanceOf(HandlerNotFoundException.class)
                .hasMessageContaining("Saluer");
    }

    @Test
    void echoue_au_demarrage_si_deux_handlers_visent_la_meme_commande() {
        assertThatThrownBy(() -> new SpringCommandBus(List.of(new SaluerHandler(), new SaluerHandler())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Saluer");
    }
}
