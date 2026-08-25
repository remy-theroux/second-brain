package xyz.sterenn.secondbrain.shared.event.amqp;

import java.util.List;
import xyz.sterenn.secondbrain.shared.event.DomainEvent;

/**
 * Les événements qu'un contexte borné sait publier ou consommer. Chaque contexte en
 * déclare un en {@code @Bean} dans son infrastructure ; {@link AmqpConfiguration} les
 * collecte pour construire la table des noms du convertisseur.
 *
 * <p>Déclarés et non scannés : un événement absent de toute déclaration échoue à la
 * désérialisation avec un message qui porte son nom, plutôt que par un
 * {@code ClassNotFoundException} sur un nom qualifié.
 */
public record DomainEventRegistration(List<Class<? extends DomainEvent>> types) {}
