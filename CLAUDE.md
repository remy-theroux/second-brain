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

Le `Makefile` fige les invocations Docker ci-dessous. C'est l'entrée par défaut pour tout
ce qui est formatage, tests et build :

| Cible | Ce qu'elle fait |
|---|---|
| `make help` | liste les cibles |
| `make format` | formate le back (Spotless) et le front (Prettier) |
| `make check` | vérifie le formatage puis lance les tests, des deux côtés |
| `make build` | produit le jar et `frontend/dist` — exactement ce que vérifie la CI |

Chacune des trois se décline en `-back` et `-front` (`make check-front`) pour n'en payer
qu'un côté. Le formatage du Java est **décidé par palantir-java-format** : ne pas se battre
avec lui, `make format-back` avant de committer. Le Javadoc, lui, n'est jamais reformaté.

`gradle.properties` porte les `--add-exports` sans lesquels le formateur échoue sur une
`IllegalAccessError` — il analyse le code avec les API internes de javac, que JEP 396 a
fermées depuis le JDK 16. Ce fichier est versionné : la CI en a besoin autant que le poste
local.

Le `Makefile` ne couvre pas le lancement d'**un** test : pour ça, et pour tout le reste, les
deux fonctions ci-dessous restent la référence.

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

**Il n'y a pas non plus de Node sur la machine hôte.** Pour le front, définir :

```bash
gfront() {
  docker run --rm \
    -u "$(id -u):$(id -g)" -e HOME=/tmp \
    -v "$PWD/frontend":/app -w /app \
    node:24-alpine "$@"
}
```

`-u` et `HOME=/tmp` sont obligatoires : sans eux, `npm install` écrit `node_modules/` et
`package-lock.json` en `root` dans le dépôt monté.

| Besoin | Commande |
|---|---|
| Tests unitaires du front | `gfront npm run test:unit` |
| Un seul fichier de test | `gfront npx vitest run src/stores/auth.spec.js` |
| Formatage du front | `gfront npm run format` |
| Build du front | `gfront npm run build` |
| Ajouter une dépendance | `gfront npm install <paquet>` |

Lancer l'application en développement (PostgreSQL + Mailpit + app + front avec hot reload) :

```bash
cp .env.example .env      # une seule fois
docker compose up --build
docker compose logs -f app
```

Le hot reload combine deux processus dans le conteneur (`docker/dev-entrypoint.sh`) :
un `gradlew -t classes` en continu qui recompile vers `build/classes`, et `bootRun`
dont DevTools surveille ce dossier. Éditer un `.java` sur l'hôte redémarre l'app en < 1 s.

**Une seule origine : <http://localhost:8080/>.** Un service Traefik publie ce port unique
et route `/api` et `/verification` vers l'application Java, tout le reste vers le front. Ni
l'app ni le serveur Vite ne publient de port. Le front est donc à la racine, l'API sous
`/api`, Swagger UI sur `/swagger-ui.html` et le health sur `/actuator/health` — ces deux
derniers ne sont routés qu'en développement.

Mailpit garde son port propre : <http://localhost:8025>, où tous les mails émis en
développement sont capturés, aucun ne sortant de la machine.

Au premier démarrage, `bootRun` peut perdre la course contre la compilation continue et
échouer sur « Main class name has not been configured » — `build/classes` était encore vide.
`docker compose run --rm --no-deps app ./gradlew --no-daemon classes` puis
`docker compose up -d app` règle le cas.

**`gtest` et `docker compose up` ne cohabitent pas** : les deux verrouillent `.gradle/` du
même répertoire, et `gtest` échoue sur « Timeout waiting to lock Build Output Cleanup
Cache ». Arrêter la pile (`docker compose down`) avant de lancer la suite de tests. Vaut
pour les cibles `make` qui touchent au back, qui passent par le même conteneur.

### Plusieurs features en parallèle

`compose.yaml` publie quatre ports hôte et nomme sa pile : deux répertoires qui font
`docker compose up` sans précaution ne se partagent pas la machine, ils se la disputent —
et comme le nom de projet est le même, le second `up` n'ouvre pas une seconde pile, il
recrée les conteneurs de la première.

Le skill `worktree` (`.claude/skills/worktree/`) crée un worktree dans
`../second-brain-<slug>` et lui écrit un `.env` portant `STACK_SUFFIX=-<slug>` et un bloc
de ports décalé (`8080+N`, `5432+N`, `1025+N`, `8025+N`). Deux mécanismes rendent
l'isolation réelle :

