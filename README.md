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
| API | http://localhost:8080 |
| Health | http://localhost:8080/actuator/health |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Adminer (UI base) | http://localhost:8081 (serveur `db`, base/user/mdp `second_brain`) |

### Hot reload

La source est montée dans le conteneur `app`. Deux processus tournent en parallèle
(voir `docker/dev-entrypoint.sh`) : un compilateur Gradle en continu (`-t classes`) qui
met à jour `build/classes`, et `bootRun` dont Spring Boot DevTools surveille ce dossier.
Modifiez un fichier `.java` : recompilation puis redémarrage automatique en < 1 s
(visible dans `docker compose logs -f app`).

### Authentification

Aucune. L'authentification HTTP Basic de départ a été retirée : tous les endpoints
sont publics en attendant le ticket « login ».

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
  second-brain:latest
```

Alternative sans Dockerfile (Cloud Native Buildpacks / Paketo) :

```bash
./gradlew bootBuildImage
```

## Structure

```
src/main/java/xyz/sterenn/secondbrain/
├── SecondBrainApplication.java
└── config/            # SecurityConfig, OpenApiConfig
src/main/resources/
├── application.yml            # config commune (pilotée par variables d'env)
├── application-dev.yml        # profil dev
└── db/migration/              # migrations Flyway
```

(Ce bloc sera complété au fil des tâches suivantes.)

## Notes de version

- Spring Boot **4.0.7** (ligne 4.0 supportée jusqu'à fin 2026). Une montée vers 4.1.x est
  possible ultérieurement — ajuster `springBoot` dans `gradle/libs.versions.toml` et
  vérifier la compatibilité springdoc.
- Hibernate valide le schéma (`ddl-auto: validate`) : **Flyway est maître du schéma**.
  Toute évolution de table passe par une nouvelle migration `V2__...sql`.
