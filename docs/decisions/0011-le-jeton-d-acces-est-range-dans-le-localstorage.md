---
status: accepté
date: 2026-08-17
decision-makers: Rémy Theroux
---

# Le jeton d'accès est rangé dans le `localStorage`

## Contexte et problème

Le front doit conserver le jeton d'accès entre deux chargements de page — sans quoi
« maintenir une connexion » n'a aucun sens, et un `F5` déconnecte. Aucun cookie
d'authentification n'existe (ADR-0003) : c'est donc au JavaScript de le ranger quelque
part.

## Facteurs de décision

- Ce qui survit à un rafraîchissement de page et reste lisible par le front se résume à
  `localStorage` et `sessionStorage`.
- Tout ce que le front peut lire, une faille XSS dans le front peut le lire aussi.
- `sessionStorage` disparaît à la fermeture de l'onglet : il ne tient pas la promesse.
- La parade sérieuse — cookie `httpOnly` `Secure` `SameSite` plus jeton de
  rafraîchissement, donc CSRF à réactiver — est un ticket entier, pas une ligne.

## Options envisagées

- `localStorage`
- `sessionStorage`
- Variable en mémoire seulement, perdue à chaque rafraîchissement
- Cookie `httpOnly` posé par le serveur, avec jeton de rafraîchissement

## Décision

Retenu : **`localStorage`**, sous les clés `second-brain.access-token` et
`second-brain.access-token-expiration`, parce que c'est la seule option qui tient la
promesse fonctionnelle sans ouvrir le chantier complet du cookie.

### Conséquences

- Bien : la connexion survit à un rafraîchissement et à la fermeture d'un onglet.
- Bien : le garde de route peut décider hors ligne, sans appel au serveur.
- Mal : une faille XSS dans le front donne le jeton, et donc l'heure de session qu'il
  reste.

Deux garde-fous limitent la portée sans la supprimer : la durée de vie d'une heure
(ADR-0010) et la règle « le serveur fait autorité » — un `401` déconnecte, quoi qu'en pense
le navigateur.

### Condition de réouverture

Le passage au cookie `httpOnly`. Il rouvre ADR-0003 (CSRF redevient obligatoire) et
ADR-0010 (le rafraîchissement arrive avec) : les trois se traitent en un seul ticket, ou
pas du tout.

## Pour aller plus loin

- `frontend/src/stores/auth.js`
- ADR-0003, ADR-0010
