package xyz.sterenn.secondbrain.knowledge.application.agent;

import java.util.function.Consumer;
import xyz.sterenn.secondbrain.knowledge.domain.CitationPolicy;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.SourceCatalogue;

/**
 * Retient les fragments tant qu'aucune citation connue n'est apparue : un affichage au fil de
 * l'eau et un garde-fou de fin de réponse s'excluent, et c'est l'ancrage qui l'emporte.
 */
final class CitationBuffer {

    private final SourceCatalogue catalogue;
    private final Consumer<String> sortie;
    private final StringBuilder accumule = new StringBuilder();

    private boolean ouvert;

    CitationBuffer(SourceCatalogue catalogue, Consumer<String> sortie) {
        this.catalogue = catalogue;
        this.sortie = sortie;
    }

    void accepte(String fragment) {
        accumule.append(fragment);
        if (ouvert) {
            sortie.accept(fragment);
            return;
        }
        // Sur le texte accumulé, jamais sur le fragment : un [3] arrive volontiers coupé
        // en « [ » puis « 3] ».
        if (CitationPolicy.finDeLaPremiereCitationValide(accumule.toString(), catalogue::contient)
                .isPresent()) {
            ouvert = true;
            sortie.accept(accumule.toString());
        }
    }

    String texte() {
        return accumule.toString();
    }

    boolean aOuvert() {
        return ouvert;
    }
}
