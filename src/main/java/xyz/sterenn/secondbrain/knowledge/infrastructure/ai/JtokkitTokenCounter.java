package xyz.sterenn.secondbrain.knowledge.infrastructure.ai;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.port.TokenCounter;

/**
 * The lazy registry loads only the requested encoding, where the default registry loads them
 * all. The {@link Encoding}, immutable and thread-safe, is built once: rebuilding it would
 * re-read the BPE tables on every call.
 */
@Component
class JtokkitTokenCounter implements TokenCounter {

    private final Encoding encoding = Encodings.newLazyEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);

    @Override
    public int count(String text) {
        return text == null || text.isEmpty() ? 0 : encoding.countTokens(text);
    }
}
