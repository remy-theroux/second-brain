# Typologie de document et visualisation du texte extrait — plan d'implémentation

> **Pour les agents d'exécution :** SOUS-COMPÉTENCE REQUISE — utiliser
> `superpowers:subagent-driven-development` (recommandé) ou `superpowers:executing-plans`
> pour dérouler ce plan tâche par tâche. Les étapes sont des cases à cocher (`- [ ]`).

**But :** un document annonce sa **typologie** — ce qui décide de la façon dont il se
découpe, et non de l'encodage de ses octets —, le schéma range le produit de l'extraction
sous cette typologie plutôt que sous un nom qui la suppose, et un écran `/documents/:id`
montre enfin ce qui a été extrait.

**Architecture :** une énumération `DocumentType` (une seule constante, `TEXTUAL`) dans le
domaine ; chaque `DocumentFormat` déclare la sienne. La typologie **n'est pas stockée** :
elle se déduit du format (ADR-0029). Les deux tables du texte extrait sont renommées pour
porter la typologie — `knowledge_text_extractions` et `knowledge_text_blocks` (ADR-0030) —,
et l'agrégat `DocumentText` devient `TextExtraction`. Une query `FindDocument` rend un
document et, quand elle existe, son extraction ; `GET /api/documents/{id}` la sert, et
`DocumentDetailView.vue` la rend.

**Stack :** Java 25 · Spring Boot 4.0.7 · PostgreSQL 17 · Flyway · JUnit 5 + AssertJ +
Testcontainers · Vue 3 · PrimeVue 4 (Aura) · Vitest (jsdom).

**Spec :** pas de spec dédiée — le raisonnement de conception est porté par ce plan et par
les deux ADR qu'il crée. La spec du flux dont il part est
`docs/superpowers/specs/2026-08-26-extraction-texte-documents-design.md` : la lire avant
d'exécuter, en particulier pour ADR-0024 (le format du texte extrait) et ADR-0026 (un
extracteur par format).

## Contraintes globales

Elles s'ajoutent implicitement aux exigences de **chaque** tâche.

- **Tout passe par Docker.** Aucun JDK, aucun Gradle, aucun Node sur l'hôte. Définir les
  fonctions `gtest` et `gfront` de `CLAUDE.md` une fois par session, avant la première
  commande.
- **`gtest` et `docker compose up` ne cohabitent pas** : `docker compose down` avant de
  lancer la suite.
- **Français** pour les commentaires, la Javadoc, les messages d'exception, les libellés et
  les noms de méthodes de test. **Anglais** pour les noms de classes, de méthodes de
  production, de packages et de fichiers front.
- **`make format-back` avant chaque commit** (`make format` si le front est touché). Le
  style est décidé par palantir-java-format et par Prettier ; ne pas se battre avec eux. La
  Javadoc et les commentaires ne sont jamais reformatés.
- **Jamais de `@Transactional` sur un handler** — la transaction appartient au bus.
- **Toute exception métier hérite de `RuntimeException`.**
- **Flyway est maître du schéma**, `ddl-auto: validate`. Ne jamais modifier une migration
  déjà appliquée : le renommage de tâche 3 est une migration **nouvelle**, `V8`, et non une
  réécriture de `V7`. Prochain numéro libre : **V8**.
- **Le domaine n'importe jamais `org.springframework.*` ni `…infrastructure.*`.**
- **Tester le port, pas l'adapter.** Injecter `TextExtractionRepository`, pas
  `JpaTextExtractionRepositoryAdapter`.
- **Pattern d'intégration imposé** : `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)`.
  Ne pas introduire `@DataJpaTest`.
- **Un test de contrôleur** suit exactement le gabarit de `ListDocumentsControllerTest` :
  `@Import({TestcontainersConfiguration.class, RecordingNotificationSenderConfiguration.class})`
  `@SpringBootTest` `@AutoConfigureMockMvc` `@Transactional`, `@BeforeEach` qui crée un
  compte vérifié par le chemin réel, `@AfterEach` `KnowledgeFixture.videLesOriginaux(...)`.
- **Dans un test `@Transactional`, un appel refusé est le dernier appel du test.**
- **Un ADR arrive dans le commit du code qu'il justifie**, avec sa ligne d'index dans
  `CLAUDE.md`. Deux ADR sont dus : **0029** (tâche 1) et **0030** (tâche 3). Gabarit :
  `docs/decisions/0000-adr-template.md`.
- **Le front n'a aucun test de composant** et n'en gagne pas ici : ce qui se teste est
  `src/api/client.js` et `src/router/index.js`. `/design-system` tient lieu de test de
  rendu (ADR-0016) — **tout composant partagé nouveau y apparaît dans le même commit**.
- **Un commit par tâche**, tests verts, préfixe conventionnel minuscule.

## Tâche 0 : ranger le plan dans le dépôt

Ce fichier vit hors du dépôt. Avant la tâche 1 :

```bash
cp <ce-fichier> docs/superpowers/plans/2026-08-26-typologie-de-document-et-visualisation.md
git add docs/superpowers/plans/2026-08-26-typologie-de-document-et-visualisation.md
git commit -m "docs: plan d'implémentation de la typologie de document et de la visualisation"
```

## Structure des fichiers

```
src/main/java/xyz/sterenn/secondbrain/knowledge/
├── domain/
│   ├── valueobject/
│   │   ├── DocumentType.java                       CRÉÉ  T1  la typologie — TEXTUAL, seule pour l'instant
│   │   └── DocumentFormat.java                     MODIF T1  + type(), + of(DocumentType)
│   ├── entity/
│   │   └── DocumentText.java → TextExtraction.java RENOM T3  tables knowledge_text_extractions / _blocks
│   ├── port/
│   │   └── DocumentTextRepository.java
│   │       → TextExtractionRepository.java          RENOM T3
│   └── exception/DocumentNotFoundException.java     MODIF T4  + constante MESSAGE
├── application/
│   ├── command/ExtractDocumentTextHandler.java      MODIF T2 (couverture) puis T3 (renommages)
│   └── query/
│       ├── FindDocument.java                        CRÉÉ  T4  Query<Optional<DocumentDetailView>>
│       ├── FindDocumentHandler.java                 CRÉÉ  T4  c'est ici que branchera la 2e typologie
│       ├── DocumentDetailView.java                  CRÉÉ  T4  le document + son extraction
│       └── TextExtractionView.java                  CRÉÉ  T4  la projection propre à la typologie textuelle
└── infrastructure/
    ├── persistence/
    │   ├── JpaDocumentTextRepositoryAdapter.java
    │   │   → JpaTextExtractionRepositoryAdapter.java     RENOM T3
    │   └── SpringDataDocumentTextRepository.java
    │       → SpringDataTextExtractionRepository.java     RENOM T3
    └── web/FindDocumentController.java              CRÉÉ  T4  GET /api/documents/{id}

src/main/resources/db/migration/
└── V8__rename_document_texts_to_text_extractions.sql  CRÉÉ T3

frontend/src/
├── api/client.js                        MODIF T5  + fetchDocument
├── components/DocumentStatusTag.vue     CRÉÉ  T5  libellé + sévérité d'un statut, partagé
├── router/index.js                      MODIF T5  + /documents/:id
├── views/DocumentDetailView.vue         CRÉÉ  T5  l'écran de visualisation
├── views/DocumentsView.vue              MODIF T5  action « Voir » + DocumentStatusTag
└── views/DesignSystemView.vue           MODIF T5  section DocumentStatusTag

docs/decisions/
├── 0029-la-typologie-d-un-document-se-deduit-de-son-format.md  CRÉÉ T1
└── 0030-chaque-typologie-a-ses-propres-tables-d-extraction.md  CRÉÉ T3
```

---

## Tâche 1 : La typologie dans le domaine

**Fichiers :**
- Créer : `src/main/java/xyz/sterenn/secondbrain/knowledge/domain/valueobject/DocumentType.java`
- Modifier : `src/main/java/xyz/sterenn/secondbrain/knowledge/domain/valueobject/DocumentFormat.java`
- Test : `src/test/java/xyz/sterenn/secondbrain/knowledge/domain/valueobject/DocumentFormatTest.java`
- Créer : `docs/decisions/0029-la-typologie-d-un-document-se-deduit-de-son-format.md`
- Modifier : `CLAUDE.md` (index des ADR + arborescence du contexte `knowledge`)

**Interfaces :**
- Produit : `enum DocumentType { TEXTUAL }` ; `DocumentType DocumentFormat.type()` ;
  `static List<DocumentFormat> DocumentFormat.of(DocumentType type)`.

**Pourquoi une énumération à une seule constante.** Elle ne sert pas à distinguer quelque
chose aujourd'hui : elle sert à ce que le jour où un `.mp3` entre, le code qui exige un
extracteur de texte pour *tout* format ne l'exige pas de lui. `TEXTUAL` plutôt que `TEXT`
parce que `DocumentFormat.TEXT` désigne déjà le `.txt` : deux `TEXT` dans un même fichier
ne veulent pas dire la même chose, et le lecteur le paierait.

- [ ] **Étape 1 : écrire les tests qui échouent**

Ajouter à `DocumentFormatTest` :

```java
    @Test
    void chaque_format_annonce_sa_typologie() {
        assertThat(DocumentFormat.values()).allSatisfy(format -> assertThat(format.type())
                .isNotNull());
    }

    @Test
    void les_quatre_formats_acceptes_se_decoupent_tous_en_texte() {
        assertThat(DocumentFormat.of(DocumentType.TEXTUAL))
                .containsExactly(
                        DocumentFormat.PDF, DocumentFormat.MARKDOWN, DocumentFormat.TEXT, DocumentFormat.DOCX);
    }
```

Import à ajouter : `xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentType`
n'est pas nécessaire (même package) ; `static org.assertj.core.api.Assertions.assertThat`
est déjà présent.

- [ ] **Étape 2 : constater l'échec**

Run : `gtest test --tests "xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormatTest"`
Attendu : ÉCHEC de compilation — `cannot find symbol: method type()`, `class DocumentType`.

- [ ] **Étape 3 : créer `DocumentType`**

