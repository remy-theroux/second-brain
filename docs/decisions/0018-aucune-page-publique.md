---
status: accepté
date: 2026-08-18
decision-makers: Rémy Theroux
---

# Aucune page publique

## Contexte et problème

La migration vers le front Vue a supprimé les vues rendues par le serveur, dont la page
d'accueil. Un visiteur anonyme est désormais renvoyé sur `/login`, qui porte le lien vers
l'inscription. C'est tout ce qu'il peut voir de l'application.

## Facteurs de décision

- Une page d'accueil recréée « parce qu'il en faut une » n'a rien à dire : le projet est un
  outil personnel, pas un produit qui se présente.
- Une page publique est une surface à maintenir, à traduire et à tenir à jour.
- La recréer par réflexe au premier passage sur le routeur est un risque réel — c'est le
  genre de manque qui se comble sans qu'on décide.

## Options envisagées

- Aucune page publique : `/login` est la porte d'entrée
- Une page d'accueil de présentation
- Une page d'accueil qui redirige selon l'état de connexion

## Décision

Retenu : **aucune page publique**. Le garde de route renvoie tout visiteur anonyme sur
`/login`.

### Conséquences

- Bien : une seule porte d'entrée, un seul écran à tenir, aucun contenu à rédiger.
- Mal : rien à montrer à qui découvre l'URL. Acceptable tant que personne ne la découvre.

### Condition de réouverture

Le jour où il y a quelque chose à dire à un visiteur. Ce sera un ticket, avec un contenu
décidé — pas une page d'accueil recréée par réflexe parce qu'un routeur a l'air incomplet
sans elle.

## Pour aller plus loin

- `frontend/src/router/index.js` — le garde d'authentification
