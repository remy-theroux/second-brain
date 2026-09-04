package xyz.sterenn.secondbrain.shared.event;

import java.time.Instant;

public interface DomainEvent {

    Instant occurredAt();
}
