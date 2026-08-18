#!/usr/bin/env bash
# Crée un worktree isolé et sa pile Docker dédiée.
#
# Usage : scripts/create-worktree.sh <feature>
#   <feature> vaut « add-dataset » (la branche sera feat/add-dataset) ou un nom de branche
#   complet contenant une barre oblique (« fix/token-expire »), repris tel quel.
#
# À lancer depuis le dépôt principal. Le worktree naît dans ../second-brain-<slug>.

set -euo pipefail

# --- Arguments -------------------------------------------------------------------------

if [ $# -ne 1 ]; then
  echo "Usage : $0 <feature>" >&2
  exit 2
fi

argument=$1
case "$argument" in
  */*) branch=$argument ;;
  *)   branch="feat/$argument" ;;
esac
slug=$(printf '%s' "${branch##*/}" | tr '[:upper:]' '[:lower:]' | tr -c 'a-z0-9-' '-' | sed 's/-\{2,\}/-/g; s/^-//; s/-$//')

if [ -z "$slug" ]; then
  echo "Nom de feature inexploitable : « $argument »." >&2
  exit 1
fi

# --- Le dépôt principal, et lui seul ---------------------------------------------------

git_dir=$(cd "$(git rev-parse --git-dir)" && pwd -P)
git_common=$(cd "$(git rev-parse --git-common-dir)" && pwd -P)
if [ "$git_dir" != "$git_common" ]; then
  echo "Tu es déjà dans un worktree lié. Reviens dans $git_common/.. pour en créer un autre." >&2
  exit 1
fi

repo_root=$(git rev-parse --show-toplevel)
cd "$repo_root"
worktree_path=$(cd .. && pwd -P)/second-brain-$slug

if [ -e "$worktree_path" ]; then
  echo "$worktree_path existe déjà." >&2
  exit 1
fi

# --- Allocation du bloc de ports -------------------------------------------------------
#
# Le bloc d'indice N donne 8080+N, 5432+N, 1025+N, 8025+N. L'indice 0 est celui du dépôt
# principal. On refuse un indice dont un seul port est occupé sur la machine, et un indice
# déjà réservé par le .env d'un worktree existant — même arrêté, sa pile lui appartient.

listening=$(ss -ltnH 2>/dev/null | awk '{print $4}' | sed 's/.*://' | sort -u || true)

claimed=""
while IFS= read -r line; do
  case "$line" in
    worktree\ *)
      env_file="${line#worktree }/.env"
      if [ -f "$env_file" ]; then
        port=$(sed -n 's/^HTTP_PORT=\([0-9]\{1,\}\).*/\1/p' "$env_file" | head -1)
        [ -n "$port" ] && claimed="$claimed $((port - 8080))"
      fi
      ;;
  esac
done < <(git worktree list --porcelain)

index=""
for candidate in $(seq 1 40); do
  busy=false
  for reserved in $claimed; do
    [ "$candidate" = "$reserved" ] && busy=true
  done
  for port in $((8080 + candidate)) $((5432 + candidate)) $((1025 + candidate)) $((8025 + candidate)); do
    printf '%s\n' "$listening" | grep -qx "$port" && busy=true
  done
  if [ "$busy" = false ]; then index=$candidate; break; fi
done

if [ -z "$index" ]; then
  echo "Aucun bloc de ports libre entre les indices 1 et 40. Supprime un worktree." >&2
  exit 1
fi

# --- Le worktree -----------------------------------------------------------------------

if git show-ref --verify --quiet "refs/heads/$branch"; then
  echo "Branche $branch existante : le worktree s'y attache."
  git worktree add "$worktree_path" "$branch"
elif git show-ref --verify --quiet "refs/remotes/origin/$branch"; then
  echo "Branche $branch trouvée sur origin : le worktree la suit."
  git worktree add --track -b "$branch" "$worktree_path" "origin/$branch"
else
  echo "Branche $branch créée depuis main."
  git worktree add -b "$branch" "$worktree_path" main
fi

# --- Son .env --------------------------------------------------------------------------
#
# Généré depuis .env.example pour que tout ajout au modèle atterrisse ici aussi. Une fois
# écrit, ce fichier fait foi : les ports d'un worktree ne bougent plus de sa vie.

sed \
  -e "s/^STACK_SUFFIX=.*/STACK_SUFFIX=-$slug/" \
  -e "s/^HTTP_PORT=.*/HTTP_PORT=$((8080 + index))/" \
  -e "s/^DB_PORT=.*/DB_PORT=$((5432 + index))/" \
  -e "s/^MAILPIT_SMTP_PORT=.*/MAILPIT_SMTP_PORT=$((1025 + index))/" \
  -e "s/^MAILPIT_WEB_PORT=.*/MAILPIT_WEB_PORT=$((8025 + index))/" \
  .env.example > "$worktree_path/.env"

# --- Vérification : la branche sait-elle s'isoler ? ------------------------------------
#
# compose.yaml est versionné, donc une branche antérieure au support de STACK_SUFFIX fige
# `name: second-brain`. Démarrer sa pile ne créerait pas une seconde pile : docker compose
# la reconnaîtrait comme celle du dépôt principal et recréerait ses conteneurs. On ne le
# découvre pas après coup.

resolved=$(cd "$worktree_path" && docker compose config 2>/dev/null | sed -n 's/^name: //p' | head -1)
if [ "$resolved" != "second-brain-$slug" ]; then
  cat >&2 <<ALERTE

Worktree créé dans $worktree_path, mais SA PILE DOCKER N'EST PAS ISOLÉE.

  Nom de projet attendu   second-brain-$slug
  Nom de projet résolu    ${resolved:-<illisible>}

Le compose.yaml de $branch ne connaît pas STACK_SUFFIX : il précède ce mécanisme. Lancer
« docker compose up » depuis ce worktree adopterait la pile du dépôt principal et
recréerait ses conteneurs.

Le code est utilisable, la pile non. Pour l'isoler, reporter main sur la branche :

  cd $worktree_path && git merge main

ALERTE
  exit 1
fi

# --- Récapitulatif ---------------------------------------------------------------------

cat <<RECAP

Worktree prêt.

  Chemin      $worktree_path
  Branche     $branch
  Pile Docker second-brain-$slug
  Application http://localhost:$((8080 + index))
  Mailpit     http://localhost:$((8025 + index))
  PostgreSQL  localhost:$((5432 + index))

Démarrer la pile :

  cd $worktree_path && docker compose up --build -d

Les fonctions Gradle et Node de ce worktree — le volume de cache est dédié, deux gtest
qui partagent le même se bloquent mutuellement sur le verrou du cache :

  gtest() {
    docker run --rm --network host \\
      -v "\$PWD":/app -w /app \\
      -v /var/run/docker.sock:/var/run/docker.sock \\
      -v second-brain-gradle-home-$slug:/home/gradle/.gradle \\
      gradle:jdk25 gradle --no-daemon "\$@"
  }

  gfront() {
    docker run --rm -u "\$(id -u):\$(id -g)" -e HOME=/tmp \\
      -v "\$PWD/frontend":/app -w /app \\
      node:24-alpine "\$@"
  }

RECAP
