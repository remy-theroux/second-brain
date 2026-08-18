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
- **Aucun CSS, aucune bibliothèque de composants.** Le HTML est nu. C'est le ticket
  « interface » qui tranchera.
- Alias d'import `@` → `frontend/src`. Pas de chemins relatifs qui remontent (`../../`).
- `<script setup>` pour tous les composants, `ref` plutôt que `reactive`.
- Un composant par fichier, une vue par route, dans `src/views/`.

## Découpage

| Ce que fait le module | Où il va |
|---|---|
| Parle HTTP : URL, en-têtes, codes, forme des corps | `src/api/` |
| Détient un état partagé entre écrans | `src/stores/` (pinia) |
| Décrit les routes et le garde | `src/router/` |
| Est l'écran d'une route | `src/views/` |
| Est réutilisé par plusieurs vues | `src/components/` (n'existe pas encore) |

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

## Tests

- **Vitest, environnement `jsdom`**, configuré dans `vite.config.js`. Fichiers `*.spec.js`
  à côté du module testé.
- On teste **ce qui peut casser silencieusement** : le store d'authentification, le garde
  de route, et la traduction des réponses d'erreur dans `src/api/`. Pas de test de rendu de
  composant, pas de `@vue/test-utils`, pas de navigateur. Un formulaire cassé se voit ; une
  session qui ne s'invalide pas ou un `422` mal lu, non.
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
