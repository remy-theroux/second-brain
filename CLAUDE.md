# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Règles

@.claude/rules/backend.md
@.claude/rules/frontend.md
@.claude/rules/decisions.md

## Langue

**Le code s'écrit en anglais, la prose du projet reste en français.** La frontière n'est
pas la nature du fichier, c'est le lecteur : ce qu'un développeur lit est en anglais, ce
qu'un utilisateur lit est en français.

**En anglais** — tout le code, commentaires compris :

- noms de classes, de méthodes, de packages, de champs et de variables ;
- commentaires et Javadoc, y compris ceux des migrations SQL et des fichiers du front ;
- **noms de méthodes de test** (`rejects_an_already_used_email`), et les libellés des
  `describe`/`it` côté Vitest.

**En français** — ce que lit un utilisateur, et rien d'autre :

- les libellés, textes et messages de l'interface ;
- les **messages d'exception métier**, qui sont affichables tels quels (voir les règles
  backend) ;
- le prompt de l'agent documentaire (`src/main/resources/agents/document-agent.md`), qui
  gouverne la langue de ses réponses ;
- les messages de commit.

**En français, sans exception : les documents de travail.** Les ADR de `docs/decisions/`,
les specs de `docs/superpowers/specs/` et les plans de `docs/superpowers/plans/` ne se
traduisent jamais, même partiellement. Ce sont des documents de réflexion, pas du code, et
un ADR accepté ne se réécrit de toute façon pas (voir `.claude/rules/decisions.md`).

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
| Une méthode | `gtest test --tests "…EmailTest.rejects_a_blank_email"` |
| Compilation seule | `gtest compileJava` |
| Build complet (ce que fait la CI) | `gtest build` |
| Refabriquer les fixtures binaires d'extraction | `gtest generateFixtures` |

`generateFixtures` écrit les documents d'essai binaires de `src/test/resources/fixtures/`
et **son produit est versionné** : elle se lance à la main, pas à chaque build. Ces fichiers
sont un socle fabriqué, pas de vrais documents — voir la spec d'extraction, décision 9.
`gtest` tournant en `root`, les fichiers produits appartiennent à `root` : les rendre avant
de committer, par
`docker run --rm -v "$PWD/src/test/resources/fixtures":/f alpine chown -R "$(id -u):$(id -g)" /f`.

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

Le worker est un conteneur à part : `docker compose logs -f worker` montre les événements
reçus. Il **ne compile jamais** — son `bootRun` porte `-x compileJava -x processResources`,
donc `build/` n'est écrit que par `app`, dont le compilateur continu recompile et dont
DevTools redémarre les deux conteneurs. Il ne démarre qu'une fois `app` **sain** (son
healthcheck : Tomcat écoute, donc `build/classes` est compilé et chargé), ce qui met la
course « Main class name has not been configured » ci-dessous hors de sa portée. Il tient sa
place à côté de `app` sur le même bind mount grâce à deux isolations Gradle : un
`--project-cache-dir` propre (`.gradle-worker/`) pour le `.gradle/` du projet, et son propre
`GRADLE_USER_HOME` (`.gradle-cache-worker/`, volume `gradle-cache-worker`) — deux conteneurs
qui partagent un cache Gradle se bloquent sur ses verrous, c'est le même « Timeout waiting
to lock » qu'entre `gtest` et la pile. Le prix est un second téléchargement des dépendances
au premier démarrage.

