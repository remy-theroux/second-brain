package xyz.sterenn.secondbrain.knowledge.domain.exception;

/**
 * The only refusal the webhook knows. It covers a channel nobody opened, a token that does not
 * match and a channel past its deadline alike: telling them apart would make the route an oracle,
 * and the caller has the same thing to do in the three cases — stop calling.
 */
public class UnknownDriveChannelException extends RuntimeException {

    public static final String MESSAGE = "Ce canal de notifications n'existe pas.";

    public UnknownDriveChannelException() {
        super(MESSAGE);
    }
}
