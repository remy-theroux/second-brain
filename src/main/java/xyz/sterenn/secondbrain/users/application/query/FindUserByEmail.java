package xyz.sterenn.secondbrain.users.application.query;

import java.util.Optional;
import xyz.sterenn.secondbrain.shared.bus.Query;

public record FindUserByEmail(String email) implements Query<Optional<UserView>> {}
