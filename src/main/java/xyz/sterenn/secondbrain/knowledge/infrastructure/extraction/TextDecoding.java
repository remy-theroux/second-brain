package xyz.sterenn.secondbrain.knowledge.infrastructure.extraction;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

final class TextDecoding {

    private TextDecoding() {}

    /**
     * UTF-8, ISO-8859-1 en repli. Le décodeur UTF-8 est monté en {@code REPORT} : par défaut
     * il remplacerait les octets invalides par un {@code U+FFFD} silencieux, et le repli ne
     * se déclencherait jamais.
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
