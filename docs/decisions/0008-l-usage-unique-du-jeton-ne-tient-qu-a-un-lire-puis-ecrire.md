---
status: accepté
date: 2026-08-06
decision-makers: Rémy Theroux
---

# L'usage unique du jeton ne tient qu'à un lire-puis-écrire

## Contexte et problème

`VerificationToken` porte deux règles : expiration à 24 h et usage unique. La seconde est
appliquée par la consommation du jeton — on lit `consumed_at`, on refuse s'il est posé, on
le pose. `VerificationToken` n'a pas de `@Version`, et la migration ne pose aucune
contrainte sur `consumed_at`.

## Facteurs de décision

- Deux clics simultanés sur le même lien passeraient tous deux le contrôle avant que l'un
  ait commité. Le cas est réel : les clients mail pré-visitent les liens.
- Vérifier deux fois une adresse est **idempotent** : la seconde vérification n'ajoute rien
  et ne casse rien.
- Un verrou optimiste se paie à chaque écriture, y compris sur les 99,9 % de cas où aucune
  concurrence n'existe.

## Options envisagées

- Laisser le lire-puis-écrire, sans garantie de la base
- `@Version` sur `VerificationToken` — verrou optimiste
- Un `UPDATE … WHERE consumed_at IS NULL` conditionnel, dont on lit le nombre de lignes

## Décision

Retenu : **laisser le lire-puis-écrire**, parce que l'invariant qu'il ne tient pas ne
protège aujourd'hui rien d'observable.

L'invariant n'est donc pas tenu par la base : il est tenu par le fait que le violer est
sans effet.

### Conséquences

- Bien : aucun coût sur le chemin nominal, aucune colonne technique de plus.
- Mal : « usage unique » est écrit dans le domaine et n'est pas garanti par le stockage.
  Un lecteur qui fait confiance à l'intitulé se trompera.

### Condition de réouverture

**Le jour où ce modèle de jeton ouvre une action non idempotente** — réinitialiser un mot
de passe, consommer un crédit, accepter une invitation. Il faudra alors l'`UPDATE`
conditionnel, qui est la parade la moins chère des deux : une requête, pas une colonne.

## Pour aller plus loin

- `users/domain/entity/VerificationToken.java`
- ADR-0007 — le jeton de vérification voyage en query string
