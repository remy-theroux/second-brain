package xyz.sterenn.secondbrain.users.application.query;

import java.util.Optional;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.shared.bus.QueryHandler;
import xyz.sterenn.secondbrain.users.domain.port.UserRepository;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;

@Component
public class FindUserByEmailHandler implements QueryHandler<FindUserByEmail, Optional<UserView>> {

    private final UserRepository userRepository;

    public FindUserByEmailHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Optional<UserView> handle(FindUserByEmail query) {
        return userRepository.findByEmail(new Email(query.email())).map(UserView::of);
    }
}
