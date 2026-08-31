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
| Sécurité | Spring Security (jeton d'accès JWT HS256, resource server) |
| Événements | RabbitMQ 4 (Spring AMQP) |
| Doc API | springdoc-openapi / Swagger UI |
| Tests | JUnit 5 + Testcontainers |
| Formatage | Spotless + palantir-java-format (back), Prettier (front) |
| Front | Vue 3 + Vite + vue-router + pinia (dossier frontend/, hors build Gradle) |
| Application | un processus API et un processus worker, même image, profil `worker` |

## Prérequis

- Docker + Docker Compose

## Démarrage rapide

```bash
cp .env.example .env          # ajuster si besoin
docker compose up --build     # démarre PostgreSQL, Mailpit, RabbitMQ, Ollama, l'app, le worker et le front
```

Au premier lancement, le wrapper Gradle télécharge Gradle puis le JDK 25 (toolchain) — c'est un peu long, ensuite c'est mis en cache.

Un service Traefik publie **un seul port** et route `/api` et `/verification` vers
l'application Java, tout le reste vers le front. Ni l'app ni le serveur Vite ne publient de
port.

| Service | URL |
|---|---|
| Application (front Vue) | http://localhost:8080/ |
| API | http://localhost:8080/api/… |
| Health | http://localhost:8080/actuator/health |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Mailpit (mails capturés en dev) | http://localhost:8025 — aucun mail ne sort de la machine |
| Console RabbitMQ (messages en dev) | http://localhost:15672 — `RABBITMQ_USER` / `RABBITMQ_PASSWORD` du `.env` (`second_brain` / `second_brain` par défaut) |

Health et Swagger ne sont routés qu'en développement : en production, le proxy n'expose que
`/api` et `/verification`.

Si l'application échoue au démarrage sur « Main class name has not been configured »,
`bootRun` a perdu la course contre la compilation continue et `build/classes` était encore
vide. Compiler une fois puis relancer :

```bash
docker compose run --rm --no-deps app ./gradlew --no-daemon classes
docker compose up -d app
```

### Hot reload

La source est montée dans le conteneur `app`. Deux processus tournent en parallèle
(voir `docker/dev-entrypoint.sh`) : un compilateur Gradle en continu (`-t classes`) qui
met à jour `build/classes`, et `bootRun` dont Spring Boot DevTools surveille ce dossier.
Modifiez un fichier `.java` : recompilation puis redémarrage automatique en < 1 s
(visible dans `docker compose logs -f app`).

## Qualité

Un `Makefile` regroupe les commandes de formatage, de test et de build, chacune déléguée à
un conteneur — il n'y a toujours ni JDK ni Node à installer :

```bash
make help      # liste les cibles
make format    # formate le Java (Spotless) et le front (Prettier)
make check     # vérifie le formatage, puis lance les tests des deux côtés
make build     # produit le jar et frontend/dist — ce que vérifie la CI
```

Les trois se déclinent en `-back` et `-front` (`make check-front`) pour n'en payer qu'un
côté. Le formatage est **bloquant en CI** des deux côtés : côté Java sans étape dédiée,
`spotlessCheck` étant accroché à la tâche `check` de Gradle.

Ces cibles ne cohabitent pas avec `docker compose up` : les deux verrouillent le `.gradle/`
du répertoire. Arrêter la pile avant `make check` ou `make build`.

## Tests

Les tests d'intégration démarrent une PostgreSQL jetable via Testcontainers :

```bash
# Sans JDK local, via un conteneur Gradle :
docker run --rm --network host -v "$PWD":/app -w /app -v /var/run/docker.sock:/var/run/docker.sock \
  gradle:jdk25 gradle --no-daemon test
```

Pour lancer l'app depuis l'IDE sur une base Testcontainers : exécuter `TestSecondBrainApplication`.

Tests du front (aucun Node requis sur l'hôte) :

```bash
docker run --rm -u "$(id -u):$(id -g)" -e HOME=/tmp \
  -v "$PWD/frontend":/app -w /app node:24-alpine npm run test:unit
```

## Build de production

Deux images indépendantes : l'API Java et le front statique.

Le front — build npm puis nginx servant `dist`, avec le repli SPA sans lequel un
rechargement de page sur `/login` rendrait 404 :

```bash
docker build -t second-brain-frontend:latest ./frontend
```

L'API — image OCI optimisée (multi-stage, layers, JRE non-root) :

```bash
docker build -t second-brain:latest .
docker run --rm -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://<host>:5432/second_brain \
  -e SPRING_DATASOURCE_USERNAME=... -e SPRING_DATASOURCE_PASSWORD=... \
  -e SPRING_MAIL_HOST=... -e SPRING_MAIL_PORT=... \
  -e SECONDBRAIN_BASE_URL=https://<domaine-public> \
  -e SECONDBRAIN_NOTIFICATION_FROM=no-reply@<domaine-public> \
  -e SECONDBRAIN_JWT_SECRET=... \
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
| `SECONDBRAIN_JWT_SECRET` | Secret de signature des jetons d'accès (HS256, 32 octets minimum) — **doit être généré aléatoirement**, jamais une phrase lisible : HS256 est symétrique, deviner ce secret permet de forger un jeton valide pour n'importe quel compte, silencieusement et durablement | **aucun** — l'application refuse de démarrer sans lui |
| `SPRING_RABBITMQ_HOST` | Broker des événements métier | `localhost` |
| `SPRING_RABBITMQ_PORT` | Broker des événements métier | `5672` |
| `SPRING_RABBITMQ_USERNAME` | Broker des événements métier — un utilisateur **dédié**, pas l'administrateur : l'application déclare son exchange, sa queue et son binding, puis publie et consomme, donc les permissions `configure` / `write` / `read` sur le vhost suffisent | `guest` — le compte par défaut de l'image, qui n'existe plus dès que le broker est créé avec ses propres identifiants |
| `SPRING_RABBITMQ_PASSWORD` | Broker des événements métier | `guest` |

Le worker se déploie **depuis la même image**, avec `SPRING_PROFILES_ACTIVE=worker` et les
mêmes variables que l'API (base, mail, stockage, secret JWT, RabbitMQ). Il n'expose aucun
port : ne pas lui donner de domaine. Sans lui, les documents déposés restent `PENDING`.

Le broker lui-même se crée avec ses propres identifiants (`RABBITMQ_DEFAULT_USER` /
`RABBITMQ_DEFAULT_PASS` sur l'image officielle) et **sans console de gestion exposée** —
l'image sans suffixe `-management` suffit. L'administrateur ainsi créé sert à créer
l'utilisateur dédié de l'application ; il n'est jamais celui que l'application utilise.

Le déploiement suppose un **reverse proxy devant les deux images**, qui route `/api` et
`/verification` vers l'API et tout le reste vers le front — c'est ce qui donne au navigateur
une origine unique, donc aucune configuration CORS à écrire. Il ne doit exposer ni
`/actuator` ni `/swagger-ui`. `SECONDBRAIN_BASE_URL` vaut alors l'origine publique de ce
proxy : c'est elle qui est écrite dans les liens des mails, et c'est elle que la redirection
de vérification résout.

Pour générer `SECONDBRAIN_JWT_SECRET` avant un déploiement :

```bash
openssl rand -base64 48
```

Alternative sans Dockerfile (Cloud Native Buildpacks / Paketo) :

```bash
./gradlew bootBuildImage
```

## Connexion

L'authentification se fait par jeton d'accès JWT :

```bash
curl -s -X POST http://localhost:8080/api/token \
  -d grant_type=password -d username=alice@exemple.fr -d password=<mot de passe>
# {"access_token":"eyJ…","token_type":"Bearer","expires_in":3600}

curl -s http://localhost:8080/api/profile -H "Authorization: Bearer eyJ…"
```

Le compte doit avoir été **vérifié** par le lien reçu par email : sinon la connexion est
refusée avec un message dédié. `/api/profile` est aujourd'hui la seule route authentifiée.

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
