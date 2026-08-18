# Migration du parcours public vers le front Vue — design

Date : 2026-08-18 · Contexte borné : `users` (+ `shared`, `config`) · Branche : `feat/login`

Pas de ticket Notion : demande directe, née du constat qu'aucun lien ne mène à l'écran de
connexion livré par le ticket « login ».

## Contexte

Le ticket « login » a livré une application Vue à deux écrans (`/login`, `/home`) servie
par Vite sur le port 5173, à côté d'un front Thymeleaf servi par Spring sur le port 8080.
Trois écrans restaient rendus par le serveur : l'accueil, l'inscription et le résultat de
la vérification d'email.

Rien ne relie les deux. `home.html` ne propose que « Créer mon compte » ; le seul moyen
d'atteindre `/login` est de taper `http://localhost:5173/` à la main. Le constat qui
déclenche ce design est exactement celui-là : *« je ne vois pas de lien pour se logger »*.

Trois écarts assumés de `CLAUDE.md` convergent vers ce ticket :

- **n° 12** — le build de production du front n'est déployé nulle part : aucun serveur ne
  sert `frontend/dist`, le `Dockerfile` de production ne construit que le back.
- **n° 2** — CSRF désactivé et session `STATELESS`, cohérents tant que l'identité voyage
  dans un en-tête `Authorization`.
- La règle `frontend.md` — *« Le navigateur ne voit qu'une seule origine : il n'y a aucune
  configuration CORS dans ce projet, et il ne doit pas y en avoir »* — n'est tenue
  aujourd'hui que par le proxy du serveur de développement Vite. En production, rien ne la
  tient, puisqu'il n'y a pas de production.

## Objectif

Un visiteur fait tout son parcours — découvrir, s'inscrire, vérifier son adresse, se
connecter, consulter son espace — dans une seule application Vue, derrière une seule
origine. L'application Java n'expose plus que des routes d'API, plus une route de
redirection pour le lien reçu par email.

**Réussi si :** derrière un unique port publié, un visiteur crée son compte depuis
l'écran Vue, reçoit son mail dans Mailpit, clique le lien, atterrit sur l'écran de
connexion avec un bandeau de confirmation, se connecte et voit son email — sans qu'aucun
template Thymeleaf existe encore dans le dépôt, et sans une ligne de configuration CORS.

## Attendus métier

```gherkin
Fonctionnalité: Parcours public dans l'application Vue

  Scénario: je crée mon compte depuis l'application Vue
    Étant donné que je suis un visiteur anonyme sur la page d'inscription
    Quand je saisis un email disponible et un mot de passe robuste
    Alors mon compte est créé
    Et je reçois un email contenant un lien de vérification

  Scénario: ma saisie est refusée champ par champ
    Étant donné que je suis un visiteur anonyme sur la page d'inscription
    Quand je saisis un email déjà utilisé ou un mot de passe trop faible
    Alors je reste sur la page d'inscription
    Et le message de refus s'affiche sous le champ fautif

  Scénario: le mail de vérification ne part pas
    Étant donné que le canal de notification est en panne
    Quand je saisis un email disponible et un mot de passe robuste
    Alors mon compte n'est pas créé
    Et un message m'invite à réessayer

  Scénario: je vérifie mon adresse depuis le lien reçu
    Étant donné que je viens de créer mon compte
    Quand je clique le lien de vérification reçu par email
    Alors j'arrive sur la page de connexion
    Et un message confirme que mon adresse est vérifiée

  Scénario: mon lien de vérification n'est plus exploitable
    Étant donné que mon lien de vérification est expiré, déjà utilisé ou falsifié
    Quand je clique ce lien
    Alors j'arrive sur la page de connexion
    Et un message m'explique que le lien n'est pas exploitable

  Scénario: un visiteur anonyme arrive sur la racine
    Étant donné que je n'ai aucun jeton valable
    Quand j'ouvre la racine de l'application
    Alors j'arrive sur la page de connexion

  Scénario: un utilisateur connecté revient sur l'inscription
    Étant donné que je suis connecté
    Quand j'ouvre la page d'inscription ou la page de connexion
    Alors je suis renvoyé vers mon espace connecté
```

