---
status: accepté
date: 2026-08-04
decision-makers: Rémy Theroux
---

# `FindUserByEmail` est livrée sans écran, comme gabarit du query bus

## Contexte et problème

Le query bus a été posé en même temps que le command bus, au premier ticket. Un bus sans
aucun message qui l'emprunte n'est pas un bus testé : sa table de routage, sa résolution de
type générique et sa transaction en lecture seule ne seraient exercées par rien.

## Facteurs de décision

- Un socle livré non exercé est un socle qui casse au premier usage réel, loin du commit
  qui l'a introduit.
- Le contexte `users` sert de gabarit aux contextes suivants : il doit montrer une query
  complète, de la classe `Query<R>` au modèle de lecture.

## Options envisagées

- Livrer le query bus sans aucune query, en attendant le premier besoin
- Livrer `FindUserByEmail` avec son handler et son `UserView`, sans écran qui la consomme
- Ne poser le query bus qu'au ticket qui en aura besoin

## Décision

Retenu : **livrer `FindUserByEmail` sans consommateur**. Elle existe pour que le query bus
soit livré testé, et elle sert de gabarit à toute query ultérieure.

### Conséquences

- Bien : le bus, sa résolution générique et son modèle de lecture sont exercés dès le
  premier commit.
- Mal : du code de production sans appelant. Un outil de couverture le signalera, et un
  lecteur pressé le supprimera.

### Condition de réouverture

Si une query réellement consommée couvre le même terrain — c'est-à-dire si un écran a un
jour besoin de chercher un compte par email —, `FindUserByEmail` cesse d'être un gabarit et
redevient du code ordinaire. Voir ADR-0015 : ce n'est pas le chemin pris par le profil.

## Pour aller plus loin

- `users/application/query/FindUserByEmail.java`
- ADR-0015 — le profil se lit par identifiant, jamais par email
