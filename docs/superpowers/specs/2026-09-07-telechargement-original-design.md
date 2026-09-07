# Retélécharger le fichier d'origine — design

Date : 2026-09-07 · Contexte : `knowledge` · Branche : `feat/telechargement-original`

Pas de ticket Notion : la demande est venue en direct — « permettre de retélécharger le
fichier uploadé sur l'app, depuis le listing et le détail d'un document ».

## Contexte

Le fichier d'origine de chaque document est conservé depuis le premier jour du contexte
`knowledge` : un objet par document dans le bucket `second-brain-originals`, dont la clé est
l'identifiant du document. `DocumentStorage` porte déjà les trois gestes — `store`, `delete`
et **`read`**.

`read` n'a aujourd'hui **qu'un seul appelant** : `ExtractDocumentTextHandler`, dans le
worker. Rien, côté API, ne redonne à l'utilisateur ce qu'il a déposé. Le stockage objet est
pourtant un état à part entière, qui ne se restaure pas avec un dump PostgreSQL (ADR-0020) :
un original qu'on ne peut pas ressortir de l'application est un original qu'on ne peut
récupérer qu'en parlant S3 à la main.

C'est donc une fonctionnalité sans découverte technique : le port existe, l'adapter existe,
le cloisonnement existe. Tout l'enjeu est de choisir la **forme** de la route et la façon
dont un front qui porte son jeton en en-tête déclenche un téléchargement.

## Objectif

Depuis la liste des documents comme depuis l'écran de détail, un clic rend le fichier tel
qu'il a été déposé, sous son nom d'origine.

**Réussi si :** le fichier téléchargé est **octet pour octet** celui qui a été déposé ; il
arrive sous son nom d'origine, accents compris ; le document d'un autre compte reste
introuvable ; un document dont le traitement a échoué se retélécharge quand même.

## Attendus métier

```gherkin
Fonctionnalité: Retélécharger le fichier d'origine

  Scénario: Téléchargement depuis la liste
    Étant donné un document déposé dans ma base de connaissance
    Quand je demande son fichier depuis la liste des documents
    Alors le fichier m'est rendu tel que je l'ai déposé, sous son nom d'origine

  Scénario: Téléchargement depuis le détail
    Étant donné un document déposé dans ma base de connaissance
    Quand je demande son fichier depuis son écran de détail
    Alors le fichier m'est rendu tel que je l'ai déposé, sous son nom d'origine

  Scénario: Document dont le traitement a échoué
    Étant donné un document dont l'extraction a échoué
    Quand je demande son fichier
    Alors le fichier m'est rendu quand même

  Scénario: Document d'un autre compte
    Étant donné un document déposé par un autre compte
    Quand je demande son fichier
    Alors il m'est répondu que ce document est introuvable

  Scénario: Original disparu du stockage
    Étant donné un document dont l'original a disparu du stockage
    Quand je demande son fichier
    Alors il m'est dit que l'original n'est plus disponible, et non que le document est introuvable
```

Le troisième scénario n'est pas un cas limite décoratif : c'est le cas qui **justifie** la
fonctionnalité. Un PDF numérisé refusé par le plancher de caractères (ADR-0025) finit
`FAILED` ; son texte n'existe pas, mais son original, lui, est intact et c'est la seule
chose qu'on puisse encore en tirer. La route ne regarde donc **jamais** le statut.

## Décisions de conception

### 1. La route est une sous-ressource : `GET /api/documents/{id}/content`

Trois formes ont été pesées : `/original`, `/download`, `/content`. `/download` est écarté
parce qu'il nomme un geste là où toutes les autres routes de l'API nomment une ressource.
`/content` l'emporte sur `/original` par symétrie avec `GET /api/documents/{id}`, qui rend
les métadonnées : la même ressource, deux représentations, l'une décrite, l'autre brute.

L'ambiguïté connue de « content » dans ce dépôt — le texte extrait est aussi du contenu —
est levée par le fait que le texte extrait n'a **pas** de route à lui : il voyage dans le
corps de `GET /api/documents/{id}`, sous la clé `extraction`. Rien ne viendra donc lui
disputer le mot.

