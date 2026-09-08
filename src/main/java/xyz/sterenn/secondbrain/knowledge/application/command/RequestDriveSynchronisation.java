package xyz.sterenn.secondbrain.knowledge.application.command;

import xyz.sterenn.secondbrain.shared.bus.Command;

/**
 * Every connected Drive, and no owner to name: the clock asks for the round, the handler tells
 * whose Drives it covers. Its single caller is the scheduled task.
 */
public record RequestDriveSynchronisation() implements Command {}
