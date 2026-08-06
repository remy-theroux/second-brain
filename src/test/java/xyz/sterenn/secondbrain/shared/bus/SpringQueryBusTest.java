package xyz.sterenn.secondbrain.shared.bus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class SpringQueryBusTest {

    record CompterLettres(String mot) implements Query<Integer> {
    }

    static class CompterLettresHandler implements QueryHandler<CompterLettres, Integer> {
        @Override
        public Integer handle(CompterLettres query) {
            return query.mot().length();
        }
    }

    @Test
    void route_la_query_et_renvoie_son_resultat() {
        QueryBus bus = new SpringQueryBus(List.of(new CompterLettresHandler()));

        int resultat = bus.ask(new CompterLettres("bonjour"));

        assertThat(resultat).isEqualTo(7);
    }

    @Test
    void echoue_si_aucun_handler_ne_traite_la_query() {
        QueryBus bus = new SpringQueryBus(List.of());

        assertThatThrownBy(() -> bus.ask(new CompterLettres("bonjour")))
            .isInstanceOf(HandlerNotFoundException.class)
            .hasMessageContaining("CompterLettres");
    }
}
