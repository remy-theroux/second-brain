# Extraction du texte et des sections d'un document — design

Date : 2026-08-26 · Contexte : `knowledge` · Branche : `feat/extraction-texte-documents`

Ticket Notion : « RAG-4 — Extraire le texte et les sections d'un document »
(<https://app.notion.com/p/3c0215c5e46e815bac9dcf53bb7b73b9>), **second des deux
livrables** du ticket. Le premier, le socle événementiel et le rôle worker, est livré :
voir `2026-08-25-evenements-metier-rabbitmq-design.md`. Celui-ci s'y branche par la ligne
que `KnowledgeEventListener.on(DocumentUploaded)` réserve déjà en commentaire.

## Contexte

Un document déposé est une ligne `knowledge_documents` en `PENDING` et un binaire sur
disque. `DocumentUploaded` part sur RabbitMQ, le worker le reçoit, et le journalise — c'est
tout. Rien n'en tire de texte, rien ne signale qu'il n'y en a pas.

Le ticket le dit lui-même : c'est le maillon le plus fragile du pipeline. La qualité des
réponses de RAG-9 est plafonnée par celle de l'extraction, et une extraction qui produit du
vide en silence ne se voit qu'à la première question restée sans réponse, trois tickets
plus loin. D'où deux exigences de rang égal : **produire du texte structuré**, et **refuser
bruyamment** quand il n'y en a pas.

Le porteur du ticket a par ailleurs posé une exigence qui ne figure pas dans le Gherkin :
le format produit doit être **matérialisé dans le domaine**, pas une convention de chaîne
de caractères ni un blob. C'est lui le livrable durable — RAG-5, RAG-6 et RAG-7 le
consommeront tous.

## Objectif

`POST /api/documents` suffit à ce qu'un document porte, quelques secondes plus tard, son
texte découpé en sections dans une forme commune à tous les formats — ou un statut `FAILED`
et un motif lisible.

**Réussi si :** un `.pdf`, un `.docx`, un `.md` et un `.txt` déposés produisent chacun des
blocs de texte titrés en base ; un PDF numérisé sans couche texte passe en `FAILED` avec
son motif ; aucun document ne passe en `EXTRACTED` avec un texte vide ; la suite de tests le
prouve sans rien lancer à la main.

## Attendus métier

```gherkin
Fonctionnalité: Extraction du texte d'un document

  Scénario: Extraction d'un document structuré
    Étant donné un document comportant des titres de sections
    Quand j'en extrais le texte
    Alors j'obtiens ses blocs de texte, chacun rattaché au titre de sa section

  Scénario: Extraction d'un document sans structure
    Étant donné un document texte dépourvu de titres
    Quand j'en extrais le texte
    Alors j'obtiens un unique bloc contenant tout le texte

  Scénario: Document numérisé sans couche texte
    Étant donné un PDF issu d'une numérisation
    Quand j'en extrais le texte
    Alors l'extraction échoue en indiquant que le document n'est pas exploitable
    Et aucun texte vide n'est produit silencieusement
```

Deux scénarios s'ajoutent, imposés par le transport et non par le ticket :

```gherkin
  Scénario: L'échec survit à la transaction annulée
    Étant donné un document dont l'extraction échoue
    Quand le worker traite son événement
    Alors le document porte le statut FAILED et le motif de l'échec

  Scénario: Un événement livré deux fois ne double pas le texte
    Étant donné un document déjà extrait
    Quand son événement de dépôt est livré une seconde fois
    Alors le document porte toujours exactement un texte extrait
```

Le second n'est pas de la prudence gratuite : AMQP est *at-least-once*, et
`knowledge_document_texts.document_id` est `UNIQUE`. Sans effacement préalable, une
redélivrance passerait le document en `FAILED` pour violation de contrainte.

## Décisions de conception

Chacune a été arbitrée avec le porteur du ticket avant l'écriture du plan. Les cinq
premières deviennent des ADR dans le commit de la tâche qui les met en œuvre ; les autres
sont des choix sans alternative crédible, notés ici pour mémoire.

### 1. Le format est une suite plate de blocs titrés — ADR-0024

`ExtractedText` porte une `List<TextBlock>` ordonnée et jamais vide. Un `TextBlock` porte
`heading` (le titre de sa section, vide s'il n'y en a pas), `headingLevel` (1 à 6, ou 0) et
`text` (le corps de la section, normalisé).

**Un bloc est une section, pas un paragraphe.** C'est ce que tranche le second scénario : un
document sans titre rend « un unique bloc contenant tout le texte », or un texte sans titre
compte bien plusieurs paragraphes. Si un bloc était un paragraphe, ce scénario en produirait
dix.

Écartés : l'arbre de sections imbriquées — RAG-5 ne sait qu'en faire d'autre que l'aplatir,
et l'aplatissement est son unique usage prévu ; le Markdown canonique en une seule chaîne —
le domaine ne matérialiserait plus rien, et RAG-5 re-parserait ce que RAG-4 vient de
sérialiser.

`headingLevel` est conservé alors qu'aucun consommateur ne le demande aujourd'hui : c'est
la seule information qui permettra plus tard de reconstruire un chemin de section
(« Chapitre 1 > Introduction ») sans réextraire tous les documents. Le niveau est une donnée
que l'extraction est seule à connaître ; le chemin se recalcule, lui, à tout moment.

### 2. Un plancher de caractères décide qu'un document est inexploitable — ADR-0025

`ExtractionPolicy.MINIMUM_USEFUL_CHARACTERS = 50`. En dessous, `ExtractedText` refuse de se
construire et lève `UnextractableDocumentException`.

Écarté : le test `isBlank()` seul. Un PDF numérisé rend rarement zéro caractère — il rend
un numéro de page, un tampon, une mention de scanner. Trois bribes suffiraient à le faire
passer pour exploitable, et c'est exactement le vide silencieux que le ticket interdit.

Écarté aussi : un ratio caractères/pages, plus fin sur un PDF de trente pages, mais la
notion de page n'existe ni en Markdown, ni en DOCX, ni en `.txt` — la règle cesserait d'être
commune à tous les formats.

Le plancher vit dans `knowledge/domain/ExtractionPolicy`, à la racine du domaine, aux côtés
de ce que `PasswordPolicy` est pour `users` : une règle métier pure, statique, testable sans
Spring.

### 3. Un extracteur par format, pas Apache Tika — ADR-0026

Le port `DocumentTextExtractor` déclare `format()` et `extract(byte[])`. Quatre adapters
dans `knowledge/infrastructure/extraction/` : PDFBox 3, POI (XWPF), commonmark-java, et le
JDK seul pour le `.txt`.

Écarté : Apache Tika. Une seule dépendance qui lit tout, mais son XHTML unifié aplatit
précisément la sémantique qu'on cherche à garder — les styles `Heading1..9` d'un DOCX et les
`#` d'un Markdown sont des informations de premier ordre ici, pas du balisage à traverser —
et `tika-parsers-standard-package` tire des dizaines de mégaoctets de transitives pour
quatre formats.

**Le format accepté au dépôt et le format lisible ne doivent pas pouvoir diverger.**
`ExtractDocumentTextHandler` indexe les extracteurs par format à la construction et **échoue
au démarrage** si une constante de `DocumentFormat` n'a pas le sien. Ajouter un format sans
son extracteur ne compile pas au sens où l'application ne démarre plus — même dispositif que
la table de routage des bus.

### 4. Les titres d'un PDF sans signets sont devinés à la taille de police — ADR-0027

Un PDF ne porte aucune sémantique de titre. Deux stratégies, dans cet ordre :

1. **Le sommaire (outline)** quand le document en a un : une section par signet, la
   profondeur du signet donnant le niveau.
2. **La taille de police** sinon : la taille du corps est celle qui porte le plus de
   caractères ; une ligne non vide, d'au plus 120 caractères, écrite au moins 15 % plus
   grand que le corps, est un titre. Les tailles de titre distinctes, rangées de la plus
   grande à la plus petite, donnent les niveaux 1, 2, 3…

Écartés : les signets seuls — la plupart des PDF personnels n'en ont pas, et le ticket
demande « un texte lisible découpé en sections » pour *chacun* des formats acceptés ;
l'heuristique seule — quand le sommaire existe, il est écrit par l'auteur et vaut mieux que
n'importe quelle mesure.

**Limite assumée du chemin par signets : la granularité est la page.** PDFBox ne sait
découper qu'en plages de pages. Deux signets qui tombent sur la même page sont fusionnés
sous le titre du premier, faute de quoi le texte de cette page serait rendu deux fois — et
un texte dupliqué est bien pire, pour un RAG, qu'un titre manquant.

**Limite assumée du texte extrait d'un PDF : les frontières de paragraphe sont perdues.**
`PDFTextStripper` sépare les lignes, pas les paragraphes ; sa détection de paragraphes
(`setAddMoreFormatting`) est un pari sur la mise en page. Une section de PDF arrive donc à
RAG-5 comme un seul paragraphe, et RAG-5 la découpera à la phrase. C'est dégradé, pas faux.

### 5. L'échec s'écrit hors de la transaction annulée, et le message est acquitté — ADR-0028

`KnowledgeEventListener.on(DocumentUploaded)` dispatche `ExtractDocumentText` dans un
`try`. Le `catch` journalise, puis dispatche une **seconde commande**,
`MarkDocumentExtractionFailed`, donc dans une **seconde transaction** — la première est
annulée, et un `document.markExtractionFailed(...)` écrit dedans disparaîtrait avec elle.
C'est le piège que RAG-6 signale déjà ; il mord dès ce ticket.

Le listener **ne relève pas** l'exception. `default-requeue-rejected=false` ferait rejeter
le message, mais rejeter n'apporte rien ici : l'issue du traitement est déjà en base, en
`FAILED`, avec son motif. Relever ne produirait qu'une pile de plus dans le journal.

Le motif affiché vient de l'exception quand elle est métier — `DocumentExtractionException`
et ses deux filles portent des messages affichables tels quels — et d'une phrase générique
sinon : le message d'une `NullPointerException` n'a rien à faire sous les yeux de
l'utilisateur.

Si la seconde commande échoue à son tour, l'exception remonte, le message est rejeté sans
remise en file, et le document reste `PENDING`. C'est le seul trou, il est journalisé en
`ERROR`, et il relève du même arbitrage qu'ADR-0023 : on fait confiance au broker et à la
base, on ne construit pas de filet au filet.

### 6. Le texte extrait est un agrégat à part, dans deux tables

`DocumentText` est une entité JPA du domaine (`knowledge_document_texts`, une ligne par
document, `document_id` `UNIQUE`), et ses blocs sont une `@ElementCollection` ordonnée vers
`knowledge_document_blocks`. Les deux tables cascadent à la suppression du document.

C'est un **agrégat distinct de `Document`** : il naît plus tard, il est remplacé en entier
à chaque réextraction (RAG-7), et ADR-0006 veut alors une référence par identifiant, pas un
`@ManyToOne`.

`TextBlock` porte donc `@Embeddable`. C'est l'extension naturelle d'ADR-0002 — les entités
JPA vivent dans le domaine, sans classe miroir ni mapper — à un objet-valeur possédé par une
entité : la seule alternative serait une classe miroir en infrastructure, exactement ce
qu'ADR-0002 refuse.

`@ElementCollection` est chargée en `EAGER`. `open-in-view` est à `false` et personne ne
charge un `DocumentText` sans vouloir ses blocs : une collection paresseuse ne ferait que
déplacer l'échec hors de la transaction.

L'ordre des blocs est porté par `@OrderColumn(name = "block_position")` et non par un champ
de `TextBlock` : la position est une propriété de la liste, pas du bloc. Un bloc extrait du
document reste le même bloc.

### 7. `EXTRACTED` et `FAILED` rejoignent `PENDING`, avec un motif

`DocumentStatus` gagne deux constantes et `knowledge_documents` une colonne
`error_message` (500 caractères, nullable).

Écarté : le vocabulaire `READY` / `ERROR` de RAG-6. `READY` y signifie « interrogeable »,
donc vectorisé ; le porter dès l'extraction ferait mentir le statut pendant deux tickets.
RAG-6 ajoutera `READY` après `EXTRACTED`, ou renommera — ce sera son arbitrage, pas le
nôtre.

Écarté aussi : les deux statuts sans colonne de motif. RAG-6 la réclame explicitement, la
migration est de trois lignes, et sans elle « pourquoi ce document a-t-il échoué ? » se
répond par `docker compose logs worker`.

`markTextExtracted()` efface le motif en même temps qu'il pose `EXTRACTED` : un document
réextrait avec succès ne doit pas garder l'explication de son échec précédent. Aucune des
deux méthodes ne garde d'état de départ : RAG-7 réextraira depuis `EXTRACTED` comme depuis
`FAILED`, et un garde posé aujourd'hui serait à retirer demain.

### 8. `ExtractDocumentText` porte le propriétaire

La commande est `ExtractDocumentText(documentId, ownerId)`, et le handler lit par
`findByIdAndOwnerId`. `DocumentUploaded` porte déjà les deux, et la règle du port tient :
« chaque méthode porte le propriétaire ; aucune lecture ne doit pouvoir l'oublier par
distraction ». Ajouter un `findById` au port pour le confort du worker ouvrirait la seule
lecture non cloisonnée de la base.

### 9. Le socle de fixtures est fabriqué, pas prélevé

Le ticket demande 5 à 10 vrais documents personnels en intégration continue. Ils ne peuvent
pas être fournis par l'agent qui écrit le code, et le porteur a tranché : **un socle
fabriqué seulement**, six fichiers écrits par une tâche Gradle `generateFixtures`, lancée à
la main une fois et dont le produit est versionné.

Conséquence assumée, et c'est celle contre laquelle le ticket mettait en garde : **aucun
vrai document ne passe jamais dans la suite**. Un PDF fabriqué par PDFBox est un PDF
aimable ; celui qu'un scanner de 2011 produit ne l'est pas. La vérification sur documents
réels reste un geste manuel, à faire sur la pile `docker compose`, et ce qu'elle révélera
sera un ticket.

## Ce qui reste hors périmètre

- **OCR**, extraction des tableaux et des images — hors périmètre du ticket.
- **Le découpage en extraits** : c'est RAG-5, qui consomme `ExtractedText`.
- **Le statut `READY` et la vectorisation** : c'est RAG-6.
- **La réextraction d'un document modifié** : c'est RAG-7. `deleteByDocumentId` existe déjà
  au port pour l'idempotence de la redélivrance ; RAG-7 s'en servira sans le créer.
- **Un écran de suivi** : c'est RAG-11. Ce ticket se contente d'ajouter les deux libellés
  de statut manquants et d'afficher le motif, pour que l'écran existant ne montre pas
  `FAILED` en anglais brut.
- **Le repli d'encodage au-delà d'UTF-8 puis ISO-8859-1** pour les `.txt` et `.md` : pas de
  détection de jeu de caractères, deux essais et c'est tout.

## Pour aller plus loin

- Plan d'implémentation : `docs/superpowers/plans/2026-08-26-extraction-texte-documents.md`
- Spec du socle événementiel : `2026-08-25-evenements-metier-rabbitmq-design.md`
- ADR-0006 (référence par identifiant), ADR-0002 (entités JPA dans le domaine),
  ADR-0020 (le disque hors transaction), ADR-0021 (le contenu en mémoire),
  ADR-0022 (ce que le front recopie), ADR-0023 (pas d'outbox)