```java
package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

/**
 * Typologie d'un document : <strong>ce qui décide de la façon dont il se découpe</strong>.
 *
 * <p>À ne pas confondre avec {@link DocumentFormat}, qui dit comment ses octets sont
 * encodés. Un {@code .pdf}, un {@code .docx}, un {@code .md} et un {@code .txt} sont quatre
 * formats et une seule typologie : tous se ramènent à une suite de blocs titrés
 * (ADR-0024). Un enregistrement sonore, lui, se découperait en segments datés, une image en
 * régions — d'autres typologies, d'autres tables, d'autres écrans.
 *
 * <p>Une seule constante aujourd'hui, et c'est volontaire : la typologie n'a rien à
 * distinguer pour l'instant. Elle existe pour que le code qui exige un extracteur de texte
 * de <em>tout</em> format cesse de le faire — voir
 * {@code ExtractDocumentTextHandler.indexeParFormat} — et pour que la lecture d'un document
 * sache quelle projection lui appliquer.
 *
 * <p>{@code TEXTUAL} et non {@code TEXT} : {@link DocumentFormat#TEXT} désigne déjà le
 * {@code .txt}, et deux constantes homonymes qui ne veulent pas dire la même chose se
 * payent à chaque relecture.
 *
 * <p>La typologie <strong>ne se stocke pas</strong> : elle se déduit du format — ADR-0029.
 */
public enum DocumentType {
    TEXTUAL
}
```

- [ ] **Étape 4 : porter la typologie sur `DocumentFormat`**

Remplacer le bloc des constantes, du champ et du constructeur, et ajouter les deux
méthodes. Le fichier devient :

```java
package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import xyz.sterenn.secondbrain.knowledge.domain.exception.UnsupportedDocumentFormatException;

/**
 * Formats de document que la base de connaissance sait accueillir.
 *
 * <p>La liste est fermée et vit ici, pas dans un contrôleur : décider ce qui peut entrer
 * dans la base de connaissance est une règle métier. Un format s'ajoute en ajoutant une
 * constante, et le message de refus se met à jour tout seul — il est construit à partir de
 * l'énumération, jamais recopié à la main.
 *
 * <p>Le format se déduit de l'extension, faute de mieux : le type MIME annoncé par le
 * navigateur est déclaratif, donc pas plus fiable, et renifler le contenu supposerait de
 * savoir déjà le lire. L'extraction, elle, vérifiera qu'un {@code .pdf} en est un.
 *
 * <p><strong>Chaque format déclare sa typologie</strong> ({@link DocumentType}) : c'est
 * elle qui dit comment le document se découpe, donc quel traitement l'attend et quelles
 * tables le reçoivent. Ajouter une constante sans la nommer ne compile pas — le
 * constructeur l'exige.
 */
public enum DocumentFormat {
    PDF(".pdf", DocumentType.TEXTUAL),
    MARKDOWN(".md", DocumentType.TEXTUAL),
    TEXT(".txt", DocumentType.TEXTUAL),
    DOCX(".docx", DocumentType.TEXTUAL);

    private final String extension;
    private final DocumentType type;

    DocumentFormat(String extension, DocumentType type) {
        this.extension = extension;
        this.type = type;
    }

    public String extension() {
        return extension;
    }

    /** La typologie de ce format : comment un document de ce format se découpe. */
    public DocumentType type() {
        return type;
    }

    /**
     * Les formats d'une typologie, dans l'ordre de déclaration.
     *
     * <p>C'est cette méthode qui permet à un traitement de ne réclamer que ce qui le
     * concerne : l'extraction de texte exige un extracteur pour chaque format
     * {@link DocumentType#TEXTUAL}, et n'a rien à dire des autres.
     */
    public static List<DocumentFormat> of(DocumentType type) {
        return Arrays.stream(values()).filter(format -> format.type == type).toList();
    }

    /**
     * Reconnaît le format d'un fichier à son extension, insensible à la casse.
     *
     * @throws UnsupportedDocumentFormatException si l'extension n'est pas reconnue — son
     *         message énonce les formats acceptés, et il est affichable tel quel
     */
    public static DocumentFormat fromFilename(String filename) {
        String normalise = filename == null ? "" : filename.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(format -> normalise.endsWith(format.extension))
                .findFirst()
                .orElseThrow(() -> new UnsupportedDocumentFormatException(acceptedExtensions()));
    }

    /** Les extensions acceptées, dans l'ordre de déclaration, pour un message lisible. */
    public static String acceptedExtensions() {
        return Arrays.stream(values()).map(DocumentFormat::extension).collect(Collectors.joining(", "));
    }
}
```

- [ ] **Étape 5 : constater le succès**

Run : `gtest test --tests "xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormatTest"`
Attendu : SUCCÈS.

- [ ] **Étape 6 : écrire ADR-0029**

`docs/decisions/0029-la-typologie-d-un-document-se-deduit-de-son-format.md`, à partir du
gabarit. Contenu attendu :

- **Titre** : « La typologie d'un document se déduit de son format, elle n'est pas stockée »
- **Contexte** : la base accueille quatre formats qui se découpent tous de la même façon ;
  d'autres viendront qui ne s'y ramènent pas. Il faut nommer cette différence quelque part.
  Reste à savoir si `knowledge_documents` porte une colonne `type`.
- **Facteurs** : une valeur dérivée et stockée peut diverger de sa source ; une migration
  de plus pour une valeur qu'on sait déjà calculer ; le besoin de filtrer par typologie
  n'existe pas encore ; `format` est déjà indexé par la lecture par propriétaire.
- **Options** : (a) colonne `type` sur `knowledge_documents`, écrite au dépôt ;
  (b) typologie dérivée de `DocumentFormat` ; (c) rien du tout, un `switch` sur le format
  là où le besoin se pose.
- **Décision** : (b), parce que le format **détermine** la typologie — un `.docx` ne peut
  pas cesser d'être textuel — et qu'une colonne ne ferait que rendre ce lien falsifiable.
- **Conséquences** — Bien : aucune migration, aucune donnée à rattraper, un seul endroit
  qui déclare le lien (le constructeur de `DocumentFormat`). Mal : « tous mes documents
  sonores » se lira par un `IN (…)` sur `format` construit depuis
  `DocumentFormat.of(…)`, pas par un prédicat direct ; et une requête SQL écrite à la main
  hors du code Java devra recopier la liste.
- **Condition de réouverture** : le jour où une lecture doit filtrer par typologie sur un
  volume qui rend l'`IN (…)` coûteux, ou le jour où deux documents du même format doivent
  pouvoir porter deux typologies — ce qui voudrait dire que le format n'est plus ce qui la
  détermine.
- **Vérification** : `DocumentFormatTest.chaque_format_annonce_sa_typologie` empêche
  d'ajouter un format sans typologie ; rien ne vérifie l'absence de colonne, sinon
  `ddl-auto: validate` qui refuserait un attribut sans colonne.

- [ ] **Étape 7 : indexer l'ADR et l'arborescence dans `CLAUDE.md`**

Deux endroits :

1. Table « Décisions d'architecture », après la ligne 0028 :
```markdown
| [0029](docs/decisions/0029-la-typologie-d-un-document-se-deduit-de-son-format.md) | La typologie d'un document se déduit de son format, elle n'est pas stockée |
```
2. Arborescence du contexte `knowledge`, ligne `valueobject/` : ajouter `DocumentType` à
   l'énumération des value objects, en le distinguant de `DocumentFormat` :
```
│   │   ├── valueobject/     Checksum (SHA-256), DocumentFormat, DocumentType (comment un
│   │   │                    document se découpe), DocumentStatus,
│   │   │                    TextBlock + ExtractedText (le format du texte extrait)
```

- [ ] **Étape 8 : formater, lancer la suite, committer**

```bash
make format-back
gtest test
git add src/main/java/xyz/sterenn/secondbrain/knowledge/domain/valueobject/ \
        src/test/java/xyz/sterenn/secondbrain/knowledge/domain/valueobject/DocumentFormatTest.java \
        docs/decisions/0029-la-typologie-d-un-document-se-deduit-de-son-format.md CLAUDE.md
git commit -m "feat: un format déclare sa typologie, qui dit comment le document se découpe"
```

---

## Tâche 2 : L'extraction n'exige un extracteur que des formats textuels

**Fichiers :**
- Modifier : `src/main/java/xyz/sterenn/secondbrain/knowledge/application/command/ExtractDocumentTextHandler.java`
- Créer : `src/test/java/xyz/sterenn/secondbrain/knowledge/application/command/ExtractorCoverageTest.java`

**Interfaces :**
- Consomme : `DocumentFormat.of(DocumentType.TEXTUAL)` (tâche 1).
- Produit : `indexeParFormat` passe de `private static` à **package-private `static`**, pour
  être exercée sans démarrer Spring. Signature inchangée :
  `static Map<DocumentFormat, DocumentTextExtractor> indexeParFormat(List<DocumentTextExtractor>)`.

**Ce qui change et pourquoi.** Le contrôle de démarrage actuel boucle sur
`DocumentFormat.values()` : *tout* format accepté au dépôt doit avoir un extracteur de
texte. C'était juste tant que tous les formats étaient textuels. Le jour où un format
sonore entre, ce contrôle réclamerait pour lui un extracteur de texte qui n'a aucun sens et
empêcherait l'application de démarrer. Il boucle désormais sur les formats de typologie
`TEXTUAL`, et refuse en plus un extracteur qui revendiquerait un format d'une autre
typologie — une erreur de branchement se voit au démarrage, pas en production.

- [ ] **Étape 1 : écrire le test qui échoue**

Nouveau fichier `src/test/java/xyz/sterenn/secondbrain/knowledge/application/command/ExtractorCoverageTest.java` :

