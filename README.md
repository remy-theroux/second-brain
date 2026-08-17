# Second Brain

API Java / Spring Boot. Environnement de développement 100 % conteneurisé — **aucun JDK requis sur votre machine**.

## Stack

| | |
|---|---|
| Langage | Java 25 (LTS) |
| Framework | Spring Boot 4.0 (Spring Framework 7) |
| Build | Gradle (Kotlin DSL) + version catalog |
| Base de données | PostgreSQL 17 |
| Migrations | Flyway (SQL versionné) |
| Sécurité | Spring Security (aucune authentification pour l'instant) |
| Doc API | springdoc-openapi / Swagger UI |
| Tests | JUnit 5 + Testcontainers |

## Prérequis

- Docker + Docker Compose

## Démarrage rapide

```bash
cp .env.example .env          # ajuster si besoin
docker compose up --build     # démarre PostgreSQL, Adminer et l'app (hot reload)
```

Au premier lancement, le wrapper Gradle télécharge Gradle puis le JDK 25 (toolchain) — c'est un peu long, ensuite c'est mis en cache.

| Service | URL |
|---|---|
| Accueil | http://localhost:8080/ |
| Health | http://localhost:8080/actuator/health |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Adminer (UI base) | http://localhost:8081 (serveur `db`, base/user/mdp `second_brain`) |
| Mailpit (mails capturés en dev) | http://localhost:8025 — aucun mail ne sort de la machine |

### Hot reload

La source est montée dans le conteneur `app`. Deux processus tournent en parallèle
(voir `docker/dev-entrypoint.sh`) : un compilateur Gradle en continu (`-t classes`) qui
met à jour `build/classes`, et `bootRun` dont Spring Boot DevTools surveille ce dossier.
Modifiez un fichier `.java` : recompilation puis redémarrage automatique en < 1 s
(visible dans `docker compose logs -f app`).

## Tests

Les tests d'intégration démarrent une PostgreSQL jetable via Testcontainers :

```bash
# Sans JDK local, via un conteneur Gradle :
docker run --rm -v "$PWD":/app -w /app -v /var/run/docker.sock:/var/run/docker.sock \
  gradle:jdk25 gradle --no-daemon test
```

Pour lancer l'app depuis l'IDE sur une base Testcontainers : exécuter `TestSecondBrainApplication`.

## Build de production

Image OCI optimisée (multi-stage, layers, JRE non-root) :

```bash
docker build -t second-brain:latest .
docker run --rm -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://<host>:5432/second_brain \
  -e SPRING_DATASOURCE_USERNAME=... -e SPRING_DATASOURCE_PASSWORD=... \
  -e SPRING_MAIL_HOST=... -e SPRING_MAIL_PORT=... \
  -e SECONDBRAIN_BASE_URL=https://<domaine-public> \
  -e SECONDBRAIN_NOTIFICATION_FROM=no-reply@<domaine-public> \
  second-brain:latest
```

Variables d'environnement lues par l'application (docker compose les fixe déjà pour le
développement, voir `compose.yaml`) :

| Variable | Rôle | Défaut |
|---|---|---|
| `SPRING_DATASOURCE_URL` / `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | Connexion PostgreSQL | — |
| `SPRING_MAIL_HOST` / `SPRING_MAIL_PORT` | Relais SMTP pour les mails de vérification | `localhost:1025` (Mailpit en dev) |
| `SECONDBRAIN_BASE_URL` | URL publique écrite dans les liens des mails envoyés | `http://localhost:8080` — **à définir en production**, sinon les liens de vérification pointent vers localhost |
| `SECONDBRAIN_NOTIFICATION_FROM` | Adresse d'expéditeur des mails de vérification | `no-reply@second-brain.localhost` |

Alternative sans Dockerfile (Cloud Native Buildpacks / Paketo) :

```bash
./gradlew bootBuildImage
```

## Architecture

Hexagonale, un dossier par couche et par bounded context :

- **domain** — le cœur métier : `entity/` (agrégats), `valueobject/` (valeurs validées et
  normalisées), `port/` (les interfaces vers l'extérieur), `exception/`, et les règles pures
  à la racine. Ne dépend de rien d'autre que du JDK, aux annotations JPA de l'entité près.
- **application** — une commande ou une query par intention, avec son handler. Aucune
  logique métier : le handler orchestre le domaine.
- **infrastructure** — les **adapters** qui implémentent les ports (JPA, hachage), le
  mapping des types du domaine vers les colonnes, et les adapters entrants (contrôleurs web).

CQRS minimal : `CommandBus.dispatch` pour écrire, `QueryBus.ask` pour lire. Les deux sont
synchrones et routent vers un handler unique, résolu au démarrage par son type générique.

**La transaction SQL est portée par le `CommandBus`** : `dispatch` est `@Transactional`,
donc tout le handler s'exécute dans une seule transaction et la moindre exception annule
l'ensemble. Corollaire : **ne jamais annoter un handler avec `@Transactional`**.

## Notes de version

- Spring Boot **4.0.7** (ligne 4.0 supportée jusqu'à fin 2026). Une montée vers 4.1.x est
  possible ultérieurement — ajuster `springBoot` dans `gradle/libs.versions.toml` et
  vérifier la compatibilité springdoc.
- Hibernate valide le schéma (`ddl-auto: validate`) : **Flyway est maître du schéma**.
  Toute évolution de table passe par une nouvelle migration `V2__...sql`.