Le vocabulaire du code suit la route, sans exception : `FindDocumentContent`,
`DocumentContentView`, `FindDocumentContentController`, `MissingDocumentContentException`.

### 2. Le corps voyage entier en mémoire, et c'est déjà décidé

`DocumentStorage.read` rend un `byte[]`. Un port qui rendrait un `InputStream` permettrait
de faire ruisseler la réponse sans jamais tenir le fichier entier, mais ADR-0021 a déjà
tranché la question au dépôt : le contenu transite entièrement en mémoire, et la même
promesse vaut au retour. La rouvrir à la sortie sans la rouvrir à l'entrée n'aurait aucun
sens — le plafond de taille est le même des deux côtés.

Conséquence assumée : un document de 20 Mo occupe 20 Mo de tas le temps de la réponse.

### 3. Le type MIME entre dans `DocumentFormat`

`DocumentFormat` porte déjà l'extension et la typologie. Le type MIME est de la même
nature : une propriété du format, pas un réglage.

| Constante | Type MIME |
|---|---|
| `PDF` | `application/pdf` |
| `MARKDOWN` | `text/markdown` |
| `TEXT` | `text/plain` |
| `DOCX` | `application/vnd.openxmlformats-officedocument.wordprocessingml.document` |

L'alternative — `application/octet-stream` pour tout le monde — ne coûtait rien au domaine
mais rendait tout corps opaque, y compris un `.txt` ouvert dans Swagger UI. Elle est
écartée.

**Le type n'est pas relu du fichier déposé.** Le `Content-Type` du multipart n'est pas
stocké et ne l'a jamais été : l'identité d'un document est son empreinte, son format se
déduit de son nom. Un `.pdf` qui serait en réalité un ZIP repartira annoncé
`application/pdf` — c'est la même confiance que celle déjà accordée à l'extension au dépôt.

### 4. Le nom du fichier voyage en `Content-Disposition`, encodé RFC 5987

`Content-Disposition: attachment; filename*=UTF-8''rapport%20%C3%A9t%C3%A9.pdf`, construit
par `ContentDisposition.attachment().filename(nom, UTF_8)` de Spring. `attachment` et non
`inline` : la demande est de récupérer le fichier, pas de le prévisualiser.

Un `filename=` nu aurait suffi tant que les noms restent ASCII. Ils ne le restent pas —
`Document.upload` accepte 255 caractères quelconques — et un accent dans un en-tête HTTP
sans encodage est un octet non spécifié.

### 5. Deux absences, deux messages, un seul code

Le document inconnu et l'original disparu sont deux situations différentes, et les
confondre ferait mentir l'écran : le document, lui, est bien là et s'affiche.

| Situation | Code | Message |
|---|---|---|
| Aucun document de cet identifiant pour ce compte | `404` | `Ce document est introuvable dans votre base de connaissance.` |
| Le document existe, l'objet a disparu du stockage | `404` | `L'original de ce document n'est plus disponible.` |
| Le stockage ne répond pas | `503` | message de disponibilité, comme la recherche pour Ollama |

`503` pour l'objet disparu a été écarté : réessayer ne changera rien, ce n'est pas une
panne. `404` dans les deux cas parce que dans les deux cas il n'y a rien à rendre.

### 6. La query rend un `Optional` vide pour le document inconnu, et **lève** pour l'original disparu

C'est un écart à la règle « une absence de résultat se représente par un `Optional` vide,
pas par une exception », et il est délibéré.

Un `Optional` ne porte qu'une seule absence ; il en faut deux, distinctes. Les trois façons
d'en sortir :

- deux `Optional` imbriqués — `Optional<Optional<…>>` est illisible ;
- un type somme (`sealed interface`) — trois classes pour deux cas, dans un contexte qui
  n'en a aucun autre exemple ;
- une exception métier pour le cas qui n'est **pas** une absence ordinaire.

C'est la troisième. Et elle se défend au-delà de l'économie : qu'une ligne
`knowledge_documents` existe sans son objet est une **rupture d'invariant**, pas un
résultat vide. `UploadDocumentHandler` écrit l'original avant de publier, et
`DeleteDocumentHandler` efface les deux ; il n'existe aucun chemin nominal qui produise cet
état. Le seul qui y mène est humain — un objet effacé à la main, une base restaurée sans
son bucket. Une exception dit ça ; un `Optional` vide dirait « il n'y a rien ici », ce qui
est faux.

