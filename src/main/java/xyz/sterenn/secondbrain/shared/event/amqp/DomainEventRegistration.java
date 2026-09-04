package xyz.sterenn.secondbrain.shared.event.amqp;

import java.util.List;
import xyz.sterenn.secondbrain.shared.event.DomainEvent;

public record DomainEventRegistration(List<Class<? extends DomainEvent>> types) {}
