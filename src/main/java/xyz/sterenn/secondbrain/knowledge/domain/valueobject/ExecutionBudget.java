package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.time.Duration;
import java.util.Objects;

public record ExecutionBudget(int maxTurns, Duration limit) {

    public ExecutionBudget {
        Objects.requireNonNull(limit, "Le budget de temps est obligatoire");
        if (maxTurns < 1) {
            throw new IllegalArgumentException("Un agent qui ne peut pas jouer un tour ne peut rien répondre");
        }
        if (limit.isZero() || limit.isNegative()) {
            throw new IllegalArgumentException("Le budget de temps doit être strictement positif");
        }
    }
}
