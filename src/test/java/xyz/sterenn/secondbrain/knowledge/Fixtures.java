package xyz.sterenn.secondbrain.knowledge;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

public final class Fixtures {

    public static final String BRUT_TXT = "brut.txt";

    public static final String STRUCTURE_MD = "structure.md";

    public static final String SANS_TITRES_MD = "sans-titres.md";

    public static final String TITRES_DOCX = "titres.docx";

    public static final String SIGNETS_PDF = "signets.pdf";

    public static final String SANS_SIGNETS_PDF = "sans-signets.pdf";

    public static final String NUMERISE_PDF = "numerise.pdf";

    private Fixtures() {}

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
