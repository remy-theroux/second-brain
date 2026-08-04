package xyz.sterenn.secondbrain.users.application.query;

import java.util.Optional;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.shared.bus.QueryHandler;
import xyz.sterenn.secondbrain.users.domain.Email;
import xyz.sterenn.secondbrain.users.domain.User;
import xyz.sterenn.secondbrain.users.domain.UserRepository;

/**
 * Aucun {@code @Transactional} ici : {@code SpringQueryBus.ask} ouvre déjà une
 * transaction en lecture seule.
 */
@Component
public class FindUserByEmailHandler implements QueryHandler<FindUserByEmail, Optional<UserView>> {

    private final UserRepository users;

    public FindUserByEmailHandler(UserRepository users) {
        this.users = users;
    }

    @Override
    public Optional<UserView> handle(FindUserByEmail query) {
        return users.findByEmail(new Email(query.email())).map(FindUserByEmailHandler::toView);
    }

    private static UserView toView(User user) {
        return new UserView(user.getId(), user.getEmail().value(), user.isVerified(), user.getCreatedAt());
    }
}
