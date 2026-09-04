package xyz.sterenn.secondbrain.users.application.query;

import java.util.Optional;
import java.util.UUID;
import xyz.sterenn.secondbrain.shared.bus.Query;

public record FindUserById(UUID id) implements Query<Optional<UserView>> {}
