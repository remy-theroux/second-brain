package xyz.sterenn.secondbrain.users.application.query;

import java.util.Optional;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.shared.bus.QueryHandler;
import xyz.sterenn.secondbrain.users.domain.port.UserRepository;

@Component
public class FindUserByIdHandler implements QueryHandler<FindUserById, Optional<UserView>> {

    private final UserRepository userRepository;

    public FindUserByIdHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Optional<UserView> handle(FindUserById query) {
        return userRepository.findById(query.id()).map(UserView::of);
    }
}
