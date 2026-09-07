package xyz.sterenn.secondbrain.knowledge.infrastructure.extraction;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

final class TextDecoding {

    private TextDecoding() {}

    /**
     * UTF-8, with ISO-8859-1 as fallback. The UTF-8 decoder is set to {@code REPORT}: by default
     * it would replace invalid bytes with a silent {@code U+FFFD}, and the fallback would never
     * fire.
     */
    static String decode(byte[] content) {
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
        } catch (CharacterCodingException notUtf8) {
            return new String(content, StandardCharsets.ISO_8859_1);
        }
    }
}
