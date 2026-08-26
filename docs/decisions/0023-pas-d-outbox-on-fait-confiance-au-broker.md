---
status: accepté
date: 2026-08-25
decision-makers: Rémy Theroux
---

# Pas d'outbox : on fait confiance au broker

## Contexte et problème

L'adapter AMQP publie les événements métier dans `afterCommit` : un rollback n'annonce rien.
L'inverse n'est pas garanti — la base a commité, et si RabbitMQ est injoignable à cet
instant, l'écriture est acquise mais l'événement est perdu. Un document resterait `PENDING`
sans que rien ne le reprenne.

## Facteurs de décision

- Publier **avant** le commit est pire : on annoncerait des faits qui n'ont pas eu lieu.
- Une outbox tient la garantie, au prix d'une table, d'un relais, d'un ordre de publication
  à préserver et d'une déduplication côté consommateur.
- Le symptôme est observable : un `ERROR` à chaque publication perdue, et des documents qui
  restent `PENDING`.
- `DomainEventPublisher` est un port. L'outbox serait une implémentation de plus, et
  **aucun handler ne changerait**.

## Options envisagées

- Publier dans `afterCommit`, sans garantie
- Outbox en base, écrite dans la transaction, relayée par un processus séparé
- Balayage périodique des documents restés `PENDING`

## Décision

Retenu : **publier dans `afterCommit`**. Les deux parades ont été étudiées et écartées : on
fait confiance au broker.

Une seconde décision est prise avec celle-ci : **`/actuator/health` ignore délibérément le
broker** (`management.health.rabbit.enabled: false`). Redémarrer l'API ne répare pas un
broker, et l'API sert tout le reste sans lui — un health rouge ne ferait que provoquer des
redémarrages inutiles.

### Conséquences

- Bien : aucune table, aucun relais, aucune déduplication. Le port reste le seul point
  d'extension le jour où ça changera.
- Bien : l'API reste disponible quand le broker ne l'est pas.
- Mal : un événement peut être perdu, et sa perte laisse un document `PENDING` que rien ne
  reprend.
- Mal : **un broker mal configuré se perd exactement de la même façon, et plus
  discrètement.** Les défauts d'`application.yml` sont `localhost`, `guest`, `guest` — un
  défaut qui ment en production, du même genre qu'ADR-0009. Un déploiement sans
  `SPRING_RABBITMQ_HOST` démarre, sert toutes les routes, et perd chaque événement dans un
  `ERROR` que personne ne lit.

Le symptôme à guetter est donc ailleurs que dans le health : l'`ERROR` à chaque publication
perdue, et les documents qui restent `PENDING`.

### Condition de réouverture

Deux déclencheurs, l'un suffit : **un événement sans état observable derrière lui** — la
perte deviendrait alors invisible, ce qui n'est plus acceptable — ou **une perte
constatée**.

## Vérification

Rien ne le vérifie automatiquement. Le seul contrôle est l'observation des logs `ERROR` de
publication et de la colonne de statut des documents.

## Pour aller plus loin

- `shared/event/amqp/AmqpDomainEventPublisher.java`, `src/main/resources/application.yml`
- ADR-0009 — l'autre défaut qui ment en production
- `docs/superpowers/specs/2026-08-25-evenements-metier-rabbitmq-design.md`
