---
status: accepté
date: 2026-08-25
decision-makers: Rémy Theroux
---

# Aucun test de rendu : `/design-system` tient lieu de contrôle

## Contexte et problème

Aucune vue ni aucun composant de `frontend/src/components/` n'est couvert par un test de
rendu. Le front est testé — store d'authentification, garde de route, traduction des
réponses d'erreur dans `src/api/` — mais rien ne rend un composant.

## Facteurs de décision

- On teste ce qui **casse silencieusement**. Un formulaire cassé se voit au premier
  passage ; une session qui ne s'invalide pas ou un `422` mal lu, non.
- `@vue/test-utils` et un DOM simulé ajoutent une dépendance, un temps de suite et une
  couche de faux positifs, pour affirmer des choses qu'un coup d'œil affirme mieux.
- Encore faut-il que le coup d'œil ait un lieu : sans page qui rassemble les composants,
  « passer voir » veut dire naviguer dans l'application et espérer croiser tous les états.

## Options envisagées

- Tests de rendu avec `@vue/test-utils`
- Tests de bout en bout dans un navigateur — Playwright ou Cypress
- Aucun test de rendu, plus une page catalogue parcourue à la main

## Décision

Retenu : **aucun test de rendu, plus `/design-system`** — une page qui rend chaque token
avec sa valeur effective, lue par `getComputedStyle` et non recopiée, et chaque composant
partagé dans chacun de ses états. La route n'existe qu'en développement.

Conséquence directe : **le passage humain n'est pas facultatif**, c'est une condition avant
mise en production. Il a un lieu, et tout composant partagé ou token nouveau y apparaît
dans le même commit — un composant absent de la page n'est pas partagé.

### Conséquences

- Bien : tous les états se voient d'un coup, y compris ceux qu'un test aurait oublié
  d'écrire.
- Bien : aucune dépendance de test supplémentaire, suite front rapide.
- Mal : un gestionnaire d'événement mal relié ou un nom de champ mal orthographié passe au
  vert. `npm run build` compile les templates sans rien affirmer sur leur comportement.
- Mal : `LoginView`, `RegisterView` et `DocumentsView` restent à parcourir en plus, leur
  logique de soumission n'étant pas dans le catalogue — la dernière avec un vrai fichier :
  le dépôt, le doublon, le format refusé, la suppression.

### Condition de réouverture

Une régression de rendu constatée en production, ou l'arrivée d'un second contributeur —
le passage humain ne se délègue pas aussi bien qu'une suite de tests.

## Pour aller plus loin

- `frontend/src/views/DesignSystemView.vue`, `frontend/src/router/index.js`
- `.claude/rules/frontend.md`, sections « Composants partagés et design system » et « Tests »
