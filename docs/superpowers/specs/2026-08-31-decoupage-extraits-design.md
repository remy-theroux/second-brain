# Découper un document en extraits contextualisés — design

Date : 2026-08-31 · Contexte : `knowledge` · Branche : `feat/chunk-and-ambeddings`

Ticket Notion : « RAG-5 — Découper un document en extraits contextualisés »
(<https://app.notion.com/p/3c0215c5e46e8190beeedb048ca7889b>), **second des deux
livrables**. Le premier, le socle vectoriel, est décrit par
`2026-08-31-socle-vectoriel-design.md` : sans lui, ni `EmbeddingPort`, ni colonne `vector`,
ni Ollama.

## Contexte

Un document déposé porte aujourd'hui son texte extrait : une `TextExtraction`, une suite
ordonnée de `TextBlock` titrés, et le statut `EXTRACTED`. Le worker reçoit
`DocumentTextExtracted`, et le **journalise**. La javadoc de `KnowledgeEventListener` le dit
depuis deux tickets : « RAG-5 remplacera cette ligne par un dispatch. » C'est ce ticket.

Le découpage décide de ce que le modèle verra. Un extrait coupé au milieu d'une phrase, ou
privé du titre de sa section, devient inintelligible une fois sorti de son document — et le
défaut ne se voit qu'à la première question restée sans réponse, quatre tickets plus loin.
C'est le même risque que l'extraction silencieuse, et il appelle la même réponse : des
invariants portés par les types, pas par des contrôles qu'on peut oublier.

Le ticket ajoute une exigence qui ne figure pas dans son Gherkin : le découpage est une
**logique de domaine pure**, testable sans contexte applicatif. C'est ce qui le rend
justiciable d'un développement piloté par les tests de bout en bout, et c'est la raison
d'être de la décision 4.

## Objectif

Un document dont le texte vient d'être extrait porte, quelques dizaines de secondes plus
tard, ses extraits vectorisés en base et le statut `READY` — ou un `FAILED` et un motif
lisible.

**Réussi si :** aucun extrait ne dépasse le plafond de tokens ; aucun ne commence ni ne finit
au milieu d'une phrase, sauf là où le texte lui-même n'offre aucune frontière ; deux extraits
consécutifs d'une même section se recouvrent ; un document plus court qu'un extrait en donne
exactement un ; chaque extrait sait dire de quel document et de quelle section il vient ; un
Ollama à terre ne laisse aucun extrait partiel derrière lui.

## Attendus métier

Les quatre scénarios du ticket :

```gherkin
Fonctionnalité: Découpage d'un document en extraits

  Scénario: Découpage d'un document long
    Étant donné un document de plusieurs sections
    Quand je le découpe
    Alors aucun extrait ne dépasse la taille maximale
    Et aucun extrait ne commence ni ne finit au milieu d'une phrase

  Scénario: Continuité entre deux extraits
    Étant donné une section plus longue qu'un extrait
    Quand je la découpe
    Alors deux extraits consécutifs partagent un recouvrement de texte

  Scénario: Document plus court qu'un extrait
    Étant donné un document d'une centaine de mots
    Quand je le découpe
    Alors j'obtiens un seul extrait

  Scénario: Extrait lu hors de son document
    Étant donné un extrait quelconque
    Quand je le lis isolément
    Alors il indique de quel document et de quelle section il provient
```

Quatre s'ajoutent, imposés par le transport et par la vectorisation, non par le ticket :

```gherkin
  Scénario: Paragraphe géant sans ponctuation
    Étant donné une section d'un seul tenant, sans aucune fin de phrase
    Quand je la découpe
    Alors j'obtiens des extraits sous le plafond, coupés faute de frontière

  Scénario: Le service de vectorisation est injoignable
    Étant donné un document dont le texte est extrait
    Quand le service de vectorisation ne répond pas
    Alors le document porte le statut FAILED et le motif nomme la vectorisation
    Et aucun extrait ne subsiste en base
    Et son texte extrait est intact

  Scénario: Un événement livré deux fois ne double pas les extraits
    Étant donné un document déjà découpé
    Quand son événement d'extraction est livré une seconde fois
    Alors le document porte toujours exactement un jeu d'extraits

  Scénario: Le document suit son cycle de vie jusqu'au bout
    Étant donné un document déposé
    Quand son traitement se termine
    Alors il porte le statut READY
```

Le troisième n'est pas de la prudence gratuite : AMQP est *at-least-once*, et c'est la même
mécanique qui a imposé l'effacement préalable à l'extraction.

## Décisions de conception

Chacune a été arbitrée avec le porteur du ticket avant l'écriture du plan. **Aucune ne donne
lieu à un ADR** : il a jugé qu'aucune n'était une décision d'architecture au sens de
`decisions.md`. Le raisonnement vit dans la javadoc des classes nommées ci-dessous et dans
les messages de commit.

### 1. Une logique pure produit un objet-valeur, le handler en fait une entité

Exactement le miroir de l'extraction, où `ExtractedText` est un objet-valeur et
`TextExtraction` l'entité qui le range.

- `Chunk` (`domain/valueobject/`) porte `heading` et `text`. Rien d'autre : **sa position
  appartient à la liste, pas à lui**, comme la position d'un `TextBlock` appartient à
  l'`@OrderColumn` de son extraction. Un extrait sorti de son document reste le même extrait.
- `TextChunk` (`domain/entity/`) porte `id`, `documentId`, `position`, `heading`, `text`,
  `embedding` et `createdAt`. Table `knowledge_text_chunks` — nommée par la **typologie** du
  document et non par le mot « document », c'est ADR-0030.

`Chunk` ne reprend pas le `headingLevel` de son `TextBlock` d'origine. Le préfixe n'en a que
faire, et le niveau reste lisible dans l'extraction, qui n'est jamais effacée : reconstruire
plus tard un chemin de section (« Chapitre 1 > Introduction ») se fera depuis là, sans avoir
à le recopier dans chaque extrait.

Écarté : faire produire des entités par `RecursiveChunker`. Le découpage n'a pas à savoir
qu'il existe une base ; et un objet-valeur se compare par ses champs, ce qui rend les
assertions de test lisibles.

Écarté aussi : loger les extraits en `@ElementCollection` d'un agrégat « découpage », comme
les blocs le sont de leur extraction. La recherche vectorielle de RAG-8 rendra des extraits
un par un, avec leur score : ils ont besoin d'une identité, une collection d'éléments n'en a
pas.

### 2. Sections, puis paragraphes, puis phrases — et une coupe forcée en dernier recours

`RecursiveChunker.chunk(ExtractedText) → List<Chunk>`, dans `knowledge/domain/`, à la racine,
aux côtés d'`ExtractionPolicy`. Cible 600 tokens, plafond 800.

1. **Une section qui tient sous le plafond donne un extrait.** On ne coupe pas un bloc de 700
   tokens pour se rapprocher de la cible : la cible gouverne l'accumulation, pas la découpe
   d'un bloc déjà valide.
2. **Sinon, découpe en paragraphes sur la double ligne vide.** Ce n'est pas une heuristique :
   `TextBlock.normalise` garantit déjà qu'une frontière de paragraphe survit sous la forme
   d'exactement deux sauts de ligne, et le `CLAUDE.md` annonce depuis le ticket précédent que
   c'est elle que RAG-5 cherchera. Accumulation gloutonne jusqu'à la cible.
3. **Un paragraphe seul au-dessus du plafond descend aux phrases**, par
   `BreakIterator.getSentenceInstance(Locale.FRENCH)` — le JDK, zéro dépendance.

Écarté : une expression régulière sur `[.!?]`. Elle coupe sur « 3.14 », sur « etc. » et sur
« M. Dupont ». `BreakIterator` se trompe aussi, moins souvent, et **sa panne est bénigne** :
une fausse frontière produit un extrait un peu court, jamais un extrait cassé. Écarté aussi :
ICU4J, qui ferait mieux au prix d'une dépendance de plusieurs mégaoctets pour un gain que
personne ne saurait mesurer ici.

4. **Une phrase seule au-dessus du plafond est coupée net à la frontière de token.** C'est le
   « paragraphe géant sans ponctuation » du ticket : un texte qui n'offre aucune frontière ne
   peut pas en imposer une. **C'est le seul endroit où la promesse « jamais au milieu d'une
   phrase » cède, et elle y est forcée.** Écrit ici plutôt que laissé à découvrir dans le
   code.

**Un cas limite du ticket tombe tout seul.** « Section vide » ne peut pas atteindre le
chunker : `TextBlock.of` refuse un corps vide, et `ExtractedTextBuilder` écarte les sections
sans corps avant même de construire un bloc. Le test existera et constatera cette garantie,
au lieu d'en bâtir une seconde au mauvais étage.

### 3. Le recouvrement se prend en phrases entières, et ne franchit pas une section

Environ 90 tokens, soit 15 % de la cible. Il se constitue des **dernières phrases entières**
de l'extrait précédent — pas d'une fenêtre glissante de tokens, qui reproduirait à la
jointure exactement la coupure que le reste de l'algorithme évite.

**Il ne franchit jamais une frontière de section.** Deux sections portent deux titres ; un
recouvrement à cheval ferait mentir le préfixe de l'extrait suivant, qui annoncerait une
section dont il ne contient pas le début. Le second scénario du ticket dit d'ailleurs « une
section plus longue qu'un extrait », pas « un document ».

**Et il cède devant le plafond.** Le recouvrement est un confort, le plafond est un
invariant : si les 90 tokens repris feraient passer l'extrait au-dessus de 800, on en reprend
moins, et zéro s'il le faut. Sans cette règle, un recouvrement suivi d'une longue phrase
produirait l'unique extrait hors plafond de tout l'algorithme — et c'est le premier scénario
du ticket qui tomberait.

### 4. Le comptage passe par un port : `cl100k_base` est la toise d'un autre

Le ticket impose jtokkit en `cl100k_base`. Il faut le dire une fois clairement :
**`cl100k_base` est le tokenizer d'OpenAI, et `bge-m3` n'en est pas un.** `bge-m3` s'appuie
sur le sentencepiece XLM-RoBERTa, dont le découpage du français est sensiblement différent.
On mesure en pieds une étoffe vendue en mètres.

C'est **sans danger** : sur du français, `cl100k` sur-compte par rapport au sentencepiece,
donc un extrait de 800 « tokens `cl100k` » reste très en deçà des 8192 que `bge-m3` accepte.
Le plafond est conservateur, il ne peut pas être dépassé par surprise. Mais 600 est un
**proxy**, pas une mesure, et un lecteur futur a le droit de le savoir.

D'où une interface `TokenCounter` dans `domain/port/`, et `JtokkitTokenCounter` en
infrastructure. Deux gains concrets, et non une abstraction posée d'avance :

- le jour où le modèle change, la toise change sans qu'on touche au chunker ;
- les tests du chunker prennent un compteur « un mot égale un token », ce qui rend les
  frontières d'extraits **lisibles dans les assertions** au lieu d'être des nombres magiques
  dont personne ne sait les recalculer.

Un second test emploie le vrai `JtokkitTokenCounter`, pour ne pas ne vérifier que la
doublure.

Écarté : jtokkit importé directement dans `RecursiveChunker`, ce que le ticket autorise
puisqu'il n'interdit que Spring. Plus court, mais ça soude le domaine au tokenizer d'un
fournisseur qu'on n'emploie pas.

### 5. Le préfixe de contexte est une règle, pas une donnée

`Chunk.contextualised(String filename)` rend `Document: rapport.pdf — Section: Introduction`
suivi du corps, et se réduit à `Document: rapport.pdf` quand le document ne porte pas de
titre à cet endroit. **C'est la seule méthode qui connaisse la forme du préfixe.** Elle sert
à vectoriser, et servira à alimenter le prompt de RAG-9.

La colonne `text` porte le **corps nu**. Trois raisons : ce qui s'affiche à l'écran reste
lisible, là où un extrait préfixé montré tel quel est du balisage sous les yeux ; changer la
forme du préfixe plus tard ne demande pas de réécrire la base, seulement de revectoriser ; et
le quatrième scénario est satisfait par les colonnes `heading` et `document_id`, qui disent
la provenance aussi bien qu'une chaîne recopiée.

Écarté : écrire le préfixe dans la colonne. Ce qui est stocké serait exactement ce qui a été
vectorisé — un vrai avantage pour le diagnostic que réclamera RAG-8 — mais au prix d'un
préfixe recopié dans chaque ligne et d'un écran qui doit le retirer pour afficher proprement.

Écarté aussi : deux colonnes, `text` nu et `embedded_text` préfixé. Le corps serait stocké
deux fois — sur une base de documents, c'est du volume réel — et deux colonnes tenues de
rester cohérentes finissent par diverger.

**Ce que ça suppose, et qui est vrai aujourd'hui :** aucune route ne renomme un document.
L'identité d'un document est son empreinte, son nom n'est qu'une étiquette, et rien ne la
change. Le jour où un renommage existerait, la chaîne recalculée cesserait de correspondre au
vecteur stocké, et ce serait à revectoriser.

### 6. Une commande, une transaction du bus

`KnowledgeEventListener.on(DocumentTextExtracted)` remplace sa ligne de journal par
`commandBus.dispatch(new IndexDocumentText(documentId, ownerId))`.

`IndexDocumentTextHandler` — « indexer » couvre les trois gestes en un mot, plutôt qu'un nom
de commande qui les énumère — enchaîne : relire le document par `findByIdAndOwnerId` (le
cloisonnement ne se relâche pas parce qu'on est dans un worker), relire son `TextExtraction`,
découper, vectoriser, effacer, écrire, poser `READY`, annoncer.

**Vectoriser avant de toucher à la base.** Transactionnellement c'est indifférent — le
rollback couvre tout —, mais ça se lit mieux, et c'est l'ordre du handler d'extraction : on
obtient ce dont on a besoin, puis on écrit.

**L'effacement avant l'écriture** répond à la redélivrance AMQP, comme à l'extraction.

**Tout tient dans la transaction ouverte par le bus**, appels Ollama compris. Le « tout ou
rien » que RAG-6 exigeait est donc gratuit : c'est le rollback, il n'y a rien à construire, et
un Ollama qui tombe au troisième lot ne laisse aucun extrait derrière lui.

Le prix, assumé : une connexion PostgreSQL tenue quelques dizaines de secondes par document —
un PDF de trente pages fait une centaine d'extraits, soit quatre lots. Sur une application
mono-utilisateur dont le worker consomme en séquence, c'est tenable. **C'est précisément le
genre de chose qu'on « corrige » spontanément faute de savoir qu'elle a été pesée** : d'où
cette section, et d'où la javadoc du handler.

Écarté : deux commandes chaînées par un événement, le découpage commité avant la
vectorisation. Le découpage deviendrait visible même Ollama à terre, mais la transaction de
vectorisation resterait longue, et le tout-ou-rien serait perdu — un document pourrait rester
découpé mais non vectorisé, état qu'il faudrait alors savoir nommer et rattraper.

Écarté aussi : l'écriture incrémentale par lot. Aucune transaction ne dépasserait quelques
secondes, mais c'est l'état partiel que RAG-6 interdit, et le handler devrait orchestrer ses
propres transactions — ce que la règle « jamais de `@Transactional` sur un handler » ferme.

### 7. Le vecteur est une colonne de l'extrait

`V10__create_knowledge_text_chunks.sql` : `id`, `document_id` (`ON DELETE CASCADE`),
`chunk_position` avec `UNIQUE (document_id, chunk_position)`, `heading`, `text`,
`embedding vector(1024) NOT NULL`, `created_at`. Index HNSW en `vector_cosine_ops` —
`bge-m3` produit des vecteurs normalisés et s'évalue au cosinus.

Une table, une ligne par extrait : c'est du un-pour-un, né en même temps et effacé en même
temps. Deux tables imposeraient une jointure sur le chemin chaud de la recherche.

Écarté : une table de vecteurs à part, portant le nom du modèle qui les a produits.
Revectoriser avec un autre modèle n'effacerait pas les extraits, et deux modèles pourraient
coexister le temps d'une comparaison — utile à RAG-14. C'est de la souplesse payée maintenant
pour un besoin qui n'existe pas, et la comparaison de deux modèles se fait aussi bien sur deux
bases.

Écarté aussi : une colonne `embedding_model` sur la ligne. Personne n'en lirait la valeur, et
ce projet ne déclare pas d'avance — `DocumentStatus` a refusé de poser `READY` tant que
personne ne l'atteignait.

**Conséquence assumée : la dimension du modèle est figée dans le type de la colonne.** Passer
à un modèle à 768 dimensions demandera une migration et une réindexation complète. C'est déjà
vrai de toute façon : les vecteurs de deux modèles ne se comparent pas.

### 8. `READY` rejoint le cycle de vie, et l'échec cesse d'être « d'extraction »

`DocumentStatus` gagne `READY` après `EXTRACTED` — sa javadoc l'annonçait mot pour mot, en
prévenant de ne pas le déclarer avant que quelqu'un l'atteigne. C'est fait, quelqu'un
l'atteint. `Document` gagne `markIndexed()`.

Le chemin d'échec réclame un petit refactor : `MarkDocumentExtractionFailed` et
`DocumentExtractionException` sont nommés pour une phase qui n'est plus la seule.

- `MarkDocumentProcessingFailed` remplace `MarkDocumentExtractionFailed`.
- `DocumentProcessingException` devient le parent de `DocumentExtractionException` (dont les
  deux filles ne bougent pas) et de `EmbeddingUnavailableException`.
- Le `motif()` du listener teste le parent.

Sans quoi une URL Ollama mal saisie s'afficherait avec le message générique, indiscernable
d'un PDF illisible. C'est la contrepartie directe de l'absence de contrôle au démarrage,
décidée dans le premier livrable.

Le `try/catch` d'ADR-0028 se recopie tel quel sur `on(DocumentTextExtracted)` : seconde
commande, seconde transaction, message acquitté.

**Effet de bord agréable :** un document dont la vectorisation échoue passe `FAILED` en
gardant son texte extrait. L'écran de détail montre donc le texte *et* le motif — on voit ce
qui a marché et où ça a cassé.

Nouvel événement `DocumentTextIndexed(documentId, ownerId, chunkCount, occurredAt)`, clé
`knowledge.document-text.indexed`, à déclarer dans `DomainEventRegistration` et à doter d'un
`@RabbitHandler` — un type déclaré sans handler est rejeté par Spring AMQP. Il ne fait que
journaliser, comme `DocumentTextExtracted` avant lui.

### 9. La cascade fait, pour la deuxième fois, que `DeleteDocumentHandler` ne bouge pas

`ON DELETE CASCADE` sur `document_id`, comme les deux tables d'extraction. C'est la seconde
fois qu'un ticket ajoute des tables au document sans toucher à sa suppression : ça commence
à ressembler à une propriété du design plutôt qu'à une chance.

### 10. Le front n'apprend qu'un libellé

`DocumentStatusTag` gagne le libellé et la sévérité de `READY`, faute de quoi les deux écrans
afficheraient un statut inconnu. C'est tout.

**Montrer les extraits à l'écran est hors périmètre.** Ce sont des artefacts machine ; c'est
RAG-8 qui aura une raison de les exposer, avec des scores de similarité à côté.

## Ce qui reste hors périmètre

- **Le découpage sémantique et la taille adaptative selon le format** : le ticket les exclut
  explicitement.
- **La recherche vectorielle** : RAG-8. Ce ticket écrit l'index HNSW, il ne l'interroge pas.
- **Un écran des extraits** : RAG-8 ou RAG-11.
- **La reprise après redémarrage et la file d'attente persistante** : RAG-6 les excluait, rien
  n'a changé.
- **La réextraction d'un document modifié** : RAG-7. `deleteByDocumentId` existera au port
  pour l'idempotence ; RAG-7 s'en servira sans le créer, comme il le fera de son homologue
  côté extraction.
- **Le cas du PDF, dont les frontières de paragraphe sont perdues à l'extraction** : une
  section de PDF arrive ici comme un seul paragraphe et sera découpée à la phrase. C'est
  dégradé, pas faux, et c'était annoncé.

## Pour aller plus loin

- Premier livrable : `2026-08-31-socle-vectoriel-design.md`
- Spec de l'extraction : `2026-08-26-extraction-texte-documents-design.md`
- Spec du socle événementiel : `2026-08-25-evenements-metier-rabbitmq-design.md`
- ADR-0024 (le texte extrait est une suite plate de blocs titrés), ADR-0028 (l'échec hors de
  la transaction annulée), ADR-0030 (les tables portent leur typologie), ADR-0006 (référence
  par identifiant), ADR-0023 (pas d'outbox)
