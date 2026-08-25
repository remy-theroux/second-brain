# Règles frontend — Vue.js

Ces règles ont été posées par le ticket « login », premier à introduire une application
Vue. Comme les règles backend, chacune est un piège rencontré ou évité sur ce repo.

## Un seul front

`frontend/` porte **tout** le parcours utilisateur : accueil, inscription, connexion,
espace connecté. Il est hors du build Gradle, se construit seul (`frontend/Dockerfile`) et
se sert seul (nginx sur `dist`).

L'application Java n'expose plus aucune vue : des routes d'API sous `/api`, plus
`GET /verification`, qui répond `302` vers `/login?verification=<code>`. Thymeleaf a été
retiré du projet ; ne pas le réintroduire pour « une petite page » — c'est le retour de la
couture qu'on a supprimée.

## Le projet front

- **JavaScript, pas TypeScript.** Aucun `tsconfig`, aucune dépendance de types. Le jour où
  le front justifiera TypeScript, ce sera un ticket, pas une dérive.
- **PrimeVue 4 avec le thème Aura, tel quel.** Pas de `definePreset`, pas de couleur
  primaire du projet : une identité visuelle sera un ticket, avec une charte. Les
  composants s'importent un par un (`import Button from 'primevue/button'`), jamais par
  enregistrement global — l'arbre des dépendances de chaque vue se lit dans ses imports.
- Alias d'import `@` → `frontend/src`. Pas de chemins relatifs qui remontent (`../../`).
- `<script setup>` pour tous les composants, `ref` plutôt que `reactive`.
- Un composant par fichier, une vue par route, dans `src/views/`.

## Style et tokens

Deux familles de variables CSS, et la frontière ne se négocie pas :

- **`--p-*` : les design tokens d'Aura. Toutes les couleurs passent par eux**
  (`--p-text-color`, `--p-text-muted-color`, `--p-content-background`,
  `--p-content-border-color`, `--p-primary-color`). Ils suivent le thème clair/sombre du
  système ; une couleur en dur ne le suivrait pas. Aucune couleur n'est définie dans le
  projet.
- **`--sb-*` : les tokens du projet**, définis dans `src/assets/main.css` et nulle part
  ailleurs — espacements (`--sb-space-xs` à `-xl`), largeurs (`--sb-sidebar-width`,
  `--sb-guest-width`), tailles de titre (`--sb-title-size`, `--sb-section-title-size`,
  `--sb-text-small`). **Un composant n'écrit jamais un `rem` nu** : il nomme le token.
  Avant ces tokens, `LoginView` et `RegisterView` portaient le même `<style scoped>` à
  l'octet près, et changer un espacement demandait de le retrouver dans chaque fichier.
- `main.css` ne pose que ce qui n'appartient à aucun composant : reset, police, et les
  classes partagées par plusieurs vues à l'identique (`.guest-form`, `.guest-switch`,
  `.table-duplicate-row`, `.table-actions`). Tout le reste est `<style scoped>`. Une classe
  posée sur un élément rendu par un composant PrimeVue (`row-class` d'un `DataTable`) est
  hors de portée d'un `<style scoped>` : si deux vues en ont besoin, elle va dans
  `main.css`, pas en `:deep()` recopié dans chacune.

## Composants partagés et design system

- `src/components/` porte ce qui sert plusieurs vues : les deux layouts
  (`GuestLayout`, `AuthenticatedLayout`), `FormField` (libellé + slot pour l'input +
  message d'erreur) et `PageTitle` (le `h1` d'un écran). Un motif copié d'une vue à
  l'autre est un composant qui n'a pas encore été extrait.
- `FormField` ne rend **pas** l'input : il vient par le slot, avec ses attributs (`id` ou
  `input-id` selon le composant PrimeVue, `invalid`, `autocomplete`). La prop `id` du
  champ sert au `for` du libellé ; la vue repose le même identifiant sur l'input. Le
  composant ne devine pas comment son enfant s'identifie.
