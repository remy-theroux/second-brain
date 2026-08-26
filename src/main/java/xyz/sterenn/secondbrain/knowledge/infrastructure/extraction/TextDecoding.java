package xyz.sterenn.secondbrain.knowledge.infrastructure.extraction;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Des octets vers du texte, pour les deux formats qui n'ont pas d'en-tête pour le dire. */
final class TextDecoding {

    private TextDecoding() {
        // classe utilitaire
    }

    /**
     * UTF-8 en premier, ISO-8859-1 en repli.
     *
     * <p>Repli et non échec : un {@code .txt} en ISO-8859-1 reste un texte parfaitement
     * lisible, et c'est l'encodage de à peu près tout ce qui a été écrit sous Windows avant
     * 2010. Aucune détection de jeu de caractères au-delà : deux essais, et c'est tout — une
     * bibliothèque de détection serait une dépendance de plus pour deviner ce que le repli
     * couvre déjà.
     *
     * <p>Le décodeur UTF-8 est monté en {@code REPORT} : par défaut il remplacerait les
     * octets invalides par un {@code U+FFFD} silencieux, et le repli ne se déclencherait
     * jamais.
     */
    static String decode(byte[] content) {
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
        } catch (CharacterCodingException pasDeLUtf8) {
            return new String(content, StandardCharsets.ISO_8859_1);
        }
    }
}
