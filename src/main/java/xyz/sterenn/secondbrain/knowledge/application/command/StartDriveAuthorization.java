package xyz.sterenn.secondbrain.knowledge.application.command;

import java.util.UUID;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveAuthorizationState;
import xyz.sterenn.secondbrain.shared.bus.Command;

public record StartDriveAuthorization(UUID ownerId, DriveAuthorizationState state) implements Command {}