Un service `garage` sert le stockage objet des fichiers d'origine (voir la section
« Persistance »). Il ne publie aucun port : seuls `app` et `worker` lui parlent, par le
réseau de la pile. Il s'amorce seul — sa commande porte `--single-node
--default-access-key --default-bucket`, qui lui fait créer sa clé d'accès et son bucket dès
le premier démarrage, donc sans conteneur d'amorçage. Pour l'interroger à la main,
`docker compose exec garage /garage bucket info second-brain-originals`. Ses deux volumes
nommés (`garage-meta`, `garage-data`) sont propres au projet Compose, comme `db-data` :
**chaque worktree a donc son propre Garage**, tout comme il a sa propre base.

Un service `ollama` sert le modèle d'embedding (`bge-m3`, 1024 dimensions) et le modèle de
génération (`qwen3:4b`), tous deux tirés au premier démarrage par le conteneur one-shot
`ollama-pull` — **deux modèles**, donc un premier démarrage plus long, et un Ollama de plus
par worktree en tire deux plutôt qu'un. Il ne publie aucun port : seuls le worker et l'app
lui parlent, par le réseau de la pile. Pour l'interroger à la main,
`docker compose exec ollama ollama list`. Le worker **ne l'attend pas** pour démarrer — un
document traité pendant le téléchargement du modèle échoue avec un motif qui nomme la
vectorisation.

Au premier démarrage, `bootRun` peut perdre la course contre la compilation continue et
échouer sur « Main class name has not been configured » — `build/classes` était encore vide.
`docker compose run --rm --no-deps app ./gradlew --no-daemon classes` puis
`docker compose up -d app` règle le cas. Ça ne vaut que pour `app` : le worker attend son
healthcheck.

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

`ollama-models` non plus ne se partage pas : c'est un volume nommé, donc propre au projet
Compose comme `db-data`, et chaque nom de projet en a le sien. Une feature de plus en
parallèle, c'est un modèle de plus téléchargé et un Ollama de plus qui tourne.

## Architecture

Architecture **hexagonale par bounded context**, avec un **CQRS minimal** posé sur
deux bus synchrones.


`src/main/resources/templates/` n'existe plus : **aucune vue n'est rendue par le
serveur.** L'application Java expose des routes d'API, plus `GET /verification` qui répond
par une redirection.

`shared/web`, supprimé en même temps que les vues, est réapparu avec le second contexte
borné — mais il ne porte plus rien qui rende du HTML : seulement `ErrorResponse` et
`ValidationErrorResponse`, les deux formes de refus que **toute** route suit. Elles vivaient
dans `users` alors qu'elles n'ont jamais rien eu de propre aux comptes ; les importer depuis
`knowledge` aurait fait dépendre un contexte borné d'un autre pour deux records de trois
lignes. `OAuth2ErrorResponse`, lui, reste dans `users` : sa forme appartient à `/api/token`
et à RFC 6749, pas au projet.

**Sens des dépendances : `infrastructure` → `application` → `domain`.** Le domaine
n'importe jamais `infrastructure` ni `org.springframework.*`. Une seule exception actée :
l'entité `User` porte les annotations `jakarta.persistence` (voir ADR-0002). Le
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
du proxy, exactement le reproche fait au jeton lui-même (ADR-0007). `VerificationToken` porte les deux règles — expiration à 24 h et usage
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

`GET /api/profile` est une route authentifiée. Le filtre resource server valide le
jeton en amont ; le contrôleur ne lit que `sub` et interroge `FindUserById`. Un jeton bien
signé dont le compte a disparu répond `401` et non `404` : il n'identifie plus personne, et
le front n'a ainsi qu'un seul cas d'échec à traiter.

`SecurityConfig` refuse par défaut sous `/api/**` : seule `/api/token` s'y déclare publique.
Une nouvelle route publique sous `/api` doit se déclarer explicitement dans `SecurityConfig` ;
sans quoi elle répond `401`.

Le secret de signature (`secondbrain.jwt.secret`, 32 octets minimum) **n'a aucune valeur
par défaut** : sans lui, l'application refuse de démarrer. `compose.yaml` et
`src/test/resources/application.properties` en fournissent un, chacun pour son
environnement — le confort est rendu là où il ne peut pas fuir en production.

### Le flux de la connexion à Google Drive

Le flux « code d'autorisation » d'OAuth 2.0, en deux routes et deux tables.
`POST /api/drive/authorizations` ouvre une **demande** : un nonce aléatoire est persisté avec son
propriétaire, et l'URL de consentement Google est rendue au front, qui y envoie le navigateur.
`GET /drive/callback` retrouve la demande par son nonce, la consomme, échange le code contre un
jeton de rafraîchissement, lit l'adresse Google du compte, **remplace** la connexion précédente
s'il y en avait une, et redirige. `DELETE /api/drive/connection` révoque, `GET /api/drive/connection`
rend l'état — jamais le jeton.

**Le callback vit hors de `/api`, et c'est la seule route du contexte dans ce cas** : le lien vient
de Google, il n'a aucun jeton en en-tête, exactement comme `GET /verification`. Il redirige en
`302` **relatif** vers `/documents?drive=<code>`, avec `ok`, `refus`, `lien-invalide` ou `echec` —
un **code**, pas un message (ADR-0017), et une URL relative que le navigateur résout contre
l'origine, donc aucune URL de front à connaître côté serveur.

**Vivre hors de `/api` a un prix, payé une fois :** la règle Traefik de `compose.yaml` énumère les
préfixes routés vers l'application Java. Sans `PathPrefix(`/drive`)`, le retour de Google atterrit
sur le front, qui répond son `index.html`, et la connexion échoue **sans un mot**. En production,
c'est Coolify qui tient ce rôle, hors du dépôt (ADR-0013) : la même règle y est à poser.

**Le nonce est la seule protection du flux**, le projet n'ayant ni CSRF ni session (ADR-0003). Il
est persisté, **à usage unique** et il expire — le cycle de vie de `VerificationToken`, sans son
empreinte salée : ce n'est pas un secret, le connaître ne donne rien. Les trois façons de présenter
un retour inexploitable (nonce illisible, demande inconnue, nonce faux) partagent **un seul
message**, pour la même raison qu'à la vérification d'email : les distinguer ferait de la route un
oracle.

**Mais « usage unique » est plus faible que ce que la table laisse croire.** Tout le callback tient
dans la transaction du bus, consommation du nonce comprise : si l'échange du code échoue, le
rollback efface le `consumed_at` et la demande **redevient consommable** pour le reste de ses dix
minutes. C'est commode — un Google momentanément à terre laisse relancer sans repartir du
consentement — mais c'est bien la fenêtre de dix minutes, et non le `consumed_at`, qui borne
réellement une demande.

**Deux allers-retours vers Google se font dans cette transaction** — le point de jeton puis
`about.get` —, chacun avec le `read-timeout` de 120 s de `spring.http.clients` : jusqu'à quatre
minutes de connexion PostgreSQL tenue par un callback. Le dépôt paie déjà ce prix pour Ollama, à
l'indexation comme à la recherche ; il se paie ici aussi, et pour la même raison — une application
mono-utilisateur le supporte.

**Les demandes d'autorisation ne se purgent jamais.** Aucune tâche ne ramasse les lignes de
`knowledge_drive_authorization_requests`, consommées ou non, expirées ou non. La croissance est
lente et bornée par le nombre de comptes, mais rien ne la nettoie.

**Le jeton de rafraîchissement est chiffré au repos** par `RefreshTokenAttributeConverter`
(AES-256-GCM, IV aléatoire préfixé au chiffré). C'est le chemin d'`Email` et de `Checksum` : un
value object du domaine, un converter `autoApply` dans `infrastructure/persistence/`, et **aucune
classe du domaine ne nomme le chiffrement**. Un écart assumé aux règles backend : ce converter-là
porte `@Component`, parce qu'il a besoin de la clé par injection — Hibernate le résout par le
`SpringBeanContainer` que `spring-orm` installe, ce qu'un test d'intégration vérifie en relisant la
colonne au `JdbcTemplate`. La clé n'a **aucun défaut** : sans elle, l'application refuse de
démarrer. Elle doit faire **exactement 32 octets une fois décodée**, et c'est **toute**
l'application qui refuse alors de démarrer, pas seulement Drive : le converter étant un singleton,
l'échec a lieu au `refresh()` du contexte.

`access_type=offline` et `prompt=consent` ne sont pas décoratifs : sans le premier Google ne
délivre aucun jeton de rafraîchissement, sans le second il n'en redélivre pas à une seconde
autorisation du même compte. Une réponse sans `refresh_token` est donc un **échec explicite** —
laisser passer une connexion sans jeton la ferait mourir au premier redémarrage. L'adresse Google
vient d'`about.get?fields=user`, que `drive.readonly` couvre déjà : demander `userinfo.email`
élargirait le consentement pour rien.

**Aucun SDK Google.** L'adapter est écrit à la main sur le `RestClient` de Spring, comme celui
d'Ollama : le point de jeton est un POST de formulaire et `about.get` un GET JSON, là où
`google-api-client` amènerait Guava, protobuf et sa propre couche HTTP pour deux appels.

**Ce qui reste hors du dépôt :** les identifiants OAuth, et le **statut « In production »** de
l'application chez Google. En statut « Testing », les jetons de rafraîchissement expirent au bout
de sept jours et la connexion meurt en silence.

**Ce qui arme le « à renouveler » :** `invalid_grant` au rafraîchissement du jeton d'accès, et
rien d'autre. Le déclencheur qui manquait à DRIVE-1 est arrivé avec les routes de la section
suivante ; le détail y est.

**Ce que le front ne fait pas encore :** aucun écran n'appelle `POST /api/drive/authorizations` ni
ne lit le `?drive=<code>` du retour. L'utilisateur revenant de Google atterrit donc sur un écran
muet, quel que soit le code — c'est DRIVE-7.

### Le flux du choix des dossiers surveillés

Quatre routes, et aucune ne lit le contenu d'un fichier. `GET /api/drive/folders` et
`GET /api/drive/folders?parent=<id>` parcourent **les dossiers seulement** — sans `parent`,
c'est la racine qui est parcourue, le handler traduisant l'absence en `root`, mot-clé par
lequel l'API Google désigne le haut d'un Drive. Un `parent` hors de `[A-Za-z0-9_-]`, qui est
l'alphabet réel d'un identifiant Drive, ne part pas chez Google : il rend une liste vide,
comme le ferait un dossier inconnu. `POST /api/drive/watched-folders`
met un dossier sous surveillance, `GET /api/drive/watched-folders` les liste,
`DELETE /api/drive/watched-folders/{id}` en retire un **sans toucher aux documents qu'il a
apportés** — le miroir est l'affaire de DRIVE-5, et le retrait ne fait rien d'autre que
retirer.

**Un dossier se désigne par son identifiant Drive, jamais par son chemin.** Le renommer ou le
déplacer ne casse pas la surveillance, exactement comme l'identité d'un document est son
contenu et non son nom. La colonne `name` n'est qu'une recopie prise au moment de la mise sous
surveillance : elle sert à l'écran, elle peut vieillir, et rien ne la rafraîchit.

**Sans connexion Drive, le parcours et la mise sous surveillance refusent — la liste, non.**
Les deux premiers rendent `409` (« Connectez un compte Google… ») ; `GET
/api/drive/watched-folders` rend une **liste vide**. C'est une lecture, et l'écran de DRIVE-7
doit pouvoir l'appeler avant qu'un Drive soit connecté.

**Le refus du dossier déjà couvert** remonte les ancêtres du candidat par
`files.get?fields=parents`, un appel par niveau, jusqu'à la racine ou jusqu'à un dossier déjà
surveillé. C'est N appels pour N niveaux, et c'est acceptable : ça n'arrive qu'à la mise sous
surveillance, un geste rare — et pas du tout tant que rien n'est surveillé, le handler ne
partant en escalade que si la connexion a déjà des dossiers. Le message **nomme le dossier
couvrant** — « Ce dossier est déjà couvert par « Notes ». » : dire seulement « déjà
couvert » obligerait l'utilisateur à chercher lequel. Le même refus couvre le dossier
redéposé tel quel, qui est alors son propre couvrant, et l'`UNIQUE (connection_id,
drive_folder_id)` reste le filet sous le contrôle applicatif.

**L'escalade est bornée à cinquante niveaux et lève plutôt que de boucler.** Drive a
supprimé le multi-parentage en septembre 2020 — un fichier n'a plus qu'un parent, les cas
anciens ayant été migrés en raccourcis —, et l'adapter ne suit donc que ce parent-là : un
cycle ne devrait pas exister. La borne n'est pas une défense contre un cycle attendu, c'est
de la défiance envers une réponse tierce, qu'on ne veut pas voir boucler dans une
transaction. C'est la même forme que la borne de cent pages du parcours, et pour la même
raison.

**Ce que ce contrôle ne fait pas, et qui est assumé :** surveiller un dossier qui est
l'**ancêtre** d'un dossier déjà surveillé n'est pas refusé. Le ticket ne le demande pas, mais
son motif — « pour qu'aucun fichier ne soit balayé deux fois » — vaut symétriquement. Le
corriger demanderait de décider quoi faire du dossier devenu redondant (le retirer ? le
laisser ?), ce qui est une décision de produit, pas une correction.

**`files.list` se pagine.** Elle rend cent entrées par défaut et un `nextPageToken` ; un Drive
personnel a des dossiers de plus de cent sous-dossiers, et ne pas suivre le jeton en perd la
moitié **en silence** — le pire mode d'échec possible pour un sélecteur. L'adapter suit le
jeton, borné à cent pages.

**Le jeton d'accès est échangé une fois et gardé jusqu'à sa mort.** `CachingGoogleAccessTokens`
garde un jeton par connexion dans une `ConcurrentHashMap` : sans lui, un aller-retour vers
Google précéderait chaque `files.list`. Une marge de soixante secondes
(`DriveAccessToken.EXPIRY_MARGIN`) écarte le jeton qui expire pendant l'appel qu'il autorise.
Ce cache est **en mémoire de processus** : il n'est donc **pas partagé entre l'app et le
worker**, et **ne survit pas à un redémarrage**. Sans conséquence — le pire cas est un échange
de plus —, mais à savoir avant de chercher pourquoi deux processus parlent deux fois au point
de jeton. Il se vide à la déconnexion explicite, et à chaque jeton que Google refuse.

**`invalid_grant` est le seul chemin vers une révocation.** C'est le seul signal que Google
donne d'un accès retiré depuis le compte, et c'est le seul qui fasse passer la connexion en
`NEEDS_RECONNECTION` : un `500`, un délai dépassé ou une panne réseau rendent `503` et
**laissent la connexion intacte**. Une connexion valide marquée « à renouveler » sur un
incident passager enverrait l'utilisateur refaire un consentement dont il n'a pas besoin.
L'exception héritant de `RuntimeException`, elle annule la transaction du bus : le statut
s'écrit donc dans une **seconde** transaction, `MarkDriveConnectionExpired` dispatchée par le
contrôleur qui a rattrapé le refus, avant de rendre sa réponse. C'est exactement la situation
d'ADR-0028, et la même réponse.

**Mais rien ne rafraîchit un jeton d'accès encore valide à l'horloge : c'est le `401` de
Google qui déclenche le rafraîchissement, donc la révocation.** Un accès retiré tue le jeton
d'accès **immédiatement**, alors que le cache le croit bon pour une heure encore : sans ce
chemin, le `files.list` prenait un `401` traité comme une panne quelconque, l'utilisateur
recevait `503` « réessayez plus tard » — un message qui invite à attendre là où il faut
reconnecter — et la connexion restait `ACTIVE` jusqu'à cinquante-neuf minutes.
`GoogleDriveFoldersAdapter` distingue donc le `401` (`DriveAccessTokenRejectedException`), et
`DriveAccess` — par où passe **tout** appel Drive du contexte — purge le jeton et rejoue
l'appel **une seule fois**. C'est ce rafraîchissement forcé qui produit l'`invalid_grant`.
Jamais de boucle : un `401` qui survit à un jeton neuf est une vraie anomalie, il rend `503`
comme le reste — l'exception est fille de `GoogleDriveUnavailableException` pour ça — et
réessayer sans fin en ferait une tempête d'appels.

Les appels à Google ont lieu **dans la transaction du bus**, `readOnly` pour le parcours : une
connexion PostgreSQL est tenue le temps des allers-retours, comme elle l'est pour Ollama à
l'indexation et à la recherche. Une application mono-utilisateur le supporte.

**Ce que le front ne fait pas encore :** aucun écran n'appelle ces quatre routes — c'est
DRIVE-7.

### Le flux de l'import d'un dossier surveillé

`POST /api/drive/watched-folders/{id}/import` répond **`202`** : le balayage n'a pas commencé
quand la réponse part, et rien n'a été créé — c'est exactement ce que `202` dit, là où `201`
promettrait une ressource. Un dossier inconnu ou d'autrui rend `404`, comme partout. La route
ne fait que publier `DriveFolderImportRequested` ; le worker balaie, télécharge et importe.

**La boucle vit hors des bus et sans transaction.** `DriveFolderImporter` est un composant
d'`application/`, appelé directement par le listener, comme `ConversationAgent` : un
`CommandHandler` tournerait dans la transaction du bus, donc tiendrait une connexion
PostgreSQL ouverte le temps de cinq cents fichiers. Pire, c'est cette transaction unique qui
rendrait faux « les documents déjà entrés restent dans ma base » — un Drive qui tombe à
mi-parcours emporterait tout ce qui est entré avant lui. La boucle dispatche donc **une
commande `ImportDriveFile` par fichier**, soit une transaction courte chacune, et un échec
n'annule que le fichier en cours.

**L'unicité de contenu s'assouplit — c'est le seul endroit où ce flux change une règle
existante**, et cette décision **attend son ADR**. `ImportDriveFileHandler` traite trois cas,
dans cet ordre :

1. un document existe déjà pour `(propriétaire, fichier Drive)` → **rien à importer**, c'est un
   second import du même fichier ; la seule écriture possible est celle de son dossier
   surveillé, ci-dessous ;
2. sinon, un document existe pour `(propriétaire, empreinte)` → on lui **rattache** sa
   provenance Drive, sans créer de second document — sauf s'il en porte déjà une, auquel cas le
   fichier est **rejeté** avec son motif : deux fichiers Drive au même contenu, c'est
   `rapport.pdf` et sa copie `rapport (1).pdf` ;
3. sinon → création avec provenance, original conservé, `DocumentUploaded` publié.

**Le cas 2 ne relance rien** : ni événement, ni extraction, ni revectorisation. Le contenu n'a
pas bougé, seule son origine est apprise. Sans cette règle, la première synchronisation d'un
Drive revectoriserait toute la base — c'est le point à ne pas casser, et un test l'observe.

**Un dossier retiré de la surveillance puis remis est un `WatchedFolder` neuf**, avec un
identifiant neuf, et les documents qu'il avait apportés portent l'ancien. C'est la seule écriture
du cas 1 : le dossier surveillé du document est remis à jour quand il diffère, **sans rien
publier**, ce qui préserve la propriété du paragraphe précédent. Sans elle, l'écran lirait
« 0 document » à côté d'un import réussi, et pour toujours.

**`UploadDocument` n'a donc pas été réutilisée**, contrairement à ce que le ticket suggérait :
elle lève `DuplicateDocumentException` au cas 2, qui est précisément le cas nominal d'un
premier import. Un drapeau `boolean fromDrive` glissé dans l'ancienne aurait fait diverger deux
comportements sous un seul nom ; une commande distincte est plus honnête.

**La provenance entre dans le document lui-même** — `source` (`MANUAL` ou `GOOGLE_DRIVE`),
l'identifiant Drive, le lien d'ouverture — et un document ne vient jamais de deux fichiers
Drive : `attachTo` refuse d'écraser une provenance existante. La quatrième colonne,
`drive_modified_time`, porte l'instant de modification que Drive annonce : c'est par elle qu'un
Google Doc natif se compare, jamais par son empreinte — les six paragraphes qui suivent le
plafond disent pourquoi.

**Le plafond se contrôle avant le téléchargement.** `files.list` rend déjà `size` ; payer le
transfert d'un fichier qu'on va refuser n'a aucun sens. `ImportPolicy.MAX_FILE_SIZE` vaut
exactement ce que vaut le dépôt manuel (20 Mo, `spring.servlet.multipart.max-file-size`) : deux
chemins d'entrée dans la même base ne doivent pas avoir deux plafonds. `size` arrive en
**chaîne** dans le JSON de Drive et **manque** aux Google Docs natifs — l'adapter le lit comme
tel plutôt que de laisser Jackson échouer.

**Un Google Doc n'a aucun binaire à télécharger** : il s'exporte, par `files.export`. L'import
choisit donc son appel sur ce que le balayage a rendu (`DriveFile.isExported()`), et pour tout
l'aval le résultat est un DOCX ordinaire — même extracteur, même découpage, même typologie
(ADR-0029). `knowledge/infrastructure/extraction/` n'a pas bougé d'une ligne.

**DOCX et non PDF, et ce n'est pas un détail** : le DOCX porte les styles `Heading`, donc
l'extracteur en tire de vraies sections, là où un export PDF ferait deviner les titres à la
taille de police (ADR-0027). C'est ce que vérifie
`GoogleDocImportTest.keeps_the_headings_of_the_google_document`, sur le texte réellement extrait.

**Le nom du document est celui du Doc suivi de `.docx`.** Un Doc n'a pas d'extension dans son
nom Drive, et `DocumentFormat.fromFilename` refuserait « Compte rendu du 3 mars » ; le nom se
complète donc au balayage, dans `GoogleWorkspaceType.exportedName`, qui **ne double pas** une
extension déjà présente — un Doc nommé `contrat.docx` est légal côté Drive.

**Le plafond de 10 Mo est celui de Google, pas celui du projet** — d'où deux constantes
distinctes dans `ImportPolicy`, `MAX_FILE_SIZE` (le dépôt, 20 Mo) et `MAX_GOOGLE_EXPORT_SIZE`
(subi). Et il **ne s'anticipe pas** : `files.list` ne rend aucun `size` pour un Doc natif, donc
le contrôle avant téléchargement ci-dessus ne peut pas jouer. Il se lit sur le refus lui-même,
un `403` portant `exportSizeLimitExceeded`, que le mécanisme de `error.errors[].reason` déjà en
place départage d'un `403` de droits : un rejet consultable dans un cas, un fichier écarté dans
l'autre. Les confondre enverrait l'utilisateur chercher un problème de partage là où il n'a
qu'un document trop gros.

**Sheets, Slides, Drawings et Forms sont ignorés en silence**, comme une image : chacun
demanderait sa typologie et ses tables (ADR-0030). Ils ne figurent donc pas dans les rejets.

**L'export n'est pas déterministe, et c'est le piège central de ce flux.** L'archive ZIP que
Google fabrique embarque des métadonnées et des horodatages : deux exports d'un Doc **inchangé**
donnent deux empreintes différentes. Un Google Doc se compare donc par son `modifiedTime`,
**jamais par son empreinte**. Le court-circuit du cas 1 précède tout calcul d'empreinte, et
`does_not_re_ingest_a_google_document_whose_modified_time_has_not_changed` le prouve avec un
bouchon qui rend deux exports différents pour un `modifiedTime` immobile. Sans cette précaution,
chaque synchronisation revectoriserait **tout le Drive**, et rien ne le signalerait qu'une
charge inexpliquée sur le worker.

**Un fichier dont Drive dit qu'il a bougé est ré-ingéré**, en revanche : le cas 1 remplace alors
le contenu, efface texte et extraits, écrase l'original et annonce `DocumentContentReplaced` —
exactement ce que fait `ReplaceDocumentContent` d'un dépôt manuel, et le worker le traite
pareil. Le contrôle d'empreinte reste, en garde-fou : un binaire dont seule la date a bougé ne
coûte rien. Ce que ce ticket ne fait toujours pas, et qui reste à DRIVE-5 : le miroir, donc le
sort des fichiers **disparus** du dossier.

**Trois sorts pour un fichier, et un seul est muet.** Il est *ignoré sans trace* quand son
format n'est pas pris en charge — un Drive est plein d'images et de vidéos, les faire figurer
noierait les fichiers dont le propriétaire peut réellement faire quelque chose. Il est *rejeté
avec un motif consultable* quand il dépasse le plafond, quand Drive refuse de le rendre, ou
quand son contenu est déjà dans la base par un autre fichier Drive. Sinon, il est *importé*. Et
**les rejets sont remplacés à chaque import, jamais cumulés** : un fichier réparé doit quitter
la liste, et une liste qui grossit à chaque passage ne se lit plus au bout de trois.

**Ce qui arrête l'import, et ce qui ne l'arrête pas.** Seuls un Drive injoignable et une
autorisation retirée l'arrêtent, et le bilan porte alors leur message ; **tout le reste écarte
un fichier et continue** — un `404` au téléchargement (supprimé entre le balayage et le
téléchargement), un hoquet du stockage objet, une écriture concurrente sur la même ligne. C'est
pourquoi l'adapter distingue ces codes d'une vraie panne : mappés sur « Google Drive est
injoignable », un fichier disparu une minute plus tôt laisserait les quatre cents suivants
dehors, sous un bilan « échec inattendu ».

**Un `403`, lui, ne se lit pas à son code : c'est le motif que Google met dans son corps qui
départage.** `error.errors[].reason` vaut `rateLimitExceeded`, `userRateLimitExceeded`,
`dailyLimitExceeded`, `backendError` ou `sharingRateLimitExceeded` → c'est un **plafonnement**,
donc une indisponibilité transitoire : l'import s'arrête et sera rejoué. Tout autre motif
(`insufficientFilePermissions`, `appNotAuthorizedToFile`, `forbidden`…) est un partage retiré :
on écarte ce fichier et on continue. Sans cette distinction, un plafonnement à mi-parcours
écrirait un rejet consultable pour chacun des quatre cents fichiers restants — quatre cents
motifs faux disant « fichier inaccessible » pour des fichiers sains, sous un import annoncé
« réussi ». **Un corps illisible penche vers le rejet** : c'est le choix le moins destructeur,
puisqu'il fait entrer les autres fichiers plutôt que de tout bloquer, et le rejet reste
consultable. Le corps est **lu, jamais journalisé** et jamais recopié dans un message affichable
— c'est la même raison qui interdit à l'adapter de journaliser `getMessage()` : un corps peut
porter un secret. Le `404` ne change pas : un fichier disparu est sans ambiguïté.

**Le balayage complet précède le premier téléchargement** : le port rend la liste de tout le
dossier et de ses sous-dossiers avant qu'un octet de contenu soit demandé. Une panne pendant le
listing — la phase la plus longue sur un gros Drive — ne laisse donc **rien** entrer du tout. La
promesse « les documents déjà entrés restent dans ma base » ne couvre que la seconde moitié de
la fenêtre.

**`files.list` se pagine ici comme au parcours des dossiers** : cent entrées par défaut et un
`nextPageToken`. Un dossier de plus de cent fichiers en perdrait la moitié **en silence** — le
pire mode d'échec possible pour un import dont personne ne relit le contenu.

**Une panne arrête la boucle et écrit le bilan par une seconde commande** (`SUCCEEDED` ou
`FAILED`, l'instant, le motif) : le balayage a annulé sa propre transaction, celle du bilan doit
donc être une autre, c'est la situation d'ADR-0028 et la même réponse. Une **autorisation
retirée** en cours d'import emprunte le même chemin et fait **deux** commandes : le bilan, et
`MarkDriveConnectionExpired`, sans quoi l'utilisateur verrait un import en échec sans savoir
qu'il doit reconnecter son compte — c'est ce que font déjà les deux contrôleurs Drive.

**Une panne avant le balayage n'écrit aucun bilan.** Les deux lectures d'entrée — la connexion,
le dossier — sont hors de ce `try`, et la route est asynchrone : un `DELETE` du dossier ou une
déconnexion entre la demande et le balayage fait sortir l'exception jusqu'au listener, le message
est rejeté sans remise en file, et le dossier garde le statut de l'import précédent, parfois
« réussi ». Rien ne **peut** être écrit sur un dossier disparu ; ce qui manquait était la trace,
et un `LOG.error` englobant la pose.

**Il n'y a pas de statut « en cours ».** Pendant tout le balayage, l'écran lit le bilan de
l'import précédent : rien n'y distingue un import qui travaille d'un import qui n'a pas commencé.

**Le worker tient la livraison AMQP pendant tout l'import**, et les deux heures de
`consumer_timeout` posées pour la vectorisation suffisent : **la vectorisation n'est pas dans
cette livraison**. Chaque document importé repart en message distinct, avec son propre budget ;
l'import ne paie que le balayage, les téléchargements et une écriture par fichier.

**Pendant ce temps, le listener ne consomme rien d'autre** : la queue du contexte n'a qu'un
consommateur, donc un seul message à la fois, et l'extraction comme l'indexation des documents
que l'import vient de créer font la queue derrière le balayage complet. Sur cinq cents fichiers,
c'est un silence long, et il ne signale aucune panne.

**Un document en échec rattaché à un fichier Drive n'est plus relançable par l'import** : le
cas 1 court-circuite pour toujours, quel que soit le motif de l'échec. Il faut le supprimer et le
redéposer. C'est le pendant exact du document resté `EXTRACTED` de la section « découpage et
vectorisation », et il se réglera au même endroit.

**Ce que l'API rend de tout ça.** `DocumentView` et `DocumentDetailView` portent la `source`
(`MANUAL` ou `GOOGLE_DRIVE`) et, quand Drive en a rendu un, le lien d'ouverture — jamais
l'identifiant du fichier Drive, dont aucun écran ne ferait rien. `WatchedFolderView` porte le
bilan du dernier import (instant, statut, motif d'échec, absents tant qu'aucun import n'a eu
lieu), le nombre de documents que le dossier a apportés et **ses rejets**, sans quoi le mot
« consultables » ci-dessus ne voudrait rien dire. Un rejet ne rend que son nom de fichier et
son motif, pour la même raison qu'un document ne rend pas son identifiant Drive.

Les rejets voyagent **avec le dossier** plutôt que derrière une route à eux : ils sont chargés
de toute façon (`@ElementCollection` en `EAGER`), ils se lisent à côté du bilan auquel ils
appartiennent, et une liste de dossiers surveillés se compte sur les doigts d'une main. Le prix
est un **1 + N** : cet `EAGER` fait une requête de rejets par dossier, là où le comptage des
documents ci-dessous, lui, est bien groupé en une seule. L'écran entier ne tient donc pas en une
requête.

**Un document sait par quel dossier surveillé il est entré** — colonne `watched_folder_id`,
posée à l'import et réécrite au seul cas du dossier ré-surveillé, ci-dessus —, et c'est ce qui
permet de compter. Le comptage est
**une seule requête groupée** pour tous les dossiers du propriétaire : un `count` par dossier
dans la boucle d'affichage serait autant de requêtes que de dossiers. Ce que ça ferme : le
comptage ne se déduit pas du Drive, qu'il faudrait rebalayer pour savoir quels fichiers sont
sous quel dossier.

Ce qu'aucun écran ne fait encore : appeler ces routes — c'est DRIVE-7, et il est désormais
**front seul**.

### Le flux du dépôt d'un document

`POST /api/documents` reçoit un multipart (`file`), dispatche `UploadDocument` et répond
`201` sans corps — même raison qu'à l'inscription : une commande ne retourne rien, et
`GET /api/documents` rend l'état complet de la base, c'est lui que le front relit.

**L'identité d'un document est son empreinte SHA-256, jamais son nom.** Le même contenu
redéposé sous un autre nom est refusé ; deux contenus différents portant le même nom sont
deux documents. Le nom se change d'un clic, le contenu non. `Checksum.of(byte[])` calcule
l'empreinte dans le domaine — `MessageDigest` vient du JDK, pas d'un framework.

Trois refus, trois codes, parce qu'ils appellent trois gestes différents : `415` change de
fichier (le message énonce les formats acceptés, construit à partir de l'énumération
`DocumentFormat`, jamais recopié), `409` renvoie vers le document existant dont il porte
l'identifiant, `413` allège le dépôt. Le doublon est détecté par une lecture avant
l'écriture, parce que c'est elle qui permet de *désigner* le document existant ; la
contrainte `UNIQUE (owner_id, checksum)` reste le filet, et ne se referme que sur deux
dépôts simultanés du même contenu.

L'unicité porte sur le couple **(propriétaire, empreinte)** : deux comptes qui déposent le
même PDF déposent deux documents. Toute la base est cloisonnée de la même façon — les trois
routes lisent le `sub` du jeton, et `findByIdAndOwnerId` rend le document d'autrui aussi
introuvable qu'un identifiant inexistant. Un `403` confirmerait l'existence de
l'identifiant demandé.

`spring.servlet.multipart.resolve-lazily` est à `true`, et ce n'est pas un réglage de
confort : sans lui, `MaxUploadSizeExceededException` est levée par `DispatcherServlet`
**avant** qu'un contrôleur soit désigné, et seul un `@RestControllerAdvice` global la
capterait — ce que ce projet évite, pour que la traduction des refus reste auprès de la
route concernée. Avec lui, le multipart est résolu au moment où le contrôleur lit son
argument, et l'`@ExceptionHandler` d'`UploadDocumentController` la voit.

L'ordre des quatre étapes est un choix : contrôle du doublon, écriture en base
(`saveAndFlush`), original, puis publication de `DocumentUploaded`. L'original avant la
publication parce que **sa conservation ne participe à aucune transaction** — vrai du
système de fichiers d'hier comme du stockage objet qui l'a remplacé (ADR-0020) : écrit
après le commit, il manquerait au consommateur qui relit ; écrit avant la ligne, il
survivrait à un rollback en désignant une ligne qui n'existe pas. La
publication, elle, est en dernier pour se lire comme ce qu'elle est, une annonce : sa place
dans la séquence n'a aucune portée transactionnelle, puisqu'elle ne prend effet qu'**au
commit** — un rollback n'annonce rien, et le broker injoignable à cet instant perd
l'événement (ADR-0023).

`DELETE /api/documents/{id}` efface la ligne puis l'original. L'extraction n'est pas
mentionnée, et ne le sera pas : les `ON DELETE CASCADE` de ses tables l'emportent avec le
document, et ce handler n'a pas eu à changer quand elles sont arrivées.

`GET /api/documents/{id}` rend un document **et ce qui en a été extrait** : le nom, le
format, la typologie, le statut, le motif d'échec le cas échéant, sa provenance (voir « Le
flux de l'import d'un dossier surveillé »), et — quand elle existe — l'extraction propre à sa
typologie. Une seule requête pour tout l'écran de détail, et non une route `/extraction` à
part : celle-là aurait rendu `404` sur un document simplement en file
d'attente. Le cloisonnement est le même que partout (`findByIdAndOwnerId`) : le document
d'autrui est introuvable, jamais interdit. Le vide devient `404` dans le contrôleur, la query
rendant un `Optional` — une query ne lève pas.

`GET /api/documents/{id}/content` rend le fichier **tel qu'il a été déposé**, sous son nom
d'origine — `Content-Disposition: attachment`, nom encodé en RFC 5987 parce qu'un accent
dans un en-tête HTTP sans encodage est un octet non spécifié. Le `Content-Type` vient de
`DocumentFormat.mediaType()` : le type MIME est une propriété du format, au même titre que
son extension, et non le `Content-Type` du multipart, qui n'a jamais été stocké. La route ne
regarde **jamais le statut** : un document dont l'extraction a échoué n'a plus que son
original, c'est précisément ce qu'on vient y chercher.

Deux absences, deux messages, un seul code. Le document inconnu rend le `404` habituel ; un
document bien présent dont l'objet a disparu du stockage rend `404 {"message": "L'original
de ce document n'est plus disponible."}` — dire « document introuvable » mentirait, l'écran
le montre. Et cette seconde absence est la seule query du contexte qui **lève là où un
`Optional` vide serait attendu** : une ligne
`knowledge_documents` sans son objet n'est pas un résultat vide, c'est une rupture
d'invariant qu'aucun chemin nominal ne produit (voir la spec du téléchargement, décision 6).
Le stockage injoignable, lui, rend `503`, comme la recherche pour Ollama.

Côté front, `DocumentsView` (`/documents`, entrée « Documents » de la barre latérale) porte
les trois gestes sur un seul écran : un `FileUpload` PrimeVue en mode avancé et
`custom-upload` — l'envoi passe par `uploadDocument` dans `src/api/client.js`, jamais par
l'URL du composant —, la liste dans un `DataTable`, et la suppression derrière un
`ConfirmPopup` (d'où `ConfirmationService` dans `main.js`). Le `201` n'ayant pas de corps,
chaque dépôt et chaque suppression relisent `GET /api/documents`. Aucun plafond de taille n'est
posé côté navigateur : le `413` et son message viennent du serveur, seule source des refus.
Aucun store : aucun autre écran ne partage cet état, la vue appelle `src/api/` directement,
et c'est elle qui déconnecte sur un `401`, comme le layout le fait pour le profil.

**Le mode avancé, et non `basic`, parce que l'écran accepte plusieurs fichiers d'un coup** : il
porte sa propre zone de glisser-déposer, et `@uploader` reçoit toute la fournée. L'envoi est
**séquentiel** — la route prend un fichier, et une rafale parallèle courrait contre le contrôle
de doublon qui précède chaque écriture. Chaque refus est donc rendu **sous le nom du fichier
qu'il vise** : un message global mentirait dès qu'un dépôt sur trois est refusé, et
`existingDocumentId` des `409` met en évidence **les** lignes des doublons plutôt que de laisser
l'utilisateur les chercher. Un `401` en cours de série l'arrête net : les fichiers suivants n'y
récolteraient que d'autres `401`.

Deux pièges du mode avancé, tous deux payés une fois. `FileUpload` refuse lui-même un fichier
déposé qui sort d'`accept`, avec un message **en anglais et codé en dur** — c'est une prop,
`invalid-file-type-message`, pas une entrée de locale, donc `primelocale/fr` ne le porte pas — et
son `clear()` emporte ses messages avec ses fichiers, ce qui effaçait le refus à la fin de la
série. La vue pose donc le message français et **draine** les messages du composant dans ses
propres refus avant de le vider : une seule liste porte tout. Conséquence à connaître :
`ACCEPTED_EXTENSIONS` n'est plus le filtre de confort qu'ADR-0022 décrivait, c'est un garde
bloquant — une divergence y retire un format que le serveur aurait accepté, au lieu de le laisser
passer jusqu'au `415`.

**La liste se relit toutes les 2 s tant qu'un document n'est pas dans un statut terminal**, et
s'arrête dès qu'ils le sont tous — un `setTimeout` réarmé après chaque lecture **réussie**,
jamais un `setInterval` qui empilerait les requêtes, désarmé à la sortie de l'écran. Ce qui dit
quels statuts sont terminaux vit dans `documentStatus.js`, **hors du `.vue`** : une horloge qui
ne s'arrête jamais ne se voit pas à l'écran, elle se voit dans les journaux du serveur six mois
plus tard, et c'est exactement le genre de logique que ce projet sort d'un composant pour la
tester — comme `sse.js` et `answerSegments.js`. `DocumentStatusTag` y a suivi ses deux tables de
libellés : les statuts n'étaient pas énumérés à deux endroits, ils ne le seront pas.


`DocumentDetailView` (`/documents/:id`, atteint par l'œil de chaque ligne) montre ce qui a
été extrait : les métadonnées, le statut, le motif d'échec le cas échéant, puis les blocs
titrés. L'écran est **adressable** — un texte extrait se relit, se partage par son URL et
survit à un F5, ce qu'une modale sur la liste n'aurait pas offert. C'est `document.type`, la
typologie, qui décide du rendu : une typologie sans affichage le dit plutôt que de rendre une
page vide. `DocumentStatusTag` porte le libellé et la sévérité d'un statut pour les deux
écrans — le motif était copié, il est devenu un composant.

Les deux écrans portent le même bouton de téléchargement, `DownloadDocumentButton` : le jeton
voyageant en en-tête, un `<a href>` ne rapporterait qu'un `401`, et le fichier est donc lu par
`fetchDocumentContent` puis remis au navigateur par une ancre `download` fabriquée, cliquée et
révoquée. Le composant porte l'appel et son état occupé mais **pas la déconnexion** : il émet
son erreur, et chaque vue la passe à son propre `handle`. Le nom du fichier lui est passé en
prop plutôt que décodé du `Content-Disposition` — les deux valeurs viennent de la réponse que
l'écran affiche déjà.

### Le flux du remplacement du contenu d'un document

`PUT /api/documents/{id}` reçoit un multipart (`file`) et rend `200` **sans corps** — même raison
qu'au dépôt : rien du document n'est à exposer, et `GET /api/documents` rend l'état complet. Les
refus sont ceux du dépôt — `422` sur le champ `file`, `415`, `409`, `413` — plus le `404` du
document introuvable, cloisonné comme partout : le document d'autrui est introuvable, jamais
interdit.

**L'identité ne bouge pas.** Le document garde son identifiant, sa date de dépôt et sa place dans
la liste ; seuls son nom, son format, son empreinte, sa taille et son statut changent. C'est toute
la différence avec le « supprimer puis redéposer » qui était jusqu'ici la seule façon de mettre un
document à jour.

**Une empreinte identique court-circuite tout.** Ni écriture, ni écrasement de l'original, ni
publication, ni le moindre appel au modèle d'embedding. Ce n'est pas une optimisation de confort :
c'est le cas **nominal** d'une synchronisation, et chaque revectorisation inutile immobilise le
worker plusieurs minutes. Le contrôle de format, lui, passe **avant** le calcul de l'empreinte :
un `.png` est refusé même s'il portait par miracle le contenu du PDF en place.

**Le nom d'un document ne suit que son contenu.** Un `PUT` qui porte le même fichier sous un autre
nom ne renomme rien : le court-circuit est sur l'empreinte, et il passe avant toute écriture.
Renommer sans remplacer demandera une route à soi.

Le contenu neuf annonce `DocumentContentReplaced`, et **non un `DocumentUploaded` republié** : un
événement est un fait au passé, le document n'a pas été déposé une seconde fois. Le coût est un
`@RabbitHandler` de plus, qui dispatche la même `ExtractDocumentText` que le dépôt ; la clé de
routage `knowledge.document-content.replaced` se dérive du nom et le binding `knowledge.#` la
couvre déjà.

**C'est le handler du remplacement qui efface le texte et les extraits de l'ancien contenu**, dans
la transaction du bus, entre l'écriture de la ligne et l'écrasement de l'original. Le
« delete-before-write » du pipeline ne suffit pas ici : il vient **après** le premier appel qui
peut échouer — la vectorisation d'un côté, le plancher de caractères de l'autre —, donc une
ré-ingestion refusée laisserait en base les extraits d'une version que le document ne porte plus,
sous une ligne qui affiche déjà la nouvelle empreinte, et la recherche les rendrait. Cet
effacement du pipeline garde tout son rôle : il répond à la **redélivrance** du même contenu, ce
qu'AMQP autorise, pas au remplacement.

**`Document` porte un `@Version` depuis cette route**, qui est la seule à muter un document en
parallèle du worker. Sans lui, un `PUT` commité pendant que le worker indexe encore la version
précédente se faisait écraser par le `save` final de celui-ci — Hibernate met à jour toutes les
colonnes —, et la ligne revenait en silence au nom, au format, à l'empreinte et à la taille d'un
contenu que le stockage ne portait plus : l'empreinte périmée ôtait alors au dépôt son
court-circuit comme son `409`. Le worker qui perd la course échoue désormais sur une
`OptimisticLockException`, que le listener écrit en `FAILED` ; l'événement de remplacement déjà en
vol relance le traitement, et le document repasse `PENDING` puis `READY`. Transitoirement faux et
réparé seul, là où l'état d'avant était durablement faux et muet.

### Le flux de l'extraction du texte

Le worker reçoit `DocumentUploaded` et dispatche `ExtractDocumentText`, qui relit le
document, relit son original par le port de stockage, choisit l'extracteur de son format,
remplace le texte extrait, pose `EXTRACTED` et annonce `DocumentTextExtracted`.

**Le format produit est le livrable durable de ce flux** : `ExtractedText`, une suite
ordonnée de `TextBlock` portant chacun le titre de sa section, son niveau et son corps
normalisé. Un bloc est une **section**, pas un paragraphe — un document sans titre rend un
unique bloc (ADR-0024). Il vit dans deux tables cascadées, `knowledge_text_extractions` et
`knowledge_text_blocks`, **nommées par la typologie et non par le document** : une autre
typologie aura les siennes (ADR-0030).

Quatre extracteurs derrière un port, un par format, et non Apache Tika (ADR-0026) : les
styles `Heading1..9` d'un DOCX et les `#` d'un Markdown sont le livrable, pas du balisage à
traverser. Un PDF, lui, ne porte aucune sémantique de titre : son sommaire d'abord, la
taille de police en repli (ADR-0027), et les frontières de paragraphe y sont perdues — une
section de PDF arrive à RAG-5 comme un seul paragraphe. **`ExtractDocumentTextHandler`
refuse de démarrer si une constante de `DocumentFormat` n'a pas son extracteur** : un format
accepté au dépôt doit être lisible.

Un document dont il ne sort pas cinquante caractères **échoue explicitement** (ADR-0025) :
c'est le cas du PDF numérisé, et le vide silencieux ne se verrait qu'à la première question
restée sans réponse. L'effacement du texte précédent avant l'écriture n'est pas décoratif :
AMQP livre au moins une fois et `document_id` est `UNIQUE`.

### Le flux du découpage et de la vectorisation

Le worker reçoit `DocumentTextExtracted` et dispatche `IndexDocumentText`, qui relit le
document et son texte, découpe, vectorise, remplace les extraits, pose `READY` et annonce
`DocumentTextIndexed`.

**Le découpage est une logique de domaine pure** — `RecursiveChunker`, aux côtés des trois
policies — et quatre niveaux de repli : une section sous le plafond donne un extrait ; sinon
on coupe aux paragraphes (la double ligne vide que `TextBlock.normalise` garantit), puis aux
phrases (`BreakIterator`, le JDK), puis net, faute de frontière. Deux extraits consécutifs
d'une même section se recouvrent d'environ 90 tokens repris **en phrases entières** ; le
recouvrement ne franchit jamais une frontière de section, et il cède devant le plafond — c'est
un confort, le plafond est un invariant.

**Le comptage passe par un port** (`TokenCounter`, adapter jtokkit en `cl100k_base`) parce que
c'est la toise d'un autre : `bge-m3` s'appuie sur un sentencepiece XLM-RoBERTa. C'est sans
danger — `cl100k` sur-compte le français, donc le plafond est conservateur — mais 600 est un
proxy, pas une mesure. Les tests du découpage prennent un compteur « un mot égale un token »,
ce qui rend les frontières lisibles dans les assertions.

**Ce qui part au modèle est préfixé, ce qui est stocké ne l'est pas.**
`Chunk.contextualised(filename)` rend `Document: rapport.pdf — Section: Introduction` suivi du
corps, et c'est la seule méthode qui connaisse cette forme ; la colonne `text` porte le corps
nu. Changer la forme du préfixe ne demandera donc pas de réécrire la base, seulement de
revectoriser — et l'écran reste lisible. Ce que ça suppose et qui est vrai : aucune route ne
renomme un document.

**Tout tient dans la transaction du bus, appels Ollama compris.** Le « tout ou rien » est
gratuit : c'est le rollback. Un Ollama à terre ne laisse aucun extrait derrière lui, le
document passe `FAILED` en gardant son texte extrait, et le motif nomme la vectorisation —
c'est à ça que sert `DocumentProcessingException`, mère commune des refus d'extraction et de
vectorisation, seule famille dont le listener montre les messages. Le prix est une connexion
PostgreSQL tenue plusieurs minutes par document — une centaine d'extraits fait quatre lots, et
un lot de 32 auprès de `bge-m3` sur CPU prend près d'une minute : pesé, et tenable pour une
application mono-utilisateur dont le worker consomme en séquence.

**Un document extrait avant l'arrivée de cette fonctionnalité reste `EXTRACTED`.** Rien ne
réémet `DocumentTextExtracted` pour lui, et il n'existe aujourd'hui aucune route qui
réindexe — ce sera RAG-7. La seule façon de le faire avancer est de le supprimer et de le
redéposer, ou de republier à la main son événement `knowledge.document-text.extracted` sur le
broker.

### Le flux de la recherche

`GET /api/search?q=…` vectorise la question par le même port qu'à l'indexation, puis rend les
huit extraits les plus proches au cosinus, chacun avec son texte, le nom de son document, sa
position et son score. Huit est une règle du domaine (`SearchPolicy.RESULTS`) et non un
réglage d'exploitant : c'est RAG-9 qui les consommera pour composer une réponse. Aucun `?k=`
n'anticipe une question qu'on ne se pose pas encore.

**Le score est une similarité — `1 - distance`, donc 1 pour identique — et aucun plancher ne
le filtre.** C'est une route de diagnostic : les scores faibles sont précisément ce qu'on
vient y regarder, un défaut de pertinence ne s'instruisant pas autrement. Une liste vide ne
vient donc que d'une base vide.

La requête est du **SQL natif** sur `SpringDataTextChunkRepository`, avec un `CAST` explicite
en `vector` — pgvector n'accepte aucune conversion implicite — et une jointure vers
`knowledge_documents` qui porte à la fois le cloisonnement et le nom du document. C'est cette
jointure imposée qui écarte le HQL, deux agrégats ne se référençant que par identifiant
(ADR-0006). Le littéral `[0.1,0.2,…]` se fabrique dans l'adapter : le domaine ne connaît
qu'`Embedding` et ne reçoit que des `ChunkMatch`.

**La question part nue**, sans le préfixe `Document: … — Section: …` que porte l'extrait à
l'indexation : `bge-m3` ne réclame aucune instruction de rôle, contrairement à un e5 — voir
la spec de la recherche vectorielle, décision 5.

L'appel de vectorisation a lieu **dans la transaction `readOnly` du query bus**, comme tout ce
qu'un handler déclenche : la seconde d'aller-retour vers Ollama y tient une connexion
PostgreSQL, ce qui est tenable pour une lecture qui n'écrit rien dans une application
mono-utilisateur. Un Ollama à terre rend `503`, une question vide `422` sur le champ `q`.

**Un document resté `EXTRACTED` n'est pas cherchable**, et rien ici ne le rattrape : c'est
RAG-7.

### Le flux de la conversation

`POST /api/chat` confie la question à un **agent** qui dispose d'un outil de recherche et
**décide** s'il l'appelle. Ce n'est pas un RAG en un coup : une question conversationnelle
n'ouvre aucune recherche, et une question documentaire peut en enchaîner plusieurs avant de
répondre.

La réponse voyage en **Server-Sent Events**, toujours dans cet ordre : `token` (un fragment
de texte, zéro ou plusieurs fois), `sources` (une fois, les extraits cités), puis `done` (le
verdict de la réponse). Un échec en cours de génération émet `error` à la place — `sources`
et `done` **ne sont alors jamais émis** : un client qui reçoit `error` sait qu'aucune
conversation complète ne suivra sur ce flux.

La boucle vit dans `ConversationAgent`, **hors des bus et sans transaction** : une
conversation dure des minutes, et une transaction ouverte tiendrait tout ce temps une
connexion PostgreSQL. Chaque recherche qu'elle déclenche passe par le `QueryBus`
(transaction courte, le temps d'un appel), la trace finale par le `CommandBus`, **après la
fermeture du flux** — son échec ne doit rien coûter à une réponse déjà livrée au client.
Cette trace n'est écrite que sur le chemin nominal : **une conversation interrompue par une
erreur ou une déconnexion n'en laisse aucune**, `RecordAgentRun` n'étant dispatché qu'après
un `answer()` allé à son terme.

**Les tokens sont retenus jusqu'à la première citation valide.** `CitationBuffer` les
tamponne le temps qu'une référence `[n]` complète apparaisse dans le texte produit ; une
réponse qui a cherché sans rien citer n'atteint jamais l'écran, elle est remplacée par
l'aveu d'ignorance — c'est le garde-fou qui interdit qu'une invention parte sourcée par
erreur.

Le catalogue des sources (`SourceCatalogue`) est **cumulatif et dédoublonné** sur
`(documentId, position)` : un extrait déjà cité garde son numéro pour toute la
conversation, même retrouvé par une recherche ultérieure — le modèle ne cite jamais deux
numéros pour un même passage.

La définition de l'agent se partage entre deux fichiers qui doivent rester en phase : la
prose dans `src/main/resources/agents/document-agent.md`, l'outil, le budget et la
température dans `DocumentAgent`. `AgentDefinitionLoader` échoue au démarrage si un
placeholder du fichier markdown reste non résolu — mieux vaut un démarrage refusé qu'un
prompt à moitié rédigé envoyé en production.

Bornes du dispositif : **4 tours**, **120 s** de budget d'exécution pour l'agent — vérifié
**en tête de boucle seulement**, donc un tour déjà lancé va à son terme même au-delà. Un
nom d'outil inventé ou un argument manquant — attendus d'un petit modèle à outils — sont
rendus au modèle comme des erreurs d'outil ordinaires et consomment un tour, pas une panne.

**L'emitter SSE se dimensionne sur le pire cas, pas sur le seul budget de l'agent** : 120 s
+ un tour au pire (le délai de lecture d'Ollama, 180 s — `OllamaChatConfiguration`) + 30 s
de marge, soit **330 s**. Un emitter plus court expirerait pendant que le serveur travaille
encore, et pas seulement dans un cas dégradé : un tour mesuré prend ~40 s sur cette machine,
et le cas nominal de quatre tours en fait ~160 s à lui seul, au-dessus d'un emitter à 150 s.
L'emitter journalise son propre cycle de vie (`onTimeout`, `onError`, `onCompletion`), avec
l'`ownerId` en contexte — sans corrélation, une ligne de journal sur une pile qui sert
plusieurs conversations ne se relie à aucune d'elles. Une expiration et une déconnexion
empruntent le même chemin d'exception (`SseEmitter.send` lève au prochain envoi), mais un
drapeau posé par `onTimeout` distingue les deux causes dans le message.

**Quatre décisions attendent leur ADR**, faute d'accord préalable du propriétaire du
dépôt sur leur rédaction — voir `docs/superpowers/specs/2026-09-04-reponse-sourcee-design.md`,
section « Ce qui reste à arbitrer » : l'orchestration hors des bus et sans transaction, la
génération confiée à LangChain4j là où la vectorisation est écrite à la main, la rétention
des tokens jusqu'à la première citation valide, et l'agent qui choisit lui-même s'il
cherche.

**Quatre limites du garde-fou sont assumées, et ne doivent pas se lire comme une garantie
totale :**

- **Il ne s'applique que si une recherche a eu lieu.** Si le modèle répond de mémoire sans
  jamais appeler l'outil, la conversation est classée `CONVERSATIONAL` et son texte part
  inchangé : la seule défense contre l'invention y est alors la consigne écrite dans
  `document-agent.md`, pas un contrôle programmatique. C'est le trou assumé de la spec, et
  c'est le ticket d'évaluation (RAG-14) qui le mesurera.
- **Un `[n]` recopié depuis un extrait compte comme une citation valide.** Les documents
  réels portent souvent leurs propres appels de note (`[2]`, `[12]`) ; un modèle qui recopie
  une phrase qui en contient un rouvre le tampon et fait attacher une source sans rapport à
  la réponse. C'est le mode d'échec le plus probable du garde-fou sur un vrai corpus.
- **L'abandon du client n'est détecté qu'après la première citation.** Seuls les envois qui
  traversent le tampon touchent la socket ; tant qu'il est fermé, une déconnexion est
  invisible et la génération va au bout de son budget. Corollaire : le flux peut rester
  **silencieux plusieurs dizaines de secondes** avant son premier octet, ce qu'un proxy
  intermédiaire peut mal prendre. Un heartbeat réglerait les deux, et c'est un ticket à
  part : un envoi périodique depuis un second thread sur un `SseEmitter`, qui n'est pas
  thread-safe en écriture concurrente, se conçoit, il ne s'improvise pas.
- **Un tour qui porte à la fois un texte cité et un appel d'outil** ouvre le tampon et
  streame ce texte, puis la boucle continue. Le garde-fou du tour suivant ne voit que le
  dernier texte : l'événement `sources` peut alors partir **vide** alors que l'écran affiche
  un texte portant un `[n]`. Improbable, non corrigé, et le correctif demande de faire vivre
  le tampon d'un tour à l'autre.

Conséquence pour le premier client de la route : **`POST` + SSE ne se consomme pas avec
`EventSource`** côté navigateur, qui ne fait que du `GET` — il faut un `fetch` et la lecture
manuelle de son `ReadableStream`.

Ce client est `ChatView` (`/chat`, entrée « Conversation » de la barre latérale, sous le
layout connecté). Les échanges vivent dans l'état de la page et **rien n'est persisté** : un
F5 vide le fil, et les traces de `knowledge_agent_runs` ne sont lisibles par aucune route.
À ne pas confondre avec un oubli d'affichage : **chaque question part seule**, l'agent n'a
aucune mémoire du tour précédent. Le fil à l'écran est un empilement d'échanges
indépendants, pas une conversation qui se souvient.

Quatre couches, et la frontière est motivée. `src/api/sse.js` ne connaît que le format de
trame — ni route, ni en-tête, ni nom d'événement. `askAgent`, dans `src/api/client.js`,
connaît la route, ses refus et ses noms d'événements. `AnswerText` et `AnswerSources` rendent
une réponse et ses sources, un `[n]` du texte dépliant la source correspondante. `ChatView`
empile les échanges. **Les deux morceaux qui peuvent casser en silence — la lecture des
trames et le découpage sur les `[n]` — vivent hors des `.vue` précisément pour être testés
unitairement** : c'est la réponse de ce projet à ADR-0016, qui renonce aux tests de rendu.

**`sse.js` existe pour deux pièges du fil**, tous deux du ressort de
`SseEmitter.SseEventBuilderImpl`. Un fragment qui porte un saut de ligne est découpé en
plusieurs lignes `data:` — `writeStringData` appelle `appendEscaped(content, "\ndata:")` —
et le lecteur les recolle avec un `\n`, sans quoi une réponse multi-ligne arriverait en
morceaux. Et Spring écrit `data:` **sans espace à lui** : le client ne retire donc pas
l'espace de tête que la spécification SSE dit de retirer, ce qui souderait le dernier mot
d'un fragment sur le premier du suivant.

L'indicateur d'attente n'est pas décoratif : le flux reste muet jusqu'à la première citation
valide, et une réponse conversationnelle arrive d'un bloc à la fin, donc c'est le seul retour
visible pendant plusieurs dizaines de secondes. Quitter l'écran annule la requête, et c'est
cette annulation qui arrête la génération côté serveur — avec la réserve déjà écrite plus
haut : l'abandon n'atteint le serveur qu'à son **envoi suivant**, donc il peut mettre autant
de temps à mordre tant que le tampon de citation est fermé.

Enfin, « base interrogeable » se juge sur les documents `READY`, pas sur leur nombre : un
document extrait mais pas encore indexé n'est pas cherchable, et c'est ce qui empêche
l'invitation à déposer de mentir sur une base pleine de fichiers en attente.

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

### Les événements métier (`shared/event`) et le rôle worker

Un handler qui a quelque chose à annoncer publie un **événement métier** par le port
`DomainEventPublisher` — en dernière étape, et explicitement : `UploadDocumentHandler`
publie `DocumentUploaded` après avoir conservé l'original. Les événements sont des records
au passé dans `<contexte>/domain/event/`, avec un seul contrat (`occurredAt`), sans import
Spring. **Ce ne sont pas des `ApplicationEvent`** : les événements techniques de Spring
restent techniques.

L'adapter (`shared/event/amqp/`) n'envoie qu'**après le commit** de la transaction ouverte
par le bus : un rollback n'annonce rien. L'inverse n'est pas garanti — voir ADR-0023.
Le transport est RabbitMQ : un exchange topic `domain.events`, une clé de routage
`<contexte>.<objet>.<fait>` dérivée de la classe (`knowledge.document.uploaded` pour
`DocumentUploaded` — le contexte vient du package, l'objet et le fait du nom simple découpé
sur ses majuscules, le dernier mot étant le fait), un corps JSON, et cette même chaîne en
en-tête de type — jamais le nom qualifié. Le domaine ne nomme rien.

**La consommation vit dans un processus à part.** Le profil Spring `worker` coupe Tomcat
(`spring.main.web-application-type=none`, donc ni contrôleurs, ni Swagger, ni actuator
HTTP), efface `SecurityConfig` (`@Profile("!worker")`) et fait exister les listeners
(`@Profile("worker")`). Sans profil, le processus est l'API. Même image, même jar : en
développement, `compose.yaml` lance `app` et `worker` ; en production, deux déploiements
Coolify de la même image, le second avec `SPRING_PROFILES_ACTIVE=worker`.

Un listener (`<contexte>/infrastructure/messaging/`) est un adapter entrant au même titre
qu'un contrôleur : il dispatche une commande sur le bus, aucune règle métier. **Une queue par
contexte, un seul listener par contexte** : la queue `domain.<contexte>.events` est liée sur
`<contexte>.#` et reçoit tout ce que le contexte annonce ; le listener porte `@RabbitListener`
sur la classe et un `@RabbitHandler` par événement, l'en-tête de type choisissant la méthode.
Deux classes `@RabbitListener` sur la même queue se disputeraient les messages, et celle qui
ne connaît pas le type le rejetterait — l'événement serait perdu. La queue et son binding sont
déclarés dans les deux rôles. Une
exception dans un listener rejette le message **sans remise en file**
(`default-requeue-rejected=false`) : sans ce réglage, un message toxique tournerait en
boucle. Pas de dead-letter queue, pas de retry : un échec finit en `FAILED` sur le document,
pas rejoué. **Et il y finit depuis une seconde transaction** — `KnowledgeEventListener`
rattrape l'exception, dispatche `MarkDocumentProcessingFailed`, puis acquitte. Un statut
d'erreur écrit dans la transaction que le bus vient d'annuler disparaîtrait avec elle, et le
document resterait éternellement en attente (ADR-0028).

**Le worker tient une livraison AMQP le temps de vectoriser tout un document**, ce qui peut
se compter en minutes — voir plus haut. `compose.yaml` pose `consumer_timeout` à deux heures
côté broker de développement pour cette raison : au-delà du défaut de 30 minutes, RabbitMQ
ferme le canal et remet le message en file quoi que dise `default-requeue-rejected`, qui ne
gouverne que `basicNack`. Un broker de production doit recevoir le même réglage — il vit dans
Coolify, hors de ce dépôt (ADR-0013). Le symptôme sans lui : un document volumineux qui ne
quitte jamais `EXTRACTED`, et le journal du worker qui rejoue la même indexation en boucle.

Les tests du socle observent des commits : ils ne sont pas `@Transactional` et nettoient
en `@AfterEach`. Le rôle worker se teste avec `@ActiveProfiles("worker")` et
`webEnvironment = NONE`. Conséquence à connaître : la propriété qui **définit** le profil
(`spring.main.web-application-type=none`) n'est vérifiée par aucun test — le test du worker
force `webEnvironment = NONE` par construction, donc il obtiendrait un contexte sans Tomcat
même si `application-worker.yml` ne posait rien. Seul le passage sur la pile
`docker compose` constate que le conteneur `worker` démarre sans serveur HTTP.

### Persistance

Flyway est **maître du schéma** ; Hibernate tourne en `ddl-auto: validate` et se
contente de vérifier la correspondance entités ↔ tables au démarrage. Les tables
sont préfixées par leur contexte (`users_users`).

`Email` est projeté sur un `varchar(320)` par `EmailAttributeConverter`, annoté
`@Converter(autoApply = true)` et rangé dans `users/infrastructure/persistence/`. Aucune
classe ne le référence : Hibernate ne le connaît que parce que le scan d'entités part du
package de `SecondBrainApplication`. Ne pas le supprimer au motif qu'il paraît inutilisé —
détail dans les règles backend, section « Adapters ».

`Checksum` suit exactement le même chemin, par `ChecksumAttributeConverter` dans
`knowledge/infrastructure/persistence/`. Les deux converters sont invisibles au code et ne
tiennent qu'au scan de packages : la même mise en garde vaut pour l'un comme pour l'autre.

`DriveAuthorizationState` et `RefreshToken` en ont un chacun, sur le même modèle — mais celui du
jeton **chiffre** au passage, et porte donc `@Component` en plus de `@Converter(autoApply = true)` :
il lui faut la clé par injection, que Hibernate seul ne saurait pas lui donner. C'est le seul de la
famille dans ce cas.

La connexion à un Drive vit dans `knowledge_drive_connections` — `owner_id` **`UNIQUE`**, ce qui
pose la règle « une connexion par compte » là où elle ne se contourne pas — et la demande
d'autorisation en cours dans `knowledge_drive_authorization_requests`. Les deux cascadent à la
suppression du compte.

Les dossiers mis sous surveillance vivent dans `knowledge_drive_watched_folders`, rattachés à la
**connexion** et non au propriétaire : `UNIQUE (connection_id, drive_folder_id)`, et deux cascades
en enfilade — déconnecter un compte Google emporte ses dossiers surveillés, supprimer le compte
emporte la connexion, donc les dossiers avec elle. **Ni l'une ni l'autre n'emporte les documents
que ces dossiers ont apportés**, c'est la promesse de DRIVE-1 et elle tient parce que rien ne relie
un document à un dossier surveillé.

Le bilan du dernier import vit sur le dossier surveillé lui-même (`last_import_at`,
`last_import_status`, `last_import_error`) et les fichiers qu'il a laissés dehors dans
`knowledge_drive_import_rejections`, une `@ElementCollection` ordonnée par `rejection_position`
— d'où la clé primaire composite et l'absence d'identifiant propre, comme les sources d'une
trace de conversation. `last_import_status` est **`NULL` tant qu'aucun import n'a eu lieu** : un
dossier mis sous surveillance et jamais importé n'a ni réussi ni échoué, et c'est ce que dit son
absence.

La provenance d'un document vit dans `knowledge_documents`, en cinq colonnes : `source`
(`MANUAL` par défaut, ce qui vaut pour toutes les lignes entrées avant l'import Drive),
`drive_file_id`, `drive_web_view_link` et `drive_modified_time`, cette dernière posée d'avance
pour DRIVE-5 (voir « Le flux de l'import d'un dossier surveillé »). `watched_folder_id`, la
cinquième, dit par quel dossier surveillé le document est entré : elle **ne porte aucune clé
étrangère**, parce que cesser de surveiller un dossier ne retire pas ses documents et que
déconnecter un compte Google emporte ses dossiers surveillés — c'est
`knowledge_agent_run_sources.document_id` une seconde fois. Une seconde unicité s'y
ajoute, `UNIQUE (owner_id, drive_file_id)` : elle **ne gêne pas les dépôts manuels**, les `NULL`
de PostgreSQL étant distincts entre eux, et elle porte le propriétaire pour la même raison que
`(owner_id, checksum)` — deux comptes qui surveillent le même Drive partagé importent chacun
leur document.

Le texte extrait d'un document vit dans **deux tables**, `knowledge_text_extractions` (une
ligne par document, `document_id` `UNIQUE`) et `knowledge_text_blocks` (ses blocs, une
`@ElementCollection` ordonnée par `block_position`, rattachés par `text_extraction_id`).
Elles portent le nom de leur **typologie**, pas celui du document : une typologie sonore ou
visuelle aura les siennes, d'une autre forme (ADR-0030). Les deux cascadent à la suppression du
document — c'est le `ON DELETE CASCADE` que `DeleteDocumentHandler` annonçait, et il n'a
rien changé à ce handler. Le format lui-même est décrit par ADR-0024.

Les extraits vectorisés vivent dans une troisième table, `knowledge_text_chunks` : une ligne
par extrait, son vecteur en colonne (`vector(1024)`), `UNIQUE (document_id, chunk_position)`
et un index HNSW en `vector_cosine_ops` : **c'est la recherche qui l'interroge**, voir « Le
flux de la recherche » ci-dessus. Le planificateur lui préfère encore un parcours séquentiel
aux volumes d'aujourd'hui — comportement normal de pgvector, qui n'invalide pas l'index : il
prendra le relais quand le volume le justifiera. La dimension est figée dans le type de la
colonne, et doit rester égale à `EmbeddingPolicy.DIMENSIONS`. Elle cascade elle aussi à la
suppression du document : c'est la deuxième fois qu'un ticket ajoute des tables sans toucher
à `DeleteDocumentHandler`.

La trace de chaque conversation vit dans `knowledge_agent_runs`, avec deux tables filles
cascadées (`knowledge_agent_run_searches`, les requêtes envoyées à l'outil de recherche, et
`knowledge_agent_run_sources`, les sources citées). **Aucune clé étrangère ne relie une
source citée à la ligne `knowledge_text_chunks` dont elle vient** : `CitedSource` recopie ce
qu'il faut pour se relire (document, section, texte) plutôt que de référencer l'extrait, qui
peut disparaître — un document supprimé ne doit pas invalider rétroactivement une trace déjà
écrite. C'est la même logique qui a valu à deux agrégats de se référencer par identifiant
plutôt que par `@ManyToOne` (ADR-0006), poussée un cran plus loin : ici, `documentId` reste
en colonne pour le diagnostic, mais sans contrainte qui l'oblige à désigner encore quelqu'un.

`DocumentStorage` porte trois écritures et non deux : `store` **refuse d'écraser** — le garde-fou
vise un handler qui l'appellerait deux fois —, là où `replace` écrase délibérément, en un seul
`PutObject`. Un `delete` suivi d'un `store` aurait laissé une fenêtre où le document n'a plus
d'original. Ni l'un ni l'autre ne participe à une transaction : ADR-0020 vaut pour les trois.

**Tout n'est pas en base.** Les fichiers d'origine des documents vivent dans un stockage
objet compatible S3, un objet par document dont la clé est son identifiant, dans le bucket
`second-brain-originals` (servi par Garage en développement, voir la section « Commandes »
ci-dessus). Ce stockage est un état à part entière : il ne se restaure pas avec un dump
PostgreSQL, et rien ne l'annule avec une transaction (ADR-0020) — le support a changé,
pas cette promesse-là.

### Décisions d'architecture (documentées, ne pas « corriger » spontanément)

Chacune a un ADR dans `docs/decisions/`, au format MADR : ce qui a été écarté, ce que ça
coûte, et à quelle condition on rouvre. **Lire l'ADR avant de proposer autre chose** — ce
qui ressemble ici à un défaut est presque toujours une décision, et l'alternative qui vient
à l'esprit y est le plus souvent déjà pesée.

| ADR | Décision |
|---|---|
| [0001](docs/decisions/0001-consigner-les-decisions-au-format-madr.md) | Consigner les décisions d'architecture au format MADR |
| [0002](docs/decisions/0002-les-entites-jpa-vivent-dans-le-domaine.md) | Les entités JPA vivent dans le domaine, sans classe miroir ni mapper |
| [0003](docs/decisions/0003-pas-de-csrf-ni-de-session-l-identite-voyage-dans-un-en-tete.md) | Pas de CSRF ni de session : l'identité voyage dans un en-tête |
| [0004](docs/decisions/0004-find-user-by-email-est-livree-sans-ecran.md) | `FindUserByEmail` est livrée sans écran, comme gabarit du query bus |
| [0005](docs/decisions/0005-la-politique-autorise-128-caracteres-la-ou-bcrypt-en-lit-72.md) | La politique autorise 128 caractères là où BCrypt en lit 72 |
| [0006](docs/decisions/0006-deux-agregats-se-referencent-par-identifiant.md) | Deux agrégats se référencent par identifiant, jamais par `@ManyToOne` |
| [0007](docs/decisions/0007-le-jeton-de-verification-voyage-en-query-string.md) | Le jeton de vérification voyage en query string |
| [0008](docs/decisions/0008-l-usage-unique-du-jeton-ne-tient-qu-a-un-lire-puis-ecrire.md) | L'usage unique du jeton ne tient qu'à un lire-puis-écrire |
| [0009](docs/decisions/0009-base-url-garde-un-defaut-qui-ment-en-production.md) | `secondbrain.base-url` garde un défaut qui ment en production |
| [0010](docs/decisions/0010-pas-de-jeton-de-rafraichissement-ni-de-revocation.md) | Pas de jeton de rafraîchissement, pas de révocation |
| [0011](docs/decisions/0011-le-jeton-d-acces-est-range-dans-le-localstorage.md) | Le jeton d'accès est rangé dans le `localStorage` |
| [0012](docs/decisions/0012-aucune-limitation-de-debit-sur-la-delivrance-de-jeton.md) | Aucune limitation de débit sur `POST /api/token` |
| [0013](docs/decisions/0013-le-deploiement-de-production-vit-dans-coolify.md) | Le déploiement de production vit dans Coolify, hors du dépôt |
| [0014](docs/decisions/0014-le-profil-serialise-directement-le-modele-de-lecture.md) | `/api/profile` sérialise directement le modèle de lecture |
| [0015](docs/decisions/0015-le-profil-se-lit-par-identifiant-jamais-par-email.md) | Le profil se lit par identifiant, jamais par email |
| [0016](docs/decisions/0016-aucun-test-de-rendu-le-design-system-tient-lieu-de-controle.md) | Aucun test de rendu : `/design-system` tient lieu de contrôle |
| [0017](docs/decisions/0017-la-redirection-de-verification-transporte-un-code.md) | La redirection de vérification transporte un code, pas un message |
| [0018](docs/decisions/0018-aucune-page-publique.md) | Aucune page publique |
| [0019](docs/decisions/0019-le-repli-spa-de-nginx-ne-s-exerce-qu-en-production.md) | Le repli SPA de nginx ne s'exerce qu'en production |
| [0020](docs/decisions/0020-le-systeme-de-fichiers-ne-participe-a-aucune-transaction.md) | Le système de fichiers ne participe à aucune transaction |
| [0021](docs/decisions/0021-le-contenu-depose-transite-entierement-en-memoire.md) | Le contenu déposé transite entièrement en mémoire |
| [0022](docs/decisions/0022-le-front-recopie-ce-qui-n-est-pas-une-regle-du-serveur.md) | Le front recopie ce qui n'est pas une règle du serveur |
| [0023](docs/decisions/0023-pas-d-outbox-on-fait-confiance-au-broker.md) | Pas d'outbox : on fait confiance au broker |
| [0024](docs/decisions/0024-le-texte-extrait-est-une-suite-plate-de-blocs-titres.md) | Le texte extrait est une suite plate de blocs titrés |
| [0025](docs/decisions/0025-un-plancher-de-caracteres-declare-un-document-inexploitable.md) | Un plancher de caractères déclare un document inexploitable |
| [0026](docs/decisions/0026-un-extracteur-par-format-plutot-qu-apache-tika.md) | Un extracteur par format, plutôt qu'Apache Tika |
| [0027](docs/decisions/0027-les-titres-d-un-pdf-sans-signets-sont-devines-a-la-taille-de-police.md) | Les titres d'un PDF sans signets sont devinés à la taille de police |
| [0028](docs/decisions/0028-l-echec-d-extraction-s-ecrit-hors-de-la-transaction-annulee.md) | L'échec d'extraction s'écrit hors de la transaction annulée |
| [0029](docs/decisions/0029-la-typologie-d-un-document-se-deduit-de-son-format.md) | La typologie d'un document se déduit de son format, elle n'est pas stockée |
| [0030](docs/decisions/0030-chaque-typologie-a-ses-propres-tables-d-extraction.md) | Chaque typologie de document a ses propres tables d'extraction |

Ces ADR remplacent la liste numérotée d'« écarts assumés » qui vivait ici ; ADR-0001 porte
la correspondance avec l'ancienne numérotation. Un ADR accepté ne se modifie pas, il se
remplace — voir `.claude/rules/decisions.md`.

## Stack et versions

**Back** — Java 25 · Spring Boot 4.0.7 (MVC, Data JPA, Security, OAuth2 Resource Server,
Validation, Mail) · Flyway · PostgreSQL 17 + pgvector · Spring AMQP · RabbitMQ 4 ·
springdoc-openapi · commonmark-java · Apache POI · PDFBox · jtokkit (comptage de tokens) ·
hibernate-vector · AWS SDK for Java v2 (stockage objet des originaux) · LangChain4j 1.19.0
(transport de la génération) · JUnit 5 + AssertJ + Testcontainers · Gradle Kotlin DSL avec
version catalog (`gradle/libs.versions.toml`).

**Front** — Vue 3 · Vite · vue-router · pinia · Vitest (jsdom) · nginx pour servir le build.
Versions gérées par `frontend/package-lock.json`, hors du version catalog Gradle.

**Développement** — Traefik v3 en reverse proxy devant l'app et le front, dans `compose.yaml`.
Sa règle de routage énumère les préfixes servis par Java : `/api`, `/verification` et `/drive` en
font partie, et **oublier le dernier envoie le retour d'autorisation Google sur le front**.
En production, c'est Coolify qui tient ce rôle, avec une configuration qui vit hors du dépôt.
RabbitMQ 4 avec sa console de gestion sur <http://localhost:15672> (`RABBITMQ_USER` /
`RABBITMQ_PASSWORD` du `.env`, `second_brain`/`second_brain` par défaut — pas de `guest`), un
conteneur `worker` de la même image que `app`, un service `ollama` qui sert les modèles
d'embedding et de génération, et un service `garage` (image `dxflrs/garage:v2.3.0`) qui sert
le stockage objet des originaux.

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

- `docs/decisions/` — un ADR par décision d'architecture, au format MADR. Le gabarit est
  `0000-adr-template.md`, ADR-0001 explique le dispositif, et l'index des décisions est la
  section « Décisions d'architecture » ci-dessus.
- `docs/ticket-template.md` — format de ticket attendu (5 sections, Gherkin
  déclaratif). La Definition of Done appartient à ce CLAUDE.md, pas aux tickets.
- `docs/superpowers/plans/` — plans d'implémentation détaillés, un par feature.
  Celui de la création de compte porte le raisonnement derrière l'architecture ci-dessus.
- `.superpowers/sdd/<date>-<feature>/` — briefs, rapports et diffs de revue par tâche.