- `name: second-brain${STACK_SUFFIX:-}` — le nom du projet compose sépare conteneurs,
  réseau et volumes nommés. Chaque feature a donc sa propre base et son propre
  `node_modules`. Vide par défaut : le dépôt principal garde son nom et ses ports.
- La contrainte `--providers.docker.constraints` sur Traefik. Le provider Docker lit
  **tout le socket**, pas seulement la pile qui l'a démarré : sans cette contrainte, chaque
  Traefik voit les conteneurs étiquetés des deux piles, donc deux routeurs nommés `backend`
  et deux nommés `frontend`. Collision de noms, et une requête part chez la mauvaise
  feature.

Conséquence à connaître : **`compose.yaml` est versionné**, donc une branche antérieure à
ce mécanisme fige `name: second-brain` et sa pile n'est pas isolable. Le script le vérifie
par `docker compose config` et refuse plutôt que de laisser détruire la pile en cours ; la
sortie est de reporter `main` sur la branche.

Le cache Gradle et le `node_modules` ne se partagent pas entre worktrees — c'est le
« Timeout waiting to lock » ci-dessus. Chaque pile paie donc un premier démarrage long, et
le `gtest` d'un worktree vise un volume `second-brain-gradle-home-<slug>` qui lui est propre.

## Architecture

Architecture **hexagonale par bounded context**, avec un **CQRS minimal** posé sur
deux bus synchrones.

```
xyz.sterenn.secondbrain
├── config/                  SecurityConfig, JwtConfiguration, OpenApiConfig,
│                            ClockConfiguration — transverse
├── shared/
│   └── bus/                 socle CQRS, aucune dépendance métier
└── users/                   bounded context (gabarit pour les suivants)
    ├── domain/              règles métier pures et transverses (PasswordPolicy,
    │   │                    AccessTokenPolicy)
    │   ├── entity/          agrégats (User, VerificationToken)
    │   ├── valueobject/     valeurs validées et normalisées (Email, RawVerificationToken,
    │   │                    AccessToken, Notification et ses implémentations)
    │   ├── port/            interfaces vers l'extérieur (UserRepository, PasswordHasher,
    │   │                    TokenHasher, VerificationTokenRepository, NotificationSender,
    │   │                    AccessTokenIssuer)
    │   └── exception/       refus métier, messages affichables tels quels
    ├── application/
    │   ├── command/         une commande + son handler par intention d'écriture
    │   └── query/           une query + son handler + son modèle de lecture
    └── infrastructure/
        ├── persistence/     ADAPTERS JPA des ports de stockage + mapping (EmailAttributeConverter)
        ├── security/        ADAPTERS des ports PasswordHasher, TokenHasher, AccessTokenIssuer
        ├── email/           ADAPTER du port NotificationSender
        └── web/             ADAPTERS entrants (un contrôleur par route, requête et
                             réponses en records)

frontend/                    application Vue 3, hors build Gradle, construite et servie
│                            en autonomie
├── Dockerfile               build npm puis nginx qui sert dist
├── nginx.conf               repli SPA (try_files) — sans lui, F5 sur /login rend 404
├── src/api/                 seul module qui parle HTTP
├── src/stores/              état partagé (pinia) : jeton, expiration, profil
├── src/router/              routes et garde d'authentification
└── src/views/               un composant par écran (LoginView, RegisterView, HomeView)
```

Le package `shared/web` n'existe plus et `src/main/resources/templates/` non plus :
**aucune vue n'est rendue par le serveur.** L'application Java expose des routes d'API,
plus `GET /verification` qui répond par une redirection.

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

### Le flux de l'inscription

`POST /api/registrations` reçoit `{email, password}`, dispatche `RegisterUser` et répond
`201` sans corps : rien du compte créé n'est lisible tant qu'il n'est pas vérifié et
qu'aucun jeton n'a été délivré, donc ni ressource à exposer ni en-tête `Location` à poser.

Un refus se rend **champ par champ** — `422 {"errors": {"email": "…"}}` — ce qui permet au
front de replacer chaque message sous sa saisie. L'échec du canal de notification, lui, ne
vise aucun champ : `503 {"message": "…"}`, le rollback ayant déjà eu lieu côté
`SpringCommandBus`.

**Deux formes d'erreur coexistent donc dans l'API**, et c'est assumé : `/api/token` répond
`{error, error_description}` parce qu'il imite le `password grant` de RFC 6749 et ne peut
pas s'en écarter sans cesser de l'imiter. La forme à suivre pour toute route future est
celle de `ValidationErrorResponse`.

