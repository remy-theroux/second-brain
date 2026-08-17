# Règles frontend — Vue.js

Ces règles ont été posées par le ticket « login », premier à introduire une application
Vue. Comme les règles backend, chacune est un piège rencontré ou évité sur ce repo.

## Deux fronts cohabitent

- `src/main/resources/templates/` — Thymeleaf servi par Spring MVC, sur le port 8080 :
  page d'accueil, inscription, vérification d'email. Rendu serveur, aucun JavaScript.
- `frontend/` — application Vue 3 servie par Vite, sur le port 5173 : connexion et espace
  connecté. **Hors du build Gradle.**

Ce n'est pas une transition à moitié faite, c'est un choix : les parcours qui n'ont besoin
d'aucun état client restent en rendu serveur. Ne pas migrer un écran Thymeleaf vers Vue
sans ticket qui le demande.

## Le projet front

- **JavaScript, pas TypeScript.** Aucun `tsconfig`, aucune dépendance de types. Le jour où
  le front justifiera TypeScript, ce sera un ticket, pas une dérive.
- **Aucun CSS, aucune bibliothèque de composants.** Le HTML est nu, comme les templates
  Thymeleaf. C'est le ticket « interface » qui tranchera.
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

- Toutes les routes API sont préfixées `/api`, et le serveur de développement Vite les
  proxifie vers Spring. **Le navigateur ne voit qu'une seule origine : il n'y a aucune
  configuration CORS dans ce projet, et il ne doit pas y en avoir.** Un besoin de CORS
  signale qu'on a contourné le proxy.
- La cible du proxy vient de `VITE_API_TARGET` (`http://app:8080` sous Compose).

## Langue

Mêmes règles que le back : **libellés, messages et textes de test en français**, noms de
fonctions, de variables et de fichiers de production en **anglais**. Les messages d'erreur
affichés viennent du serveur (`error_description`) et sont affichables tels quels — ne pas
les réécrire côté front.

## Tests

- **Vitest, environnement `jsdom`**, configuré dans `vite.config.js`. Fichiers `*.spec.js`
  à côté du module testé.
- On teste **ce qui peut casser silencieusement** : le store d'authentification et le garde
  de route. Pas de test de rendu de composant, pas de `@vue/test-utils`, pas de navigateur.
  Un formulaire cassé se voit ; une session qui ne s'invalide pas, non.
- `fetch` est bouché par `vi.stubGlobal`, jamais par une vraie requête.
- `setActivePinia(createPinia())` et `localStorage.clear()` en `beforeEach` : un store est
  un singleton, et `localStorage` survit d'un test à l'autre.

## Build et outillage

- **Aucun Node sur l'hôte.** Tout passe par la fonction `gfront` de `CLAUDE.md`, qui
  s'exécute sous l'UID de l'utilisateur : sans cela, `npm install` écrit `node_modules/` en
  `root` dans le dépôt.
- `package-lock.json` est versionné, la CI fait `npm ci`.
- Les dépendances s'installent par `gfront npm install <paquet>`, jamais en éditant
  `package.json` à la main : les versions résolues doivent atterrir dans le lock.
- **Le build de production du front n'est pas déployé** (aucun serveur ne sert
  `frontend/dist`). `npm run build` reste lancé en CI parce que c'est le seul contrôle qui
  compile les templates des composants.
