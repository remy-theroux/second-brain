---
status: accepté
date: 2026-08-25
decision-makers: Rémy Theroux
---

# Le déploiement de production vit dans Coolify, hors du dépôt

## Contexte et problème

Le dépôt décrit entièrement l'environnement de développement : `compose.yaml` monte
PostgreSQL, Mailpit, RabbitMQ, l'app, le worker, le front et Traefik. La production, elle,
n'est décrite nulle part. Le routage — `/api` et `/verification` vers le back, tout le reste
vers le front, ni Swagger ni actuator exposés —, le service RabbitMQ et le second
déploiement du worker (même image, `SPRING_PROFILES_ACTIVE=worker`, variables
`SPRING_RABBITMQ_*`) vivent dans la configuration Coolify.

## Facteurs de décision

- Coolify tient déjà le rôle de reverse proxy, de gestionnaire de secrets et
  d'orchestrateur : rejouer sa configuration dans le dépôt la dupliquerait sans la
  remplacer.
- Un `compose.production.yaml` versionné ne serait appliqué par rien : ce serait de la
  documentation qui a l'air d'être du code.
- Le dépôt porte déjà ce qui construit et sert les images : `Dockerfile`,
  `frontend/Dockerfile`, `frontend/nginx.conf`.

## Options envisagées

- La configuration de production vit dans Coolify, hors du dépôt
- Un `compose.production.yaml` versionné, appliqué à la main
- Infrastructure as code complète — Terraform ou équivalent devant Coolify

## Décision

Retenu : **la configuration de production reste dans Coolify**, parce que la seule
alternative honnête serait de la piloter depuis le dépôt, ce qu'aucun outil du projet ne
fait aujourd'hui.

### Conséquences

- Bien : une seule source appliquée, pas de fichier versionné qui ment sur l'état réel.
- Mal : **deux configurations de routage doivent rester cohérentes à la main** — celle de
  Traefik dans `compose.yaml` et celle de Coolify. Rien ne signale leur divergence.
- Mal : le déploiement n'est pas reproductible depuis le dépôt seul. Reconstruire
  l'environnement demande un accès à Coolify et de la mémoire.

### Condition de réouverture

Un second environnement à tenir (préproduction), ou un second déploiement à créer de zéro.
C'est le moment où la mémoire cesse de suffire et où le coût de l'infrastructure as code
devient inférieur à celui de l'oubli.

## Pour aller plus loin

- `compose.yaml` — la seule pile réellement décrite par le dépôt
- ADR-0009 et ADR-0023 — les variables dont l'absence ne se voit pas au démarrage
