package xyz.sterenn.secondbrain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * La base sait-elle héberger des vecteurs ?
 *
 * <p>Deux questions distinctes, et il faut les deux. L'extension peut être <em>fournie</em>
 * par l'image sans être <em>activée</em> sur la base : c'est le rôle de la migration. Et une
 * extension activée sans opérateur utilisable ne servirait à rien — d'où le second test, qui
 * calcule une vraie distance plutôt que de lire une ligne de catalogue.
 *
 * <p>Ce test vit à la racine et non dans {@code knowledge} : c'est une capacité de la base,
 * pas une règle d'un contexte borné. Il rejoint {@code SecondBrainApplicationTests}, qui
 * vérifie déjà que Flyway migre la base Testcontainers.
 */
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
        // Deux vecteurs orthogonaux : leur distance cosinus vaut exactement 1.
        Double distance = jdbcClient
                .sql("SELECT '[1,0,0]'::vector <=> '[0,1,0]'::vector")
                .query(Double.class)
                .single();

        assertThat(distance).isEqualTo(1.0d);
    }
}
