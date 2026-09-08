package xyz.sterenn.secondbrain.knowledge.application.command;

import xyz.sterenn.secondbrain.shared.bus.Command;

/** One round of the clock: every subscription about to lapse, and every connection without one. */
public record RenewDriveChannels() implements Command {}