## Décisions de conception

### 1. Une origine unique, tenue par un reverse proxy, en développement comme en production

Le navigateur ne connaît qu'un hôte et qu'un port. Un reverse proxy route par préfixe de
chemin : `/api` et `/verification` vers l'application Java, tout le reste vers le front.

En développement, c'est un service **Traefik** dans `compose.yaml`, configuré par les
labels des conteneurs. En production, c'est Coolify qui tient ce rôle, avec les mêmes
règles ; sa configuration vit hors du dépôt.

Ce choix est ce qui **conserve la règle « aucun CORS »** en la rendant enfin vraie
partout : jusqu'ici elle ne tenait qu'en développement, par le proxy de Vite, et rien ne
la tenait en production. Un besoin de CORS resterait le signal qu'on a contourné le proxy.

L'alternative — deux origines publiques et une `CorsConfigurationSource` côté Spring —
aurait imposé un preflight sur chaque appel authentifié et obligé à réécrire la règle
frontend plutôt qu'à l'honorer.

### 2. Thymeleaf disparaît entièrement du dépôt

Pas « un template survit pour la vérification » : zéro. Le starter sort de
`build.gradle.kts`, `src/main/resources/templates/` est supprimé, et avec lui
`ShowHomeController`, `ShowRegistrationFormController`, `RegistrationForm` et le package
`shared/web` devenu vide.

Une seule page rendue par le serveur aurait suffi à maintenir en vie une chaîne complète —
starter, résolveur de vues, tests MockMvc de rendu — pour un écran. Et elle aurait recréé
exactement la couture qu'on supprime : une page servie par le back avec un lien en dur
vers le front.

### 3. La vérification reste une navigation, pas un appel d'API

`GET /verification?compte=&jeton=` **ne passe pas sous `/api`** et reste une route de
l'application Java. C'est l'exception explicitement posée : le lien part par email, il doit
fonctionner dans n'importe quel client mail, sans JavaScript, sans front en ligne.

La route dispatche `VerifyAccount` comme aujourd'hui, puis répond `302` vers
`/login?verification=<code>`. Le `Location` est **relatif** : le navigateur le résout
contre l'origine de la requête, donc l'application Java n'a aucune propriété « URL du
front » à connaître, et rien à configurer de plus que `secondbrain.base-url` qui existe
déjà.

Faire porter la vérification par le front (`POST /api/verification` appelé au montage d'un
écran) aurait supprimé l'exception demandée et rendu la vérification d'adresse dépendante
de JavaScript et de la disponibilité du front.

### 4. Le résultat de la vérification voyage en **code**, pas en message

La redirection transporte `verification=ok`, `lien-invalide`, `lien-expire` ou
`lien-deja-utilise`. Le front porte la rédaction française correspondante.

C'est un **amendement de la règle** `frontend.md`, qui dit aujourd'hui : « Les messages
d'erreur affichés viennent du serveur (`error_description`) et sont affichables tels
quels — ne pas les réécrire côté front. » La règle devient : *un code quand le transport
est une redirection, le message du serveur partout ailleurs.*

Passer le message lui-même en query string l'aurait collé dans l'historique du navigateur
et dans les logs d'accès du proxy, pour zéro gain — c'est déjà le reproche fait au jeton
de vérification (écart n° 6). Le prix payé est une duplication : les trois libellés de
refus existent côté domaine **et** côté front, et peuvent diverger.

Les codes ne sont pas plus bavards que les messages qu'ils remplacent : les trois causes
d'un lien invalide — UUID illisible, compte inconnu, jeton faux — partagent le seul code
`lien-invalide`, comme elles partagent aujourd'hui un seul message. Les distinguer ferait
de la route un oracle d'existence de compte.

