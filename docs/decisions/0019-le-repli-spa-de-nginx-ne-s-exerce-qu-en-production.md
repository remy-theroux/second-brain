---
status: accepté
date: 2026-08-18
decision-makers: Rémy Theroux
---

# Le repli SPA de nginx ne s'exerce qu'en production

## Contexte et problème

En production, le front est servi par nginx depuis `dist`, et `frontend/nginx.conf` pose un
`try_files` : sans lui, un `F5` sur `/login` rend un 404. En développement, c'est Vite qui
sert — et Vite sert `index.html` sur toute route inconnue par construction. Le repli n'est
donc exercé par aucun environnement avant la production.

## Facteurs de décision

- Faire tourner nginx en développement remplacerait le rechargement à chaud par un build à
  chaque modification : c'est payer tous les jours pour un contrôle utile deux fois.
- Une erreur dans `nginx.conf` ne casse qu'une chose, connue et reproductible : le
  rafraîchissement sur une route profonde.
- Le contrôle manuel tient en deux commandes.

## Options envisagées

- Vite en développement, nginx en production seulement, contrôle manuel du repli
- nginx en développement aussi, pour exercer le même chemin
- Un test automatisé qui construit l'image et interroge `/login`

## Décision

Retenu : **Vite en développement, contrôle manuel**. Le contrôle se fait après toute
modification de `nginx.conf` :

```bash
docker build -t second-brain-frontend ./frontend
# puis un curl sur /login, qui doit rendre index.html et non un 404
```

### Conséquences

- Bien : le rechargement à chaud est conservé, et c'est lui qui fait le rythme de travail
  du front.
- Mal : une erreur dans `nginx.conf` ne se voit qu'une fois déployée, sur un chemin que
  personne n'emprunte avant un rafraîchissement.

### Condition de réouverture

Une seconde occurrence du symptôme, ou toute complexification de `nginx.conf` au-delà du
`try_files` — en-têtes de sécurité, compression, cache. Le test automatisé (construire
l'image, interroger `/login`) devient alors dû ; il est écarté aujourd'hui parce qu'il
ferait construire une image à chaque exécution de la CI front.

## Pour aller plus loin

- `frontend/nginx.conf`, `frontend/Dockerfile`
