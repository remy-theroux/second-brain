package xyz.sterenn.secondbrain.knowledge;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

public final class Fixtures {

    public static final String RAW_TXT = "brut.txt";

    public static final String STRUCTURED_MD = "structure.md";

    public static final String HEADINGLESS_MD = "sans-titres.md";

    public static final String HEADINGS_DOCX = "titres.docx";

    public static final String BOOKMARKS_PDF = "signets.pdf";

    public static final String BOOKMARKLESS_PDF = "sans-signets.pdf";

    public static final String SCANNED_PDF = "numerise.pdf";

    private Fixtures() {}

    public static byte[] read(String name) {
        try (InputStream stream = Fixtures.class.getResourceAsStream("/fixtures/" + name)) {
            if (stream == null) {
                throw new IllegalStateException("Fixture absente : /fixtures/" + name
                        + " — les binaires se refabriquent par `gtest generateFixtures`");
            }
            return stream.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