### 5. Les refus d'inscription sont rendus **champ par champ**

```
422 { "errors": { "email": "…", "password": "…" } }
503 { "message": "…" }
```

`422` couvre les trois refus métier (`InvalidEmailException`, `EmailAlreadyUsedException`,
`WeakPasswordException`) et l'absence de champ (`@NotBlank`). `503` couvre l'échec du canal
de notification, qui ne vise aucun champ : le rollback a déjà eu lieu côté `SpringCommandBus`,
ce n'est pas la saisie qui est fautive.

C'est ce qui préserve l'expérience actuelle de Thymeleaf, qui place le message sous le
champ. Le prix est **deux formes d'erreur dans l'API** : celle-ci, et `{error,
error_description}` sur `/api/token`, imposée par la forme du `password grant` de RFC 6749
et déjà documentée comme telle. La seconde ne peut pas s'aligner sur la première sans
cesser d'imiter la RFC ; c'est donc la première qui est nôtre, et la seule à suivre pour
toute route future.

`ProblemDetail` / RFC 9457 a été écarté : une troisième forme, la plus verbeuse, pour une
API à trois routes.

### 6. `POST /api/registrations`, `201` sans corps

La route est nommée par ce qu'elle crée du point de vue du visiteur — une inscription — et
non `POST /api/users`, qui promettrait une ressource utilisateur exposée qu'aucune route ne
sert. Le `201` n'a ni corps ni `Location` : le compte créé n'est lisible par personne tant
qu'il n'est pas vérifié et qu'aucun jeton n'a été délivré.

### 7. Le support de liaison devient un **record**

`RegistrationForm` était une classe mutable à accesseurs JavaBean pour une seule raison :
`th:field` lit la valeur via `BeanWrapper`. Cette raison disparaît avec Thymeleaf. Le
remplaçant est `RegistrationRequest`, un record annoté `@NotBlank`, conforme à la règle
générale du dépôt.

`RegisterUserController` conserve son nom, son unique mapping et sa structure : signature
`@Valid @RequestBody RegistrationRequest, BindingResult`. La présence du `BindingResult`
empêche Spring de lever `MethodArgumentNotValidException`, donc **la traduction des refus
reste locale au contrôleur** — pas de `@RestControllerAdvice`, qui déplacerait la
traduction hors de vue et vaudrait pour tout le contexte.

### 8. Le front se build et se sert en autonomie

`frontend/Dockerfile`, multi-étapes : `node:24-alpine` construit `dist`, `nginx:alpine` le
sert. La configuration nginx porte le repli SPA `try_files $uri $uri/ /index.html` — sans
lui, un rechargement de page sur `/login` répond 404, puisque aucun fichier ne porte ce nom.

Aucun lien avec le build Gradle : l'image se construit seule, se déploie seule, et l'écart
n° 12 se referme.

En développement, le service front reste l'image `node:24-alpine` du compose lançant
`npm run dev`, avec le hot reload. Le `Dockerfile` ne sert donc qu'à la production. Une
cible `dev` dans ce même Dockerfile ne ferait que reproduire ce que le compose fait déjà
en trois lignes.

**Conséquence assumée** : le repli SPA n'est exercé par aucun environnement avant le
déploiement. `docker compose up` fait tourner Vite, qui sert `index.html` sur toute route
inconnue par construction. Une erreur dans la configuration nginx ne se verrait qu'en
production.

### 9. Vite ne proxifie plus rien

Le bloc `server.proxy` de `vite.config.js` et la variable `VITE_API_TARGET` disparaissent :
le navigateur tape désormais l'origine du proxy, qui route `/api` vers l'application Java
sans que Vite voie passer ces requêtes. Garder ce bloc laisserait une configuration morte
racontant une histoire fausse sur qui tient l'origine unique.

