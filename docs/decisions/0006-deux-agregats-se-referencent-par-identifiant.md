---
status: accepté
date: 2026-08-06
decision-makers: Rémy Theroux
---

# Deux agrégats se référencent par identifiant, jamais par `@ManyToOne`

## Contexte et problème

`VerificationToken` appartient à un compte. JPA propose de l'exprimer par un
`@ManyToOne User`, ce qui donnerait un graphe d'objets navigable. La question se repose à
chaque nouvel agrégat qui en désigne un autre.

## Facteurs de décision

- Un agrégat est une frontière de cohérence. Deux agrégats distincts ne se modifient pas
  dans la même transaction, donc rien ne justifie de les charger ensemble.
- Une association JPA transforme une lecture innocente en cascade de requêtes, ou en
  `LazyInitializationException` hors transaction.
- La cohérence référentielle est le travail de la base, pas celui du graphe d'objets.

## Options envisagées

- `@ManyToOne User` sur `VerificationToken`
- Un champ `UUID accountId`, plus une clé étrangère en base

## Décision

Retenu : **un `UUID`, plus la clé étrangère**. Deux agrégats distincts ne se tiennent pas
par une association JPA ; la cohérence est garantie en base, pas par le graphe d'objets.

### Conséquences

- Bien : chaque agrégat se charge seul, se teste seul, et aucune lecture n'en traîne un
  autre derrière elle.
- Bien : la frontière d'agrégat se lit dans le type du champ.
- Mal : parcourir du jeton vers le compte demande une seconde requête explicite, par le
  port du compte.

### Condition de réouverture

Aucune prévue. Cette règle vaut pour tout nouvel agrégat du projet ; l'exception serait un
couple d'entités qui n'en forme en réalité qu'un seul — auquel cas la vraie correction est
de les fusionner, pas de les associer.

## Pour aller plus loin

- `users/domain/entity/VerificationToken.java`
- `.claude/rules/backend.md`, section « Domaine »
