---
status: accepté
date: 2026-08-17
decision-makers: Rémy Theroux
---

# Le profil se lit par identifiant, jamais par email

## Contexte et problème

Le jeton d'accès porte `sub` — l'UUID du compte — et rien d'autre : pas d'email, pas
d'`iss`. `GET /api/profile` doit donc choisir par quoi recharger le compte. Une query
`FindUserByEmail` existe déjà (ADR-0004), sans consommateur.

## Facteurs de décision

- L'UUID est immuable ; l'email ne l'est pas, et ne le sera pas le jour où changer
  d'adresse deviendra possible.
- C'est l'identifiant que le jeton porte : chercher par email quand on détient un UUID
  demanderait une lecture de plus pour retrouver ce qu'on a déjà.
- Le JWT ne porte pas l'email précisément pour ne pas le faire voyager à chaque requête.

## Options envisagées

- Lire par identifiant, via `FindUserById`
- Ajouter l'email au JWT et lire par email, via `FindUserByEmail`

## Décision

Retenu : **lire par identifiant**. Chercher par email quand on détient un UUID immuable
serait un contresens, et ajouter l'email au jeton le ferait voyager sans nécessité.

Un jeton bien signé dont le compte a disparu répond `401`, pas `404` : il n'identifie plus
personne, et le front n'a ainsi qu'un seul cas d'échec à traiter.

### Conséquences

- Bien : le jeton reste minimal, et le profil suit le compte même si son adresse change.
- Bien : un seul code d'échec côté front pour « jeton inexploitable ».
- Mal : `FindUserByEmail` reste sans consommateur — c'est le sujet d'ADR-0004.

### Condition de réouverture

Aucune prévue. Un besoin de chercher un compte par email viendra d'ailleurs (une
administration, une invitation), pas du profil.

## Pour aller plus loin

- `users/infrastructure/web/` — le contrôleur du profil et `JwtSubject`
- ADR-0004 — `FindUserByEmail` est livrée sans écran
