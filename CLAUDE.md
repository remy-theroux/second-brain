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
| Une classe de test | `gtest test --tests "xyz.sterenn.secondbrain.users.domain.valueobject.EmailTest"` |
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
base/user/mdp `second_brain`), Mailpit <http://localhost:8025> — tous les mails
émis en développement y sont capturés, aucun ne sort de la machine.

## Architecture

Architecture **hexagonale par bounded context**, avec un **CQRS minimal** posé sur
deux bus synchrones.

```
xyz.sterenn.secondbrain
├── config/                  SecurityConfig, OpenApiConfig, ClockConfiguration — transverse
├── shared/
│   ├── bus/                 socle CQRS, aucune dépendance métier
│   └── web/                 pages n'appartenant à aucun contexte (accueil)
└── users/                   bounded context (gabarit pour les suivants)
    ├── domain/              règles métier pures et transverses (PasswordPolicy)
    │   ├── entity/          agrégats (User, VerificationToken)
    │   ├── valueobject/     valeurs validées et normalisées (Email, RawVerificationToken,
    │   │                    Notification et ses implémentations)
    │   ├── port/            interfaces vers l'extérieur (UserRepository, PasswordHasher,
    │   │                    TokenHasher, VerificationTokenRepository, NotificationSender)
    │   └── exception/       refus métier, messages affichables tels quels
    ├── application/
    │   ├── command/         une commande + son handler par intention d'écriture
    │   └── query/            une query + son handler + son modèle de lecture
    └── infrastructure/
        ├── persistence/     ADAPTERS JPA des ports de stockage + mapping (EmailAttributeConverter)
        ├── security/        ADAPTERS des ports PasswordHasher et TokenHasher
        ├── email/           ADAPTER du port NotificationSender
        └── web/              ADAPTERS entrants (un contrôleur par route + form de liaison)
```

**Sens des dépendances : `infrastructure` → `application` → `domain`.** Le domaine
n'importe jamais `infrastructure` ni `org.springframework.*`. Une seule exception actée :
l'entité `User` porte les annotations `jakarta.persistence` (voir « Écarts assumés »). Le
mapping du value object `Email` sur sa colonne, lui, est entièrement du côté infrastructure :
`EmailAttributeConverter` est `autoApply`, donc `User` ne le nomme pas.

### Le flux d'une écriture

Contrôleur → `commandBus.dispatch(new RegisterUser(...))` → routage vers
`RegisterUserHandler` → domaine → port `UserRepository` → adapter JPA.

Le contrôleur ne connaît ni le handler ni le domaine autrement que par les
exceptions métier qu'il traduit en erreurs de champ. Le handler n'a aucune logique
métier : il convertit en value objects, orchestre, écrit.

### Le flux de la vérification d'email

L'inscription émet un jeton aléatoire, n'en persiste que l'empreinte salée
(`TokenHasher`, adapter BCrypt) et envoie le clair par le port `NotificationSender`.
Notifier est une décision du domaine ; l'email n'est qu'un canal, et l'adapter
`users/infrastructure/email/` est seul à connaître l'URL publique, le sujet et le corps.
`Notification` est une interface **scellée** : l'adapter fait un `switch` exhaustif, donc
un nouveau type de notification non traité ne compile pas.

`GET /verification?compte=&jeton=` recharge le jeton du compte, le compare via le hasher
puis le consomme. `VerificationToken` porte les deux règles — expiration à 24 h et usage
unique — et lève lui-même le refus correspondant. Les trois façons de présenter un lien
inexploitable (UUID illisible, compte inconnu, jeton faux) partagent volontairement un
seul message : les distinguer ferait de la route un oracle d'existence de compte.

L'envoi se fait **dans la transaction du bus** : une panne du canal annule l'inscription.
Tant que « renvoyer le lien » n'existe pas, un compte créé sans notification serait
définitivement invérifiable.

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

`Email` est projeté sur un `varchar(320)` par `EmailAttributeConverter`, annoté
`@Converter(autoApply = true)` et rangé dans `users/infrastructure/persistence/`. Aucune
classe ne le référence : Hibernate ne le connaît que parce que le scan d'entités part du
package de `SecondBrainApplication`. Ne pas le supprimer au motif qu'il paraît inutilisé —
détail dans les règles backend, section « Adapters ».

### Écarts assumés (documentés, ne pas « corriger » spontanément)

1. `User` est une `@Entity` située dans `domain/entity/` : pas de classe miroir ni de
   mapper. L'hexagone fuit sur ce point précis, et sur lui seul — l'entité ne connaît même
   pas le converter qui projette son `Email`, appliqué par `autoApply` depuis
   l'infrastructure.
2. CSRF désactivé et session `STATELESS` dans `SecurityConfig` — il n'y a pas encore
   de session HTTP à protéger. Le ticket « login » lèvera cette dette.
3. `FindUserByEmail` n'est consommée par aucun écran : elle existe pour que le query
   bus soit livré testé, et sert de gabarit.
4. BCrypt ignore les octets au-delà du 72e alors que la politique autorise 128
   caractères. Comportement standard.
5. `VerificationToken` référence son compte par un `UUID` et non par un `@ManyToOne` :
   deux agrégats distincts ne se tiennent pas par une association JPA. La cohérence est
   garantie par la clé étrangère en base, pas par le graphe d'objets.

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
