package xyz.sterenn.secondbrain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class VectorExtensionTest {

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void la_base_active_l_extension_de_recherche_vectorielle() {
        Optional<String> version = jdbcClient
                .sql("SELECT extversion FROM pg_extension WHERE extname = 'vector'")
                .query(String.class)
                .optional();

        assertThat(version).isPresent();
    }

    @Test
    void la_base_calcule_la_distance_cosinus_entre_deux_vecteurs() {
        Double distance = jdbcClient
                .sql("SELECT '[1,0,0]'::vector <=> '[0,1,0]'::vector")
                .query(Double.class)
                .single();

        assertThat(distance).isEqualTo(1.0d);
    }
}
