---
name: worktree
description: Utiliser quand il faut travailler sur plusieurs features de second-brain en parallèle, isoler une feature du worktree courant, ou lancer une deuxième pile docker compose sans arrêter celle qui tourne déjà — création du worktree, de ses ports et de sa pile Docker dédiée.
---

# Worktree isolé et pile Docker dédiée

## Le principe

Un worktree git suffit à isoler le **code**. Il ne suffit pas à isoler l'**application qui
tourne** : `compose.yaml` publie quatre ports hôte et nomme sa pile. Deux worktrees qui
font `docker compose up` sans précaution se battent pour le 8080, le 5432, et surtout pour
les mêmes conteneurs.

Ce skill crée les deux à la fois : le worktree, et le bloc de ports qui lui appartient.

## Quand l'utiliser

- Démarrer une feature pendant qu'une autre est en cours et doit rester démarrée.
- Reprendre une branche existante (`feat/add-dataset`, `feat/confirm-user`) sans toucher
  au worktree courant.
- Comparer deux implémentations côte à côte dans le navigateur.

**Ne pas l'utiliser** pour un correctif d'une ligne sur la branche courante : un worktree
coûte une pile Docker complète, soit ~2 Go de RAM.

## Créer

Depuis le **dépôt principal** (`~/projects/second-brain`), une seule commande :

```bash
.claude/skills/worktree/scripts/create-worktree.sh add-dataset
```

L'argument est un nom de feature (`add-dataset` → branche `feat/add-dataset`) ou un nom de
branche complet s'il contient une barre oblique (`fix/token-expire`, repris tel quel).

Le script attache la branche si elle existe en local, la suit si elle n'existe que sur
`origin`, la crée depuis `main` sinon. Il écrit le récapitulatif à lire : chemin, ports,
et les fonctions `gtest`/`gfront` à coller pour ce worktree.

Puis, dans le worktree :

```bash
docker compose up --build -d
```

Le premier démarrage est long : chaque pile a son propre cache Gradle et son propre
`node_modules`. Les partager entre worktrees rejouerait le « Timeout waiting to lock »
documenté dans `CLAUDE.md`.

## Ce que le script décide, et ce qu'il ne faut pas défaire

| Ce qu'il pose | Pourquoi |
|---|---|
| `STACK_SUFFIX=-<slug>` dans le `.env` | Nomme le projet compose, donc sépare conteneurs, réseau et volumes |
| Un bloc de ports d'indice N : `8080+N`, `5432+N`, `1025+N`, `8025+N` | Un seul décalage à retenir par feature |
| Le `.env` généré depuis `.env.example` | Tout ajout au modèle atterrit dans les worktrees suivants |

**Le `.env` d'un worktree fait foi une fois écrit.** Le script ne réalloue jamais un bloc
déjà réservé par un `.env` existant, même si sa pile est arrêtée. Éditer ces ports à la
main casse cette garantie : deux worktrees peuvent alors se voir attribuer le même bloc.

## Pièges

| Symptôme | Cause |
|---|---|
| `localhost:8081` sert l'application de l'autre feature | La contrainte Traefik de `compose.yaml` a été retirée ou la pile n'a pas été recréée après sa modification (`docker compose up -d --force-recreate proxy`) |
| `git worktree add` refuse : *branch already checked out* | La branche est déjà ouverte dans un autre worktree — `git worktree list` |
| `Timeout waiting to lock Build Output Cleanup Cache` | Un `gtest` tourne pendant que la pile du **même** worktree est up, ou deux `gtest` partagent le volume `second-brain-gradle-home` au lieu du volume suffixé |
| `git worktree remove` : *Permission denied* | `build/`, `.gradle/` et `frontend/node_modules` appartiennent à `root`, écrits par le conteneur app — voir « Supprimer » |
| Le lien de vérification d'un mail pointe sur le mauvais port | La pile a été démarrée sans son `.env` — vérifier `docker compose config \| grep SECONDBRAIN_BASE_URL` |

## Supprimer

Volontairement manuel : `down -v` détruit une base de données, ce n'est pas une opération
à déclencher par un script qu'on lance vite.

```bash
cd ../second-brain-<slug>
docker compose down -v          # conteneurs ET volumes : la base, le cache Gradle, node_modules
cd -

# Le conteneur app tourne en root dans le bind mount et y laisse des répertoires que
# l'utilisateur ne peut pas effacer. Sans cette étape, git worktree remove échoue sur
# « Permission denied ».
docker run --rm -v "$PWD/../second-brain-<slug>":/w alpine \
  sh -c 'rm -rf /w/.gradle /w/.gradle-cache /w/build /w/frontend/node_modules'

git worktree remove ../second-brain-<slug>
git branch -d feat/<slug>       # seulement si la branche est fusionnée
```

Sans `-v`, les volumes de la pile survivent au worktree et un futur worktree homonyme
repartirait sur son ancienne base. Si `git worktree remove` a déjà échoué à mi-chemin,
`rm -rf` le répertoire puis `git worktree prune` rattrapent l'état.