Le contrôleur déclare un `BindingResult` en paramètre : sa présence empêche Spring de lever
`MethodArgumentNotValidException`, donc la traduction des refus reste dans le contrôleur
plutôt que dans un `@RestControllerAdvice` qui vaudrait pour tout le contexte.

### Le flux de la vérification d'email

L'inscription émet un jeton aléatoire, n'en persiste que l'empreinte salée
(`TokenHasher`, adapter BCrypt) et envoie le clair par le port `NotificationSender`.
Notifier est une décision du domaine ; l'email n'est qu'un canal, et l'adapter
`users/infrastructure/email/` est seul à connaître l'URL publique, le sujet et le corps.
`Notification` est une interface **scellée** : l'adapter fait un `switch` exhaustif, donc
un nouveau type de notification non traité ne compile pas.

`GET /verification?compte=&jeton=` recharge le jeton du compte, le compare via le hasher
puis le consomme. C'est la **seule action du back qui ne soit pas derrière l'API**, et elle
le reste : le lien part par email, il doit fonctionner dans n'importe quel client mail, sans
JavaScript et sans que le front soit en ligne.

La route ne rend plus de page : elle répond `302` vers `/login?verification=<code>`, où le
code vaut `ok`, `lien-invalide`, `lien-expire` ou `lien-deja-utilise`. Le `Location` est
**relatif**, donc résolu par le navigateur contre l'origine de la requête — l'application n'a
aucune URL de front à connaître, et l'origine unique du reverse proxy suffit. Le front porte
la rédaction française correspondante (`VERIFICATION_MESSAGES` dans `LoginView.vue`) : faire
voyager le message en query string le collerait dans l'historique du navigateur et les logs
du proxy, exactement le reproche fait au jeton lui-même (écart n° 6). `VerificationToken` porte les deux règles — expiration à 24 h et usage
unique — et lève lui-même le refus correspondant. Les trois façons de présenter un lien
inexploitable (UUID illisible, compte inconnu, jeton faux) partagent volontairement un
seul message : les distinguer ferait de la route un oracle d'existence de compte.

L'envoi se fait **dans la transaction du bus** : une panne du canal annule l'inscription.
Tant que « renvoyer le lien » n'existe pas, un compte créé sans notification serait
définitivement invérifiable.

### Le flux de la connexion

`POST /api/token` a la **forme** du `password grant` de RFC 6749 §4.3
(`application/x-www-form-urlencoded`, `grant_type=password&username=&password=`, réponse
`access_token` / `token_type` / `expires_in`, refus `{error, error_description}`) sans
serveur d'autorisation derrière : OAuth 2.1 a supprimé ce type d'autorisation, et un
client *first-party* n'a ni redirection ni consentement à gérer.

Se connecter est une **query**, pas une commande : il faut retourner un jeton et rien
n'est écrit en base. `AuthenticateUserHandler` normalise l'email, compare le mot de passe
par le port `PasswordHasher`, refuse un compte non vérifié, puis fait émettre le jeton par
le port `AccessTokenIssuer`. Le refus est une exception métier avec un message affichable
— écart assumé et motivé à la règle « une query rend un `Optional` vide » : cette query ne
demande pas si un compte existe, elle réclame un jeton.

**L'ordre des contrôles est un choix de sécurité** : mot de passe d'abord, vérification
d'adresse ensuite. Seul celui qui connaît déjà le mot de passe apprend qu'un compte existe
mais n'est pas vérifié.

L'adapter `JwtAccessTokenIssuer` signe un JWT HS256 portant `sub` (UUID du compte), `iat`
et `exp` — **pas d'email, pas d'`iss`**. La durée de vie (1 h) est une règle du domaine
(`AccessTokenPolicy.LIFETIME`), pas une propriété de configuration : un exploitant ne doit
pas pouvoir la porter à trente jours par un fichier.

`GET /api/profile` est la seule route authentifiée. Le filtre resource server valide le
jeton en amont ; le contrôleur ne lit que `sub` et interroge `FindUserById`. Un jeton bien
signé dont le compte a disparu répond `401` et non `404` : il n'identifie plus personne, et
le front n'a ainsi qu'un seul cas d'échec à traiter.

`SecurityConfig` refuse par défaut sous `/api/**` : seule `/api/token` s'y déclare publique,
et `GET /api/profile` reste aujourd'hui la seule route authentifiée. Une nouvelle route
publique sous `/api` doit se déclarer explicitement dans `SecurityConfig` ; sans quoi elle
répond `401`.

