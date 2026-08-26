---
status: accepté
date: 2026-08-17
decision-makers: Rémy Theroux
---

# `/api/profile` sérialise directement le modèle de lecture

## Contexte et problème

`GET /api/profile` retourne `UserView` — le modèle de lecture de la query — tel quel, sans
record de réponse intermédiaire dans `infrastructure/web/`. La forme de l'API est donc
celle de la query, y compris pour `createdAt`.

## Facteurs de décision

- `UserView` est déjà une projection dédiée aux écrans : elle n'expose ni l'empreinte du
  mot de passe ni rien de l'agrégat. Le travail de filtrage est fait.
- Un record de réponse identique à la projection, champ pour champ, est un fichier qui ne
  sert qu'à recopier.
- Le couplage n'a de coût que le jour où les deux formes doivent diverger.

## Options envisagées

- Sérialiser `UserView` directement
- Un record de réponse dans `infrastructure/web/`, alimenté depuis `UserView`

## Décision

Retenu : **sérialiser `UserView` directement**, parce que la projection est déjà taillée
pour l'écran et qu'un intermédiaire identique n'apporterait qu'un fichier de plus.

### Conséquences

- Bien : une seule forme à faire évoluer tant qu'API et écran demandent la même chose.
- Mal : un champ ajouté à `UserView` pour un besoin de lecture interne apparaît
  immédiatement dans la réponse publique de l'API. Rien ne l'empêche, aucun test ne le voit.

### Condition de réouverture

Le jour où l'API et l'écran divergent — un champ utile à l'un et pas à l'autre, ou un nom
que l'API doit figer alors que la projection bouge. Il faudra alors un record de réponse
dans `infrastructure/web/`, et ce jour-là seulement.

## Pour aller plus loin

- `users/application/query/UserView.java`, `users/infrastructure/web/`
