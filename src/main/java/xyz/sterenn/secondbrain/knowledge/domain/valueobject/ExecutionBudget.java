package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.time.Duration;
import java.util.Objects;

public record ExecutionBudget(int maxTurns, Duration limit) {

    public ExecutionBudget {
        Objects.requireNonNull(limit, "A time budget is required");
        if (maxTurns < 1) {
            throw new IllegalArgumentException("An agent that cannot play a single turn cannot answer anything");
        }
        if (limit.isZero() || limit.isNegative()) {
            throw new IllegalArgumentException("The time budget must be strictly positive");
        }
    }
}