Le secret de signature (`secondbrain.jwt.secret`, 32 octets minimum) **n'a aucune valeur
par défaut** : sans lui, l'application refuse de démarrer. `compose.yaml` et
`src/test/resources/application.properties` en fournissent un, chacun pour son
environnement — le confort est rendu là où il ne peut pas fuir en production.

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
2. CSRF désactivé et session `STATELESS` dans `SecurityConfig`. Ce n'était une dette que
   tant qu'aucune authentification n'existait ; le ticket « login » l'a levée autrement
   qu'annoncé — non pas en réactivant CSRF, mais en n'introduisant aucun cookie
   d'authentification. L'identité voyage dans un en-tête `Authorization`, qu'un navigateur
   n'envoie jamais spontanément : il n'y a rien à contrefaire depuis un site tiers. Le jour
   où un cookie d'authentification apparaît, CSRF redevient obligatoire. L'origine unique,
   elle, ne tient plus au proxy du serveur de développement mais au reverse proxy — Traefik
   en développement, Coolify en production : la règle « aucune configuration CORS » est
   désormais vraie partout, et non plus seulement en local.
3. `FindUserByEmail` n'est consommée par aucun écran : elle existe pour que le query
   bus soit livré testé, et sert de gabarit.
4. BCrypt ignore les octets au-delà du 72e alors que la politique autorise 128
   caractères. Comportement standard.
5. `VerificationToken` référence son compte par un `UUID` et non par un `@ManyToOne` :
   deux agrégats distincts ne se tiennent pas par une association JPA. La cohérence est
   garantie par la clé étrangère en base, pas par le graphe d'objets.
6. Le jeton de vérification voyage en query string. Il apparaît donc dans l'historique du
   navigateur, dans les logs d'accès de tout reverse-proxy en amont (nginx et Traefik
   journalisent la query string par défaut), et dans les logs applicatifs dès que
   `org.springframework.web` passe en `DEBUG` — ce qui est le cas du profil `dev`. Le
   masquage soigné des `toString()` ne couvre donc pas ce chemin-là. Acceptable pour un
   jeton à usage unique et de courte durée ; à rediscuter si un jeton du même modèle sert
   un jour à réinitialiser un mot de passe.
7. L'usage unique n'est garanti que par un lire-puis-écrire. `VerificationToken` n'a pas
   de `@Version` et la migration ne pose aucune contrainte sur `consumed_at` : deux clics
   simultanés sur le même lien passeraient tous deux le contrôle avant que l'un ait
   commité. Sans conséquence ici — vérifier deux fois est idempotent — mais l'invariant
   n'est pas tenu par la base, et il le faudra le jour où ce modèle de jeton ouvrira une
   action non idempotente.
8. `secondbrain.base-url` a une valeur par défaut qui ment en production. Déployée sans la
   variable, l'application démarre, envoie des mails, et tous les liens pointent vers
   `http://localhost:8080` : la panne ne se manifeste que chez l'utilisateur. La valeur
   par défaut est conservée parce que les tests et le développement local en dépendent ;
   c'est donc la première variable à poser sur un vrai déploiement.
9. Pas de jeton de rafraîchissement, pas de révocation. Un JWT vaut jusqu'à son `exp` :
   « se déconnecter » efface le jeton du navigateur et rien de plus, et un jeton volé reste
   valable jusqu'à une heure. C'est ce qui rend la durée de vie courte non négociable.
10. Le jeton est rangé dans le `localStorage` du navigateur. Il survit donc à un
    rafraîchissement de page — sans quoi « maintenir une connexion » n'aurait aucun sens —
    mais une faille XSS dans le front le donnerait. La parade (cookie `httpOnly` `Secure`
    `SameSite` plus jeton de rafraîchissement, donc CSRF à réactiver) est un ticket entier.
11. `POST /api/token` n'a aucune limitation de débit : rien n'empêche une recherche
    exhaustive de mot de passe. Dans la même veine, un email inconnu revient sans calcul
    BCrypt, donc plus vite qu'un email connu : le temps de réponse trahit l'existence d'un
    compte. Hacher un leurre systématiquement corrigerait le second point, mais boucher
    cette fissure avant d'avoir fermé la porte à côté serait se raconter une histoire. Les
    deux se traitent ensemble, avec la journalisation des tentatives.
12. Le front se construit et se sert en autonomie (`frontend/Dockerfile` : build npm puis
    nginx servant `dist`), mais **rien dans le dépôt ne décrit son déploiement**. Le
    routage de production — `/api` et `/verification` vers le back, tout le reste vers le
    front, ni Swagger ni actuator exposés — vit dans la configuration Coolify, hors du
    dépôt. Deux configurations de routage doivent donc rester cohérentes à la main.
