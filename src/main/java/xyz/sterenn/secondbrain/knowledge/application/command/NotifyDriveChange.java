package xyz.sterenn.secondbrain.knowledge.application.command;

import xyz.sterenn.secondbrain.shared.bus.Command;

/**
 * What a Drive notification carries, and it is all in headers: the body is empty, on a change as
 * on the sync that opens a channel. Nothing here says which file moved — nothing ever will.
 */
public record NotifyDriveChange(String channelId, String channelToken, String resourceState) implements Command {

    /** The token is the only thing that authenticates a notification: it never reaches a log. */
    @Override
    public String toString() {
        return "NotifyDriveChange[channelId=" + channelId + ", channelToken=***, resourceState=" + resourceState + "]";
    }
}
