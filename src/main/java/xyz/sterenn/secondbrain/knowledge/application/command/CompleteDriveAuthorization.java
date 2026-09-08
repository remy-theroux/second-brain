package xyz.sterenn.secondbrain.knowledge.application.command;

import xyz.sterenn.secondbrain.shared.bus.Command;

/**
 * No owner: it comes from the request found by its state, which is the whole point of the
 * dispositif — the callback carries no token.
 */
public record CompleteDriveAuthorization(String state, String authorizationCode) implements Command {

    /** Masks the code: until it is exchanged, it is worth a single-use access to a Drive. */
    @Override
    public String toString() {
        return "CompleteDriveAuthorization[state=" + state + ", authorizationCode=***]";
    }
}