13. `/api/profile` sérialise directement le modèle de lecture `UserView` (dont
    `createdAt`). La forme de l'API est donc couplée à celle de la query. Acceptable pour
    une projection dédiée aux écrans ; le jour où l'API et un écran divergeront, il faudra
    un record de réponse dans `infrastructure/web/`.
14. `FindUserByEmail` reste sans écran (voir l'écart n° 3) : le profil lit par identifiant,
    puisque c'est l'identifiant que le jeton porte. Chercher par email quand on détient un
    UUID immuable serait un contresens.
15. `LoginView.vue`, `RegisterView.vue` et `HomeView.vue` ne sont couverts par aucun test
    automatisé. Le choix
    délibéré a été de tester le store d'authentification et le garde de route — les deux
    endroits où un échec passerait silencieusement — et non le rendu des composants. La
    correction des deux écrans repose donc sur `npm run build` (qui compile les templates
    sans rien affirmer sur leur comportement) et sur un passage humain dans un navigateur.
    Conséquence directe : un gestionnaire d'événement mal relié ou un nom de champ mal
    orthographié passerait au vert. Le passage humain n'est donc pas une étape facultative
    mais une condition avant toute mise en production.
16. Les libellés de refus de vérification existent en deux endroits : les exceptions du
    domaine (`InvalidVerificationLinkException` et ses sœurs) et `VERIFICATION_MESSAGES`
    dans `LoginView.vue`, qui traduit les codes portés par la redirection. Ils peuvent
    diverger sans qu'aucun test ne le voie. C'est le prix du choix de faire voyager un code
    plutôt qu'un message dans une URL.
17. Il n'existe plus aucune page publique. Un visiteur anonyme est renvoyé sur `/login`,
    qui porte le lien vers l'inscription, et c'est tout ce qu'il peut voir de
    l'application. Le jour où il y aura quelque chose à dire à un visiteur, ce sera un
    ticket, pas une page d'accueil recréée par réflexe.
18. Le repli SPA de nginx (`try_files`) n'est exercé par aucun environnement avant la
    production : `docker compose` fait tourner Vite, qui sert `index.html` sur toute route
    inconnue par construction. Une erreur dans `frontend/nginx.conf` ne se verrait qu'une
    fois déployée. Le contrôle se fait à la main :
    `docker build -t second-brain-frontend ./frontend` puis un `curl` sur `/login`.

## Stack et versions

**Back** — Java 25 · Spring Boot 4.0.7 (MVC, Data JPA, Security, OAuth2 Resource Server,
Validation, Mail) · Flyway · PostgreSQL 17 · springdoc-openapi · JUnit 5 +
AssertJ + Testcontainers · Gradle Kotlin DSL avec version catalog
(`gradle/libs.versions.toml`).

**Front** — Vue 3 · Vite · vue-router · pinia · Vitest (jsdom) · nginx pour servir le build.
Versions gérées par `frontend/package-lock.json`, hors du version catalog Gradle.

**Développement** — Traefik v3 en reverse proxy devant l'app et le front, dans `compose.yaml`.
En production, c'est Coolify qui tient ce rôle, avec une configuration qui vit hors du dépôt.

**Ne pas changer ces versions.** Spring Boot 4 a redécoupé ses modules par rapport
à Boot 3 : plusieurs annotations ont changé de package (`@AutoConfigureMockMvc` vit
dans `org.springframework.boot.webmvc.test.autoconfigure`, l'auto-config Flyway dans
`spring-boot-starter-flyway`, le resource server dans
`spring-boot-starter-security-oauth2-resource-server` — l'ancien
`spring-boot-starter-oauth2-resource-server` est déprécié). Boot 4 est aussi passé à
Jackson 3 : le databind vit sous `tools.jackson`, mais **les annotations restent sous
`com.fasterxml.jackson.annotation`**. Si un import ne se résout pas, chercher la classe
dans les jars du cache Gradle plutôt que de réécrire le code.

## Documents de référence

- `docs/ticket-template.md` — format de ticket attendu (5 sections, Gherkin
  déclaratif). La Definition of Done appartient à ce CLAUDE.md, pas aux tickets.
- `docs/superpowers/plans/` — plans d'implémentation détaillés, un par feature.
  Celui de la création de compte porte le raisonnement derrière l'architecture ci-dessus.
- `.superpowers/sdd/<date>-<feature>/` — briefs, rapports et diffs de revue par tâche.