- **`/design-system` est le catalogue** : `src/views/DesignSystemView.vue` rend chaque
  token avec sa valeur effective (lue par `getComputedStyle`, pas recopiée), chaque
  composant partagé dans chacun de ses états, et les composants PrimeVue tels qu'on les
  emploie. **Tout composant partagé nouveau ou tout token nouveau y apparaît dans le même
  commit.** Un composant absent de la page n'est pas partagé.
- La route n'existe qu'en développement : `import.meta.env.DEV` en spread conditionnel
  dans `routes`, ce qui retire la route **et** la vue du bundle de production. Vitest
  tourne avec `DEV` à `true`, donc `router/index.spec.js` vérifie la présence de la route ;
  son absence en production ne se vérifie que sur `dist/`
  (`grep -l "Tokens du projet" dist/assets/*.js` doit ne rien trouver).
- La méta `layout: 'bare'` désactive tout layout dans `App.vue`. Elle n'existe que pour le
  design system, trop large pour la carte invité ; un troisième layout pour une seule page
  serait de trop. Ne pas l'étendre sans y réfléchir : une page connectée sans barre
  latérale est une page sans déconnexion.

## Découpage

| Ce que fait le module | Où il va |
|---|---|
| Parle HTTP : URL, en-têtes, codes, forme des corps | `src/api/` |
| Détient un état partagé entre écrans | `src/stores/` (pinia) |
| Décrit les routes et le garde | `src/router/` |
| Est l'écran d'une route | `src/views/` |
| Est réutilisé par plusieurs vues | `src/components/` |

- **`src/api/` est le seul endroit qui appelle `fetch`.** Un composant qui appelle une URL
  en direct est un bug : les en-têtes d'authentification et la traduction des erreurs
  cessent d'être au même endroit.
- Une vue n'appelle pas `src/api/` directement quand un store porte déjà l'état concerné :
  elle passe par le store.

## Authentification

- Le jeton d'accès est dans `localStorage`, sous `second-brain.access-token`, avec son
  instant d'expiration sous `second-brain.access-token-expiration`. Dette assumée
  (XSS) documentée dans `CLAUDE.md`.
- **`isAuthenticated()` est une fonction, jamais un `computed`.** Son résultat dépend de
  l'horloge, qui n'est pas une dépendance réactive : un `computed` renverrait une valeur
  mise en cache après expiration, et le garde de route laisserait passer.
- L'état local n'est qu'une optimisation. **Le serveur fait autorité** : un `401` sur un
  appel authentifié déconnecte, quoi qu'en pense le navigateur.
- Le garde de route est une **fonction exportée**, pas une closure passée à `beforeEach` :
  c'est ce qui permet de le tester avec un `createMemoryHistory`.

## Communication avec le back

- Toutes les routes API sont préfixées `/api`. **Le navigateur ne voit qu'une seule
  origine : il n'y a aucune configuration CORS dans ce projet, et il ne doit pas y en
  avoir.** Un besoin de CORS signale qu'on a contourné le proxy.
- C'est un **reverse proxy** qui tient cette origine, pas Vite : Traefik dans
  `compose.yaml`, Coolify en production. Les deux routent `/api` et `/verification` vers
  Spring, tout le reste vers le front. `vite.config.js` n'a plus de bloc `proxy` — le
  remettre y ferait mentir sur qui route quoi.
- Le seul réglage réseau restant côté Vite est `server.hmr.clientPort`, alimenté par
  `VITE_PUBLIC_PORT` : le WebSocket du rechargement à chaud doit viser le port public, le
  5173 du conteneur n'étant plus publié.

## Langue

Mêmes règles que le back : **libellés, messages et textes de test en français**, noms de
fonctions, de variables et de fichiers de production en **anglais**.

