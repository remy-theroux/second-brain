package xyz.sterenn.secondbrain.users.infrastructure.web;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.sterenn.secondbrain.shared.bus.QueryBus;
import xyz.sterenn.secondbrain.users.application.query.FindUserById;
import xyz.sterenn.secondbrain.users.application.query.UserView;

@RestController
public class ShowProfileController {

    private final QueryBus queryBus;

    public ShowProfileController(QueryBus queryBus) {
        this.queryBus = queryBus;
    }

    @GetMapping("/api/profile")
    @SecurityRequirement(name = "bearer")
    public ResponseEntity<UserView> showProfile(@AuthenticationPrincipal Jwt jwt) {
        UUID accountId;
        try {
            accountId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return queryBus.ask(new FindUserById(accountId))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }
}
