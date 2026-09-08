package xyz.sterenn.secondbrain.knowledge.application.drive;

/**
 * The types of a failure, and nothing else. A refusal from Google carries the response body in
 * its own message — hence the value it refused, the address of the webhook or the token of the
 * channel — and that refusal travels as the cause of every Drive exception this context throws.
 * Logging the exception would print that body through the cause chain, by the very door the
 * adapters close when they log a status rather than a message.
 */
public final class DriveFailures {

    /** A chain deep enough to say where it came from, and bounded: two exceptions can cause each other. */
    private static final int MAX_DEPTH = 5;

    private static final String CAUSED_BY = " <- ";

    private DriveFailures() {}

    public static String describe(Throwable failure) {
        StringBuilder chain = new StringBuilder();
        Throwable current = failure;
        for (int depth = 0; current != null && depth < MAX_DEPTH; depth++) {
            chain.append(depth == 0 ? "" : CAUSED_BY).append(current.getClass().getSimpleName());
            current = current.getCause();
        }
        return chain.toString();
    }
}