Les messages d'erreur affichés viennent du serveur et sont affichables tels quels — ne pas
les réécrire côté front. Ça vaut pour `error_description` de `/api/token` comme pour les
`errors` par champ de `/api/registrations`.

**Une seule exception, et elle est bornée : quand le transport est une redirection, le
serveur envoie un code, pas un message.** `GET /verification` redirige vers
`/login?verification=<code>` et c'est `VERIFICATION_MESSAGES` dans `LoginView.vue` qui
porte les libellés. Un message en query string atterrirait dans l'historique du navigateur
et les logs du proxy. La contrepartie — deux endroits qui peuvent diverger — est un écart
assumé documenté dans `CLAUDE.md`.

Ce qui n'est **pas** un message d'erreur peut se traduire côté front : le libellé d'une
énumération sérialisée par l'API (`STATUS_LABELS` dans `DocumentsView.vue`) est une affaire
d'écran. Et un filtre de sélecteur de fichiers (`ACCEPTED_EXTENSIONS`) n'est pas une règle :
la règle est au serveur, dont le `415` énonce la liste qui fait foi. Les deux copies sont
l'écart n° 21 de `CLAUDE.md`.

Le dépôt d'un fichier passe par `FileUpload` en `custom-upload` : le composant ne connaît
aucune URL, et l'appel part de `src/api/` comme tout le reste. Poser un `Content-Type` à la
main sur un `FormData` casserait le multipart, le navigateur seul connaissant le boundary.

## Tests

- **Vitest, environnement `jsdom`**, configuré dans `vite.config.js`. Fichiers `*.spec.js`
  à côté du module testé.
- On teste **ce qui peut casser silencieusement** : le store d'authentification, le garde
  de route, et la traduction des réponses d'erreur dans `src/api/`. Pas de test de rendu de
  composant, pas de `@vue/test-utils`, pas de navigateur. Un formulaire cassé se voit ; une
  session qui ne s'invalide pas ou un `422` mal lu, non. C'est `/design-system` qui tient
  lieu de test de rendu : le passage humain se fait là, sur tous les états d'un coup.
- `fetch` est bouché par `vi.stubGlobal`, jamais par une vraie requête.
- `setActivePinia(createPinia())` et `localStorage.clear()` en `beforeEach` : un store est
  un singleton, et `localStorage` survit d'un test à l'autre.

## Build et outillage

- **Aucun Node sur l'hôte.** Tout passe par la fonction `gfront` de `CLAUDE.md`, qui
  s'exécute sous l'UID de l'utilisateur : sans cela, `npm install` écrit `node_modules/` en
  `root` dans le dépôt.
- `package-lock.json` est versionné, la CI fait `npm ci`.
- **Le style est décidé par Prettier** (`frontend/.prettierrc.json` : pas de
  point-virgule, guillemets simples, 100 colonnes) : `make format-front` avant de
  committer, et ne pas défaire son travail à la main. `npm run format:check` est une étape
  bloquante de la CI. Prettier s'installe comme toute dépendance, par
  `gfront npm install -D`, jamais en éditant `package.json`.
- Le périmètre de Prettier s'arrête à `frontend/`. `CLAUDE.md`, `README.md` et les autres
  Markdown de la racine sont rédigés à la main et `package-lock.json` est réécrit par npm :
  les trois sont hors de sa portée, le dernier par `.prettierignore`.
- Les dépendances s'installent par `gfront npm install <paquet>`, jamais en éditant
  `package.json` à la main : les versions résolues doivent atterrir dans le lock.
- `npm run build` produit ce que sert l'image nginx : ce n'est plus seulement un contrôle
  de compilation, c'est l'artefact déployé. Il reste lancé en CI, et il reste le seul
  contrôle qui compile les templates des composants — aucun test ne les rend.
- Le repli SPA (`nginx.conf`, `try_files`) ne s'exerce que sur l'image construite : le
  vérifier à la main après toute modification de `nginx.conf`.