```java
package xyz.sterenn.secondbrain.knowledge.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.List;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentTextExtractor;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentType;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ExtractedText;
import org.junit.jupiter.api.Test;

/**
 * Le contrôle de couverture des extracteurs, exercé sans Spring.
 *
 * <p>Il vit dans {@code ExtractDocumentTextHandler} et s'exécute à la construction du
 * bean : en production, un trou fait échouer le démarrage. Ici, on l'appelle directement —
 * c'est la seule façon de vérifier ce qu'il refuse sans casser le contexte de toute la
 * suite.
 */
class ExtractorCoverageTest {

    /** Extracteur d'essai : il ne sait rien lire, seul le format qu'il revendique compte. */
    private record ExtracteurFactice(DocumentFormat format) implements DocumentTextExtractor {
        @Override
        public ExtractedText extract(byte[] content) {
            throw new UnsupportedOperationException("Cet extracteur n'est là que pour son format");
        }
    }

    private static List<DocumentTextExtractor> couvreLaTypologieTextuelle() {
        return DocumentFormat.of(DocumentType.TEXTUAL).stream()
                .map(format -> (DocumentTextExtractor) new ExtracteurFactice(format))
                .toList();
    }

    @Test
    void accepte_un_extracteur_par_format_textuel() {
        assertThat(ExtractDocumentTextHandler.indexeParFormat(couvreLaTypologieTextuelle()))
                .containsOnlyKeys(DocumentFormat.of(DocumentType.TEXTUAL).toArray(DocumentFormat[]::new));
    }

    @Test
    void refuse_un_format_textuel_sans_extracteur() {
        List<DocumentTextExtractor> incomplet = couvreLaTypologieTextuelle().stream()
                .filter(extracteur -> extracteur.format() != DocumentFormat.DOCX)
                .toList();

        assertThatIllegalStateException()
                .isThrownBy(() -> ExtractDocumentTextHandler.indexeParFormat(incomplet))
                .withMessageContaining("DOCX");
    }

    @Test
    void refuse_deux_extracteurs_pour_le_meme_format() {
        List<DocumentTextExtractor> doublon = new java.util.ArrayList<>(couvreLaTypologieTextuelle());
        doublon.add(new ExtracteurFactice(DocumentFormat.PDF));

        assertThatIllegalStateException()
                .isThrownBy(() -> ExtractDocumentTextHandler.indexeParFormat(doublon))
                .withMessageContaining("PDF");
    }

    @Test
    void refuse_un_extracteur_de_texte_qui_revendique_un_format_d_une_autre_typologie() {
        // Aucun format non textuel n'existe encore : ce que ce test fige, c'est que le
        // contrôle interroge la typologie du format revendiqué, et non la seule
        // appartenance à `DocumentFormat.values()`. Il deviendra un vrai cas de refus le
        // jour où la deuxième typologie arrivera ; en attendant il vérifie que la liste
        // complète des formats textuels est exactement ce que le handler accepte.
        assertThat(DocumentFormat.of(DocumentType.TEXTUAL)).containsExactlyElementsOf(List.of(DocumentFormat.values()));
    }
}
```

- [ ] **Étape 2 : constater l'échec**

Run : `gtest test --tests "xyz.sterenn.secondbrain.knowledge.application.command.ExtractorCoverageTest"`
Attendu : ÉCHEC de compilation — `indexeParFormat(...) has private access in ExtractDocumentTextHandler`.

- [ ] **Étape 3 : ouvrir la méthode et restreindre le contrôle à la typologie textuelle**

Dans `ExtractDocumentTextHandler`, remplacer la Javadoc et le corps de `indexeParFormat` :

```java
    /**
     * Indexe les extracteurs, et <strong>fait échouer le démarrage</strong> si un format
     * <em>textuel</em> accepté au dépôt n'a pas le sien.
     *
     * <p>C'est la contrepartie du choix d'un extracteur par format (ADR-0026) : ajouter une
     * constante à {@link DocumentFormat} sans écrire son adapter serait, sinon, un document
     * accepté puis irrémédiablement en échec. Même dispositif que la table de routage des
     * bus : le défaut se voit au démarrage, pas en production.
     *
     * <p><strong>La boucle porte sur {@link DocumentType#TEXTUAL}, pas sur tous les
     * formats.</strong> Un format d'une autre typologie — un enregistrement sonore, une
     * image — ne se découpe pas en blocs titrés : lui réclamer un extracteur de texte
     * empêcherait l'application de démarrer pour un besoin qui n'existe pas. Symétriquement,
     * un extracteur de texte qui revendiquerait un format non textuel est un branchement
     * faux, et il est refusé ici plutôt qu'au premier document traité.
     *
     * <p>Package-private plutôt que privée : c'est un contrôle, et un contrôle se teste.
     * Voir {@code ExtractorCoverageTest}.
     */
    static Map<DocumentFormat, DocumentTextExtractor> indexeParFormat(
            List<DocumentTextExtractor> documentTextExtractors) {
        Map<DocumentFormat, DocumentTextExtractor> parFormat = new EnumMap<>(DocumentFormat.class);
        for (DocumentTextExtractor extracteur : documentTextExtractors) {
            if (extracteur.format().type() != DocumentType.TEXTUAL) {
                throw new IllegalStateException("L'extracteur " + extracteur.getClass().getName()
                        + " revendique le format " + extracteur.format() + ", qui n'est pas de typologie textuelle");
            }
            DocumentTextExtractor precedent = parFormat.put(extracteur.format(), extracteur);
            if (precedent != null) {
                throw new IllegalStateException("Deux extracteurs revendiquent le format " + extracteur.format() + " : "
                        + precedent.getClass().getName() + " et "
                        + extracteur.getClass().getName());
            }
        }
        for (DocumentFormat format : DocumentFormat.of(DocumentType.TEXTUAL)) {
            if (!parFormat.containsKey(format)) {
                throw new IllegalStateException(
                        "Aucun extracteur ne sait lire " + format + " : un format accepté au dépôt doit être lisible");
            }
        }
        return Map.copyOf(parFormat);
    }
```

Ajouter l'import `xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentType`.

- [ ] **Étape 4 : constater le succès**

Run : `gtest test --tests "xyz.sterenn.secondbrain.knowledge.application.command.ExtractorCoverageTest"`
Attendu : SUCCÈS (4 tests).

- [ ] **Étape 5 : formater, lancer la suite, committer**

```bash
make format-back
gtest test
git add src/main/java/xyz/sterenn/secondbrain/knowledge/application/command/ExtractDocumentTextHandler.java \
        src/test/java/xyz/sterenn/secondbrain/knowledge/application/command/ExtractorCoverageTest.java
git commit -m "feat: l'extraction de texte n'exige un extracteur que des formats textuels"
```

---

## Tâche 3 : Les tables du texte extrait portent leur typologie

