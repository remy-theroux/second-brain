package xyz.sterenn.secondbrain.knowledge;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * Les documents d'essai de {@code src/test/resources/fixtures/}, et de quoi les lire.
 *
 * <p>Dans le package racine du contexte et {@code public} : les tests des extracteurs s'en
 * servent, mais aussi ceux de la commande d'extraction et du worker, qui vivent ailleurs.
 * Un utilitaire de test partagé va au même endroit que {@link KnowledgeFixture}.
 *
 * <p>Les noms sont exposés en constantes pour les tests qui vivent hors de ce package — un
 * {@code Fixtures.lire("scan.pdf")} écrit de mémoire à l'autre bout du projet échouerait à
 * l'exécution, pas à la compilation. Les tests d'extracteurs, eux, gardent le littéral : ils
 * sont à côté du fichier, et il s'y lit mieux.
 *
 * <p>La lecture passe par le classpath et non par un chemin de fichier : c'est ce que la CI
 * voit, et un chemin relatif dépendrait du répertoire de travail de Gradle.
 */
public final class Fixtures {

    /** Texte brut, sans structure, avec accents et paragraphes. */
    public static final String BRUT_TXT = "brut.txt";

    /** Markdown à trois niveaux de titres, avec un bloc de code piégeux. */
    public static final String STRUCTURE_MD = "structure.md";

    /** Markdown sans aucun titre : un unique bloc attendu. */
    public static final String SANS_TITRES_MD = "sans-titres.md";

    /** DOCX à trois niveaux, titres portés par les styles {@code HeadingN}. */
    public static final String TITRES_DOCX = "titres.docx";

    /** PDF à sommaire, plus une page de garde hors section. */
    public static final String SIGNETS_PDF = "signets.pdf";

    /** PDF sans sommaire, titres reconnaissables à la seule taille de police. */
    public static final String SANS_SIGNETS_PDF = "sans-signets.pdf";

    /** PDF numérisé : une image, aucune couche texte. Doit échouer. */
    public static final String NUMERISE_PDF = "numerise.pdf";

    private Fixtures() {
        // classe utilitaire
    }

    public static byte[] lire(String nom) {
        try (InputStream flux = Fixtures.class.getResourceAsStream("/fixtures/" + nom)) {
            if (flux == null) {
                throw new IllegalStateException("Fixture absente : /fixtures/" + nom
                        + " — les binaires se refabriquent par `gtest generateFixtures`");
            }
            return flux.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
