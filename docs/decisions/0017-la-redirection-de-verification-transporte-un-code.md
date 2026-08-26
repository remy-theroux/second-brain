---
status: accepté
date: 2026-08-18
decision-makers: Rémy Theroux
---

# La redirection de vérification transporte un code, pas un message

## Contexte et problème

`GET /verification` ne rend plus de page : elle répond `302` vers
`/login?verification=<code>`, où le code vaut `ok`, `lien-invalide`, `lien-expire` ou
`lien-deja-utilise`. Le front porte la rédaction française correspondante
(`VERIFICATION_MESSAGES` dans `LoginView.vue`).

C'est la seule entorse à la règle « les messages d'erreur viennent du serveur et
s'affichent tels quels ».

## Facteurs de décision

- Le `Location` est relatif, donc résolu par le navigateur : l'application n'a aucune URL
  de front à connaître.
- Un message en clair dans une query string atterrit dans l'historique du navigateur et
  dans les logs du proxy — exactement le reproche fait au jeton lui-même (ADR-0007).
- Les libellés de refus existent déjà côté domaine, dans les exceptions.

## Options envisagées

- Un code dans la query string, traduit par le front
- Le message rédigé dans la query string
- Le serveur rend à nouveau une page HTML de retour de vérification

## Décision

Retenu : **un code**, parce que c'est la seule option qui ne fasse pas voyager de prose
dans une URL journalisée, sans remettre du rendu HTML côté serveur — ce que la migration
vers le front Vue venait précisément de supprimer.

### Conséquences

- Bien : rien de lisible ne traîne dans les logs du proxy, et le back ignore tout de la
  rédaction.
- Mal : **les libellés existent en deux endroits** — les exceptions du domaine
  (`InvalidVerificationLinkException` et ses sœurs) et `VERIFICATION_MESSAGES` dans
  `LoginView.vue`. Ils peuvent diverger sans qu'aucun test ne le voie.

Les trois façons de présenter un lien inexploitable — UUID illisible, compte inconnu, jeton
faux — partagent volontairement un seul code : les distinguer ferait de la route un oracle
d'existence de compte.

### Condition de réouverture

Un troisième endroit à traduire, ou une divergence constatée. Le remède serait alors une
liste de codes partagée, générée depuis l'énumération du back — un ticket, pas une
retouche.

## Pour aller plus loin

- `frontend/src/views/LoginView.vue`, `users/domain/exception/`
- ADR-0022 — la même nature de duplication, côté documents