**Fichiers :**
- Créer : `src/main/resources/db/migration/V8__rename_document_texts_to_text_extractions.sql`
- Renommer : `knowledge/domain/entity/DocumentText.java` → `TextExtraction.java`
- Renommer : `knowledge/domain/port/DocumentTextRepository.java` → `TextExtractionRepository.java`
- Renommer : `knowledge/infrastructure/persistence/JpaDocumentTextRepositoryAdapter.java` → `JpaTextExtractionRepositoryAdapter.java`
- Renommer : `knowledge/infrastructure/persistence/SpringDataDocumentTextRepository.java` → `SpringDataTextExtractionRepository.java`
- Modifier : `knowledge/application/command/ExtractDocumentTextHandler.java`
- Modifier : `knowledge/application/command/DeleteDocumentHandler.java` (Javadoc périmée)
- Renommer : `src/test/…/infrastructure/persistence/JpaDocumentTextRepositoryAdapterTest.java` → `JpaTextExtractionRepositoryAdapterTest.java`
- Modifier : `src/test/…/application/command/ExtractDocumentTextTest.java`, `DeleteDocumentCascadeTest.java`, `src/test/…/infrastructure/messaging/KnowledgeEventListenerTest.java` (toute référence au type/port renommé)
- Créer : `docs/decisions/0030-chaque-typologie-a-ses-propres-tables-d-extraction.md`
- Modifier : `CLAUDE.md` (index des ADR, arborescence, sections « Persistance » et « Le flux de l'extraction du texte »)

**Interfaces :**
- Consomme : `DocumentType` (tâche 1).
- Produit : `TextExtraction.of(UUID documentId, ExtractedText text, Instant extractedAt)`,
  `TextExtraction.text()`, `getId()`, `getDocumentId()`, `getBlocks()`, `getExtractedAt()` —
  **API identique**, seul le nom change. Port `TextExtractionRepository` :
  `TextExtraction save(TextExtraction)`, `Optional<TextExtraction> findByDocumentId(UUID)`,
  `void deleteByDocumentId(UUID)`.

**Ce que le renommage achète.** `knowledge_document_texts` et `knowledge_document_blocks`
supposent qu'un document produit du texte en blocs. C'est vrai des quatre formats
d'aujourd'hui et faux du prochain. Nommées par leur typologie, ces tables laissent la place
à `knowledge_audio_segments` ou `knowledge_image_regions` sans que le nom de l'une mente sur
le contenu de l'autre. `extraction` plutôt que `document` dans le nom parce que la ligne
n'est pas un document : c'est le produit d'un traitement, qui naît plus tard et se remplace
en entier.

**Une migration nouvelle, pas une réécriture de `V7`.** `V7` est appliquée — au minimum sur
la base de développement — et une migration appliquée ne se modifie jamais : Flyway
comparerait les empreintes et refuserait de démarrer. `V8` renomme.

- [ ] **Étape 1 : écrire la migration**

`src/main/resources/db/migration/V8__rename_document_texts_to_text_extractions.sql` :

```sql
-- Les deux tables du texte extrait portent désormais leur TYPOLOGIE, pas le mot « document ».
--
-- `knowledge_document_texts` supposait qu'un document produit du texte en blocs titrés.
-- C'est vrai des quatre formats acceptés — tous de typologie TEXTUAL — et faux du prochain :
-- un enregistrement sonore se découpe en segments datés, une image en régions. Nommées par
-- leur typologie, ces tables laissent la place aux suivantes sans qu'aucune ne mente sur ce
-- qu'elle contient — ADR-0030.
--
-- « extraction » et non « document » : la ligne n'est pas un document, c'est le produit d'un
-- traitement. Elle naît plus tard que lui et se remplace en entier à chaque réextraction.
--
-- RENAME et non DROP + CREATE : les données déjà extraites survivent, et les deux
-- ON DELETE CASCADE de V7 sont conservés tels quels — PostgreSQL renomme la contrainte,
-- pas son comportement.

ALTER TABLE knowledge_document_texts RENAME TO knowledge_text_extractions;
ALTER TABLE knowledge_document_blocks RENAME TO knowledge_text_blocks;

ALTER TABLE knowledge_text_blocks RENAME COLUMN document_text_id TO text_extraction_id;

-- Les contraintes gardent sinon le nom de l'ancienne table, et un message d'erreur de
-- production désignerait un objet qui n'existe plus. `RENAME CONSTRAINT` renomme aussi
-- l'index qui porte une clé primaire ou une contrainte d'unicité.
ALTER TABLE knowledge_text_extractions
    RENAME CONSTRAINT pk_knowledge_document_texts TO pk_knowledge_text_extractions;
ALTER TABLE knowledge_text_extractions
    RENAME CONSTRAINT uq_knowledge_document_texts_document TO uq_knowledge_text_extractions_document;
ALTER TABLE knowledge_text_extractions
    RENAME CONSTRAINT fk_knowledge_document_texts_document TO fk_knowledge_text_extractions_document;

ALTER TABLE knowledge_text_blocks
    RENAME CONSTRAINT pk_knowledge_document_blocks TO pk_knowledge_text_blocks;
ALTER TABLE knowledge_text_blocks
    RENAME CONSTRAINT fk_knowledge_document_blocks_text TO fk_knowledge_text_blocks_extraction;
```

- [ ] **Étape 2 : constater l'échec**

Run : `gtest test --tests "xyz.sterenn.secondbrain.knowledge.infrastructure.persistence.JpaDocumentTextRepositoryAdapterTest"`
Attendu : ÉCHEC au démarrage du contexte —
`Schema-validation: missing table [knowledge_document_texts]`. La migration a renommé, les
annotations JPA désignent encore l'ancien nom. C'est `ddl-auto: validate` qui parle : le
test prouve que la migration s'est appliquée.

- [ ] **Étape 3 : renommer l'entité**

```bash
git mv src/main/java/xyz/sterenn/secondbrain/knowledge/domain/entity/DocumentText.java \
       src/main/java/xyz/sterenn/secondbrain/knowledge/domain/entity/TextExtraction.java
```

Dans le fichier : `DocumentText` → `TextExtraction` partout (classe, constructeurs,
fabrique, type de retour). Puis les trois annotations et la Javadoc de tête :

```java
/**
 * Le texte extrait d'un document, dans la forme commune aux quatre formats acceptés.
 *
 * <p><strong>Agrégat distinct de {@link Document}</strong>, et non des colonnes de plus sur
 * lui : il naît plus tard, et il est remplacé en entier à chaque réextraction. Les deux se
 * référencent donc par identifiant, jamais par {@code @ManyToOne} — ADR-0006.
 *
 * <p><strong>C'est l'extraction de la typologie textuelle</strong>, et son nom le dit :
 * {@code knowledge_text_extractions}, pas {@code knowledge_document_texts}. Une autre
 * typologie aura ses propres tables et son propre agrégat — ADR-0030.
 *
 * <p>Les blocs sont une {@code @ElementCollection} et non des entités : un bloc n'a pas
 * d'identité propre, il n'existe que par le texte qui le contient, et rien ne le désigne de
 * l'extérieur. Sa position est portée par {@code @OrderColumn} plutôt que par un champ de
 * {@link TextBlock} : elle appartient à la liste, pas au bloc — un bloc extrait de son
 * document reste le même bloc.
 *
 * <p>{@code EAGER}, à contre-courant de l'habitude : {@code open-in-view} est à {@code false}
 * et personne ne charge une {@code TextExtraction} sans vouloir ses blocs. Une collection
 * paresseuse ne ferait que déplacer l'échec hors de la transaction du bus.
 */
@Entity
@Table(name = "knowledge_text_extractions")
public class TextExtraction {
```

et, sur la collection :

```java
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "knowledge_text_blocks",
            joinColumns = @JoinColumn(name = "text_extraction_id", nullable = false))
    @OrderColumn(name = "block_position")
    private List<TextBlock> blocks = new ArrayList<>();
```

Les messages de `Objects.requireNonNull` restent inchangés.

- [ ] **Étape 4 : renommer le port et ses deux adapters**

```bash
git mv src/main/java/xyz/sterenn/secondbrain/knowledge/domain/port/DocumentTextRepository.java \
       src/main/java/xyz/sterenn/secondbrain/knowledge/domain/port/TextExtractionRepository.java
git mv src/main/java/xyz/sterenn/secondbrain/knowledge/infrastructure/persistence/JpaDocumentTextRepositoryAdapter.java \
       src/main/java/xyz/sterenn/secondbrain/knowledge/infrastructure/persistence/JpaTextExtractionRepositoryAdapter.java
git mv src/main/java/xyz/sterenn/secondbrain/knowledge/infrastructure/persistence/SpringDataDocumentTextRepository.java \
       src/main/java/xyz/sterenn/secondbrain/knowledge/infrastructure/persistence/SpringDataTextExtractionRepository.java
```

Dans les trois fichiers, renommer type et références. Le port devient :

```java
/**
 * Ce que le domaine attend du stockage de l'extraction textuelle d'un document.
 *
 * <p>Aucune méthode ne porte le propriétaire : le cloisonnement se fait en amont, sur le
 * document, par {@code findByIdAndOwnerId}. Une extraction se lit toujours après lui.
 */
public interface TextExtractionRepository {

    TextExtraction save(TextExtraction textExtraction);

    Optional<TextExtraction> findByDocumentId(UUID documentId);

    void deleteByDocumentId(UUID documentId);
}
```

Conserver intégralement les commentaires existants des adapters — en particulier celui qui
explique le `flush()` après `deleteByDocumentId` (sans lui, Hibernate ordonnerait l'insert
avant le delete et la contrainte `UNIQUE` se refermerait). Renommer aussi le champ selon la
règle de nommage : `textExtractionRepository`, `springDataTextExtractionRepository`.

- [ ] **Étape 5 : mettre à jour les appelants**

Dans `ExtractDocumentTextHandler` : le type `DocumentTextRepository` devient
`TextExtractionRepository`, le champ et le paramètre `documentTextRepository` deviennent
`textExtractionRepository`, et `DocumentText.of(...)` devient `TextExtraction.of(...)`.
Ajuster les imports.

Dans `DeleteDocumentHandler`, la Javadoc annonce encore que la table n'existe pas. La
remplacer par le constat :

```java
    /**
     * Efface la ligne, puis l'original sur disque.
     *
     * <p>L'extraction n'est pas mentionnée ici et ne le sera pas : le
     * {@code ON DELETE CASCADE} de {@code knowledge_text_extractions} vers
     * {@code knowledge_documents} l'emporte avec le document, et celui de
     * {@code knowledge_text_blocks} vers l'extraction emporte les blocs. Cette méthode n'a
     * pas eu à changer quand la table est arrivée, et n'aura pas à changer quand une
     * deuxième typologie ajoutera les siennes — à condition qu'elles cascadent aussi
     * (ADR-0030).
     *
     * <p>La ligne d'abord, le fichier ensuite : un système de fichiers ne participe à aucune
     * transaction (ADR-0020).
     */
```

- [ ] **Étape 6 : renommer et ajuster les tests**

```bash
git mv src/test/java/xyz/sterenn/secondbrain/knowledge/infrastructure/persistence/JpaDocumentTextRepositoryAdapterTest.java \
       src/test/java/xyz/sterenn/secondbrain/knowledge/infrastructure/persistence/JpaTextExtractionRepositoryAdapterTest.java
```

Puis, dans ce fichier et dans `ExtractDocumentTextTest`, `DeleteDocumentCascadeTest` et
`KnowledgeEventListenerTest` : `DocumentTextRepository` → `TextExtractionRepository`,
`documentTextRepository` → `textExtractionRepository`, `DocumentText` →
`TextExtraction`. Vérifier en plus que `DeleteDocumentCascadeTest` et
`KnowledgeEventListenerTest` ne comptent pas de lignes en SQL sur les anciens noms de
tables :

```bash
grep -rn "knowledge_document_texts\|knowledge_document_blocks\|document_text_id" src/ docs/ CLAUDE.md
```
Attendu après correction : seules les occurrences de `V7__create_knowledge_document_texts.sql`
et de `V8__…` (qui les renomme) subsistent. **Ne pas toucher à `V7`.**

- [ ] **Étape 7 : constater le succès**

```bash
gtest test
```
Attendu : SUCCÈS de toute la suite. En particulier
`JpaTextExtractionRepositoryAdapterTest` (le port écrit et relit sur les nouvelles tables)
et `DeleteDocumentCascadeTest` (les cascades renommées fonctionnent toujours).

- [ ] **Étape 8 : écrire ADR-0030**

`docs/decisions/0030-chaque-typologie-a-ses-propres-tables-d-extraction.md` :

- **Titre** : « Chaque typologie de document a ses propres tables d'extraction »
- **Contexte** : le texte extrait vit dans deux tables nommées d'après le document. D'autres
  typologies produiront d'autres découpages — segments datés, régions — qui ne rentrent ni
  dans `knowledge_document_blocks` ni dans sa forme.
- **Facteurs** : `ddl-auto: validate` interdit un schéma flou ; ADR-0024 a déjà refusé le
  JSONB au motif que la base doit savoir lire ce qu'elle stocke ; RAG-5 devra référencer un
  bloc par son identité ; un `ON DELETE CASCADE` par typologie coûte une ligne de migration.
- **Options** : (a) une table d'extraction générique avec une colonne `payload` JSONB ;
  (b) une table par typologie, nommée par elle ; (c) une table unique avec des colonnes
  nullables couvrant toutes les typologies.
- **Décision** : (b), parce que c'est la seule qui garde à la base la connaissance de ce
  qu'elle contient, et parce qu'elle est la conséquence directe d'ADR-0024.
- **Conséquences** — Bien : chaque typologie a le schéma exact de son découpage, contraintes
  comprises ; le nom d'une table dit ce qu'elle contient ; rien à migrer quand une typologie
  s'ajoute. Mal : une lecture « toutes les extractions d'un document, quelle que soit sa
  typologie » devra faire une union ou interroger la typologie d'abord — c'est ce que fait
  `FindDocumentHandler` ; et chaque typologie paie sa migration et son adapter.
- **Condition de réouverture** : le jour où une lecture transverse aux typologies devient
  courante, ou le jour où une typologie n'a pas de forme stable — auquel cas c'est ADR-0024
  qu'il faudrait rouvrir d'abord.
- **Vérification** : `ddl-auto: validate` au démarrage, et
  `DeleteDocumentCascadeTest` pour les cascades.

- [ ] **Étape 9 : mettre `CLAUDE.md` à jour**

Quatre endroits :

1. Table des ADR :
```markdown
| [0030](docs/decisions/0030-chaque-typologie-a-ses-propres-tables-d-extraction.md) | Chaque typologie de document a ses propres tables d'extraction |
```
2. Arborescence : `entity/ Document, DocumentText (le texte extrait, agrégat à part)` devient
   `entity/ Document, TextExtraction (le texte extrait, agrégat à part)`, et
   `port/ …, DocumentTextRepository, …` devient `…, TextExtractionRepository, …`.
3. Section « Le flux de l'extraction du texte », dernière phrase du deuxième paragraphe :
   remplacer « Il vit dans deux tables cascadées, `knowledge_document_texts` et
   `knowledge_document_blocks`. » par « Il vit dans deux tables cascadées,
   `knowledge_text_extractions` et `knowledge_text_blocks`, **nommées par la typologie et
   non par le document** : une autre typologie aura les siennes (ADR-0030). »
4. Section « Persistance », paragraphe sur les deux tables : y reporter les nouveaux noms et
   `text_extraction_id`.

- [ ] **Étape 10 : formater et committer**

```bash
make format-back
gtest test
git add -A
git commit -m "refactor: les tables du texte extrait portent leur typologie, pas le mot document"
```

---

## Tâche 4 : Lire un document et ce qui en a été extrait

**Fichiers :**
- Créer : `knowledge/application/query/FindDocument.java`
- Créer : `knowledge/application/query/DocumentDetailView.java`
- Créer : `knowledge/application/query/TextExtractionView.java`
- Créer : `knowledge/application/query/FindDocumentHandler.java`
- Créer : `knowledge/infrastructure/web/FindDocumentController.java`
- Modifier : `knowledge/domain/exception/DocumentNotFoundException.java`
- Test : `src/test/java/xyz/sterenn/secondbrain/knowledge/infrastructure/web/FindDocumentControllerTest.java`
- Modifier : `CLAUDE.md` (section « Le flux du dépôt d'un document » — la nouvelle route)

**Interfaces :**
- Consomme : `TextExtractionRepository.findByDocumentId` (tâche 3), `DocumentFormat.type()`
  (tâche 1), `DocumentRepository.findByIdAndOwnerId`, `ExtractedText.characterCount()`.
- Produit : `GET /api/documents/{id}` → `200` `DocumentDetailView` ou `404` `ErrorResponse` ;
  `DocumentNotFoundException.MESSAGE` ;
  `record TextExtractionView(Instant extractedAt, int characterCount, List<TextBlockView> blocks)`
  avec `record TextBlockView(String heading, int headingLevel, String text)`.

**Décisions portées par cette tâche.**

- `GET /api/documents/{id}` et non `/api/documents/{id}/extraction` : l'écran a besoin du
  nom, du statut et du motif d'échec autant que des blocs, et un document en attente doit
  répondre `200` « pas encore » plutôt que `404` « rien ici ». Une seule requête, un seul
  écran.
- La query rend un `Optional` vide, elle ne lève pas : c'est la règle
  (`.claude/rules/backend.md`, « Bus, commandes et queries »). Le `404` et son message sont
  posés par le contrôleur, qui est déjà l'endroit où `DeleteDocumentController` traduit le
  même refus. Pour que les deux disent exactement la même chose, le message devient une
  constante du domaine.
- **`DocumentView` (la liste) ne bouge pas.** Elle n'a pas besoin de la typologie : c'est le
  détail qui choisit un rendu. Une projection grandit au rythme de ses écrans.
- `FindDocumentHandler` est **le point de branchement de la deuxième typologie** : c'est là
  qu'un `switch` sur `document.getFormat().type()` choisira la projection.

- [ ] **Étape 1 : écrire le test qui échoue**

`src/test/java/xyz/sterenn/secondbrain/knowledge/infrastructure/web/FindDocumentControllerTest.java` :

```java
package xyz.sterenn.secondbrain.knowledge.infrastructure.web;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.knowledge.Fixtures;
import xyz.sterenn.secondbrain.knowledge.KnowledgeFixture;
import xyz.sterenn.secondbrain.knowledge.application.command.ExtractDocumentText;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.users.AccountFixture;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration.RecordingNotificationSender;
import xyz.sterenn.secondbrain.users.domain.port.AccessTokenIssuer;

/**
 * Le détail est le seul écran qui montre ce qui a été extrait d'un document. Il doit
 * répondre quelque chose d'utile dans les trois états : en attente, extrait, en échec — un
 * {@code 404} sur un document qui existe mais n'a pas encore été traité laisserait
 * l'utilisateur croire qu'il a disparu.
 */
@Import({TestcontainersConfiguration.class, RecordingNotificationSenderConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FindDocumentControllerTest {

    private static final String MOT_DE_PASSE = "chevalpile42";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private RecordingNotificationSender recordingNotificationSender;

    @Autowired
    private AccessTokenIssuer accessTokenIssuer;

    @Autowired
    private DocumentRepository documentRepository;

    @Value("${secondbrain.storage.originals-path}")
    private String cheminDesOriginaux;

    private UUID alice;
    private String jetonAlice;

    @BeforeEach
    void prepare_un_compte_connecte() {
        recordingNotificationSender.clear();
        alice = AccountFixture.registerVerified(
                commandBus, recordingNotificationSender, "alice@exemple.fr", MOT_DE_PASSE);
        jetonAlice = KnowledgeFixture.jeton(accessTokenIssuer, alice);
    }

    @AfterEach
    void efface_les_originaux() {
        KnowledgeFixture.videLesOriginaux(cheminDesOriginaux);
    }

    @Test
    void rend_le_document_et_le_texte_qui_en_a_ete_extrait() throws Exception {
        Document document = depose(jetonAlice, alice, "structure.md", Fixtures.STRUCTURE_MD);
        commandBus.dispatch(new ExtractDocumentText(document.getId(), alice));

        mockMvc.perform(get("/api/documents/" + document.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jetonAlice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename").value("structure.md"))
                .andExpect(jsonPath("$.format").value("MARKDOWN"))
                .andExpect(jsonPath("$.type").value("TEXTUAL"))
                .andExpect(jsonPath("$.status").value("EXTRACTED"))
                .andExpect(jsonPath("$.extraction.extractedAt").isNotEmpty())
                .andExpect(jsonPath("$.extraction.characterCount").isNumber())
                // `hasItem` et non `blocks[0]` : ce qui est vérifié est qu'un titre de la
                // fixture arrive jusqu'à l'écran, pas sa place dans la liste — c'est déjà
                // ce que fixe `ExtractDocumentTextTest`, et le préambule d'un document
                // peut légitimement occuper le premier bloc.
                .andExpect(jsonPath("$.extraction.blocks[*].heading", hasItem("Journal de bord")))
                .andExpect(jsonPath("$.extraction.blocks[0].text").isNotEmpty());
    }

    @Test
    void annonce_la_typologie_sans_extraction_tant_que_le_traitement_n_a_pas_eu_lieu() throws Exception {
        Document document = depose(jetonAlice, alice, "notes.txt", Fixtures.BRUT_TXT);

        mockMvc.perform(get("/api/documents/" + document.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jetonAlice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.type").value("TEXTUAL"))
                .andExpect(jsonPath("$.extraction").doesNotExist())
                .andExpect(jsonPath("$.errorMessage").doesNotExist());
    }

    @Test
    void rend_le_motif_d_un_document_en_echec_et_aucune_extraction() throws Exception {
        // Déposé sous un nom en .txt : ce test ne teste pas l'extraction, seulement ce que
        // le détail montre d'un document déjà marqué en échec.
        Document document = depose(jetonAlice, alice, "scan.txt", Fixtures.BRUT_TXT);
        document.markExtractionFailed("Ce document ne contient pas de texte exploitable.");
        documentRepository.save(document);

        mockMvc.perform(get("/api/documents/" + document.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jetonAlice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorMessage").value("Ce document ne contient pas de texte exploitable."))
                .andExpect(jsonPath("$.extraction").doesNotExist());
    }

    @Test
    void rend_introuvable_un_identifiant_inconnu() throws Exception {
        mockMvc.perform(get("/api/documents/" + UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jetonAlice))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void rend_introuvable_le_document_d_un_autre_compte() throws Exception {
        UUID bob = AccountFixture.registerVerified(
                commandBus, recordingNotificationSender, "bob@exemple.fr", MOT_DE_PASSE);
        Document chezBob =
                depose(KnowledgeFixture.jeton(accessTokenIssuer, bob), bob, "chez-bob.txt", Fixtures.BRUT_TXT);

        // Un 403 confirmerait que cet identifiant existe : introuvable est le seul refus juste.
        mockMvc.perform(get("/api/documents/" + chezBob.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jetonAlice))
                .andExpect(status().isNotFound());
    }

    @Test
    void refuse_la_lecture_sans_jeton() throws Exception {
        mockMvc.perform(get("/api/documents/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    /**
     * Dépose une fixture par la route réelle et rend le document créé.
     *
     * <p>Le propriétaire est un paramètre parce que la route ne le rend pas : le {@code 201}
     * est sans corps, et il faut bien relire quelque part le document qu'on vient de créer.
     * Le nom de dépôt et le nom de fixture sont deux arguments distincts à dessein — c'est
     * l'extension du premier qui décide du format.
     */
    private Document depose(String jeton, UUID proprietaire, String nom, String fixture) throws Exception {
        mockMvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile("file", nom, "application/octet-stream", Fixtures.lire(fixture)))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jeton))
                .andExpect(status().isCreated());
        return documentRepository.findAllByOwnerId(proprietaire).stream()
                .filter(document -> document.getFilename().equals(nom))
                .findFirst()
                .orElseThrow();
    }
}
```

- [ ] **Étape 2 : constater l'échec**

Run : `gtest test --tests "xyz.sterenn.secondbrain.knowledge.infrastructure.web.FindDocumentControllerTest"`
Attendu : ÉCHEC de compilation (`FindDocument` n'existe pas). Après création des types, les
tests échoueront en `404` sur une route non mappée.

- [ ] **Étape 3 : donner un nom au refus**

Dans `DocumentNotFoundException`, extraire le message en constante pour que le contrôleur de
lecture et celui de suppression disent le même mot :

```java
public class DocumentNotFoundException extends RuntimeException {

    /**
     * Le refus, affichable tel quel. Constante parce que deux routes le rendent : la
     * suppression le traduit depuis l'exception, la lecture depuis un {@code Optional} vide
     * — une query ne lève pas. Les deux doivent dire exactement la même chose, sans quoi
     * l'utilisateur croirait à deux causes différentes.
     */
    public static final String MESSAGE = "Ce document est introuvable dans votre base de connaissance.";

    public DocumentNotFoundException() {
        super(MESSAGE);
    }
}
```

Le littéral actuel disparaît au profit de la constante — vérifier que le texte est repris
**au caractère près**, `DeleteDocumentControllerTest` l'assertant.

- [ ] **Étape 4 : écrire les projections**

`TextExtractionView.java` :

```java
package xyz.sterenn.secondbrain.knowledge.application.query;

import java.time.Instant;
import java.util.List;
import xyz.sterenn.secondbrain.knowledge.domain.entity.TextExtraction;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.TextBlock;

/**
 * Projection de lecture du texte extrait : la forme que l'écran de détail affiche.
 *
 * <p>Propre à la typologie {@code TEXTUAL} — une autre typologie aura la sienne, portée par
 * le même champ {@code extraction} de {@link DocumentDetailView} (ADR-0030).
 *
 * <p>Ni l'identifiant de l'extraction ni celui du document : le premier n'est désigné par
 * personne, le second est déjà celui de la ressource demandée.
 *
 * <p>{@code characterCount} est calculé par le domaine et compte les <em>corps</em> seuls,
 * jamais les titres : c'est la mesure qu'{@code ExtractionPolicy} utilise pour décider
 * qu'un document est inexploitable (ADR-0025), et l'écran doit montrer la même.
 */
public record TextExtractionView(Instant extractedAt, int characterCount, List<TextBlockView> blocks) {

    /** Un bloc, c'est-à-dire une section : son titre, son niveau, son corps normalisé. */
    public record TextBlockView(String heading, int headingLevel, String text) {}

    public static TextExtractionView of(TextExtraction extraction) {
        return new TextExtractionView(
                extraction.getExtractedAt(),
                extraction.text().characterCount(),
                extraction.getBlocks().stream()
                        .map(TextExtractionView::blockOf)
                        .toList());
    }

    private static TextBlockView blockOf(TextBlock block) {
        return new TextBlockView(block.getHeading(), block.getHeadingLevel(), block.getText());
    }
}
```

`TextBlock` est une `@Embeddable` mutable et non un record : ses accesseurs sont bien
`getHeading()`, `getHeadingLevel()` et `getText()`.

`DocumentDetailView.java` :

```java
package xyz.sterenn.secondbrain.knowledge.application.query;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentStatus;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentType;

/**
 * Projection de lecture d'un document et de ce qui en a été extrait — ce que l'écran de
 * détail affiche.
 *
 * <p>Plus riche que {@link DocumentView}, et c'est voulu : la liste sert à reconnaître un
 * dépôt, le détail à le lire. Format et taille apparaissent ici et pas là-bas.
 *
 * <p>{@code type} est la <strong>typologie</strong>, déduite du format (ADR-0029) : c'est
 * elle qui dit au front quel rendu appliquer à {@code extraction}. Elle voyage en code, pas
 * en libellé, comme tout ce que l'API sérialise d'une énumération.
 *
 * <p>{@code extraction} est omis quand il est nul : un document en attente ou en échec n'en
 * a pas, et un {@code null} explicite ne dirait rien de plus que son absence. Même
 * traitement pour {@code errorMessage}, pour la même raison qu'en liste.
 */
public record DocumentDetailView(
        UUID id,
        String filename,
        DocumentFormat format,
        DocumentType type,
        DocumentStatus status,
        @JsonInclude(JsonInclude.Include.NON_NULL) String errorMessage,
        long sizeBytes,
        Instant createdAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) TextExtractionView extraction) {

    /** Le document seul, sans extraction : en attente, en échec, ou typologie non lue. */
    public static DocumentDetailView of(Document document, TextExtractionView extraction) {
        return new DocumentDetailView(
                document.getId(),
                document.getFilename(),
                document.getFormat(),
                document.getFormat().type(),
                document.getStatus(),
                document.getErrorMessage(),
                document.getSizeBytes(),
                document.getCreatedAt(),
                extraction);
    }
}
```

- [ ] **Étape 5 : écrire la query et son handler**

`FindDocument.java` :

```java
package xyz.sterenn.secondbrain.knowledge.application.query;

import java.util.Optional;
import java.util.UUID;
import xyz.sterenn.secondbrain.shared.bus.Query;

/**
 * Lit un document de la base de connaissance d'un compte, avec ce qui en a été extrait.
 *
 * <p>{@code ownerId} n'est pas un filtre de confort : il cloisonne. Un document qui n'est
 * pas au demandeur est rendu introuvable, jamais interdit.
 *
 * <p>Rend un {@link Optional} vide quand il n'y a rien : une query ne lève pas. C'est le
 * contrôleur qui traduit ce vide en {@code 404}.
 */
public record FindDocument(UUID documentId, UUID ownerId) implements Query<Optional<DocumentDetailView>> {}
```

`FindDocumentHandler.java` :

```java
package xyz.sterenn.secondbrain.knowledge.application.query;

import java.util.Optional;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.TextExtractionRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentType;
import xyz.sterenn.secondbrain.shared.bus.QueryHandler;

/**
 * Aucun {@code @Transactional} ici : {@code SpringQueryBus.ask} ouvre déjà une transaction
 * en lecture seule.
 *
 * <p><strong>C'est ici que la deuxième typologie se branchera.</strong> Le document est lu
 * d'abord, sa typologie ensuite, et c'est elle qui décide quel dépôt interroger — chaque
 * typologie a les siens (ADR-0030). Aujourd'hui il n'y en a qu'une, et le {@code if}
 * ci-dessous est le point d'accroche, pas une précaution inutile : sans lui, un format
 * sonore irait chercher son texte dans la table des extractions textuelles.
 */
@Component
public class FindDocumentHandler implements QueryHandler<FindDocument, Optional<DocumentDetailView>> {

    private final DocumentRepository documentRepository;
    private final TextExtractionRepository textExtractionRepository;

    public FindDocumentHandler(
            DocumentRepository documentRepository, TextExtractionRepository textExtractionRepository) {
        this.documentRepository = documentRepository;
        this.textExtractionRepository = textExtractionRepository;
    }

    @Override
    public Optional<DocumentDetailView> handle(FindDocument query) {
        return documentRepository
                .findByIdAndOwnerId(query.documentId(), query.ownerId())
                .map(document -> DocumentDetailView.of(document, extractionDe(document)));
    }

    /** {@code null} quand il n'y a rien à montrer : en attente, en échec, ou typologie non lue. */
    private TextExtractionView extractionDe(Document document) {
        if (document.getFormat().type() != DocumentType.TEXTUAL) {
            return null;
        }
        return textExtractionRepository
                .findByDocumentId(document.getId())
                .map(TextExtractionView::of)
                .orElse(null);
    }
}
```

- [ ] **Étape 6 : écrire le contrôleur**

`FindDocumentController.java` :

```java
package xyz.sterenn.secondbrain.knowledge.infrastructure.web;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import xyz.sterenn.secondbrain.knowledge.application.query.FindDocument;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DocumentNotFoundException;
import xyz.sterenn.secondbrain.shared.bus.QueryBus;
import xyz.sterenn.secondbrain.shared.web.ErrorResponse;

/**
 * Rend un document et ce qui en a été extrait.
 *
 * <p>Une seule requête pour tout l'écran de détail : le nom, le statut, le motif d'échec et
 * les blocs. Une route {@code /extraction} séparée aurait rendu {@code 404} sur un document
 * en attente, ce qui se lit « ce document n'existe pas » alors qu'il est simplement en file.
 *
 * <p>Le vide du {@link java.util.Optional} devient {@code 404} ici, et nulle part ailleurs :
 * la query ne lève pas. Le message est celui de
 * {@link DocumentNotFoundException#MESSAGE} — le même que rend la suppression, parce que
 * c'est le même refus.
 */
@RestController
class FindDocumentController {

    private final QueryBus queryBus;

    FindDocumentController(QueryBus queryBus) {
        this.queryBus = queryBus;
    }

    @GetMapping("/api/documents/{id}")
    @SecurityRequirement(name = "bearer")
    ResponseEntity<Object> find(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return queryBus.ask(new FindDocument(id, JwtSubject.accountId(jwt)))
                .<ResponseEntity<Object>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse(DocumentNotFoundException.MESSAGE)));
    }

    @ExceptionHandler(JwtSubject.UnreadableSubjectException.class)
    ResponseEntity<Void> jetonIllisible() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}
```

> Vérifier la forme exacte du `@ExceptionHandler(JwtSubject.UnreadableSubjectException.class)`
> sur `ListDocumentsController` et la recopier, corps et type de retour compris — les trois
> contrôleurs existants doivent rester identiques sur ce point.

- [ ] **Étape 7 : constater le succès**

Run : `gtest test --tests "xyz.sterenn.secondbrain.knowledge.infrastructure.web.FindDocumentControllerTest"`
Attendu : SUCCÈS (6 tests).

> Si `rend_le_document_et_le_texte_qui_en_a_ete_extrait` échoue sur le titre attendu,
> ouvrir `src/test/resources/fixtures/structure.md` et reprendre son premier titre de
> niveau 1 tel quel — c'est la fixture qui fait foi, pas ce plan.

- [ ] **Étape 8 : documenter la route dans `CLAUDE.md`**

Dans la section « Le flux du dépôt d'un document », après le paragraphe sur
`DELETE /api/documents/{id}`, ajouter :

```markdown
`GET /api/documents/{id}` rend un document **et ce qui en a été extrait** : le nom, le
format, la typologie, le statut, le motif d'échec le cas échéant, et — quand elle existe —
l'extraction propre à sa typologie. Une seule requête pour tout l'écran de détail, et non une
route `/extraction` à part : celle-là aurait rendu `404` sur un document simplement en file
d'attente. Le cloisonnement est le même que partout (`findByIdAndOwnerId`) : le document
d'autrui est introuvable, jamais interdit. Le vide devient `404` dans le contrôleur, la query
rendant un `Optional` — une query ne lève pas.
```

- [ ] **Étape 9 : formater, lancer la suite, committer**

```bash
make format-back
gtest test
git add -A
git commit -m "feat: une route rend un document et le texte qui en a été extrait"
```

---

## Tâche 5 : L'écran qui montre ce qui a été extrait

**Fichiers :**
- Modifier : `frontend/src/api/client.js`
- Modifier : `frontend/src/api/client.spec.js`
- Créer : `frontend/src/components/DocumentStatusTag.vue`
- Créer : `frontend/src/views/DocumentDetailView.vue`
- Modifier : `frontend/src/views/DocumentsView.vue`
- Modifier : `frontend/src/views/DesignSystemView.vue`
- Modifier : `frontend/src/router/index.js`
- Modifier : `frontend/src/router/index.spec.js`
- Modifier : `CLAUDE.md` (arborescence `frontend/`, section « Le flux du dépôt d'un document »)

**Interfaces :**
- Consomme : `GET /api/documents/{id}` (tâche 4) →
  `{id, filename, format, type, status, errorMessage?, sizeBytes, createdAt, extraction?}`
  avec `extraction = {extractedAt, characterCount, blocks: [{heading, headingLevel, text}]}`.
- Produit : `fetchDocument(token, id)` dans `src/api/client.js` ; composant partagé
  `DocumentStatusTag` (prop `status`) ; route nommée `document`, chemin `/documents/:id`.

**Pourquoi un composant pour le statut.** `STATUS_LABELS` vit aujourd'hui dans
`DocumentsView`. Le détail en a besoin aussi, et le recopier serait exactement le motif que
la règle front interdit : « un motif copié d'une vue à l'autre est un composant qui n'a pas
encore été extrait ». Le composant emporte le libellé **et** la sévérité, et il paraît dans
`/design-system` dans le même commit — sans quoi il n'est pas partagé (ADR-0016).

- [ ] **Étape 1 : écrire les tests du client qui échouent**

Dans `frontend/src/api/client.spec.js`, ajouter un `describe` à l'intérieur de
`describe('base de connaissance')`, en suivant le gabarit des trois existants :

```js
  describe('lecture d’un document', () => {
    it('lit le document et son extraction avec le jeton du porteur', async () => {
      const attendu = {
        id: 'doc-1',
        filename: 'notes.md',
        type: 'TEXTUAL',
        status: 'EXTRACTED',
        extraction: { extractedAt: '2026-08-26T10:00:00Z', characterCount: 120, blocks: [] },
      }
      fetch.mockResolvedValue(reponse(200, attendu))

      const document = await fetchDocument('jeton-abc', 'doc-1')

      const [url, options] = fetch.mock.calls[0]
      expect(url).toBe('/api/documents/doc-1')
      expect(options.headers.Authorization).toBe('Bearer jeton-abc')
      expect(document).toEqual(attendu)
    })

    it('signale une session expirée sur un 401', async () => {
      fetch.mockResolvedValue(reponse(401, null))

      await expect(fetchDocument('jeton-perime', 'doc-1')).rejects.toThrow(UnauthorizedError)
    })

    it('rend le message du serveur sur un document introuvable', async () => {
      fetch.mockResolvedValue(reponse(404, { message: 'Ce document est introuvable.' }))

      await expect(fetchDocument('jeton-abc', 'doc-1')).rejects.toThrow('Ce document est introuvable.')
    })

    it('rend un message par défaut quand le corps n’est pas du JSON', async () => {
      fetch.mockResolvedValue({ ok: false, status: 502, json: () => Promise.reject(new Error('pas du JSON')) })

      await expect(fetchDocument('jeton-abc', 'doc-1')).rejects.toThrow("Ce document n'a pas pu être chargé.")
    })
  })
```

Ajouter `fetchDocument` à l'import en tête de fichier.

- [ ] **Étape 2 : écrire les tests du routeur qui échouent**

Dans `frontend/src/router/index.spec.js`, ajouter dans le `describe('page des documents')` :

```js
  it("renvoie au login le détail d'un document demandé sans jeton", async () => {
    const router = createTestRouter()

    await router.push('/documents/doc-1')

    expect(router.currentRoute.value.name).toBe('login')
  })

  it("ouvre le détail d'un document pour un porteur de jeton", async () => {
    authenticate()
    const router = createTestRouter()

    await router.push('/documents/doc-1')

    expect(router.currentRoute.value.name).toBe('document')
    expect(router.currentRoute.value.params.id).toBe('doc-1')
  })
```

- [ ] **Étape 3 : constater l'échec**

Run : `gfront npm run test:unit`
Attendu : ÉCHEC — `fetchDocument is not a function`, et la route `/documents/doc-1` résolue
en `documents` (le chemin `/documents` ne matche pas, la route inconnue non plus) plutôt
qu'en `document`.

- [ ] **Étape 4 : ajouter `fetchDocument` au client**

À la suite de `listDocuments` dans `frontend/src/api/client.js` :

```js
/**
 * Lit un document et ce qui en a été extrait. Le corps porte la typologie (`type`), qui dit
 * quelle forme a `extraction` — absent tant que rien n'a été extrait.
 */
export async function fetchDocument(token, id) {
  const response = await fetch(`/api/documents/${id}`, {
    headers: { Authorization: `Bearer ${token}` },
  })

  if (response.status === 401) {
    throw new UnauthorizedError()
  }
  if (response.ok) {
    return response.json()
  }

  // Le corps n'est pas garanti d'être du JSON (proxy en panne, 502 HTML…) : un parsing
  // qui échoue ne doit pas remplacer le message métier par une erreur de syntaxe.
  const payload = await response.json().catch(() => null)
  // Le 404 porte son message, affichable tel quel.
  throw new Error(payload?.message ?? "Ce document n'a pas pu être chargé.")
}
```

- [ ] **Étape 5 : extraire `DocumentStatusTag`**

`frontend/src/components/DocumentStatusTag.vue` :

```vue
<script setup>
import Tag from 'primevue/tag'

// Le statut voyage en code, comme tout ce que l'API sérialise d'une énumération ; le
// libellé est une affaire d'écran, et cette copie est assumée — ADR-0022. Il vit ici et
// non dans une vue parce que deux écrans l'affichent : la liste et le détail. Le motif
// d'échec, lui, vient du serveur et s'affiche tel quel — le front n'en réécrit aucun.
const LABELS = {
  PENDING: 'En attente de traitement',
  EXTRACTED: 'Texte extrait',
  FAILED: 'Traitement en échec',
}

// La sévérité est une décision de rendu, pas une donnée : « en attente » n'est ni un
// succès ni une erreur.
const SEVERITIES = {
  PENDING: 'secondary',
  EXTRACTED: 'success',
  FAILED: 'danger',
}

defineProps({
  status: { type: String, required: true },
})
</script>

<template>
  <Tag :value="LABELS[status] ?? status" :severity="SEVERITIES[status] ?? 'secondary'" />
</template>
```

- [ ] **Étape 6 : brancher la liste dessus et lui ajouter l'action « Voir »**

Dans `frontend/src/views/DocumentsView.vue` :

1. Supprimer la constante `STATUS_LABELS` et son commentaire (ils sont partis dans le
   composant), ajouter `import DocumentStatusTag from '@/components/DocumentStatusTag.vue'`.
2. Colonne « Statut » :
```vue
      <Column header="Statut">
        <template #body="{ data }">
          <DocumentStatusTag :status="data.status" />
          <div v-if="data.errorMessage" class="document-error">{{ data.errorMessage }}</div>
        </template>
      </Column>
```
3. Colonne d'actions — un bouton de plus, avant la suppression :
```vue
      <Column class="table-actions">
        <template #body="{ data }">
          <Button
            type="button"
            icon="pi pi-eye"
            text
            rounded
            :aria-label="`Voir ${data.filename}`"
            @click="router.push({ name: 'document', params: { id: data.id } })"
          />
          <Button
            type="button"
            icon="pi pi-trash"
            severity="danger"
            text
            rounded
            :disabled="busy"
            :aria-label="`Supprimer ${data.filename}`"
            @click="confirmRemoval($event, data)"
          />
        </template>
      </Column>
```
`router` est déjà en portée (`const router = useRouter()`). Le bouton n'est pas désactivé
par `busy` : consulter n'écrit rien.

- [ ] **Étape 7 : écrire l'écran de détail**

`frontend/src/views/DocumentDetailView.vue` :

```vue
<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Button from 'primevue/button'
import Message from 'primevue/message'
import ProgressSpinner from 'primevue/progressspinner'
import PageTitle from '@/components/PageTitle.vue'
import DocumentStatusTag from '@/components/DocumentStatusTag.vue'
import { fetchDocument, UnauthorizedError } from '@/api/client'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const document = ref(null)
const loading = ref(false)
const errorMessage = ref('')

// Le serveur fait autorité : un 401 déconnecte, quoi qu'en pense le navigateur. Toute
// autre panne s'affiche — y compris le 404, dont le message vient du serveur.
async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    document.value = await fetchDocument(auth.token, route.params.id)
  } catch (error) {
    if (error instanceof UnauthorizedError) {
      auth.logout()
      await router.push({ name: 'login' })
      return
    }
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
}

function formatDate(isoInstant) {
  return new Date(isoInstant).toLocaleString('fr-FR', { dateStyle: 'medium', timeStyle: 'short' })
}

function formatSize(bytes) {
  if (bytes < 1024) {
    return `${bytes} o`
  }
  if (bytes < 1024 * 1024) {
    return `${Math.round(bytes / 1024)} Ko`
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} Mo`
}

// Le titre d'un bloc est décalé selon son niveau : c'est la seule chose qui rende visible
// la hiérarchie d'une suite volontairement plate (ADR-0024). Le décalage se compte en
// tokens du projet, jamais en `rem` nus.
function headingIndent(level) {
  return { paddingLeft: `calc(var(--sb-space-lg) * ${Math.max(level - 1, 0)})` }
}

onMounted(load)
</script>

<template>
  <section class="document-detail">
    <div>
      <Button
        type="button"
        icon="pi pi-arrow-left"
        label="Documents"
        text
        @click="router.push({ name: 'documents' })"
      />
    </div>

    <ProgressSpinner v-if="loading" style="width: 2rem; height: 2rem" />

    <Message v-if="errorMessage" severity="error">{{ errorMessage }}</Message>

    <template v-if="document">
      <PageTitle>{{ document.filename }}</PageTitle>

      <dl class="meta">
        <div><dt>Statut</dt><dd><DocumentStatusTag :status="document.status" /></dd></div>
        <div><dt>Format</dt><dd>{{ document.format }}</dd></div>
        <div><dt>Taille</dt><dd>{{ formatSize(document.sizeBytes) }}</dd></div>
        <div><dt>Déposé le</dt><dd>{{ formatDate(document.createdAt) }}</dd></div>
      </dl>

      <!-- Le motif vient du serveur et s'affiche tel quel : le front ne réécrit aucun
           message d'erreur. -->
      <Message v-if="document.errorMessage" severity="warn">{{ document.errorMessage }}</Message>

      <template v-if="document.extraction">
        <h2 class="section-title">Texte extrait</h2>
        <p class="summary">
          {{ document.extraction.blocks.length }} bloc(s) · {{ document.extraction.characterCount }} caractères ·
          extrait le {{ formatDate(document.extraction.extractedAt) }}
        </p>

        <article v-for="(block, index) in document.extraction.blocks" :key="index" class="block">
          <h3 v-if="block.heading" class="block-heading" :style="headingIndent(block.headingLevel)">
            {{ block.heading }}
          </h3>
          <p class="block-text">{{ block.text }}</p>
        </article>
      </template>

      <!-- Trois façons de n'avoir rien à montrer, trois phrases : « en attente » n'est pas
           un échec, et « typologie non lue » n'en est pas un non plus. -->
      <p v-else-if="document.status === 'PENDING'" class="empty">
        Le texte de ce document n'a pas encore été extrait.
      </p>
      <p v-else-if="document.status === 'FAILED'" class="empty">
        Rien n'a pu être extrait de ce document.
      </p>
      <p v-else class="empty">Cette typologie de document n'a pas encore d'affichage.</p>
    </template>
  </section>
</template>

<style scoped>
.document-detail {
  display: flex;
  flex-direction: column;
  gap: var(--sb-space-md);
}

.meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sb-space-lg);
  margin: 0;
}

.meta div {
  display: flex;
  flex-direction: column;
  gap: var(--sb-space-xs);
}

.meta dt {
  font-size: var(--sb-text-small);
  color: var(--p-text-muted-color);
}

.meta dd {
  margin: 0;
}

.section-title {
  margin: 0;
  font-size: var(--sb-section-title-size);
}

.summary {
  margin: 0;
  font-size: var(--sb-text-small);
  color: var(--p-text-muted-color);
}

.block {
  padding-top: var(--sb-space-md);
  border-top: 1px solid var(--p-content-border-color);
}

.block-heading {
  margin: 0 0 var(--sb-space-xs);
  font-size: var(--sb-section-title-size);
}

/* Le corps d'un bloc est déjà normalisé par le domaine : ses sauts de ligne sont
   significatifs, et un `pre-wrap` est la seule façon de ne pas les perdre. */
.block-text {
  margin: 0;
  white-space: pre-wrap;
}

.empty {
  margin: 0;
  color: var(--p-text-muted-color);
}
</style>
```

- [ ] **Étape 8 : déclarer la route**

Dans `frontend/src/router/index.js`, ajouter l'import et la route **après** `/documents`
(l'ordre n'importe pas pour vue-router, mais la lecture oui) :

```js
import DocumentDetailView from '@/views/DocumentDetailView.vue'
```
```js
  { path: '/documents', name: 'documents', component: DocumentsView, meta: { requiresAuth: true } },
  // Le détail est adressable : un texte extrait se relit, se partage par son URL et
  // survit à un F5. Une modale sur la liste n'aurait rien de tout ça.
  {
    path: '/documents/:id',
    name: 'document',
    component: DocumentDetailView,
    meta: { requiresAuth: true },
  },
```

- [ ] **Étape 9 : cataloguer le composant dans `/design-system`**

Dans `frontend/src/views/DesignSystemView.vue` :

1. `import DocumentStatusTag from '@/components/DocumentStatusTag.vue'` et
   `const DOCUMENT_STATUSES = ['PENDING', 'EXTRACTED', 'FAILED']`.
2. Une section, placée juste avant « Tableau — DataTable » :
```vue
      <section>
        <h2>Statut de document — DocumentStatusTag</h2>
        <p class="muted">
          Le libellé et la sévérité d'un statut, au même endroit pour la liste et pour le
          détail. Le code vient de l'API, le libellé est une affaire d'écran — ADR-0022.
        </p>
        <div class="row">
          <DocumentStatusTag v-for="status in DOCUMENT_STATUSES" :key="status" :status="status" />
        </div>
      </section>
```
3. Dans la section « Tableau — DataTable », remplacer le statut en texte brut de la colonne
   par `<DocumentStatusTag :status="data.status" />` si la copie l'affiche en clair, pour
   que le catalogue montre l'écran tel qu'il est.

- [ ] **Étape 10 : constater le succès**

```bash
gfront npm run test:unit
gfront npm run build
```
Attendu : SUCCÈS des deux. Le `build` est le seul contrôle qui compile les templates —
aucun test ne les rend.

- [ ] **Étape 11 : passage humain**

```bash
docker compose up --build -d
```
- <http://localhost:8080/design-system> : la section « Statut de document » montre les trois
  états.
- <http://localhost:8080/documents> : déposer `src/test/resources/fixtures/structure.md`,
  attendre le passage en « Texte extrait » (le worker), cliquer sur l'œil.
- L'écran de détail montre les blocs, titres décalés par niveau, sauts de ligne conservés.
  Recharger la page (F5) : elle se recharge seule, l'URL suffit.
- Déposer `src/test/resources/fixtures/numerise.pdf` : la liste passe en « Traitement en
  échec », le détail montre le motif du serveur et « Rien n'a pu être extrait ».
- `docker compose down` avant de relancer `gtest`.

- [ ] **Étape 12 : mettre `CLAUDE.md` à jour**

1. Arborescence `frontend/` : ajouter `DocumentStatusTag` à la ligne `src/components/` et
   `DocumentDetailView` à la ligne `src/views/`.
2. Section « Le flux du dépôt d'un document », après le paragraphe sur `DocumentsView` :
```markdown
`DocumentDetailView` (`/documents/:id`, atteint par l'œil de chaque ligne) montre ce qui a
été extrait : les métadonnées, le statut, le motif d'échec le cas échéant, puis les blocs
titrés. L'écran est **adressable** — un texte extrait se relit, se partage par son URL et
survit à un F5, ce qu'une modale sur la liste n'aurait pas offert. C'est `document.type`, la
typologie, qui décide du rendu : une typologie sans affichage le dit plutôt que de rendre une
page vide. `DocumentStatusTag` porte le libellé et la sévérité d'un statut pour les deux
écrans — le motif était copié, il est devenu un composant.
```

- [ ] **Étape 13 : formater et committer**

```bash
make format
gfront npm run test:unit
git add -A
git commit -m "feat: un écran montre le texte extrait d'un document, bloc par bloc"
```

---

## Vérification de bout en bout

```bash
docker compose down
make check          # formatage + tests, des deux côtés
make build          # ce que vérifie la CI : jar + frontend/dist
```

Puis, sur la pile :

```bash
docker compose up --build -d
docker compose logs -f worker    # « extraction » sur chaque dépôt
```

Contrôles manuels, dans l'ordre :

1. La base de développement se migre sans erreur — `docker compose logs app | grep -i flyway`
   montre `V8` appliquée. Un texte extrait **avant** cette montée est toujours lisible : le
   renommage n'a rien perdu.
2. Déposer les quatre formats (`structure.md`, `brut.txt`, `titres.docx`, `signets.pdf`).
   Chacun passe en « Texte extrait », et son détail montre des blocs.
3. `numerise.pdf` passe en « Traitement en échec » et son détail porte le motif du serveur.
4. `curl` la route sur un identifiant inconnu avec un jeton valable :
   `404 {"message":"Ce document est introuvable dans votre base de connaissance."}`.
5. Supprimer un document extrait, puis vérifier en base que les deux tables sont vides pour
   lui — la cascade renommée fonctionne :
   ```sql
   SELECT count(*) FROM knowledge_text_extractions WHERE document_id = '<id>';
   ```
6. `grep -rn "knowledge_document_texts\|knowledge_document_blocks" src/main src/test frontend CLAUDE.md docs`
   ne rend que `V7__create_knowledge_document_texts.sql` et le corps de `V8`.

---

## Ce que ce plan ne fait pas

- **Il n'ajoute aucune deuxième typologie.** `DocumentType` n'a qu'une constante, et
  `DocumentFormat` que ses quatre formats. Le ticket demande la typologie « pour le texte
  seulement maintenant » : tout ce qui est fait ici l'est pour que la suivante n'ait rien à
  défaire.
- **Il ne fait pas router le worker par typologie.** `KnowledgeEventListener` dispatche
  toujours `ExtractDocumentText` sur `DocumentUploaded`. Le jour où une typologie non
  textuelle entrera, c'est là qu'il faudra choisir — et le listener n'ayant pas le droit de
  lire un repository, ce sera probablement une commande `ProcessDocument` qui relira le
  document et déléguera. À décider à ce moment-là, pas maintenant.
- **Il n'ajoute pas la typologie à `DocumentView`** (la liste). Aucun écran n'en a besoin
  là-bas.
- **Il ne réextrait rien.** Pas de bouton « relancer le traitement » sur un document en
  échec : c'est un autre geste, avec sa propre commande et son propre refus.
- **Il n'ajoute pas de test de composant Vue.** `@vue/test-utils` reste absent ; ce qui se
  teste au front reste `src/api/`, `src/stores/` et `src/router/`, et `/design-system` tient
  lieu de contrôle de rendu (ADR-0016).
- **Il ne pagine pas les blocs.** Un document long rend tous ses blocs d'un coup. À rouvrir
  si un PDF de plusieurs centaines de pages rend l'écran inutilisable.