En revanche le client HMR doit savoir où revenir : il vise par défaut le port sur lequel
Vite écoute, qui n'est plus publié. `server.hmr.clientPort` est fixé depuis
`VITE_PUBLIC_PORT`, fourni par le compose.

### 10. La page d'accueil publique disparaît

`/` redirige vers `/home`, qui exige un jeton : un visiteur anonyme atterrit donc sur
`/login`, qui porte le lien vers l'inscription. `RegisterView` porte le lien retour.

C'est une suppression, pas un oubli : `home.html` ne contenait qu'un titre et un lien vers
l'inscription. Recréer une page d'accueil publique en Vue serait recréer un écran vide. Le
jour où il y aura quelque chose à dire à un visiteur anonyme, ce sera un ticket.

### 11. L'inscription n'a pas de store

`RegisterView` appelle `src/api/` directement. La règle frontend l'interdit *« quand un
store porte déjà l'état concerné »* — ici aucun état n'est partagé entre écrans : la saisie
vit dans le composant, et le résultat est une navigation. Créer un store d'inscription
serait un singleton pour une variable locale.

### 12. Un seul port publié

`app` et `frontend` ne publient plus rien ; seul Traefik publie `${HTTP_PORT:-8080}`.
C'est la réponse directe au constat de départ : il n'y a plus qu'une adresse à retenir, et
plus de question « laquelle des deux applications fonctionne ». Mailpit garde son `8025` —
c'est un outil de développement, pas l'application.

En développement, Traefik route aussi `/swagger-ui`, `/v3/api-docs` et `/actuator` vers
l'application Java, sans quoi ils tomberaient sur l'attrape-tout du front. **En production,
le proxy n'expose que `/api` et `/verification`** : ni Swagger ni actuator.

## Architecture

### Routes après migration

| Route | Servie par | Accès | Réponse |
|---|---|---|---|
| `POST /api/registrations` | app | publique | `201` vide · `422 {errors}` · `503 {message}` |
| `POST /api/token` | app | publique | inchangée |
| `GET /api/profile` | app | authentifiée | inchangée |
| `GET /verification?compte=&jeton=` | app | publique, hors `/api` | `302` → `/login?verification=<code>` |
| `/`, `/login`, `/register`, `/home` | front | — | SPA |

### Backend — fichiers touchés

```
users/infrastructure/web/
├── RegisterUserController.java      MODIFIÉ  @RestController, POST /api/registrations
├── RegistrationRequest.java         NOUVEAU  record, remplace RegistrationForm
├── ValidationErrorResponse.java     NOUVEAU  record(Map<String,String> errors)
├── ErrorResponse.java               NOUVEAU  record(String message)
├── VerifyAccountController.java     MODIFIÉ  302 relatif + code, plus de Model
├── RegistrationForm.java            SUPPRIMÉ
└── ShowRegistrationFormController.java  SUPPRIMÉ

shared/web/ShowHomeController.java   SUPPRIMÉ  (package vidé)
src/main/resources/templates/        SUPPRIMÉ  (home, register, verification)
config/SecurityConfig.java           MODIFIÉ  /api/registrations explicitement publique
build.gradle.kts                     MODIFIÉ  starter-thymeleaf retiré
```

Le domaine, l'application (commandes, queries, handlers), les bus, la persistance, la
sécurité et l'adapter email **ne bougent pas d'une ligne**. C'est la vérification que
l'hexagone tenait : changer entièrement le mode de présentation ne touche que
`infrastructure/web`.

### Frontend — fichiers touchés

```
src/views/RegisterView.vue        NOUVEAU  formulaire, erreurs par champ
src/views/LoginView.vue           MODIFIÉ  bandeau ?verification=, lien vers /register
src/api/client.js                 MODIFIÉ  register() + ValidationError
src/api/client.spec.js            NOUVEAU  traduction des réponses d'erreur
src/router/index.js               MODIFIÉ  route /register, meta guestOnly
src/router/index.spec.js          MODIFIÉ  cas guestOnly sur /register
vite.config.js                    MODIFIÉ  proxy retiré, hmr.clientPort
Dockerfile                        NOUVEAU  build node → nginx
nginx.conf                        NOUVEAU  try_files SPA
```

