package xyz.sterenn.secondbrain.knowledge.domain.exception;

/**
 * The only refusal the webhook knows. It covers a channel nobody opened, a token that does not
 * match and a channel past its deadline alike: telling them apart would make the route an oracle,
 * and the caller has the same thing to do in the three cases — stop calling.
 *
 * <p>Its message is in English where every other business refusal is in French, and that is the
 * rule rather than an exception to it: the rule says a business message is displayable as it is,
 * which supposes a human reader. This one has none — the route answers 404 without a body, and
 * its only caller is Google.
 */
public class UnknownDriveChannelException extends RuntimeException {

    public static final String MESSAGE = "This Drive notification channel does not exist.";

    public UnknownDriveChannelException() {
        super(MESSAGE);
    }
}
