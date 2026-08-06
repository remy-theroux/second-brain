# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Règles

@.claude/rules/backend.md
@.claude/rules/frontend.md

## Langue

Le projet est intégralement en français : commentaires, Javadoc, messages
d'exception, libellés d'interface, noms de méthodes de test, messages de commit.
Les noms de classes, de méthodes de production et de packages restent en anglais.

## Commandes

**Il n'y a aucun JDK ni Gradle sur la machine hôte.** Tout passe par Docker.
Définir cette fonction une fois par session avant toute commande Gradle :

```bash
gtest() {
  docker run --rm \
    --network host \
    -v "$PWD":/app -w /app \
    -v /var/run/docker.sock:/var/run/docker.sock \
    -v second-brain-gradle-home:/home/gradle/.gradle \
    gradle:jdk25 gradle --no-daemon "$@"
}
```

- `--network host` est **obligatoire** : Testcontainers démarre PostgreSQL en
  conteneur frère et s'y connecte via `localhost:<port mappé>`.
- Le volume nommé `second-brain-gradle-home` conserve le cache Gradle et le
  JDK 25 téléchargé par la toolchain. Premier lancement long, suivants rapides.

| Besoin | Commande |
|---|---|
| Toute la suite | `gtest test` |
| Une classe de test | `gtest test --tests "xyz.sterenn.secondbrain.users.domain.EmailTest"` |
| Un package | `gtest test --tests "xyz.sterenn.secondbrain.shared.bus.*"` |
| Une méthode | `gtest test --tests "…EmailTest.refuse_un_email_vide"` |
| Compilation seule | `gtest compileJava` |
| Build complet (ce que fait la CI) | `gtest build` |

Lancer l'application en développement (PostgreSQL + Adminer + app avec hot reload) :

```bash
cp .env.example .env      # une seule fois
docker compose up --build
docker compose logs -f app
```

Le hot reload combine deux processus dans le conteneur (`docker/dev-entrypoint.sh`) :
un `gradlew -t classes` en continu qui recompile vers `build/classes`, et `bootRun`
dont DevTools surveille ce dossier. Éditer un `.java` sur l'hôte redémarre l'app en < 1 s.

Points d'entrée : app <http://localhost:8080/>, Swagger UI `/swagger-ui.html`,
health `/actuator/health`, Adminer <http://localhost:8081> (serveur `db`,
base/user/mdp `second_brain`).

## Architecture

Architecture **hexagonale par bounded context**, avec un **CQRS minimal** posé sur
deux bus synchrones.

```
xyz.sterenn.secondbrain
├── config/                  SecurityConfig, OpenApiConfig — transverse
├── shared/
│   ├── bus/                 socle CQRS, aucune dépendance métier
│   └── web/                 pages n'appartenant à aucun contexte (accueil)
└── users/                   bounded context (gabarit pour les suivants)
    ├── domain/              entités, value objects, règles, PORTS (interfaces)
    ├── application/
    │   ├── command/         une commande + son handler par intention d'écriture
    │   └── query/           une query + son handler + son modèle de lecture
    └── infrastructure/
        ├── persistence/     ADAPTER JPA du port UserRepository
        ├── security/        ADAPTER du port PasswordHasher
        └── web/             ADAPTER entrant (contrôleur + form de liaison)
```

**Sens des dépendances : `infrastructure` → `application` → `domain`.** Le domaine
n'importe jamais `infrastructure` ni `org.springframework.*`. Une seule exception
actée : il porte les annotations `jakarta.persistence` (voir « Écarts assumés »).

### Le flux d'une écriture

Contrôleur → `commandBus.dispatch(new RegisterUser(...))` → routage vers
`RegisterUserHandler` → domaine → port `UserRepository` → adapter JPA.

Le contrôleur ne connaît ni le handler ni le domaine autrement que par les
exceptions métier qu'il traduit en erreurs de champ. Le handler n'a aucune logique
métier : il convertit en value objects, orchestre, écrit.

### Les deux bus (`shared/bus`)

- `Command` / `CommandHandler<C>` / `CommandBus.dispatch(Command)` — écriture, ne
  retourne rien.
- `Query<R>` / `QueryHandler<Q, R>` / `QueryBus.ask(Query<R>)` — lecture typée par
  le paramètre `R` de la query, donc sans cast côté appelant.
- La table de routage est construite **au démarrage** en résolvant le paramètre
  générique de chaque handler (`GenericTypeResolver`). Deux handlers pour le même
  message → échec au démarrage, pas au runtime.
- Les bus sont déclarés en `@Bean` dans `BusConfiguration`, avec `ObjectProvider`
  et non `List<Handler>` injectée : le contexte doit démarrer même sans aucun handler.

### La transaction vit dans le bus

`SpringCommandBus.dispatch` est `@Transactional`, `SpringQueryBus.ask` est
`@Transactional(readOnly = true)`. Tout ce que le handler déclenche s'exécute donc
dans une seule transaction, et la moindre `RuntimeException` annule l'ensemble.
Conséquences directes, détaillées dans les règles backend : **jamais de
`@Transactional` sur un handler**, et **toutes les exceptions métier héritent de
`RuntimeException`** (une exception checked ne déclenche pas de rollback par défaut).

### Persistance

Flyway est **maître du schéma** ; Hibernate tourne en `ddl-auto: validate` et se
contente de vérifier la correspondance entités ↔ tables au démarrage. Les tables
sont préfixées par leur contexte (`users_users`).

### Écarts assumés (documentés, ne pas « corriger » spontanément)

1. `User` est une `@Entity` située dans `domain/` : pas de classe miroir ni de
   mapper. L'hexagone fuit sur ce point précis, les ports tiennent partout ailleurs.
2. CSRF désactivé et session `STATELESS` dans `SecurityConfig` — il n'y a pas encore
   de session HTTP à protéger. Le ticket « login » lèvera cette dette.
3. `FindUserByEmail` n'est consommée par aucun écran : elle existe pour que le query
   bus soit livré testé, et sert de gabarit.
4. BCrypt ignore les octets au-delà du 72e alors que la politique autorise 128
   caractères. Comportement standard.

## Stack et versions

Java 25 · Spring Boot 4.0.7 (MVC, Data JPA, Security, Validation) · Thymeleaf ·
Flyway · PostgreSQL 17 · springdoc-openapi · JUnit 5 + AssertJ + Testcontainers ·
Gradle Kotlin DSL avec version catalog (`gradle/libs.versions.toml`).

**Ne pas changer ces versions.** Spring Boot 4 a redécoupé ses modules par rapport
à Boot 3 : plusieurs annotations ont changé de package (`@AutoConfigureMockMvc` vit
dans `org.springframework.boot.webmvc.test.autoconfigure`, l'auto-config Flyway dans
`spring-boot-starter-flyway`). Si un import ne se résout pas, chercher la classe dans
les jars du cache Gradle plutôt que de réécrire le code.

## Documents de référence

- `docs/ticket-template.md` — format de ticket attendu (5 sections, Gherkin
  déclaratif). La Definition of Done appartient à ce CLAUDE.md, pas aux tickets.
- `docs/superpowers/plans/` — plans d'implémentation détaillés, un par feature.
  Celui de la création de compte porte le raisonnement derrière l'architecture ci-dessus.
- `.superpowers/sdd/<date>-<feature>/` — briefs, rapports et diffs de revue par tâche.
