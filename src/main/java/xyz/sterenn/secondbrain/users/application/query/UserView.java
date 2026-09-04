package xyz.sterenn.secondbrain.users.application.query;

import java.time.Instant;
import java.util.UUID;
import xyz.sterenn.secondbrain.users.domain.entity.User;

public record UserView(UUID id, String email, boolean verified, Instant createdAt) {

    public static UserView of(User user) {
        return new UserView(user.getId(), user.getEmail().value(), user.isVerified(), user.getCreatedAt());
    }
}
