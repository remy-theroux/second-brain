package xyz.sterenn.secondbrain.knowledge.infrastructure.web;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.sterenn.secondbrain.knowledge.application.query.FindDriveConnection;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DriveConnectionNotFoundException;
import xyz.sterenn.secondbrain.shared.bus.QueryBus;
import xyz.sterenn.secondbrain.shared.web.ErrorResponse;

@RestController
public class ShowDriveConnectionController {

    private final QueryBus queryBus;

    public ShowDriveConnectionController(QueryBus queryBus) {
        this.queryBus = queryBus;
    }

    @GetMapping("/api/drive/connection")
    @SecurityRequirement(name = "bearer")
    public ResponseEntity<Object> show(@AuthenticationPrincipal Jwt jwt) {
        return queryBus.ask(new FindDriveConnection(JwtSubject.accountId(jwt)))
                .<ResponseEntity<Object>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse(DriveConnectionNotFoundException.MESSAGE)));
    }

    @ExceptionHandler(JwtSubject.UnreadableSubjectException.class)
    public ResponseEntity<Object> unreadableSubject() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}