Le garde passe de la comparaison `to.name === 'login'` à `to.meta.guestOnly`, porté par
`/login` et `/register`. `isAuthenticated()` reste une fonction, pas un `computed`.

### Compose

Service `proxy` (Traefik v3, provider Docker, `exposedByDefault=false`), labels de routage
sur `app` (priorité 100) et `frontend` (priorité 1, attrape-tout). `SECONDBRAIN_BASE_URL`
pointe l'origine du proxy, donc le lien du mail et la cible de la redirection partagent
l'hôte. `.env.example` remplace `APP_PORT` et `FRONTEND_PORT` par `HTTP_PORT`.

## Tests

**Back**

| Test | Ce qu'il vérifie |
|---|---|
| `RegisterUserControllerTest` | RÉÉCRIT — `201`, `422` par champ pour chaque refus métier et pour un champ vide, `503` sur panne du canal |
| `VerifyAccountControllerTest` | RÉÉCRIT — `302` et `Location` attendu pour le succès et pour les trois refus |
| `SecurityConfigTest` | `/api/registrations` et `/verification` joignables sans jeton, `/api/profile` toujours `401` sans jeton |
| `ShowHomeControllerTest`, `ShowRegistrationFormControllerTest` | SUPPRIMÉS |

Le reste de la suite est inchangé et doit rester vert sans retouche — c'est le contrôle
que la migration n'a pas débordé de `infrastructure/web`.

**Front**

| Test | Ce qu'il vérifie |
|---|---|
| `api/client.spec.js` | NOUVEAU — `register()` traduit `422` en erreurs par champ, `503` en message global, et ne casse pas sur un corps non-JSON |
| `router/index.spec.js` | `/register` renvoie vers `/home` pour un connecté ; `/home` renvoie vers `/login` sans jeton |
| `stores/auth.spec.js` | inchangé |

Les vues restent sans test automatisé (écart n° 15 inchangé, et désormais étendu à
`RegisterView`). Le passage humain dans un navigateur reste une condition, pas une option.

## Hors-périmètre

- Toute feuille de style : le HTML reste nu, c'est le ticket « interface » qui tranchera.
- La configuration Coolify (domaines, TLS, règles de routage) : elle vit hors du dépôt.
- Le renvoi d'un lien de vérification expiré — le refus reste un cul-de-sac.
- Limitation de débit sur `/api/token` et `/api/registrations` (écart n° 11 inchangé, et
  l'inscription rejoint la connexion comme surface sans protection).
- Jeton de rafraîchissement et révocation (écart n° 9).
- Le passage du jeton en cookie `httpOnly` (écart n° 10), qui rendrait CSRF obligatoire.
- Tests de rendu des composants Vue.

## Écarts assumés introduits

1. Les libellés de refus de vérification existent en deux endroits : les exceptions du
   domaine, et le front qui traduit les codes de la redirection. Ils peuvent diverger sans
   qu'aucun test ne le voie.
2. Plus aucune page publique : un visiteur anonyme voit l'écran de connexion et rien
   d'autre.
3. Le repli SPA de nginx n'est exercé par aucun environnement avant la production.
4. Le routage de développement (Traefik, dans le dépôt) et celui de production (Coolify,
   hors dépôt) sont deux configurations distinctes qui doivent rester cohérentes à la main.

## Pointeurs

- `docs/superpowers/specs/2026-08-17-login-jwt-design.md` — décision 6 (« le front est un
  projet séparé, servi par Vite, qui proxifie le back ») que ce design révise.
- `.claude/rules/frontend.md` — sections « Deux fronts cohabitent », « Communication avec
  le back » et « Langue » à reprendre.
- `CLAUDE.md` — arborescence, flux de la vérification, écarts n° 2, 12 et 15.
