package xyz.sterenn.secondbrain.shared.event;

import java.time.Instant;

/**
 * Fait métier survenu et acquis. Au passé, nommé par ce qui s'est passé
 * ({@code DocumentUploaded}), jamais par ce qu'on voudrait qu'il déclenche.
 *
 * <p>Sans import Spring, comme {@code shared/bus} : un contexte borné publie sans rien
 * savoir du transport. Les événements techniques de Spring ({@code ApplicationEvent}) sont
 * autre chose et ne passent pas par ici.
 *
 * <p>Un seul contrat, l'instant : c'est ce qu'un consommateur ou un journal veut toujours.
 * Pas d'identifiant d'événement — rien ne dédoublonne, voir la décision 3 de la spec.
 * Chaque contexte déclare ses événements en records dans {@code <contexte>/domain/event/}.
 */
public interface DomainEvent {

    Instant occurredAt();
}
