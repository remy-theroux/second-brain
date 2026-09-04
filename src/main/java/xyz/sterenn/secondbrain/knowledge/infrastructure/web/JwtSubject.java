package xyz.sterenn.secondbrain.knowledge.infrastructure.web;

import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

final class JwtSubject {

    private JwtSubject() {}

    static UUID accountId(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new UnreadableSubjectException();
        }
    }

    static final class UnreadableSubjectException extends RuntimeException {}
}
