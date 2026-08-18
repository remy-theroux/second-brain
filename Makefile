# Commandes de qualité et de build, back et front.
#
# Il n'y a ni JDK ni Node sur la machine hôte : chaque cible délègue à un conteneur. Ce
# fichier fige les deux invocations `docker run` décrites dans CLAUDE.md, qu'il fallait
# jusqu'ici redéfinir en fonction shell à chaque session.
#
# ATTENTION : ces cibles ne cohabitent pas avec `docker compose up`. Les deux verrouillent
# le `.gradle/` du répertoire, et Gradle échoue sur « Timeout waiting to lock Build Output
# Cleanup Cache ». Arrêter la pile (`docker compose down`) avant `make check` ou `make build`.

# Le volume nommé conserve le cache Gradle et le JDK 25 de la toolchain d'un lancement à
# l'autre. Surchargeable (`make GRADLE_HOME_VOLUME=… `) pour qu'un worktree ait le sien :
# deux répertoires qui partagent ce volume se disputent le même verrou.
GRADLE_HOME_VOLUME ?= second-brain-gradle-home

# --network host est obligatoire : Testcontainers démarre PostgreSQL en conteneur frère et
# s'y connecte via localhost:<port mappé>.
GRADLE := docker run --rm \
	--network host \
	-v "$(CURDIR)":/app -w /app \
	-v /var/run/docker.sock:/var/run/docker.sock \
	-v $(GRADLE_HOME_VOLUME):/home/gradle/.gradle \
	gradle:jdk25 gradle --no-daemon

# -u et HOME=/tmp sont obligatoires : sans eux, npm écrit node_modules/ et
# package-lock.json en root dans le dépôt monté.
NPM := docker run --rm \
	-u "$(shell id -u):$(shell id -g)" -e HOME=/tmp \
	-v "$(CURDIR)/frontend":/app -w /app \
	node:24-alpine npm

.DEFAULT_GOAL := help

.PHONY: help format format-back format-front check check-back check-front build build-back build-front

help: ## Liste les cibles disponibles
	@grep -hE '^[a-z-]+:.*?## ' $(MAKEFILE_LIST) \
		| awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-14s\033[0m %s\n", $$1, $$2}'

## --- Formatage -------------------------------------------------------------

format: format-back format-front ## Formate le back et le front

format-back: ## Formate le Java (google-java-format, style AOSP)
	$(GRADLE) spotlessApply

format-front: frontend/node_modules ## Formate le front (prettier)
	$(NPM) run format

## --- Qualité ---------------------------------------------------------------

check: check-back check-front ## Vérifie le formatage et lance les tests, des deux côtés

check-back: ## Formatage Java vérifié, puis la suite de tests
	$(GRADLE) spotlessCheck test

check-front: frontend/node_modules ## Formatage du front vérifié, puis les tests unitaires
	$(NPM) run format:check
	$(NPM) run test:unit

## --- Build -----------------------------------------------------------------

# `gradle build` déclenche `check`, donc `spotlessCheck` et `test` : build-back recouvre
# check-back. C'est voulu — `build` est exactement ce que lance la CI.
build: build-back build-front ## Construit les deux artefacts (= ce que vérifie la CI)

build-back: ## Compile, vérifie et produit le jar
	$(GRADLE) build

build-front: frontend/node_modules ## Produit frontend/dist
	$(NPM) run build

# Seule cible non-.PHONY : elle correspond à un vrai répertoire, réinstallé quand le lock
# change. Sans elle, `prettier` et `vitest` n'existent pas dans le conteneur.
frontend/node_modules: frontend/package-lock.json
	$(NPM) ci
	@touch $@
