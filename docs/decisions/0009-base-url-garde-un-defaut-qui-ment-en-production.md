---
status: accepté
date: 2026-08-06
decision-makers: Rémy Theroux
---

# `secondbrain.base-url` garde un défaut qui ment en production

## Contexte et problème

L'adapter email construit les liens de vérification à partir de `secondbrain.base-url`,
dont la valeur par défaut est `http://localhost:8080`. Déployée sans la variable,
l'application démarre, envoie des mails, et tous les liens pointent vers la machine du
destinataire. La panne ne se manifeste ni au démarrage ni dans les logs : elle se manifeste
chez l'utilisateur, qui clique sur un lien mort.

## Facteurs de décision

- Les tests et le développement local dépendent de ce défaut ; sans lui, chaque suite et
  chaque `docker compose up` réclame une variable de plus.
- Le secret de signature JWT a tranché l'arbitrage inverse — pas de défaut, refus de
  démarrer — parce qu'un défaut de secret fuit en production. Un défaut d'URL ne fuit pas,
  il casse un lien.
- Un défaut qui ment est plus dangereux qu'une absence de défaut, mais moins qu'un secret
  partagé.

## Options envisagées

- Aucun défaut : l'application refuse de démarrer sans la variable, comme pour le secret JWT
- Un défaut `http://localhost:8080`, documenté
- Un défaut conditionné au profil : obligatoire hors `dev`

## Décision

Retenu : **le défaut est conservé**, parce que les tests et le développement local en
dépendent, et parce qu'un lien mort se constate et se répare — contrairement à un secret
faible, qui ne se constate jamais.

C'est donc **la première variable à poser sur un vrai déploiement**.

### Conséquences

- Bien : la suite de tests et la pile de développement démarrent sans configuration.
- Mal : un déploiement incomplet passe tous les contrôles de démarrage et casse en aval,
  chez l'utilisateur.

### Condition de réouverture

L'option « obligatoire hors profil `dev` » reste la bonne réponse et n'a été écartée que
faute d'un besoin pressant. Elle devient due dès qu'un deuxième environnement de production
apparaît, ou à la première occurrence constatée du symptôme.

## Pour aller plus loin

- `users/infrastructure/email/` — le seul endroit qui connaît l'URL publique
- ADR-0023 — les défauts de RabbitMQ mentent de la même façon, en plus discret
