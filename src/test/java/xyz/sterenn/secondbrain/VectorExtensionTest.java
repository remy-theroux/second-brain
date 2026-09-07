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
    void the_database_enables_the_vector_search_extension() {
        Optional<String> version = jdbcClient
                .sql("SELECT extversion FROM pg_extension WHERE extname = 'vector'")
                .query(String.class)
                .optional();

        assertThat(version).isPresent();
    }

    @Test
    void the_database_computes_the_cosine_distance_between_two_vectors() {
        Double distance = jdbcClient
                .sql("SELECT '[1,0,0]'::vector <=> '[0,1,0]'::vector")
                .query(Double.class)
                .single();

        assertThat(distance).isEqualTo(1.0d);
    }
}