Le précédent existe : `AuthenticateUserHandler` est une query qui lève, pour une raison de
même famille (elle ne demande pas si un compte existe, elle réclame un jeton), et
`CLAUDE.md` l'assume déjà en toutes lettres.

**Un ADR pourrait être dû ici.** Il n'est pas écrit : `.claude/rules/decisions.md` interdit
d'en rédiger un sans accord préalable. Le code part sans, et la décision est consignée ici.

### 7. La lecture du stockage a lieu dans la transaction `readOnly` du query bus

Comme l'appel de vectorisation de `SearchChunksHandler`, et pour la même raison : la
transaction appartient au bus, un handler ne s'y soustrait pas. Le prix est une connexion
PostgreSQL tenue le temps d'un aller-retour S3 — quelques dizaines de millisecondes vers un
Garage local, sans commune mesure avec la seconde que coûte déjà une recherche.

### 8. Le front ne peut pas se contenter d'un lien

Le jeton d'accès voyage dans l'en-tête `Authorization` (ADR-0003). Un `<a href="/api/…">`
n'en emporte aucun : le navigateur ferait une requête anonyme et récolterait un `401`.

Le seul chemin est donc : `fetch` avec l'en-tête → `Blob` → `URL.createObjectURL` → une
ancre `download` fabriquée, cliquée, retirée → `URL.revokeObjectURL`.

Les deux alternatives sont écartées : un jeton en query string atterrirait dans
l'historique du navigateur et les logs du proxy, exactement le reproche qu'ADR-0007 fait
déjà au jeton de vérification ; une URL signée à durée courte demanderait une route de plus
et une notion de signature que le projet n'a pas.

`src/api/client.js` reste le seul module qui parle HTTP : il expose
`fetchDocumentContent(token, id)` et rend un `Blob`. Ce qu'on fait du blob est une affaire
d'écran, pas de HTTP.

### 9. Le geste est un composant partagé, pas un utilitaire

Les deux écrans portent le même geste. La règle front est explicite : « un motif copié
d'une vue à l'autre est un composant qui n'a pas encore été extrait ». Ce sera
`DownloadDocumentButton.vue`, dans `src/components/`, avec sa vignette dans
`/design-system` — même commit, comme l'exige la règle.

Il porte l'appel, la sauvegarde et son état occupé. Il **ne porte pas la déconnexion** : il
émet son erreur, et la vue la passe à son `handle` existant, qui déconnecte sur
`UnauthorizedError`. Le serveur fait autorité, et c'est l'écran qui en tire les
conséquences — un composant partagé ne pousse pas de route.

Un module `src/utils/` exportant un `saveBlob` a été écarté : il ouvrirait un répertoire
absent du tableau de découpage des règles front, pour une fonction qui n'aurait qu'un seul
appelant de plus que le composant.

### 10. Le nom du fichier est passé en prop, il n'est pas relu de l'en-tête

Le composant reçoit `filename` en prop plutôt que de décoder le `Content-Disposition` de la
réponse. Les deux écrans affichent déjà ce nom : le lire une seconde fois par un décodage
RFC 5987 côté navigateur serait du code à écrire, à tester et à maintenir pour obtenir
exactement la même chaîne.

Ce n'est pas une divergence au sens d'ADR-0022 : les deux valeurs viennent du même
`GET /api/documents`, dans la même page, à la même seconde. L'en-tête reste posé côté
serveur — pour `curl`, pour Swagger UI, et parce qu'une réponse `attachment` sans nom est
incomplète.

## Ce qui n'est pas fait

- **Aucune prévisualisation.** Pas de `inline`, pas de visionneuse PDF intégrée.
- **Aucune reprise de téléchargement.** Ni `Range`, ni `ETag`, ni `Last-Modified` : le
  corps part entier ou pas du tout.
- **Aucun test de rendu du bouton.** La règle front l'interdit ; ce qui est testé, c'est
  `fetchDocumentContent` dans `client.spec.js`, et `/design-system` tient lieu de contrôle
  visuel (ADR-0016).
- **Aucun téléchargement groupé.** Un document à la fois.
