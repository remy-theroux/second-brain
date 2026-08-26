# Extraction du texte et des sections d'un document — plan d'implémentation

> **Pour les agents d'exécution :** SOUS-COMPÉTENCE REQUISE — utiliser
> `superpowers:subagent-driven-development` (recommandé) ou `superpowers:executing-plans`
> pour dérouler ce plan tâche par tâche. Les étapes sont des cases à cocher (`- [ ]`).

**But :** un document déposé porte, quelques secondes plus tard, son texte découpé en blocs
titrés dans une forme commune aux quatre formats acceptés — ou un statut `FAILED` et un
motif lisible.

**Architecture :** un objet-valeur `ExtractedText` (liste ordonnée de `TextBlock`) est le
format commun, matérialisé dans `knowledge/domain/`. Quatre adapters du port
`DocumentTextExtractor`, un par format, le produisent. `KnowledgeEventListener` consomme
`DocumentUploaded` et dispatche `ExtractDocumentText` ; l'échec s'écrit dans une seconde
transaction par `MarkDocumentExtractionFailed`. Le texte est persisté dans un agrégat à part,
`DocumentText`, sur deux tables cascadées.

**Stack :** Java 25 · Spring Boot 4.0.7 · PDFBox 3.0.7 · Apache POI 5.5.1 (XWPF) ·
commonmark-java 0.24.0 · PostgreSQL 17 · RabbitMQ 4 · JUnit 5 + AssertJ + Testcontainers.

**Spec :** `docs/superpowers/specs/2026-08-26-extraction-texte-documents-design.md` — le plan
argumente depuis elle ; la lire avant d'exécuter.

## Contraintes globales

Elles s'ajoutent implicitement aux exigences de **chaque** tâche.

- **Tout passe par Docker.** Aucun JDK, aucun Gradle, aucun Node sur l'hôte. Définir les
  fonctions `gtest` et `gfront` de `CLAUDE.md` une fois par session, avant la première
  commande.
- **`gtest` et `docker compose up` ne cohabitent pas** : `docker compose down` avant de
  lancer la suite.
- **Français** pour les commentaires, la Javadoc, les messages d'exception, les libellés et
  les noms de méthodes de test. **Anglais** pour les noms de classes, de méthodes de
  production et de packages.
- **`make format-back` avant chaque commit** (`make format` si le front est touché). Le
  style est décidé par palantir-java-format ; ne pas se battre avec lui. La Javadoc et les
  commentaires ne sont jamais reformatés : leur mise en forme reste à la charge du rédacteur.
- **Jamais de `@Transactional` sur un handler** — la transaction appartient au bus.
- **Toute exception métier hérite de `RuntimeException`** — une exception checked ne
  déclenche pas de rollback.
- **Flyway est maître du schéma**, `ddl-auto: validate`. Ne jamais modifier une migration
  déjà appliquée. Les migrations de ce plan sont `V6` et `V7`.
- **Le domaine n'importe jamais `org.springframework.*` ni `…infrastructure.*`.** Seule
  exception : `jakarta.persistence` sur les entités et sur `TextBlock` (ADR-0002, étendu par
  la décision 6 de la spec).
- **Tester le port, pas l'adapter.** Injecter `DocumentTextRepository`, pas
  `JpaDocumentTextRepositoryAdapter`.
- **Pattern d'intégration imposé** : `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)`.
  Ne pas introduire `@DataJpaTest`.
- **Un ADR arrive dans le commit du code qu'il justifie**, avec sa ligne d'index dans
  `CLAUDE.md`. Cinq ADR sont dus : 0024 et 0025 (tâche 1), 0026 (tâche 4), 0027 (tâche 6),
  0028 (tâche 8). Gabarit : `docs/decisions/0000-adr-template.md`.
- **Toute dépendance nouvelle passe par `gradle/libs.versions.toml`.** Versions exactes,
  vérifiées sur Maven Central le 2026-08-26 : PDFBox `3.0.7`, POI `5.5.1`,
  commonmark `0.24.0`. Aucune n'est couverte par le BOM Spring Boot.
- **Un commit par tâche**, tests verts, préfixe conventionnel minuscule (`feat:`, `fix:`,
  `refactor:`, `conf:`, `test:`, `docs:`).

## Structure des fichiers

```
src/main/java/xyz/sterenn/secondbrain/knowledge/
├── domain/
│   ├── ExtractionPolicy.java                    CRÉÉ  T1  plancher de caractères, sans dépendance
│   ├── valueobject/
│   │   ├── TextBlock.java                       CRÉÉ  T1  @Embeddable : heading, level, text
│   │   ├── ExtractedText.java                   CRÉÉ  T1  le format — liste non vide de blocs
│   │   ├── ExtractedTextBuilder.java            CRÉÉ  T1  assemblage tolérant, chemin des extracteurs
│   │   └── DocumentStatus.java                  MODIF T2  + EXTRACTED, FAILED
│   ├── entity/
│   │   ├── Document.java                        MODIF T2  + errorMessage, markTextExtracted, markExtractionFailed
│   │   └── DocumentText.java                    CRÉÉ  T3  agrégat du texte extrait
│   ├── exception/
│   │   ├── DocumentExtractionException.java     CRÉÉ  T1  mère des deux refus d'extraction
│   │   ├── UnreadableDocumentException.java     CRÉÉ  T1  le fichier ne se lit pas
│   │   └── UnextractableDocumentException.java  CRÉÉ  T1  il se lit, mais ne dit rien
│   ├── port/
│   │   ├── DocumentTextRepository.java          CRÉÉ  T3
│   │   └── DocumentTextExtractor.java           CRÉÉ  T4
│   └── event/
│       └── DocumentTextExtracted.java           CRÉÉ  T7  → knowledge.document-text.extracted
├── application/
│   ├── command/
│   │   ├── ExtractDocumentText.java             CRÉÉ  T7
│   │   ├── ExtractDocumentTextHandler.java      CRÉÉ  T7  indexe les extracteurs, échoue au démarrage sur un trou
│   │   ├── MarkDocumentExtractionFailed.java    CRÉÉ  T8
│   │   └── MarkDocumentExtractionFailedHandler.java CRÉÉ T8
│   └── query/DocumentView.java                  MODIF T9  + errorMessage
└── infrastructure/
    ├── extraction/
    │   ├── Section.java                         CRÉÉ  T4  section brute, avant normalisation
    │   ├── TextDecoding.java                    CRÉÉ  T4  UTF-8 puis repli ISO-8859-1
    │   ├── PlainTextExtractor.java              CRÉÉ  T4  .txt
    │   ├── CommonmarkTextExtractor.java         CRÉÉ  T4  .md
    │   ├── PoiDocxTextExtractor.java            CRÉÉ  T5  .docx
    │   ├── TextLine.java                        CRÉÉ  T6  une ligne et sa plus grande police
    │   ├── HeadingFontStripper.java             CRÉÉ  T6  PDFTextStripper qui mesure
    │   ├── HeadingHeuristic.java                CRÉÉ  T6  taille de police → sections
    │   └── PdfBoxTextExtractor.java             CRÉÉ  T6  .pdf — signets, puis police
    ├── persistence/
    │   ├── JpaDocumentTextRepositoryAdapter.java     CRÉÉ T3
    │   └── SpringDataDocumentTextRepository.java     CRÉÉ T3  package-private
    └── messaging/
        ├── KnowledgeEventListener.java          MODIF T7/T8  dispatche, rattrape, marque
        └── KnowledgeMessagingConfiguration.java MODIF T7  déclare DocumentTextExtracted

src/main/resources/db/migration/
├── V6__add_knowledge_documents_error_message.sql  CRÉÉ T2
└── V7__create_knowledge_document_texts.sql        CRÉÉ T3

src/test/java/xyz/sterenn/secondbrain/knowledge/fixtures/
└── FixtureFactory.java                          CRÉÉ T5, ÉTENDU T6  écrit les binaires, lancé à la main

src/test/resources/fixtures/                     CRÉÉ T4 (texte), T5 (docx), T6 (pdf)
build.gradle.kts                                 MODIF T4, T5, T6  dépendances + tâche generateFixtures
gradle/libs.versions.toml                        MODIF T4, T5, T6
frontend/src/views/DocumentsView.vue             MODIF T9
docs/decisions/0024…0028                         CRÉÉS T1, T4, T6, T8
CLAUDE.md                                        MODIF à chaque tâche portant un ADR, + T3, T7
```

---

## Tâche 1 : Le format dans le domaine

Le livrable central du ticket. Aucune persistance, aucun Spring : cette tâche se teste
entièrement en unitaire pur.

**Fichiers :**
- Créer : `src/main/java/xyz/sterenn/secondbrain/knowledge/domain/ExtractionPolicy.java`
- Créer : `src/main/java/xyz/sterenn/secondbrain/knowledge/domain/valueobject/TextBlock.java`
- Créer : `src/main/java/xyz/sterenn/secondbrain/knowledge/domain/valueobject/ExtractedText.java`
- Créer : `src/main/java/xyz/sterenn/secondbrain/knowledge/domain/valueobject/ExtractedTextBuilder.java`
- Créer : `src/main/java/xyz/sterenn/secondbrain/knowledge/domain/exception/DocumentExtractionException.java`
- Créer : `src/main/java/xyz/sterenn/secondbrain/knowledge/domain/exception/UnreadableDocumentException.java`
- Créer : `src/main/java/xyz/sterenn/secondbrain/knowledge/domain/exception/UnextractableDocumentException.java`
- Créer : `docs/decisions/0024-le-texte-extrait-est-une-suite-plate-de-blocs-titres.md`
- Créer : `docs/decisions/0025-un-plancher-de-caracteres-declare-un-document-inexploitable.md`
- Modifier : `CLAUDE.md` (index des ADR + arborescence du contexte `knowledge`)
- Tests : `src/test/java/xyz/sterenn/secondbrain/knowledge/domain/valueobject/TextBlockTest.java`,
  `.../ExtractedTextTest.java`, `.../ExtractedTextBuilderTest.java`

**Interfaces :**
- Consomme : rien.
- Produit :
  - `TextBlock.of(String heading, int headingLevel, String text) → TextBlock` (lève
    `IllegalArgumentException` si le texte normalisé est vide, ou si un titre non vide porte
    un niveau hors de 1..6)
  - `TextBlock.untitled(String text) → TextBlock`
  - `TextBlock.getHeading() → String`, `getHeadingLevel() → int`, `getText() → String`
  - `TextBlock.MAX_HEADING_LENGTH = 255`, `TextBlock.MAX_HEADING_LEVEL = 6`
  - `static String TextBlock.normalise(String)` — **package-private**, `domain.valueobject`
  - `new ExtractedText(List<TextBlock> blocks)` (lève `UnextractableDocumentException`)
  - `ExtractedText.blocks() → List<TextBlock>`, `ExtractedText.characterCount() → int`
  - `ExtractedText.untitled(String text) → ExtractedText`
  - `new ExtractedTextBuilder()`, `.section(String heading, int level, String text) → this`,
    `.untitled(String text) → this`, `.build() → ExtractedText`
  - `ExtractionPolicy.MINIMUM_USEFUL_CHARACTERS = 50`
  - `DocumentExtractionException` (abstraite), `UnreadableDocumentException(Throwable)`,
    `UnreadableDocumentException()`, `UnextractableDocumentException()`

- [ ] **Étape 1 : Écrire les tests de `TextBlock`**

`src/test/java/xyz/sterenn/secondbrain/knowledge/domain/valueobject/TextBlockTest.java` :

```java
package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class TextBlockTest {

    @Test
    void garde_le_titre_son_niveau_et_le_texte() {
        TextBlock bloc = TextBlock.of("Introduction", 1, "Le corps de la section.");

        assertThat(bloc.getHeading()).isEqualTo("Introduction");
        assertThat(bloc.getHeadingLevel()).isEqualTo(1);
        assertThat(bloc.getText()).isEqualTo("Le corps de la section.");
    }

    @Test
    void un_bloc_sans_titre_porte_le_niveau_zero() {
        TextBlock bloc = TextBlock.untitled("Tout le texte du document.");

        assertThat(bloc.getHeading()).isEmpty();
        assertThat(bloc.getHeadingLevel()).isZero();
    }

    @Test
    void ramene_le_niveau_a_zero_quand_le_titre_est_vide() {
        assertThat(TextBlock.of("   ", 3, "Du texte.").getHeadingLevel()).isZero();
    }

    @Test
    void refuse_un_niveau_hors_de_un_a_six_pour_un_titre_renseigne() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> TextBlock.of("Titre", 7, "Du texte."))
                .withMessageContaining("1 à 6");
    }

    @Test
    void refuse_un_bloc_dont_le_texte_est_vide_une_fois_normalise() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> TextBlock.untitled("  \n\n \t "))
                .withMessageContaining("sans texte");
    }

    @Test
    void normalise_les_fins_de_ligne_et_les_espaces_de_fin() {
        TextBlock bloc = TextBlock.untitled("Première ligne   \r\nDeuxième ligne\r");

        assertThat(bloc.getText()).isEqualTo("Première ligne\nDeuxième ligne");
    }

    @Test
    void conserve_la_frontiere_de_paragraphe_mais_pas_la_mise_en_page() {
        TextBlock bloc = TextBlock.untitled("Paragraphe un.\n\n\n\n\nParagraphe deux.");

        assertThat(bloc.getText()).isEqualTo("Paragraphe un.\n\nParagraphe deux.");
    }

    @Test
    void efface_le_trait_d_union_conditionnel_qui_couperait_les_mots() {
        assertThat(TextBlock.untitled("consti\u00ADtution").getText()).isEqualTo("constitution");
    }

    @Test
    void ramene_l_espace_insecable_a_un_espace_ordinaire_sans_coller_les_mots() {
        assertThat(TextBlock.untitled("Article\u00A0premier : le texte.").getText())
                .isEqualTo("Article premier : le texte.");
    }

    @Test
    void efface_la_marque_d_ordre_des_octets_en_tete_de_fichier() {
        assertThat(TextBlock.untitled("\uFEFFPremière ligne du fichier.").getText())
                .isEqualTo("Première ligne du fichier.");
    }

    @Test
    void aplatit_les_blancs_d_un_titre_sur_une_seule_ligne() {
        TextBlock bloc = TextBlock.of("Chapitre\n premier   ", 1, "Du texte.");

        assertThat(bloc.getHeading()).isEqualTo("Chapitre premier");
    }

    @Test
    void tronque_un_titre_trop_long_plutot_que_de_le_refuser() {
        String tresLong = "T".repeat(TextBlock.MAX_HEADING_LENGTH + 42);

        assertThat(TextBlock.of(tresLong, 1, "Du texte.").getHeading())
                .hasSize(TextBlock.MAX_HEADING_LENGTH);
    }

    @Test
    void deux_blocs_de_meme_contenu_sont_egaux() {
        assertThat(TextBlock.of("Titre", 2, "Corps.")).isEqualTo(TextBlock.of("Titre", 2, "Corps."));
    }
}
```

- [ ] **Étape 2 : Lancer le test, vérifier qu'il échoue**

```bash
gtest test --tests "xyz.sterenn.secondbrain.knowledge.domain.valueobject.TextBlockTest"
```

Attendu : ÉCHEC de compilation, `cannot find symbol: class TextBlock`.

- [ ] **Étape 3 : Écrire `TextBlock`**

`src/main/java/xyz/sterenn/secondbrain/knowledge/domain/valueobject/TextBlock.java` :

```java
package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.text.Normalizer;
import java.util.Objects;

/**
 * Un bloc de texte extrait d'un document, rattaché au titre de sa section.
 *
 * <p><strong>Un bloc est une section, pas un paragraphe.</strong> C'est ce que tranche le
 * second scénario du ticket : un document sans titre rend « un unique bloc contenant tout
 * le texte », or un texte sans titre compte bien plusieurs paragraphes. Voir ADR-0024.
 *
 * <p>Le titre est vide et son niveau nul quand le document n'en porte pas — jamais
 * {@code null} : un consommateur qui préfixe ses extraits n'a pas à distinguer deux formes
 * d'absence.
 *
 * <p>{@code headingLevel} n'a aujourd'hui aucun consommateur. Il est conservé parce qu'il
 * est la seule information permettant plus tard de reconstruire un chemin de section
 * (« Chapitre 1 &gt; Introduction ») sans réextraire toute la base : le niveau, l'extraction
 * est seule à le connaître, le chemin se recalcule à tout moment.
 *
 * <p>{@code @Embeddable} dans le domaine : c'est ADR-0002 — les entités JPA vivent dans le
 * domaine, sans classe miroir ni mapper — étendu à un objet-valeur possédé par une entité.
 * La position dans le document n'est <em>pas</em> un champ : elle appartient à la liste, et
 * c'est {@code @OrderColumn} qui la porte côté {@code DocumentText}.
 */
@Embeddable
public class TextBlock {

    /** Ce que la colonne accepte, et bien au-delà de ce qu'un titre lisible réclame. */
    public static final int MAX_HEADING_LENGTH = 255;

    /** Six niveaux, comme HTML et comme les styles Heading1..6 de Word. */
    public static final int MAX_HEADING_LEVEL = 6;

    @Column(nullable = false, length = MAX_HEADING_LENGTH)
    private String heading;

    @Column(name = "heading_level", nullable = false)
    private int headingLevel;

    // columnDefinition explicite : sans lui, Hibernate attendrait un varchar(255) et
    // `ddl-auto: validate` refuserait de démarrer contre une colonne `text`.
    @Column(nullable = false, columnDefinition = "text")
    private String text;

    protected TextBlock() {
        // requis par JPA
    }

    private TextBlock(String heading, int headingLevel, String text) {
        this.heading = heading;
        this.headingLevel = headingLevel;
        this.text = text;
    }

    /**
     * @throws IllegalArgumentException si le texte est vide une fois normalisé, ou si un
     *     titre renseigné porte un niveau hors de 1 à {@value #MAX_HEADING_LEVEL}. C'est une
     *     erreur de programmation d'un extracteur, pas un refus métier : un document sans
     *     texte se refuse à l'échelle du document, par {@link ExtractedText}.
     */
    public static TextBlock of(String heading, int headingLevel, String text) {
        String titre = normaliseHeading(heading);
        String corps = normalise(text);
        if (corps.isEmpty()) {
            throw new IllegalArgumentException("Un bloc sans texte n'en est pas un : il ne se construit pas");
        }
        if (!titre.isEmpty() && (headingLevel < 1 || headingLevel > MAX_HEADING_LEVEL)) {
            throw new IllegalArgumentException(
                    "Le niveau d'un titre va de 1 à " + MAX_HEADING_LEVEL + ", reçu : " + headingLevel);
        }
        return new TextBlock(titre, titre.isEmpty() ? 0 : headingLevel, corps);
    }

    /** Le document ne portait pas de titre à cet endroit : niveau nul, titre vide. */
    public static TextBlock untitled(String text) {
        return of("", 0, text);
    }

    /**
     * Normalise un texte extrait, sans jamais toucher à ce qui porte du sens.
     *
     * <p>Les fins de ligne sont ramenées à {@code \n}, les espaces de fin de ligne effacés,
     * et toute suite de trois sauts de ligne ou plus ramenée à deux : <strong>la frontière
     * de paragraphe survit, la mise en page non</strong>. C'est cette double ligne que RAG-5
     * cherchera pour découper.
     *
     * <p>NFC parce que deux extracteurs peuvent rendre le même « é » sous deux formes, et
     * que deux blocs identiques doivent être égaux. Quatre caractères invisibles sont
     * traités à part : la marque d'ordre des octets ({@code U+FEFF}) et l'espace insécable
     * ({@code U+00A0}) deviennent des espaces — les effacer collerait les mots —, tandis que
     * l'octet nul ({@code U+0000}, qu'un PDF mal formé sème) et le trait d'union conditionnel
     * ({@code U+00AD}, qui couperait les mots) disparaissent.
     *
     * <p>Package-private : {@link ExtractedTextBuilder} en a besoin pour décider si une
     * section a un corps, avant de tenter de construire un bloc qui serait refusé.
     */
    static String normalise(String brut) {
        if (brut == null) {
            return "";
        }
        return Normalizer.normalize(brut, Normalizer.Form.NFC)
                .replace('\uFEFF', ' ') // marque d'ordre des octets, en tête d'un fichier UTF-8
                .replace('\u00A0', ' ') // espace insécable : un espace, pas rien — l'effacer collerait les mots
                .replace("\u0000", "") // octet nul : un PDF mal formé en sème, et PostgreSQL le refuse
                .replace("\u00AD", "") // trait d'union conditionnel : il couperait les mots
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[ \\t\\x0B\\f]+(?=\\n)", "")
                .replaceAll("\n{3,}", "\n\n")
                .strip();
    }

    /** Un titre tient sur une ligne : ses blancs sont aplatis, sa longueur bornée. */
    private static String normaliseHeading(String brut) {
        String titre = normalise(brut).replaceAll("\\s+", " ").strip();
        return titre.length() > MAX_HEADING_LENGTH
                ? titre.substring(0, MAX_HEADING_LENGTH).strip()
                : titre;
    }

    public String getHeading() {
        return heading;
    }

    public int getHeadingLevel() {
        return headingLevel;
    }

    public String getText() {
        return text;
    }

    @Override
    public boolean equals(Object autre) {
        return autre instanceof TextBlock bloc
                && headingLevel == bloc.headingLevel
                && Objects.equals(heading, bloc.heading)
                && Objects.equals(text, bloc.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(heading, headingLevel, text);
    }

    /** Volontairement sans le corps : un bloc pèse parfois plusieurs milliers de caractères. */
    @Override
    public String toString() {
        return "TextBlock[heading=" + heading + ", level=" + headingLevel + ", " + text.length() + " caractères]";
    }
}
```

- [ ] **Étape 4 : Lancer le test, vérifier qu'il passe**

```bash
gtest test --tests "xyz.sterenn.secondbrain.knowledge.domain.valueobject.TextBlockTest"
```

Attendu : SUCCÈS, 13 tests.

- [ ] **Étape 5 : Écrire les tests d'`ExtractedText` et d'`ExtractedTextBuilder`**

`src/test/java/xyz/sterenn/secondbrain/knowledge/domain/valueobject/ExtractedTextTest.java` :

```java
package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;
import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.knowledge.domain.ExtractionPolicy;
import xyz.sterenn.secondbrain.knowledge.domain.exception.UnextractableDocumentException;

class ExtractedTextTest {

    private static final String ASSEZ_LONG = "Un texte assez long pour franchir le plancher des cinquante.";

    @Test
    void garde_ses_blocs_dans_l_ordre_ou_ils_arrivent() {
        TextBlock premier = TextBlock.of("Un", 1, ASSEZ_LONG);
        TextBlock second = TextBlock.of("Deux", 1, ASSEZ_LONG);

        assertThat(new ExtractedText(List.of(premier, second)).blocks()).containsExactly(premier, second);
    }

    @Test
    void refuse_un_document_sans_aucun_bloc() {
        assertThatExceptionOfType(UnextractableDocumentException.class)
                .isThrownBy(() -> new ExtractedText(List.of()))
                .withMessageContaining("pas de texte exploitable");
    }

    @Test
    void refuse_un_document_sous_le_plancher_de_caracteres() {
        String troisBribes = "3 Page 1";
        assertThat(troisBribes.length()).isLessThan(ExtractionPolicy.MINIMUM_USEFUL_CHARACTERS);

        assertThatExceptionOfType(UnextractableDocumentException.class)
                .isThrownBy(() -> ExtractedText.untitled(troisBribes));
    }

    @Test
    void additionne_les_caracteres_de_tous_ses_blocs_sans_compter_les_titres() {
        ExtractedText texte = new ExtractedText(
                List.of(TextBlock.of("Un titre qui ne compte pas", 1, ASSEZ_LONG), TextBlock.untitled(ASSEZ_LONG)));

        assertThat(texte.characterCount()).isEqualTo(ASSEZ_LONG.length() * 2);
    }

    @Test
    void ne_se_laisse_pas_modifier_par_la_liste_qu_on_lui_a_donnee() {
        List<TextBlock> mutable = new java.util.ArrayList<>(List.of(TextBlock.untitled(ASSEZ_LONG)));
        ExtractedText texte = new ExtractedText(mutable);

        mutable.clear();

        assertThat(texte.blocks()).hasSize(1);
    }
}
```

`src/test/java/xyz/sterenn/secondbrain/knowledge/domain/valueobject/ExtractedTextBuilderTest.java` :

```java
package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.knowledge.domain.exception.UnextractableDocumentException;

class ExtractedTextBuilderTest {

    private static final String ASSEZ_LONG = "Un texte assez long pour franchir le plancher des cinquante.";

    @Test
    void assemble_les_sections_dans_l_ordre() {
        ExtractedText texte = new ExtractedTextBuilder()
                .section("Introduction", 1, ASSEZ_LONG)
                .section("Détail", 2, ASSEZ_LONG)
                .build();

        assertThat(texte.blocks()).extracting(TextBlock::getHeading).containsExactly("Introduction", "Détail");
        assertThat(texte.blocks()).extracting(TextBlock::getHeadingLevel).containsExactly(1, 2);
    }

    @Test
    void ecarte_sans_bruit_une_section_dont_le_corps_est_vide() {
        ExtractedText texte = new ExtractedTextBuilder()
                .section("Un titre suivi de rien", 1, "   \n  ")
                .section("Le vrai contenu", 1, ASSEZ_LONG)
                .build();

        assertThat(texte.blocks()).extracting(TextBlock::getHeading).containsExactly("Le vrai contenu");
    }

    @Test
    void un_document_sans_titre_donne_un_unique_bloc() {
        ExtractedText texte = new ExtractedTextBuilder().untitled(ASSEZ_LONG).build();

        assertThat(texte.blocks()).singleElement().satisfies(bloc -> {
            assertThat(bloc.getHeading()).isEmpty();
            assertThat(bloc.getText()).isEqualTo(ASSEZ_LONG);
        });
    }

    @Test
    void refuse_de_construire_quand_toutes_les_sections_ont_ete_ecartees() {
        ExtractedTextBuilder blocs = new ExtractedTextBuilder().section("Titre seul", 1, "").untitled("  ");

        assertThatExceptionOfType(UnextractableDocumentException.class).isThrownBy(blocs::build);
    }
}
```

- [ ] **Étape 6 : Lancer les deux tests, vérifier qu'ils échouent**

```bash
gtest test --tests "xyz.sterenn.secondbrain.knowledge.domain.valueobject.Extracted*"
```

Attendu : ÉCHEC de compilation, `cannot find symbol: class ExtractedText`.

- [ ] **Étape 7 : Écrire les exceptions, la politique, `ExtractedText` et son builder**

`.../domain/exception/DocumentExtractionException.java` :

```java
package xyz.sterenn.secondbrain.knowledge.domain.exception;

/**
 * Mère des deux façons dont l'extraction d'un document peut refuser d'aboutir.
 *
 * <p>Elle existe pour une raison précise : c'est elle que le consommateur d'événements
 * interroge pour décider si le message d'échec peut être montré à l'utilisateur. Un refus
 * métier porte un message affichable tel quel ; une {@code NullPointerException} n'en porte
 * aucun qu'on puisse afficher. Voir ADR-0028.
 *
 * <p>{@code RuntimeException} et non checked : c'est ce qui déclenche le rollback promis par
 * le {@code CommandBus}.
 */
public abstract class DocumentExtractionException extends RuntimeException {

    protected DocumentExtractionException(String message) {
        super(message);
    }

    protected DocumentExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

`.../domain/exception/UnreadableDocumentException.java` :

```java
package xyz.sterenn.secondbrain.knowledge.domain.exception;

/**
 * Le fichier n'a pas pu être ouvert : zip corrompu, PDF tronqué, ou {@code .docx} qui n'est
 * un {@code .docx} que par son extension.
 *
 * <p>Le format se déduit de l'extension au dépôt, faute de mieux ; c'est ici, et seulement
 * ici, qu'on découvre qu'elle mentait.
 */
public class UnreadableDocumentException extends DocumentExtractionException {

    private static final String MESSAGE =
            "Ce fichier n'a pas pu être lu : il est peut-être endommagé, ou son contenu ne correspond pas à son extension.";

    public UnreadableDocumentException(Throwable cause) {
        super(MESSAGE, cause);
    }

    public UnreadableDocumentException() {
        super(MESSAGE);
    }
}
```

`.../domain/exception/UnextractableDocumentException.java` :

```java
package xyz.sterenn.secondbrain.knowledge.domain.exception;

/**
 * Le fichier s'est ouvert, mais il n'en sort pas assez de texte pour être exploitable.
 *
 * <p>C'est le cas du PDF issu d'une numérisation : la couche texte est absente, et
 * l'extraction ne rend qu'un numéro de page ou une mention de scanner. Le ticket exige que
 * ce cas <strong>échoue</strong> plutôt que de produire du vide en silence — le vide ne se
 * verrait qu'à la première question restée sans réponse, trois tickets plus loin.
 *
 * <p>Le seuil est {@code ExtractionPolicy.MINIMUM_USEFUL_CHARACTERS}. Voir ADR-0025.
 */
public class UnextractableDocumentException extends DocumentExtractionException {

    public UnextractableDocumentException() {
        super("Ce document ne contient pas de texte exploitable :"
                + " s'il s'agit d'une numérisation, il faudra le repasser par une reconnaissance de caractères.");
    }
}
```

`.../domain/ExtractionPolicy.java` :

```java
package xyz.sterenn.secondbrain.knowledge.domain;

/**
 * Ce que le domaine tient pour du texte exploitable.
 *
 * <p>Règle métier pure, statique, sans dépendance — à la racine du domaine, comme
 * {@code PasswordPolicy} l'est pour {@code users} : elle se teste sans Spring.
 *
 * <p>Le plancher n'est pas une précaution de confort. Un PDF numérisé rend rarement zéro
 * caractère : il rend un numéro de page, un tampon, une mention de scanner. Un test
 * {@code isBlank()} seul les laisserait passer, et c'est exactement le vide silencieux que
 * le ticket interdit. Voir ADR-0025.
 */
public final class ExtractionPolicy {

    /**
     * En dessous, aucun document n'est tenu pour exploitable. Cinquante caractères, c'est
     * moins d'une phrase : le seuil vise la numérisation muette, pas le document bref.
     */
    public static final int MINIMUM_USEFUL_CHARACTERS = 50;

    private ExtractionPolicy() {
        // règle métier, pas un objet
    }

    public static boolean isExploitable(int characterCount) {
        return characterCount >= MINIMUM_USEFUL_CHARACTERS;
    }
}
```

`.../domain/valueobject/ExtractedText.java` :

```java
package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.util.List;
import java.util.Objects;
import xyz.sterenn.secondbrain.knowledge.domain.ExtractionPolicy;
import xyz.sterenn.secondbrain.knowledge.domain.exception.UnextractableDocumentException;

/**
 * <strong>Le format commun à tous les documents extraits</strong> : une suite ordonnée de
 * blocs de texte, chacun rattaché au titre de sa section. Voir ADR-0024.
 *
 * <p>C'est le contrat entre l'extraction et tout ce qui viendra après — RAG-5 le découpe,
 * RAG-6 l'enchaîne, RAG-7 le remplace. Quatre formats de fichier entrent, une seule forme
 * en sort.
 *
 * <p><strong>Il est impossible d'en construire un qui soit vide.</strong> Le constructeur
 * compact refuse la liste vide et le total sous le plancher d'{@link ExtractionPolicy}, en
 * levant {@link UnextractableDocumentException} — le refus exigé par le troisième scénario
 * du ticket est donc porté par le type, pas par un contrôle qu'un extracteur pourrait
 * oublier.
 *
 * <p>Seul le corps des blocs compte dans le plancher, jamais les titres : un document dont
 * il ne resterait que des titres n'a pas de contenu.
 */
public record ExtractedText(List<TextBlock> blocks) {

    public ExtractedText {
        Objects.requireNonNull(blocks, "Les blocs de texte sont obligatoires");
        blocks = List.copyOf(blocks);
        if (!ExtractionPolicy.isExploitable(characterCount(blocks))) {
            throw new UnextractableDocumentException();
        }
    }

    /**
     * Un document dépourvu de toute structure : un seul bloc, sans titre.
     *
     * <p>Réservé au texte dont on sait déjà qu'il n'est pas vide. Un extracteur qui assemble
     * un document passe par {@link ExtractedTextBuilder}, qui écarte les sections vides
     * avant de tenter le refus.
     */
    public static ExtractedText untitled(String text) {
        return new ExtractedText(List.of(TextBlock.untitled(text)));
    }

    public int characterCount() {
        return characterCount(blocks);
    }

    private static int characterCount(List<TextBlock> blocks) {
        return blocks.stream().mapToInt(bloc -> bloc.getText().length()).sum();
    }
}
```

`.../domain/valueobject/ExtractedTextBuilder.java` :

```java
package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.util.ArrayList;
import java.util.List;

/**
 * Assemble un {@link ExtractedText} section par section. C'est le chemin normal d'un
 * extracteur : il parcourt un document, annonce chaque section telle qu'il la lit, et laisse
 * ici la question de savoir ce qui mérite un bloc.
 *
 * <p><strong>Une section dont le corps est vide est écartée sans bruit.</strong> Un
 * extracteur rencontre en permanence des titres suivis d'un autre titre, des paragraphes de
 * mise en page, des pages blanches : lui faire porter ce filtrage le rendrait bavard, et
 * chacun le rendrait à sa façon.
 *
 * <p>Conséquence à connaître : <strong>un titre immédiatement suivi d'un autre titre est
 * perdu.</strong> {@code # A} puis {@code ## B} ne rend que « B ». Le remède serait un chemin
 * de section, que {@code headingLevel} permet de reconstruire plus tard sans réextraire ;
 * l'inventer ici reviendrait à figer une convention d'affichage dans le domaine.
 *
 * <p>Mutable, et c'est assumé : c'est un échafaudage, pas une valeur. Ce qui sort de
 * {@link #build()} est immuable.
 */
public final class ExtractedTextBuilder {

    private final List<TextBlock> blocks = new ArrayList<>();

    /** Une section titrée. Sans corps, elle n'entre pas. */
    public ExtractedTextBuilder section(String heading, int headingLevel, String text) {
        if (!TextBlock.normalise(text).isEmpty()) {
            blocks.add(TextBlock.of(heading, headingLevel, text));
        }
        return this;
    }

    /** Ce qui n'appartient à aucune section : avant le premier titre, ou dans un document qui n'en a pas. */
    public ExtractedTextBuilder untitled(String text) {
        return section("", 0, text);
    }

    /**
     * @throws xyz.sterenn.secondbrain.knowledge.domain.exception.UnextractableDocumentException
     *     si rien n'a été retenu, ou si le total reste sous le plancher — c'est le refus
     *     exigé par le troisième scénario du ticket, et il tombe ici pour tous les
     *     extracteurs à la fois
     */
    public ExtractedText build() {
        return new ExtractedText(blocks);
    }
}
```

- [ ] **Étape 8 : Lancer toute la suite du domaine `knowledge`, vérifier qu'elle passe**

```bash
gtest test --tests "xyz.sterenn.secondbrain.knowledge.domain.*"
```

Attendu : SUCCÈS. Les tests préexistants (`DocumentTest`, `ChecksumTest`,
`DocumentFormatTest`, `DocumentUploadedTest`) restent verts — rien de ce qu'ils touchent n'a
changé.

- [ ] **Étape 9 : Écrire les deux ADR**

`docs/decisions/0024-le-texte-extrait-est-une-suite-plate-de-blocs-titres.md` — copier le
bloc du gabarit `docs/decisions/0000-adr-template.md` et le remplir depuis la **décision 1**
de la spec. Points qui doivent y figurer :

- Contexte : quatre formats entrent, RAG-5/6/7 consomment ; il faut une forme unique.
- Options : blocs plats titrés · arbre de sections imbriquées · Markdown canonique.
- Décision : **blocs plats titrés**, parce que le second scénario du ticket
  (« un unique bloc contenant tout le texte ») exclut qu'un bloc soit un paragraphe.
- Conséquences — Mal : un titre immédiatement suivi d'un autre titre est perdu (voir la
  Javadoc d'`ExtractedTextBuilder`) ; `headingLevel` est conservé sans consommateur.
- Condition de réouverture : le jour où un consommateur réclame la hiérarchie complète —
  RAG-8 remontant un extrait avec son chemin de section, par exemple. La sortie sera un
  chemin recalculé depuis `headingLevel`, pas un arbre en base.
- Pour aller plus loin : la spec, `ExtractedText`, `ExtractedTextBuilder`.

`docs/decisions/0025-un-plancher-de-caracteres-declare-un-document-inexploitable.md` —
depuis la **décision 2** de la spec :

- Contexte : le troisième scénario exige un échec explicite ; reste à définir « sans texte ».
- Options : plancher de 50 caractères · `isBlank()` seul · ratio caractères/pages.
- Décision : **le plancher**, parce qu'un scan rend des bribes, pas rien.
- Conséquences — Mal : un document légitime de moins de 50 caractères est refusé ; le seuil
  est un réglage, pas une vérité.
- Condition de réouverture : un vrai document refusé à tort. Le remède sera de baisser le
  seuil, pas de le supprimer.
- Vérification : `ExtractedTextTest.refuse_un_document_sous_le_plancher_de_caracteres`.

- [ ] **Étape 10 : Mettre `CLAUDE.md` à jour**

Deux endroits, dans le **même commit** que les ADR :

1. Table « Décisions d'architecture », après la ligne 0023 :

```markdown
| [0024](docs/decisions/0024-le-texte-extrait-est-une-suite-plate-de-blocs-titres.md) | Le texte extrait est une suite plate de blocs titrés |
| [0025](docs/decisions/0025-un-plancher-de-caracteres-declare-un-document-inexploitable.md) | Un plancher de caractères déclare un document inexploitable |
```

2. Arborescence du contexte `knowledge`, sous `domain/` :

```
│   ├── domain/
│   │   ├── ExtractionPolicy      plancher de caractères sous lequel un document est inexploitable
│   │   ├── entity/          Document
│   │   ├── valueobject/     Checksum (SHA-256), DocumentFormat, DocumentStatus,
│   │   │                    TextBlock + ExtractedText (le format du texte extrait)
```

- [ ] **Étape 11 : Formater et committer**

```bash
make format-back
gtest test --tests "xyz.sterenn.secondbrain.knowledge.*"
git add src/main/java/xyz/sterenn/secondbrain/knowledge/domain \
        src/test/java/xyz/sterenn/secondbrain/knowledge/domain \
        docs/decisions/0024-le-texte-extrait-est-une-suite-plate-de-blocs-titres.md \
        docs/decisions/0025-un-plancher-de-caracteres-declare-un-document-inexploitable.md \
        CLAUDE.md
git commit -m "feat: le texte extrait a une forme, matérialisée dans le domaine"
```

---

## Tâche 2 : Les statuts d'issue et le motif d'échec

`PENDING` est aujourd'hui la seule valeur de `DocumentStatus`, et le commentaire de
l'énumération annonce déjà que « le ticket qui orchestrera l'ingestion ajoutera les siens ».
C'est ce ticket.

**Fichiers :**
- Modifier : `src/main/java/xyz/sterenn/secondbrain/knowledge/domain/valueobject/DocumentStatus.java`
- Modifier : `src/main/java/xyz/sterenn/secondbrain/knowledge/domain/entity/Document.java`
- Créer : `src/main/resources/db/migration/V6__add_knowledge_documents_error_message.sql`
- Tests : `src/test/java/xyz/sterenn/secondbrain/knowledge/domain/entity/DocumentTest.java` (modifié),
  `src/test/java/xyz/sterenn/secondbrain/knowledge/infrastructure/persistence/JpaDocumentRepositoryAdapterTest.java` (modifié)

**Interfaces :**
- Consomme : rien de la tâche 1.
- Produit :
  - `DocumentStatus.EXTRACTED`, `DocumentStatus.FAILED`
  - `Document.markTextExtracted()` — pose `EXTRACTED` et **efface** le motif
  - `Document.markExtractionFailed(String reason)` — pose `FAILED` et le motif, tronqué à
    `Document.MAX_ERROR_MESSAGE_LENGTH` ; lève `IllegalArgumentException` sur un motif vide
  - `Document.getErrorMessage() → String` (`null` tant qu'aucun échec)
  - `Document.MAX_ERROR_MESSAGE_LENGTH = 500`

- [ ] **Étape 1 : Écrire les tests de transition sur `Document`**

À ajouter à `DocumentTest`, sans toucher aux tests existants :

```java
    @Test
    void un_document_extrait_porte_le_statut_extracted_et_aucun_motif() {
        Document document = unDocumentDepose();

        document.markTextExtracted();

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.EXTRACTED);
        assertThat(document.getErrorMessage()).isNull();
    }

    @Test
    void un_document_en_echec_porte_le_statut_failed_et_son_motif() {
        Document document = unDocumentDepose();

        document.markExtractionFailed("Ce document ne contient pas de texte exploitable.");

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.FAILED);
        assertThat(document.getErrorMessage()).isEqualTo("Ce document ne contient pas de texte exploitable.");
    }

    @Test
    void une_extraction_reussie_efface_le_motif_de_l_echec_precedent() {
        Document document = unDocumentDepose();
        document.markExtractionFailed("Un premier échec.");

        document.markTextExtracted();

        assertThat(document.getErrorMessage()).isNull();
    }

    @Test
    void refuse_un_echec_sans_motif() {
        Document document = unDocumentDepose();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> document.markExtractionFailed("   "))
                .withMessageContaining("motif");
    }

    @Test
    void tronque_un_motif_trop_long_pour_sa_colonne() {
        Document document = unDocumentDepose();

        document.markExtractionFailed("M".repeat(Document.MAX_ERROR_MESSAGE_LENGTH + 42));

        assertThat(document.getErrorMessage()).hasSize(Document.MAX_ERROR_MESSAGE_LENGTH);
    }
```

Ajouter la fabrique privée si `DocumentTest` n'en a pas déjà une équivalente — la réutiliser
si elle existe, sous son nom existant :

```java
    private static Document unDocumentDepose() {
        return Document.upload(
                UUID.randomUUID(), "notes.md", DocumentFormat.MARKDOWN, Checksum.of("contenu".getBytes(UTF_8)), 7L);
    }
```

Imports à compléter : `static org.assertj.core.api.Assertions.assertThatIllegalArgumentException`,
`static java.nio.charset.StandardCharsets.UTF_8`.

- [ ] **Étape 2 : Lancer le test, vérifier qu'il échoue**

```bash
gtest test --tests "xyz.sterenn.secondbrain.knowledge.domain.entity.DocumentTest"
```

Attendu : ÉCHEC de compilation, `cannot find symbol: method markTextExtracted()`.

- [ ] **Étape 3 : Ajouter les deux constantes de statut**

Remplacer le corps de `DocumentStatus` (la Javadoc de l'énumération annonce l'ajout ; elle
doit être réécrite, pas conservée) :

```java
package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

/**
 * Étape d'un document dans la chaîne d'ingestion.
 *
 * <p>Trois valeurs, et c'est tout ce que ce ticket peut honnêtement porter : le texte est
 * extrait, ou il ne l'est pas. RAG-6 ajoutera ce qui suit la vectorisation — probablement
 * un {@code READY} après {@code EXTRACTED}. Ne pas le déclarer d'avance : un état que
 * personne n'atteint fait croire à un cycle de vie qui n'existe pas.
 *
 * <p>{@code FAILED} n'est pas un état terminal. Une réextraction (RAG-7) en repart, et
 * {@code markTextExtracted} efface alors le motif de l'échec précédent.
 */
public enum DocumentStatus {

    /** Déposé, son fichier d'origine conservé, en attente de traitement. */
    PENDING,

    /** Son texte a été extrait et rangé dans un {@code DocumentText}. */
    EXTRACTED,

    /** Le traitement a échoué ; le motif est lisible sur le document. */
    FAILED
}
```

- [ ] **Étape 4 : Ajouter le motif et les deux transitions à `Document`**

Dans `Document`, à la suite de `MAX_FILENAME_LENGTH` :

```java
    /**
     * De quoi porter le plus long des messages de refus métier, avec de la marge. Ce qui
     * dépasse est tronqué : un motif est une explication, pas une trace d'exécution.
     */
    public static final int MAX_ERROR_MESSAGE_LENGTH = 500;
```

Après le champ `status` :

```java
    // Nullable, à l'inverse de tout le reste de cette entité : un document qui n'a pas
    // échoué n'a pas de motif, et une chaîne vide voudrait dire « échoué sans raison ».
    @Column(name = "error_message", length = MAX_ERROR_MESSAGE_LENGTH)
    private String errorMessage;
```

Après la fabrique `upload`, avant les accesseurs :

```java
    /**
     * Le texte de ce document a été extrait et rangé.
     *
     * <p>Efface le motif d'un échec précédent : un document réextrait avec succès ne doit
     * pas garder l'explication de ce qui a raté la fois d'avant.
     *
     * <p>Aucun garde sur l'état de départ, volontairement. RAG-7 réextraira depuis
     * {@code EXTRACTED} comme depuis {@code FAILED} ; un garde posé aujourd'hui serait à
     * retirer demain.
     */
    public void markTextExtracted() {
        this.status = DocumentStatus.EXTRACTED;
        this.errorMessage = null;
    }

    /**
     * Le traitement de ce document a échoué, pour la raison donnée.
     *
     * <p>Le motif est <strong>affichable tel quel</strong> : c'est l'appelant qui garantit
     * qu'il ne transporte pas une trace technique — voir {@code KnowledgeEventListener} et
     * ADR-0028. Ici, on garantit seulement qu'il existe et qu'il tient dans sa colonne.
     *
     * @throws IllegalArgumentException si le motif est absent ou vide — un échec sans motif
     *     n'apprend rien de plus qu'un document resté en attente
     */
    public void markExtractionFailed(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Un échec sans motif n'apprend rien : le motif est obligatoire");
        }
        String motif = reason.strip();
        this.status = DocumentStatus.FAILED;
        this.errorMessage =
                motif.length() > MAX_ERROR_MESSAGE_LENGTH ? motif.substring(0, MAX_ERROR_MESSAGE_LENGTH) : motif;
    }
```

Et l'accesseur, à côté de `getStatus()` :

```java
    /** {@code null} tant qu'aucun traitement n'a échoué. */
    public String getErrorMessage() {
        return errorMessage;
    }
```

- [ ] **Étape 5 : Lancer le test unitaire, vérifier qu'il passe**

```bash
gtest test --tests "xyz.sterenn.secondbrain.knowledge.domain.entity.DocumentTest"
```

Attendu : SUCCÈS.

- [ ] **Étape 6 : Écrire la migration V6**

`src/main/resources/db/migration/V6__add_knowledge_documents_error_message.sql` :

```sql
-- Le motif d'un traitement qui a échoué, affichable tel quel à l'utilisateur.
--
-- Nullable, à l'inverse de tout le reste de la table : un document qui n'a pas échoué n'a
-- pas de motif, et une chaîne vide voudrait dire « échoué sans raison ». `status` seul dit
-- qu'il y a eu échec ; cette colonne dit lequel.
--
-- 500 caractères : de quoi porter le plus long des messages de refus métier avec de la
-- marge. Ce qui dépasse est tronqué côté entité — un motif est une explication, pas une
-- trace d'exécution.

ALTER TABLE knowledge_documents
    ADD COLUMN error_message VARCHAR(500);
```

- [ ] **Étape 7 : Écrire le test d'intégration de la persistance du statut**

À ajouter à `JpaDocumentRepositoryAdapterTest`, en respectant le style de la classe (elle est
`@SpringBootTest` + `@Import(TestcontainersConfiguration.class)` et injecte le **port**) :

```java
    @Test
    void conserve_le_statut_d_echec_et_son_motif() {
        Document document = documentRepository.save(unDocumentDepose());

        document.markExtractionFailed("Ce document ne contient pas de texte exploitable.");
        documentRepository.save(document);

        assertThat(documentRepository.findByIdAndOwnerId(document.getId(), document.getOwnerId()))
                .get()
                .satisfies(relu -> {
                    assertThat(relu.getStatus()).isEqualTo(DocumentStatus.FAILED);
                    assertThat(relu.getErrorMessage()).isEqualTo("Ce document ne contient pas de texte exploitable.");
                });
    }
```

Réutiliser la fabrique de document déjà présente dans la classe plutôt que d'en ajouter une.

- [ ] **Étape 8 : Lancer le test d'intégration, vérifier qu'il passe**

```bash
docker compose down
gtest test --tests "xyz.sterenn.secondbrain.knowledge.infrastructure.persistence.JpaDocumentRepositoryAdapterTest"
```

Attendu : SUCCÈS. Le démarrage vaut vérification de la migration : `ddl-auto: validate`
compare `error_message` à l'entité, et un `length` qui ne correspondrait pas ferait échouer
le contexte sur `Schema-validation: wrong column type`.

- [ ] **Étape 9 : Formater et committer**

```bash
make format-back
gtest test --tests "xyz.sterenn.secondbrain.knowledge.*"
git add src/main/java/xyz/sterenn/secondbrain/knowledge/domain \
        src/main/resources/db/migration/V6__add_knowledge_documents_error_message.sql \
        src/test/java/xyz/sterenn/secondbrain/knowledge
git commit -m "feat: un document sait dire que son extraction a réussi ou pourquoi elle a échoué"
```

---

## Tâche 3 : La persistance du texte extrait

Le format de la tâche 1 atterrit en base. Deux tables, un agrégat à part, une cascade.

**Fichiers :**
- Créer : `src/main/java/xyz/sterenn/secondbrain/knowledge/domain/entity/DocumentText.java`
- Créer : `src/main/java/xyz/sterenn/secondbrain/knowledge/domain/port/DocumentTextRepository.java`
- Créer : `src/main/java/xyz/sterenn/secondbrain/knowledge/infrastructure/persistence/SpringDataDocumentTextRepository.java`
- Créer : `src/main/java/xyz/sterenn/secondbrain/knowledge/infrastructure/persistence/JpaDocumentTextRepositoryAdapter.java`
- Créer : `src/main/resources/db/migration/V7__create_knowledge_document_texts.sql`
- Modifier : `CLAUDE.md` (arborescence + section « Persistance »)
- Test : `src/test/java/xyz/sterenn/secondbrain/knowledge/infrastructure/persistence/JpaDocumentTextRepositoryAdapterTest.java`

**Interfaces :**
- Consomme : `ExtractedText`, `TextBlock` (tâche 1).
- Produit :
  - `DocumentText.of(UUID documentId, ExtractedText text, Instant extractedAt) → DocumentText`
  - `DocumentText.getId() → UUID`, `getDocumentId() → UUID`, `getExtractedAt() → Instant`,
    `getBlocks() → List<TextBlock>`, `text() → ExtractedText`
  - `DocumentTextRepository.save(DocumentText) → DocumentText`
  - `DocumentTextRepository.findByDocumentId(UUID) → Optional<DocumentText>`
  - `DocumentTextRepository.deleteByDocumentId(UUID) → void`

- [ ] **Étape 1 : Écrire la migration V7**

`src/main/resources/db/migration/V7__create_knowledge_document_texts.sql` :

```sql
-- Le texte extrait d'un document, dans la forme commune aux quatre formats acceptés
-- (voir ADR-0024) : une suite ordonnée de blocs, chacun rattaché au titre de sa section.
--
-- DEUX TABLES ET NON UNE COLONNE JSONB. Le format est versionné par Flyway comme le reste
-- du schéma, la base sait lire ce qu'elle stocke, et RAG-5 pourra référencer un bloc.
--
-- UN AGRÉGAT À PART DE `knowledge_documents`, et non des colonnes de plus sur lui : le
-- texte naît plus tard que le document, et il est remplacé en entier à chaque réextraction.
-- `document_id` est UNIQUE : un document a un texte, jamais deux. C'est cette contrainte qui
-- impose au handler d'effacer avant d'écrire, une redélivrance AMQP étant toujours possible.
--
-- LES DEUX CASCADES SONT LE `ON DELETE CASCADE` promis par `DeleteDocumentHandler`, dont la
-- Javadoc annonce depuis RAG-3 que « le ticket qui la créera posera un ON DELETE CASCADE, et
-- cette méthode n'aura pas à changer ». Elle ne change pas.

CREATE TABLE knowledge_document_texts (
    id           UUID                     NOT NULL DEFAULT gen_random_uuid(),
    document_id  UUID                     NOT NULL,
    extracted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_knowledge_document_texts PRIMARY KEY (id),
    CONSTRAINT uq_knowledge_document_texts_document UNIQUE (document_id),
    CONSTRAINT fk_knowledge_document_texts_document FOREIGN KEY (document_id)
        REFERENCES knowledge_documents (id) ON DELETE CASCADE
);

-- Table d'une @ElementCollection : pas de clé technique, la position dans la liste fait
-- partie de l'identité. `text` est un nom de colonne valide en PostgreSQL — TEXT y est un
-- mot-clé non réservé —, et `block_position` évite d'avoir à se poser la question pour
-- `position`, qui en est un aussi mais que Hibernate écrirait sans guillemets.
CREATE TABLE knowledge_document_blocks (
    document_text_id UUID         NOT NULL,
    block_position   INTEGER      NOT NULL,
    heading          VARCHAR(255) NOT NULL,
    heading_level    INTEGER      NOT NULL,
    text             TEXT         NOT NULL,
    CONSTRAINT pk_knowledge_document_blocks PRIMARY KEY (document_text_id, block_position),
    CONSTRAINT fk_knowledge_document_blocks_text FOREIGN KEY (document_text_id)
        REFERENCES knowledge_document_texts (id) ON DELETE CASCADE
);
```

- [ ] **Étape 2 : Écrire le test d'intégration du port**

`src/test/java/xyz/sterenn/secondbrain/knowledge/infrastructure/persistence/JpaDocumentTextRepositoryAdapterTest.java`.
Calquer les annotations et le montage du compte propriétaire sur
`JpaDocumentRepositoryAdapterTest`, qui existe déjà — un document a besoin d'un
propriétaire, la clé étrangère traverse les deux contextes.

```java
package xyz.sterenn.secondbrain.knowledge.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DocumentText;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentTextRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Checksum;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ExtractedText;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.TextBlock;

/**
 * On teste le <strong>port</strong>, jamais l'adapter : c'est le contrat du domaine qui doit
 * tenir. Le montage du propriétaire est celui de {@code JpaDocumentRepositoryAdapterTest} —
 * la clé étrangère de {@code knowledge_documents} traverse les deux contextes bornés.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class JpaDocumentTextRepositoryAdapterTest {

    private static final String CORPS =
            "Un texte assez long pour franchir le plancher des cinquante caractères exigé par le domaine.";

    @Autowired
    private DocumentTextRepository documentTextRepository;

    @Autowired
    private DocumentRepository documentRepository;

    // Reprendre ici la même dépendance et la même méthode de création de compte que
    // JpaDocumentRepositoryAdapterTest : ne pas en inventer une seconde.

    @Test
    void conserve_les_blocs_dans_l_ordre_avec_leur_titre_et_leur_niveau() {
        Document document = unDocumentDepose();
        ExtractedText texte = new ExtractedText(List.of(
                TextBlock.of("Introduction", 1, CORPS),
                TextBlock.of("Détail", 2, CORPS),
                TextBlock.untitled(CORPS)));

        documentTextRepository.save(DocumentText.of(document.getId(), texte, Instant.parse("2026-08-26T10:00:00Z")));

        assertThat(documentTextRepository.findByDocumentId(document.getId()))
                .get()
                .satisfies(relu -> {
                    assertThat(relu.getBlocks()).containsExactlyElementsOf(texte.blocks());
                    assertThat(relu.getBlocks())
                            .extracting(TextBlock::getHeading)
                            .containsExactly("Introduction", "Détail", "");
                    assertThat(relu.getBlocks()).extracting(TextBlock::getHeadingLevel).containsExactly(1, 2, 0);
                    assertThat(relu.getExtractedAt()).isEqualTo(Instant.parse("2026-08-26T10:00:00Z"));
                });
    }

    @Test
    void rend_le_format_du_domaine_tel_qu_il_a_ete_range() {
        Document document = unDocumentDepose();
        ExtractedText texte = ExtractedText.untitled(CORPS);
        documentTextRepository.save(DocumentText.of(document.getId(), texte, Instant.now()));

        DocumentText relu = documentTextRepository.findByDocumentId(document.getId()).orElseThrow();

        assertThat(relu.text()).isEqualTo(texte);
    }

    @Test
    void conserve_un_bloc_bien_plus_long_que_255_caracteres() {
        Document document = unDocumentDepose();
        String tresLong = CORPS.repeat(200);
        documentTextRepository.save(
                DocumentText.of(document.getId(), ExtractedText.untitled(tresLong), Instant.now()));

        assertThat(documentTextRepository.findByDocumentId(document.getId()))
                .get()
                .satisfies(relu -> assertThat(relu.getBlocks().getFirst().getText()).isEqualTo(tresLong));
    }

    @Test
    void efface_le_texte_d_un_document_et_ses_blocs_avec() {
        Document document = unDocumentDepose();
        documentTextRepository.save(DocumentText.of(document.getId(), ExtractedText.untitled(CORPS), Instant.now()));

        documentTextRepository.deleteByDocumentId(document.getId());

        assertThat(documentTextRepository.findByDocumentId(document.getId())).isEmpty();
    }

    @Test
    void reste_muet_quand_aucun_texte_n_a_ete_extrait() {
        assertThat(documentTextRepository.findByDocumentId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void un_second_texte_peut_remplacer_le_premier_apres_effacement() {
        Document document = unDocumentDepose();
        documentTextRepository.save(DocumentText.of(document.getId(), ExtractedText.untitled(CORPS), Instant.now()));

        documentTextRepository.deleteByDocumentId(document.getId());
        documentTextRepository.save(DocumentText.of(
                document.getId(), ExtractedText.untitled(CORPS + " Deuxième version."), Instant.now()));

        assertThat(documentTextRepository.findByDocumentId(document.getId()))
                .get()
                .satisfies(relu -> assertThat(relu.getBlocks().getFirst().getText()).endsWith("Deuxième version."));
    }

    private Document unDocumentDepose() {
        return documentRepository.save(Document.upload(
                unCompteVerifie(),
                "notes-" + UUID.randomUUID() + ".md",
                DocumentFormat.MARKDOWN,
                Checksum.of(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8)),
                42L));
    }

    // unCompteVerifie() : recopier la méthode de JpaDocumentRepositoryAdapterTest,
    // ou l'extraire dans KnowledgeFixture si les deux classes s'en servent à l'identique.
}
```

**Note d'exécution :** la cascade `ON DELETE CASCADE` depuis `knowledge_documents` n'est pas
testée ici, et c'est délibéré — dans un test `@Transactional`, Hibernate garde le
`DocumentText` dans son cache de premier niveau et le rendrait après un `delete` du document
sans jamais interroger la base. Cette cascade se vérifie à la tâche 8, dans un test non
transactionnel.

- [ ] **Étape 3 : Lancer le test, vérifier qu'il échoue**

```bash
docker compose down
gtest test --tests "…JpaDocumentTextRepositoryAdapterTest"
```

Attendu : ÉCHEC de compilation, `cannot find symbol: class DocumentText`.

- [ ] **Étape 4 : Écrire l'entité `DocumentText`**

`src/main/java/xyz/sterenn/secondbrain/knowledge/domain/entity/DocumentText.java` :

```java
package xyz.sterenn.secondbrain.knowledge.domain.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ExtractedText;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.TextBlock;

/**
 * Le texte extrait d'un document, dans la forme commune aux quatre formats acceptés.
 *
 * <p><strong>Agrégat distinct de {@link Document}</strong>, et non des colonnes de plus sur
 * lui : il naît plus tard, et il est remplacé en entier à chaque réextraction. Les deux se
 * référencent donc par identifiant, jamais par {@code @ManyToOne} — ADR-0006.
 *
 * <p>Les blocs sont une {@code @ElementCollection} et non des entités : un bloc n'a pas
 * d'identité propre, il n'existe que par le texte qui le contient, et rien ne le désigne de
 * l'extérieur. Sa position est portée par {@code @OrderColumn} plutôt que par un champ de
 * {@link TextBlock} : elle appartient à la liste, pas au bloc — un bloc extrait de son
 * document reste le même bloc.
 *
 * <p>{@code EAGER}, à contre-courant de l'habitude : {@code open-in-view} est à {@code false}
 * et personne ne charge un {@code DocumentText} sans vouloir ses blocs. Une collection
 * paresseuse ne ferait que déplacer l'échec hors de la transaction du bus.
 */
@Entity
@Table(name = "knowledge_document_texts")
public class DocumentText {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    // unique = true : un document a un texte, jamais deux. C'est cette contrainte qui impose
    // au handler d'effacer avant d'écrire — une redélivrance AMQP est toujours possible.
    @Column(name = "document_id", nullable = false, unique = true, columnDefinition = "uuid")
    private UUID documentId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "knowledge_document_blocks",
            joinColumns = @JoinColumn(name = "document_text_id", nullable = false))
    @OrderColumn(name = "block_position")
    private List<TextBlock> blocks = new ArrayList<>();

    @Column(name = "extracted_at", nullable = false)
    private Instant extractedAt;

    protected DocumentText() {
        // requis par JPA
    }

    private DocumentText(UUID documentId, List<TextBlock> blocks, Instant extractedAt) {
        this.documentId = documentId;
        this.blocks = blocks;
        this.extractedAt = extractedAt;
    }

    /**
     * Range un texte fraîchement extrait sous l'identifiant de son document.
     *
     * <p>{@link ExtractedText} garantit déjà qu'il n'est ni vide ni sous le plancher : il n'y
     * a rien à revalider ici, seulement à recopier dans une liste que JPA peut gérer.
     */
    public static DocumentText of(UUID documentId, ExtractedText text, Instant extractedAt) {
        Objects.requireNonNull(documentId, "Le document dont ce texte est extrait est obligatoire");
        Objects.requireNonNull(text, "Le texte extrait est obligatoire");
        Objects.requireNonNull(extractedAt, "L'instant de l'extraction est obligatoire");
        return new DocumentText(documentId, new ArrayList<>(text.blocks()), extractedAt);
    }

    /** Le format du domaine, tel que RAG-5 le consommera. */
    public ExtractedText text() {
        return new ExtractedText(blocks);
    }

    public UUID getId() {
        return id;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    /** Copie : la liste interne est gérée par Hibernate, personne d'autre n'y touche. */
    public List<TextBlock> getBlocks() {
        return List.copyOf(blocks);
    }

    public Instant getExtractedAt() {
        return extractedAt;
    }
}
```

- [ ] **Étape 5 : Écrire le port et son adapter**

`.../domain/port/DocumentTextRepository.java` :

```java
package xyz.sterenn.secondbrain.knowledge.domain.port;

import java.util.Optional;
import java.util.UUID;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DocumentText;

/**
 * Port sortant vers le stockage du texte extrait.
 *
 * <p>Aucune méthode ne porte le propriétaire, à l'inverse de {@code DocumentRepository} : un
 * texte se lit toujours par l'identifiant de son document, lequel a déjà été chargé par
 * {@code findByIdAndOwnerId}. Le cloisonnement est fait en amont, il n'a pas à l'être deux
 * fois.
 */
public interface DocumentTextRepository {

    DocumentText save(DocumentText documentText);

    Optional<DocumentText> findByDocumentId(UUID documentId);

    /**
     * Efface le texte d'un document, ses blocs avec. Silencieux s'il n'y en a pas.
     *
     * <p>Existe dès ce ticket pour une raison précise : AMQP livre <em>au moins</em> une
     * fois, et {@code document_id} est {@code UNIQUE}. Sans effacement préalable, une
     * redélivrance de {@code DocumentUploaded} ferait échouer l'écriture sur la contrainte,
     * et le document passerait en {@code FAILED} pour un traitement qui avait réussi.
     */
    void deleteByDocumentId(UUID documentId);
}
```

`.../infrastructure/persistence/SpringDataDocumentTextRepository.java` :

```java
package xyz.sterenn.secondbrain.knowledge.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DocumentText;

/**
 * Détail Spring Data, volontairement package-private : rien hors de ce package ne doit en
 * dépendre.
 */
interface SpringDataDocumentTextRepository extends JpaRepository<DocumentText, UUID> {

    Optional<DocumentText> findByDocumentId(UUID documentId);

    /**
     * Suppression dérivée, et non un {@code @Modifying @Query} : elle charge l'entité avant
     * de la retirer, ce qui laisse Hibernate effacer aussi les lignes de la collection.
     * Une requête de suppression en masse court-circuiterait la collection et laisserait des
     * blocs orphelins — la clé étrangère les rattraperait, mais en levant une erreur.
     */
    void deleteByDocumentId(UUID documentId);
}
```

`.../infrastructure/persistence/JpaDocumentTextRepositoryAdapter.java` :

```java
package xyz.sterenn.secondbrain.knowledge.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DocumentText;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentTextRepository;

/** Adapter du port {@link DocumentTextRepository}. */
@Component
public class JpaDocumentTextRepositoryAdapter implements DocumentTextRepository {

    private final SpringDataDocumentTextRepository springDataDocumentTextRepository;

    JpaDocumentTextRepositoryAdapter(SpringDataDocumentTextRepository springDataDocumentTextRepository) {
        this.springDataDocumentTextRepository = springDataDocumentTextRepository;
    }

    @Override
    public DocumentText save(DocumentText documentText) {
        return springDataDocumentTextRepository.saveAndFlush(documentText);
    }

    @Override
    public Optional<DocumentText> findByDocumentId(UUID documentId) {
        return springDataDocumentTextRepository.findByDocumentId(documentId);
    }

    /**
     * Le flush n'est pas décoratif : le handler efface puis écrit dans la même transaction,
     * et {@code document_id} est {@code UNIQUE}. Sans lui, Hibernate ordonnerait l'insertion
     * avant la suppression au moment du vidage, et la contrainte se refermerait sur une ligne
     * que l'on venait justement de retirer.
     */
    @Override
    public void deleteByDocumentId(UUID documentId) {
        springDataDocumentTextRepository.deleteByDocumentId(documentId);
        springDataDocumentTextRepository.flush();
    }
}
```

- [ ] **Étape 6 : Lancer le test, vérifier qu'il passe**

```bash
gtest test --tests "…JpaDocumentTextRepositoryAdapterTest"
```

Attendu : SUCCÈS, 6 tests.

**Si le contexte refuse de démarrer sur `Schema-validation: wrong column type ... text`** :
c'est le seul point de ce plan où le mapping peut devoir changer. `@Column(columnDefinition
= "text")` sur `TextBlock.text` est ce qui fait attendre `text` à Hibernate plutôt qu'un
`varchar(255)`. Corriger l'annotation ou la migration — **jamais `ddl-auto`**.

- [ ] **Étape 7 : Mettre `CLAUDE.md` à jour**

Arborescence du contexte `knowledge` :

```
│   │   ├── entity/          Document, DocumentText (le texte extrait, agrégat à part)
…
│   │   ├── port/            DocumentRepository, DocumentStorage, DocumentTextRepository
```

Section « Persistance », après le paragraphe sur `ChecksumAttributeConverter` :

```markdown
Le texte extrait d'un document vit dans **deux tables**, `knowledge_document_texts` (une
ligne par document, `document_id` `UNIQUE`) et `knowledge_document_blocks` (ses blocs, une
`@ElementCollection` ordonnée par `block_position`). Les deux cascadent à la suppression du
document — c'est le `ON DELETE CASCADE` que `DeleteDocumentHandler` annonçait, et il n'a
rien changé à ce handler. Le format lui-même est décrit par ADR-0024.
```

- [ ] **Étape 8 : Formater et committer**

```bash
make format-back
gtest test --tests "xyz.sterenn.secondbrain.knowledge.*"
git add src/main/java src/main/resources/db/migration/V7__create_knowledge_document_texts.sql \
        src/test/java CLAUDE.md
git commit -m "feat: le texte extrait se range dans un agrégat à part, blocs ordonnés"
```

---

## Tâche 4 : Le port d'extraction, et les deux formats textuels

Le port et ses deux premiers adapters, les plus simples. C'est ici que se pose le motif que
les tâches 5 et 6 reprendront à l'identique.

**Fichiers :**
- Créer : `src/main/java/xyz/sterenn/secondbrain/knowledge/domain/port/DocumentTextExtractor.java`
- Créer : `src/main/java/xyz/sterenn/secondbrain/knowledge/infrastructure/extraction/Section.java`
- Créer : `src/main/java/xyz/sterenn/secondbrain/knowledge/infrastructure/extraction/TextDecoding.java`
- Créer : `src/main/java/xyz/sterenn/secondbrain/knowledge/infrastructure/extraction/PlainTextExtractor.java`
- Créer : `src/main/java/xyz/sterenn/secondbrain/knowledge/infrastructure/extraction/CommonmarkTextExtractor.java`
- Créer : `src/test/resources/fixtures/brut.txt`, `structure.md`, `sans-titres.md`
- Créer : `src/test/java/xyz/sterenn/secondbrain/knowledge/Fixtures.java`
- Modifier : `gradle/libs.versions.toml`, `build.gradle.kts`
- Créer : `docs/decisions/0026-un-extracteur-par-format-plutot-qu-apache-tika.md`
- Modifier : `CLAUDE.md` (index des ADR, arborescence, stack)
- Tests : `.../extraction/PlainTextExtractorTest.java`, `.../extraction/CommonmarkTextExtractorTest.java`

**Interfaces :**
- Consomme : `ExtractedText`, `ExtractedTextBuilder`, `UnreadableDocumentException` (tâche 1),
  `DocumentFormat` (existant).
- Produit :
  - `DocumentTextExtractor.format() → DocumentFormat`
  - `DocumentTextExtractor.extract(byte[] content) → ExtractedText`
  - `record Section(String heading, int level, String body)` — package-private ;
    `Section.assemble(List<Section>) → ExtractedText`
  - `static String TextDecoding.decode(byte[])` — package-private
  - `Fixtures.lire(String nom) → byte[]` et ses constantes de noms (`BRUT_TXT`,
    `STRUCTURE_MD`, `SANS_TITRES_MD`, `TITRES_DOCX`, `SIGNETS_PDF`, `SANS_SIGNETS_PDF`,
    `NUMERISE_PDF`) — **public**, dans `xyz.sterenn.secondbrain.knowledge`, parce que les
    tâches 7 et 8 s'en servent depuis d'autres packages

- [ ] **Étape 1 : Déclarer la dépendance commonmark**

Dans `gradle/libs.versions.toml`, sous `[versions]` :

```toml
# Extraction du texte : aucune de ces trois versions n'est portée par le BOM Spring Boot.
commonmark = "0.24.0"
```

Sous `[libraries]` :

```toml
# Markdown : le parseur de référence CommonMark en Java. Il donne les titres comme des
# nœuds `Heading` avec leur niveau, là où une expression régulière sur `^#` prendrait pour
# un titre le `#` d'un commentaire dans un bloc de code.
commonmark = { module = "org.commonmark:commonmark", version.ref = "commonmark" }
```

Dans `build.gradle.kts`, après le bloc « Documentation API » :

```kotlin
    // Extraction du texte des documents. Un extracteur par format plutôt qu'Apache Tika,
    // dont l'XHTML unifié aplatit précisément la sémantique qu'on cherche à garder
    // (ADR-0026). Aucune de ces versions n'est couverte par le BOM Spring Boot.
    implementation(libs.commonmark)
```

- [ ] **Étape 2 : Écrire les fixtures textuelles**

`src/test/resources/fixtures/brut.txt` — un texte sans structure, plus de 50 caractères,
avec des accents et des paragraphes :

```
Notes prises pendant la réunion du 12 mars.

Rien de tout ceci n'est structuré : pas de titre, pas de numérotation, juste
des paragraphes qui se suivent. C'est le cas le plus fréquent d'un fichier
déposé à la volée depuis un bloc-notes.

Le second paragraphe existe pour vérifier que la frontière entre paragraphes
survit à la normalisation, parce que c'est elle que le découpage cherchera.
```

`src/test/resources/fixtures/structure.md` :

```markdown
# Journal de bord

Ce document porte trois niveaux de titres, ce qui suffit à vérifier que chacun
retrouve son niveau et son corps.

## Première section

Le corps de la première section, assez long pour compter dans le plancher de
caractères que le domaine impose à tout document.

### Un détail de la première section

Un troisième niveau, pour que la borne haute ne soit pas seulement théorique.

## Seconde section

Le corps de la seconde section. Il contient un bloc de code, dont le dièse ne
doit surtout pas être pris pour un titre :

```
# ceci est un commentaire shell, pas une section
echo bonjour
```

Et une dernière phrase après le bloc de code.
```

**Attention en écrivant ce fichier :** il contient une clôture de bloc de code imbriquée.
L'écrire avec un `cat > fichier <<'EOF'` dont le marqueur n'est pas ` ``` `, ou avec
l'éditeur, mais surtout vérifier après coup que les trois lignes de ` ``` ` sont bien
présentes — c'est ce que le test sur le bloc de code vérifie.

`src/test/resources/fixtures/sans-titres.md` :

```markdown
Un fichier Markdown peut parfaitement n'avoir aucun titre. Celui-ci n'en a pas,
et l'extraction doit donc en rendre un unique bloc, sans titre et de niveau nul.

Il porte tout de même deux paragraphes, et un peu d'*emphase* ainsi qu'un
`bout de code en ligne`, dont le balisage ne doit pas se retrouver dans le
texte extrait.
```

- [ ] **Étape 3 : Écrire l'utilitaire de lecture des fixtures**

Il vit dans le package racine du contexte, pas auprès des extracteurs : les tâches 7 et 8
s'en servent depuis `…application.command` et `…infrastructure.messaging`.

`src/test/java/xyz/sterenn/secondbrain/knowledge/infrastructure/extraction/Fixtures.java` :

```java
package xyz.sterenn.secondbrain.knowledge;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * Les documents d'essai de {@code src/test/resources/fixtures/}, et de quoi les lire.
 *
 * <p>Dans le package racine du contexte et {@code public} : les tests des extracteurs s'en
 * servent, mais aussi ceux de la commande d'extraction et du worker, qui vivent ailleurs.
 * Un utilitaire de test partagé va au même endroit que {@code KnowledgeFixture}.
 *
 * <p>Les noms sont exposés en constantes pour les tests qui vivent hors de ce package — un
 * `Fixtures.lire("scan.pdf")` écrit de mémoire à l'autre bout du projet échouerait à
 * l'exécution, pas à la compilation. Les tests d'extracteurs, eux, gardent le littéral : ils
 * sont à côté du fichier, et il s'y lit mieux.
 *
 * <p>La lecture passe par le classpath et non par un chemin de fichier : c'est ce que la CI
 * voit, et un chemin relatif dépendrait du répertoire de travail de Gradle.
 */
public final class Fixtures {

    /** Texte brut, sans structure, avec accents et paragraphes. */
    public static final String BRUT_TXT = "brut.txt";

    /** Markdown à trois niveaux de titres, avec un bloc de code piégeux. */
    public static final String STRUCTURE_MD = "structure.md";

    /** Markdown sans aucun titre : un unique bloc attendu. */
    public static final String SANS_TITRES_MD = "sans-titres.md";

    /** DOCX à trois niveaux, titres portés par les styles {@code HeadingN}. Tâche 5. */
    public static final String TITRES_DOCX = "titres.docx";

    /** PDF à sommaire, plus une page de garde hors section. Tâche 6. */
    public static final String SIGNETS_PDF = "signets.pdf";

    /** PDF sans sommaire, titres reconnaissables à la seule taille de police. Tâche 6. */
    public static final String SANS_SIGNETS_PDF = "sans-signets.pdf";

    /** PDF numérisé : une image, aucune couche texte. Doit échouer. Tâche 6. */
    public static final String NUMERISE_PDF = "numerise.pdf";

    private Fixtures() {
        // classe utilitaire
    }

    public static byte[] lire(String nom) {
        try (InputStream flux = Fixtures.class.getResourceAsStream("/fixtures/" + nom)) {
            if (flux == null) {
                throw new IllegalStateException("Fixture absente : /fixtures/" + nom
                        + " — les binaires se refabriquent par `gtest generateFixtures`");
            }
            return flux.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
```

- [ ] **Étape 4 : Écrire les tests des deux extracteurs**

`.../extraction/PlainTextExtractorTest.java` :

```java
package xyz.sterenn.secondbrain.knowledge.infrastructure.extraction;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.knowledge.Fixtures;
import xyz.sterenn.secondbrain.knowledge.domain.exception.UnextractableDocumentException;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ExtractedText;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.TextBlock;

/** Aucun Spring : un extracteur est un adapter, mais il n'a besoin d'aucun contexte. */
class PlainTextExtractorTest {

    private final PlainTextExtractor extracteur = new PlainTextExtractor();

    @Test
    void sait_lire_le_format_texte() {
        assertThat(extracteur.format()).isEqualTo(DocumentFormat.TEXT);
    }

    @Test
    void rend_un_unique_bloc_sans_titre() {
        ExtractedText texte = extracteur.extract(Fixtures.lire("brut.txt"));

        assertThat(texte.blocks()).singleElement().satisfies(bloc -> {
            assertThat(bloc.getHeading()).isEmpty();
            assertThat(bloc.getHeadingLevel()).isZero();
            assertThat(bloc.getText()).contains("Notes prises pendant la réunion");
        });
    }

    @Test
    void conserve_la_frontiere_entre_les_paragraphes() {
        assertThat(extracteur.extract(Fixtures.lire("brut.txt")).blocks())
                .first()
                .extracting(TextBlock::getText, org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .contains("\n\n");
    }

    @Test
    void lit_un_fichier_encode_en_iso_8859_1_plutot_que_d_echouer() {
        byte[] latin1 = "Une réunion très intéressante, tenue à Bruxelles en février.".getBytes(ISO_8859_1);

        assertThat(extracteur.extract(latin1).blocks().getFirst().getText()).contains("très intéressante");
    }

    @Test
    void refuse_un_fichier_qui_ne_dit_rien() {
        assertThatExceptionOfType(UnextractableDocumentException.class)
                .isThrownBy(() -> extracteur.extract("   \n\n  ".getBytes(UTF_8)));
    }
}
```

`.../extraction/CommonmarkTextExtractorTest.java` :

```java
package xyz.sterenn.secondbrain.knowledge.infrastructure.extraction;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.knowledge.Fixtures;
import xyz.sterenn.secondbrain.knowledge.domain.exception.UnextractableDocumentException;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ExtractedText;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.TextBlock;

class CommonmarkTextExtractorTest {

    private final CommonmarkTextExtractor extracteur = new CommonmarkTextExtractor();

    @Test
    void sait_lire_le_format_markdown() {
        assertThat(extracteur.format()).isEqualTo(DocumentFormat.MARKDOWN);
    }

    @Test
    void rattache_chaque_bloc_au_titre_de_sa_section() {
        ExtractedText texte = extracteur.extract(Fixtures.lire("structure.md"));

        assertThat(texte.blocks())
                .extracting(TextBlock::getHeading)
                .containsExactly(
                        "Journal de bord",
                        "Première section",
                        "Un détail de la première section",
                        "Seconde section");
    }

    @Test
    void rend_le_niveau_de_chaque_titre() {
        assertThat(extracteur.extract(Fixtures.lire("structure.md")).blocks())
                .extracting(TextBlock::getHeadingLevel)
                .containsExactly(1, 2, 3, 2);
    }

    @Test
    void ne_prend_pas_pour_un_titre_le_diese_d_un_bloc_de_code() {
        ExtractedText texte = extracteur.extract(Fixtures.lire("structure.md"));

        assertThat(texte.blocks())
                .extracting(TextBlock::getHeading)
                .doesNotContain("ceci est un commentaire shell, pas une section");
        assertThat(texte.blocks().getLast().getText()).contains("echo bonjour");
    }

    @Test
    void un_markdown_sans_titre_donne_un_unique_bloc() {
        ExtractedText texte = extracteur.extract(Fixtures.lire("sans-titres.md"));

        assertThat(texte.blocks()).singleElement().satisfies(bloc -> {
            assertThat(bloc.getHeading()).isEmpty();
            assertThat(bloc.getHeadingLevel()).isZero();
        });
    }

    @Test
    void rend_le_texte_et_non_le_balisage() {
        String extrait = extracteur.extract(Fixtures.lire("sans-titres.md")).blocks().getFirst().getText();

        assertThat(extrait).contains("emphase").doesNotContain("*emphase*").doesNotContain("`");
    }

    @Test
    void refuse_un_markdown_qui_n_a_que_des_titres() {
        assertThatExceptionOfType(UnextractableDocumentException.class)
                .isThrownBy(() -> extracteur.extract("# Un titre\n\n## Un autre\n".getBytes(UTF_8)));
    }
}
```

- [ ] **Étape 5 : Lancer les deux tests, vérifier qu'ils échouent**

```bash
gtest test --tests "xyz.sterenn.secondbrain.knowledge.infrastructure.extraction.*"
```

Attendu : ÉCHEC de compilation, `cannot find symbol: class PlainTextExtractor`.

- [ ] **Étape 6 : Écrire le port, la section brute, le décodage et les deux extracteurs**

`.../domain/port/DocumentTextExtractor.java` :

```java
package xyz.sterenn.secondbrain.knowledge.domain.port;

import xyz.sterenn.secondbrain.knowledge.domain.exception.UnextractableDocumentException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.UnreadableDocumentException;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ExtractedText;

/**
 * Port sortant vers la lecture d'un format de fichier.
 *
 * <p>Un adapter par format, pas un adapter universel : les styles {@code Heading1..9} d'un
 * DOCX et les {@code #} d'un Markdown sont des informations de premier ordre ici, et un
 * lecteur unique les aplatirait au lieu de les exploiter. Voir ADR-0026.
 *
 * <p>{@link #format()} n'est pas décoratif : c'est par lui que l'application indexe ses
 * extracteurs, et qu'elle <strong>refuse de démarrer</strong> si une constante de
 * {@link DocumentFormat} n'a pas le sien. Un format accepté au dépôt doit être lisible.
 *
 * <p>Le contenu arrive entier, en mémoire : c'est ce que le plafond de téléversement borne
 * (ADR-0021), et ce que le calcul de l'empreinte imposait déjà.
 */
public interface DocumentTextExtractor {

    /** Le format que cet extracteur sait lire, et lui seul. */
    DocumentFormat format();

    /**
     * @throws UnreadableDocumentException si le fichier ne s'ouvre pas — endommagé, ou d'un
     *     autre format que son extension ne le dit
     * @throws UnextractableDocumentException s'il s'ouvre mais n'en sort pas assez de texte
     */
    ExtractedText extract(byte[] content);
}
```

`.../infrastructure/extraction/Section.java` :

```java
package xyz.sterenn.secondbrain.knowledge.infrastructure.extraction;

import java.util.List;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ExtractedText;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ExtractedTextBuilder;

/**
 * Une section telle qu'un extracteur la lit, avant toute normalisation.
 *
 * <p>Elle existe pour une raison de structure, pas de confort : un extracteur parcourt son
 * fichier <strong>dans un {@code try}</strong> qui traduit les pannes de la bibliothèque en
 * {@code UnreadableDocumentException}. Construire un type du domaine à l'intérieur de ce
 * {@code try} y ferait passer {@code UnextractableDocumentException} — un refus métier —
 * pour une panne de lecture. Les sections brutes sortent donc du {@code try}, et
 * {@link #assemble} les convertit après.
 */
record Section(String heading, int level, String body) {

    /** Une section sans titre : avant le premier, ou dans un document qui n'en a pas. */
    static Section untitled(String body) {
        return new Section("", 0, body);
    }

    /**
     * @throws xyz.sterenn.secondbrain.knowledge.domain.exception.UnextractableDocumentException
     *     si rien n'en ressort, ou si le total reste sous le plancher
     */
    static ExtractedText assemble(List<Section> sections) {
        ExtractedTextBuilder blocs = new ExtractedTextBuilder();
        sections.forEach(section -> blocs.section(section.heading(), section.level(), section.body()));
        return blocs.build();
    }
}
```

`.../infrastructure/extraction/TextDecoding.java` :

```java
package xyz.sterenn.secondbrain.knowledge.infrastructure.extraction;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Des octets vers du texte, pour les deux formats qui n'ont pas d'en-tête pour le dire. */
final class TextDecoding {

    private TextDecoding() {
        // classe utilitaire
    }

    /**
     * UTF-8 en premier, ISO-8859-1 en repli.
     *
     * <p>Repli et non échec : un {@code .txt} en ISO-8859-1 reste un texte parfaitement
     * lisible, et c'est l'encodage de à peu près tout ce qui a été écrit sous Windows avant
     * 2010. Aucune détection de jeu de caractères au-delà : deux essais, et c'est tout — une
     * bibliothèque de détection serait une dépendance de plus pour deviner ce que le repli
     * couvre déjà.
     *
     * <p>Le décodeur UTF-8 est monté en {@code REPORT} : par défaut il remplacerait les
     * octets invalides par un {@code U+FFFD} silencieux, et le repli ne se déclencherait
     * jamais.
     */
    static String decode(byte[] content) {
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
        } catch (CharacterCodingException pasDeLUtf8) {
            return new String(content, StandardCharsets.ISO_8859_1);
        }
    }
}
```

`.../infrastructure/extraction/PlainTextExtractor.java` :

```java
package xyz.sterenn.secondbrain.knowledge.infrastructure.extraction;

import java.util.List;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentTextExtractor;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ExtractedText;

/**
 * Le {@code .txt} : aucune bibliothèque, aucun titre à chercher.
 *
 * <p>Un fichier texte ne porte pas de structure — c'est sa définition. Deviner des titres à
 * la ponctuation ou à la casse serait inventer une sémantique que le format n'a pas ; il
 * rend donc un unique bloc, et c'est le second scénario du ticket.
 */
@Component
public class PlainTextExtractor implements DocumentTextExtractor {

    @Override
    public DocumentFormat format() {
        return DocumentFormat.TEXT;
    }

    @Override
    public ExtractedText extract(byte[] content) {
        // Rien à ouvrir, donc rien à traduire en UnreadableDocumentException : un tableau
        // d'octets se décode toujours, au pire dans le mauvais jeu de caractères.
        return Section.assemble(List.of(Section.untitled(TextDecoding.decode(content))));
    }
}
```

`.../infrastructure/extraction/CommonmarkTextExtractor.java` :

```java
package xyz.sterenn.secondbrain.knowledge.infrastructure.extraction;

import java.util.ArrayList;
import java.util.List;
import org.commonmark.node.Heading;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.text.LineBreakRendering;
import org.commonmark.renderer.text.TextContentRenderer;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentTextExtractor;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ExtractedText;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.TextBlock;

/**
 * Le {@code .md} : le seul format dont les titres sont explicites et sans ambiguïté.
 *
 * <p>Par le parseur CommonMark et non par une expression régulière sur {@code ^#} : le
 * dièse d'un commentaire dans un bloc de code n'est pas un titre, et un titre souligné
 * (« setext », un texte suivi d'une ligne de {@code ===}) en est un. Une expression
 * régulière se tromperait dans les deux sens.
 *
 * <p>Seuls les nœuds de <strong>premier niveau</strong> sont parcourus : un titre à
 * l'intérieur d'une citation ou d'un élément de liste ne découpe pas le document. Il
 * ressort dans le texte de sa section, ce qui est sa place.
 *
 * <p>{@code SEPARATE_BLOCKS} conserve la double ligne entre deux blocs — c'est elle que le
 * découpage de RAG-5 cherchera —, tandis que le rendu texte laisse tomber le balisage :
 * l'emphase et les accents graves n'ont rien à faire dans un extrait envoyé à un modèle.
 */
@Component
public class CommonmarkTextExtractor implements DocumentTextExtractor {

    private final Parser parser = Parser.builder().build();

    private final TextContentRenderer renderer =
            TextContentRenderer.builder().lineBreakRendering(LineBreakRendering.SEPARATE_BLOCKS).build();

    @Override
    public DocumentFormat format() {
        return DocumentFormat.MARKDOWN;
    }

    @Override
    public ExtractedText extract(byte[] content) {
        // Aucun try : CommonMark n'a pas de document invalide. Tout ce qu'on lui donne est
        // du Markdown, au pire du Markdown qui ne dit rien — et c'est `assemble` qui refuse.
        Node document = parser.parse(TextDecoding.decode(content));

        List<Section> sections = new ArrayList<>();
        String titre = "";
        int niveau = 0;
        StringBuilder corps = new StringBuilder();

        for (Node noeud = document.getFirstChild(); noeud != null; noeud = noeud.getNext()) {
            if (noeud instanceof Heading titreMarkdown) {
                sections.add(new Section(titre, niveau, corps.toString()));
                titre = renderer.render(titreMarkdown);
                niveau = Math.min(titreMarkdown.getLevel(), TextBlock.MAX_HEADING_LEVEL);
                corps.setLength(0);
            } else {
                corps.append(renderer.render(noeud)).append("\n\n");
            }
        }
        sections.add(new Section(titre, niveau, corps.toString()));

        // La toute première section est vide dès que le document commence par un titre :
        // `assemble` l'écarte, comme toute section sans corps.
        return Section.assemble(sections);
    }
}
```

- [ ] **Étape 7 : Lancer les tests, vérifier qu'ils passent**

```bash
gtest test --tests "xyz.sterenn.secondbrain.knowledge.infrastructure.extraction.*"
```

Attendu : SUCCÈS, 12 tests.

**Si `ne_prend_pas_pour_un_titre_le_diese_d_un_bloc_de_code` échoue :** vérifier d'abord
`structure.md` — le bloc de code y a probablement perdu ses clôtures à l'écriture, et le
dièse est alors réellement un titre. Le fichier est en cause avant le code.

- [ ] **Étape 8 : Écrire l'ADR-0026**

`docs/decisions/0026-un-extracteur-par-format-plutot-qu-apache-tika.md`, depuis la
**décision 3** de la spec :

- Contexte : quatre formats à lire ; Tika est le réflexe.
- Options : un extracteur par format (PDFBox, POI, commonmark, JDK) · Apache Tika ·
  Tika pour le texte brut et des bibliothèques dédiées pour la structure.
- Décision : **un extracteur par format**, parce que la structure est le livrable, pas un
  sous-produit — et que l'XHTML unifié de Tika l'aplatit.
- Conséquences — Bien : chaque adapter exploite ce que son format sait dire, et un format
  nouveau est un adapter nouveau, sans toucher aux autres. Mal : trois dépendances au lieu
  d'une, trois cycles de mise à jour, et un cinquième format demandera du travail plutôt
  qu'une ligne de configuration.
- À citer : le refus de démarrage sur un `DocumentFormat` sans extracteur, qui est la
  contrepartie de ce choix.
- Condition de réouverture : le jour où la liste des formats acceptés dépasse la demi-douzaine,
  ou qu'un format demandé (`.epub`, `.pptx`, `.odt`) n'a pas de bibliothèque dédiée
  raisonnable. La sortie sera alors Tika **en repli** derrière les extracteurs dédiés, pas à
  leur place.
- Vérification : `ExtractDocumentTextHandler` échoue au démarrage sur un format sans
  extracteur (tâche 7) ; `gtest test --tests "…infrastructure.extraction.*"`.

- [ ] **Étape 9 : Mettre `CLAUDE.md` à jour**

Trois endroits :

1. Table des ADR :

```markdown
| [0026](docs/decisions/0026-un-extracteur-par-format-plutot-qu-apache-tika.md) | Un extracteur par format, plutôt qu'Apache Tika |
```

2. Arborescence du contexte `knowledge` :

```
│   │   ├── port/            DocumentRepository, DocumentStorage, DocumentTextRepository,
│   │   │                    DocumentTextExtractor
…
│       ├── extraction/      ADAPTERS du port DocumentTextExtractor, un par format
```

3. Section « Stack et versions », ligne **Back**, après `Spring AMQP · RabbitMQ 4` :
`commonmark-java` (les deux autres bibliothèques arrivent aux tâches 5 et 6 ; les ajouter
alors plutôt que d'annoncer d'avance ce qui n'est pas là).

- [ ] **Étape 10 : Formater et committer**

```bash
make format-back
gtest test --tests "xyz.sterenn.secondbrain.knowledge.*"
git add src/main/java src/test/java src/test/resources/fixtures \
        gradle/libs.versions.toml build.gradle.kts \
        docs/decisions/0026-un-extracteur-par-format-plutot-qu-apache-tika.md CLAUDE.md
git commit -m "feat: extrait le texte d'un .txt et d'un .md, un adapter par format"
```

---

## Tâche 5 : Le DOCX, et la fabrique de fixtures binaires

Le seul format dont les titres sont à la fois explicites et fragiles : Word les porte par un
style, dont l'identifiant n'est pas toujours l'anglais.

**Fichiers :**
- Créer : `src/main/java/xyz/sterenn/secondbrain/knowledge/infrastructure/extraction/PoiDocxTextExtractor.java`
- Créer : `src/test/java/xyz/sterenn/secondbrain/knowledge/fixtures/FixtureFactory.java`
- Créer : `src/test/resources/fixtures/titres.docx` (produit par la tâche Gradle, versionné)
- Modifier : `gradle/libs.versions.toml`, `build.gradle.kts` (dépendance + tâche `generateFixtures`)
- Modifier : `CLAUDE.md` (stack, commandes)
- Test : `.../extraction/PoiDocxTextExtractorTest.java`

**Interfaces :**
- Consomme : `DocumentTextExtractor`, `Section`, `Fixtures` (tâche 4).
- Produit :
  - `PoiDocxTextExtractor` — `format()` rend `DocumentFormat.DOCX`
  - `FixtureFactory.main(String[])` — écrit les binaires dans `src/test/resources/fixtures/`
  - Tâche Gradle `generateFixtures`

- [ ] **Étape 1 : Déclarer la dépendance POI**

`gradle/libs.versions.toml`, sous `[versions]`, à côté de `commonmark` :

```toml
poi = "5.5.1"
```

Sous `[libraries]` :

```toml
# DOCX : POI est la seule bibliothèque Java qui lise le style d'un paragraphe Word, donc
# la seule qui distingue un titre d'un paragraphe en gras.
poi-ooxml = { module = "org.apache.poi:poi-ooxml", version.ref = "poi" }
```

`build.gradle.kts`, à la suite de `implementation(libs.commonmark)` :

```kotlin
    implementation(libs.poi.ooxml)
```

- [ ] **Étape 2 : Écrire la fabrique de fixtures et sa tâche Gradle**

`src/test/java/xyz/sterenn/secondbrain/knowledge/fixtures/FixtureFactory.java` :

```java
package xyz.sterenn.secondbrain.knowledge.fixtures;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

/**
 * Fabrique les documents d'essai binaires de {@code src/test/resources/fixtures/}.
 *
 * <p><strong>Se lance à la main, une fois, et son produit est versionné</strong> :
 * {@code gtest generateFixtures}. Elle n'est ni un test ni une étape de build — un fichier
 * refabriqué à chaque exécution ferait un diff à chaque exécution, et la suite ne testerait
 * plus que sa propre sortie du jour.
 *
 * <p>Ce que ces fichiers <strong>ne sont pas</strong> : de vrais documents personnels. Le
 * ticket en demandait cinq à dix, en intégration continue ; le porteur a tranché pour un
 * socle fabriqué (spec, décision 9). Un PDF écrit par PDFBox est un PDF aimable, celui qu'un
 * scanner produit ne l'est pas. La vérification sur documents réels reste un geste manuel,
 * sur la pile {@code docker compose}, et ce qu'elle révélera sera un ticket.
 */
public final class FixtureFactory {

    private static final Path FIXTURES = Path.of("src", "test", "resources", "fixtures");

    private FixtureFactory() {
        // point d'entrée, pas un objet
    }

    public static void main(String[] arguments) throws IOException {
        Files.createDirectories(FIXTURES);
        ecrisTitresDocx();
        System.out.println("Fixtures écrites dans " + FIXTURES.toAbsolutePath());
    }

    /** Un DOCX à trois niveaux de titres, portés par les styles standard {@code HeadingN}. */
    private static void ecrisTitresDocx() throws IOException {
        try (XWPFDocument docx = new XWPFDocument();
                OutputStream sortie = Files.newOutputStream(FIXTURES.resolve("titres.docx"))) {
            titre(docx, "Heading1", "Rapport annuel");
            paragraphe(docx, "Le corps de l'introduction, assez long pour compter dans le plancher"
                    + " de caractères que le domaine impose à tout document extrait.");
            titre(docx, "Heading2", "Première partie");
            paragraphe(docx, "Le corps de la première partie, sur deux paragraphes.");
            paragraphe(docx, "Le second paragraphe de la première partie, pour vérifier que la"
                    + " frontière entre paragraphes survit à l'extraction.");
            titre(docx, "Heading3", "Un détail de la première partie");
            paragraphe(docx, "Un troisième niveau, pour que la borne haute ne soit pas théorique.");
            titre(docx, "Heading2", "Seconde partie");
            paragraphe(docx, "Le corps de la seconde partie, qui clôt le document.");
            docx.write(sortie);
        }
    }

    private static void titre(XWPFDocument docx, String style, String texte) {
        XWPFParagraph paragraphe = docx.createParagraph();
        paragraphe.setStyle(style);
        paragraphe.createRun().setText(texte);
    }

    private static void paragraphe(XWPFDocument docx, String texte) {
        docx.createParagraph().createRun().setText(texte);
    }
}
```

Dans `build.gradle.kts`, après le bloc `tasks.withType<Test>` :

```kotlin
// Fabrique les fixtures binaires d'extraction (docx, pdf) dans src/test/resources/fixtures/.
// Lancée À LA MAIN, une fois — `gtest generateFixtures` — et son produit est versionné.
// Ni test, ni étape de build : un binaire refabriqué à chaque exécution ferait un diff à
// chaque exécution, et la suite ne testerait plus que sa propre sortie du jour.
tasks.register<JavaExec>("generateFixtures") {
    group = "build"
    description = "Écrit les documents d'essai binaires ; à lancer à la main, puis committer"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "xyz.sterenn.secondbrain.knowledge.fixtures.FixtureFactory"
}
```

- [ ] **Étape 3 : Fabriquer la fixture DOCX**

```bash
docker compose down
gtest generateFixtures
ls -l src/test/resources/fixtures/
```

Attendu : `titres.docx` existe, quelques kilo-octets. **Le committer avec le reste de la
tâche** — c'est un artefact versionné, pas un produit de build.

- [ ] **Étape 4 : Écrire le test de l'extracteur DOCX**

`.../extraction/PoiDocxTextExtractorTest.java` :

```java
package xyz.sterenn.secondbrain.knowledge.infrastructure.extraction;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.junit.jupiter.api.Test;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle;
import xyz.sterenn.secondbrain.knowledge.Fixtures;
import xyz.sterenn.secondbrain.knowledge.domain.exception.UnextractableDocumentException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.UnreadableDocumentException;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ExtractedText;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.TextBlock;

class PoiDocxTextExtractorTest {

    private static final String CORPS =
            "Un corps de section assez long pour franchir le plancher de cinquante caractères.";

    private final PoiDocxTextExtractor extracteur = new PoiDocxTextExtractor();

    @Test
    void sait_lire_le_format_docx() {
        assertThat(extracteur.format()).isEqualTo(DocumentFormat.DOCX);
    }

    @Test
    void rattache_chaque_bloc_au_titre_de_sa_section() {
        ExtractedText texte = extracteur.extract(Fixtures.lire("titres.docx"));

        assertThat(texte.blocks())
                .extracting(TextBlock::getHeading)
                .containsExactly(
                        "Rapport annuel", "Première partie", "Un détail de la première partie", "Seconde partie");
        assertThat(texte.blocks()).extracting(TextBlock::getHeadingLevel).containsExactly(1, 2, 3, 2);
    }

    @Test
    void conserve_la_frontiere_entre_deux_paragraphes_d_une_meme_section() {
        ExtractedText texte = extracteur.extract(Fixtures.lire("titres.docx"));

        assertThat(texte.blocks().get(1).getText()).contains("\n\n");
    }

    @Test
    void reconnait_un_style_de_titre_nomme_en_francais() throws IOException {
        // Un Word français donne parfois un identifiant de style opaque et ne nomme le style
        // que dans <w:name> : c'est le repli que ce test exerce.
        byte[] docx = unDocxAuStylePersonnalise("Style42", "Titre 1", "Chapitre premier", CORPS);

        assertThat(extracteur.extract(docx).blocks())
                .singleElement()
                .satisfies(bloc -> {
                    assertThat(bloc.getHeading()).isEqualTo("Chapitre premier");
                    assertThat(bloc.getHeadingLevel()).isEqualTo(1);
                });
    }

    @Test
    void refuse_un_fichier_qui_n_est_pas_un_docx() {
        assertThatExceptionOfType(UnreadableDocumentException.class)
                .isThrownBy(() -> extracteur.extract("Ceci n'est pas un document Word.".getBytes(UTF_8)))
                .withMessageContaining("n'a pas pu être lu");
    }

    @Test
    void refuse_un_docx_qui_ne_dit_rien() throws IOException {
        try (XWPFDocument vide = new XWPFDocument();
                ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            vide.createParagraph().createRun().setText("   ");
            vide.write(sortie);

            assertThatExceptionOfType(UnextractableDocumentException.class)
                    .isThrownBy(() -> extracteur.extract(sortie.toByteArray()));
        }
    }

    private static byte[] unDocxAuStylePersonnalise(
            String identifiant, String nomDuStyle, String titre, String corps) throws IOException {
        try (XWPFDocument docx = new XWPFDocument();
                ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            XWPFStyles styles = docx.createStyles();
            CTStyle definition = CTStyle.Factory.newInstance();
            definition.setStyleId(identifiant);
            definition.addNewName().setVal(nomDuStyle);
            styles.addStyle(new XWPFStyle(definition));

            XWPFParagraph paragrapheDeTitre = docx.createParagraph();
            paragrapheDeTitre.setStyle(identifiant);
            paragrapheDeTitre.createRun().setText(titre);
            docx.createParagraph().createRun().setText(corps);

            docx.write(sortie);
            return sortie.toByteArray();
        }
    }
}
```

- [ ] **Étape 5 : Lancer le test, vérifier qu'il échoue**

```bash
gtest test --tests "…PoiDocxTextExtractorTest"
```

Attendu : ÉCHEC de compilation, `cannot find symbol: class PoiDocxTextExtractor`.

- [ ] **Étape 6 : Écrire l'extracteur DOCX**

`.../infrastructure/extraction/PoiDocxTextExtractor.java` :

```java
package xyz.sterenn.secondbrain.knowledge.infrastructure.extraction;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.poi.EmptyFileException;
import org.apache.poi.UnsupportedFileFormatException;
import org.apache.poi.ooxml.POIXMLException;
import org.apache.poi.openxml4j.exceptions.OpenXML4JRuntimeException;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.exception.UnreadableDocumentException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentTextExtractor;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ExtractedText;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.TextBlock;

/**
 * Le {@code .docx} : les titres y sont explicites, mais nommés par le producteur du fichier.
 *
 * <p>Un titre Word est un paragraphe dont le <em>style</em> s'appelle {@code Heading1} à
 * {@code Heading9}. Deux pièges : un paragraphe simplement mis en gras et en grand n'est pas
 * un titre — et il ne doit pas en devenir un —, et l'identifiant du style n'est pas toujours
 * l'anglais. D'où les deux essais de {@link #niveauDeTitre} : l'identifiant d'abord, puis le
 * nom déclaré du style, où un Word français écrit « Titre 1 ».
 *
 * <p>Les tableaux ne sont pas lus : {@code getParagraphs()} rend le corps du document, pas
 * les cellules. C'est le hors-périmètre du ticket, pas un oubli.
 */
@Component
public class PoiDocxTextExtractor implements DocumentTextExtractor {

    private static final Pattern HEADING_STYLE =
            Pattern.compile("^(?:heading|titre)\\s*([1-9])$", Pattern.CASE_INSENSITIVE);

    @Override
    public DocumentFormat format() {
        return DocumentFormat.DOCX;
    }

    @Override
    public ExtractedText extract(byte[] content) {
        List<Section> sections;
        try (XWPFDocument docx = new XWPFDocument(new ByteArrayInputStream(content))) {
            sections = lis(docx);
        } catch (IOException
                | UnsupportedFileFormatException
                | EmptyFileException
                | OpenXML4JRuntimeException
                | POIXMLException illisible) {
            // POI signale un zip qui n'est pas un docx par une famille d'exceptions dont
            // certaines héritent d'IllegalArgumentException : les nommer une à une plutôt
            // que d'attraper RuntimeException, qui masquerait un vrai défaut d'ici.
            throw new UnreadableDocumentException(illisible);
        }
        // Hors du try : le refus d'un document muet est métier, pas une panne de lecture.
        return Section.assemble(sections);
    }

    private static List<Section> lis(XWPFDocument docx) {
        List<Section> sections = new ArrayList<>();
        String titre = "";
        int niveau = 0;
        StringBuilder corps = new StringBuilder();

        for (XWPFParagraph paragraphe : docx.getParagraphs()) {
            String texte = paragraphe.getText();
            if (texte.isBlank()) {
                continue;
            }
            OptionalInt niveauDuTitre = niveauDeTitre(docx, paragraphe);
            if (niveauDuTitre.isPresent()) {
                sections.add(new Section(titre, niveau, corps.toString()));
                titre = texte;
                niveau = Math.min(niveauDuTitre.getAsInt(), TextBlock.MAX_HEADING_LEVEL);
                corps.setLength(0);
            } else {
                // Un paragraphe Word est un paragraphe : la double ligne le dit à RAG-5.
                corps.append(texte).append("\n\n");
            }
        }
        sections.add(new Section(titre, niveau, corps.toString()));
        return sections;
    }

    /** L'identifiant du style d'abord, son nom déclaré ensuite. Vide si ce n'est pas un titre. */
    private static OptionalInt niveauDeTitre(XWPFDocument docx, XWPFParagraph paragraphe) {
        String identifiant = paragraphe.getStyleID();
        if (identifiant == null) {
            return OptionalInt.empty();
        }
        OptionalInt parIdentifiant = niveau(identifiant);
        if (parIdentifiant.isPresent()) {
            return parIdentifiant;
        }
        XWPFStyles styles = docx.getStyles();
        XWPFStyle style = styles == null ? null : styles.getStyle(identifiant);
        return style == null ? OptionalInt.empty() : niveau(style.getName());
    }

    private static OptionalInt niveau(String nomDeStyle) {
        if (nomDeStyle == null) {
            return OptionalInt.empty();
        }
        Matcher correspondance = HEADING_STYLE.matcher(nomDeStyle.strip());
        return correspondance.matches()
                ? OptionalInt.of(Integer.parseInt(correspondance.group(1)))
                : OptionalInt.empty();
    }
}
```

- [ ] **Étape 7 : Lancer le test, vérifier qu'il passe**

```bash
gtest test --tests "xyz.sterenn.secondbrain.knowledge.infrastructure.extraction.*"
```

Attendu : SUCCÈS, 18 tests.

**Si `refuse_un_fichier_qui_n_est_pas_un_docx` échoue** en laissant remonter une exception
POI non listée : ajouter son type au `catch`, et **ne pas** le remplacer par
`RuntimeException` — un `catch` large masquerait une erreur de programmation d'ici en la
déguisant en fichier corrompu.

- [ ] **Étape 8 : Mettre `CLAUDE.md` à jour**

Deux endroits :

1. « Stack et versions », ligne **Back** : ajouter `Apache POI` à côté de `commonmark-java`.
2. Section « Commandes », dans le tableau `gtest` :

```markdown
| Refabriquer les fixtures binaires d'extraction | `gtest generateFixtures` |
```

Et sous le tableau, une phrase : « `generateFixtures` écrit `src/test/resources/fixtures/` et
**son produit est versionné** : elle se lance à la main, pas à chaque build. Ces fichiers sont
un socle fabriqué, pas de vrais documents — voir la spec d'extraction, décision 9. »

- [ ] **Étape 9 : Formater et committer**

```bash
make format-back
gtest test --tests "xyz.sterenn.secondbrain.knowledge.*"
git add src/main/java src/test/java src/test/resources/fixtures/titres.docx \
        gradle/libs.versions.toml build.gradle.kts CLAUDE.md
git commit -m "feat: extrait le texte d'un .docx, titres par style Word"
```

---

## Tâche 6 : Le PDF — signets, puis taille de police

Le format le plus fragile, et celui que le ticket vise en premier. Un PDF ne porte aucune
sémantique de titre : il n'y a que des glyphes posés à des coordonnées.

**Fichiers :**
- Créer : `.../infrastructure/extraction/TextLine.java`, `HeadingFontStripper.java`,
  `HeadingHeuristic.java`, `PdfBoxTextExtractor.java`
- Modifier : `src/test/java/xyz/sterenn/secondbrain/knowledge/fixtures/FixtureFactory.java`
- Créer : `src/test/resources/fixtures/signets.pdf`, `sans-signets.pdf`, `numerise.pdf`
- Modifier : `gradle/libs.versions.toml`, `build.gradle.kts`
- Créer : `docs/decisions/0027-les-titres-d-un-pdf-sans-signets-sont-devines-a-la-taille-de-police.md`
- Modifier : `CLAUDE.md` (index des ADR, stack)
- Tests : `.../extraction/HeadingHeuristicTest.java`, `.../extraction/PdfBoxTextExtractorTest.java`

**Interfaces :**
- Consomme : `DocumentTextExtractor`, `Section`, `Fixtures` (tâche 4), `FixtureFactory` (tâche 5).
- Produit :
  - `record TextLine(String text, float fontSize)` — package-private
  - `HeadingFontStripper` — package-private ; `lines(PDDocument) → List<TextLine>`
  - `static List<Section> HeadingHeuristic.decouper(List<TextLine>)` — package-private
  - `PdfBoxTextExtractor` — `format()` rend `DocumentFormat.PDF`

- [ ] **Étape 1 : Déclarer la dépendance PDFBox**

`gradle/libs.versions.toml`, `[versions]` :

```toml
pdfbox = "3.0.7"
```

`[libraries]` :

```toml
# PDF : PDFBox donne le sommaire du document et, à défaut, la taille de police de chaque
# glyphe — les deux seules pistes de titre qu'un PDF puisse offrir (ADR-0027).
pdfbox = { module = "org.apache.pdfbox:pdfbox", version.ref = "pdfbox" }
```

`build.gradle.kts`, à la suite de `implementation(libs.poi.ooxml)` :

```kotlin
    implementation(libs.pdfbox)
```

- [ ] **Étape 2 : Étendre la fabrique de fixtures aux trois PDF**

Dans `FixtureFactory`, ajouter les appels au `main` :

```java
        ecrisTitresDocx();
        ecrisSignetsPdf();
        ecrisSansSignetsPdf();
        ecrisNumerisePdf();
```

Et les trois méthodes, avec les outils partagés :

```java
    private static final PDFont CORPS = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDFont TITRE = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    /** Un PDF de trois pages, avec un sommaire : un signet par page, plus une page de garde. */
    private static void ecrisSignetsPdf() throws IOException {
        try (PDDocument pdf = new PDDocument();
                OutputStream sortie = Files.newOutputStream(FIXTURES.resolve("signets.pdf"))) {
            PDPage garde = pageDeTexte(pdf, 11f, List.of(
                    "Page de garde du rapport, avant tout signet.",
                    "Elle appartient a personne : aucune section ne la couvre."));
            PDPage premiere = pageDeTexte(pdf, 11f, List.of(
                    "Le corps de la premiere partie, assez long pour compter",
                    "dans le plancher de caracteres du domaine."));
            PDPage seconde = pageDeTexte(pdf, 11f, List.of(
                    "Le corps de la seconde partie, qui clot le document",
                    "et vaut lui aussi plus de cinquante caracteres."));

            PDDocumentOutline sommaire = new PDDocumentOutline();
            pdf.getDocumentCatalog().setDocumentOutline(sommaire);
            sommaire.addLast(signet("Premiere partie", premiere));
            sommaire.addLast(signet("Seconde partie", seconde));
            // `garde` n'a volontairement aucun signet : c'est ce que le bloc sans titre couvre.
            assert garde != null;

            pdf.save(sortie);
        }
    }

    /** Un PDF sans sommaire, dont les titres ne se distinguent que par la taille de police. */
    private static void ecrisSansSignetsPdf() throws IOException {
        try (PDDocument pdf = new PDDocument();
                OutputStream sortie = Files.newOutputStream(FIXTURES.resolve("sans-signets.pdf"))) {
            PDPage page = new PDPage(PDRectangle.A4);
            pdf.addPage(page);
            try (PDPageContentStream flux = new PDPageContentStream(pdf, page)) {
                float y = 780f;
                y = ligne(flux, TITRE, 18f, y, "Rapport annuel");
                y = ligne(flux, CORPS, 11f, y, "Le corps de l'introduction, ecrit dans la taille");
                y = ligne(flux, CORPS, 11f, y, "qui porte de tres loin le plus de caracteres.");
                y = ligne(flux, TITRE, 14f, y, "Premiere partie");
                y = ligne(flux, CORPS, 11f, y, "Le corps de la premiere partie, dans la meme taille");
                y = ligne(flux, CORPS, 11f, y, "que tout le reste du corps du document.");
                y = ligne(flux, TITRE, 14f, y, "Seconde partie");
                ligne(flux, CORPS, 11f, y, "Le corps de la seconde partie, qui clot le document.");
            }
            pdf.save(sortie);
        }
    }

    /**
     * Un PDF numerise : une image, aucune couche texte. C'est le troisieme scenario du
     * ticket — l'extraction doit echouer, pas rendre du vide.
     */
    private static void ecrisNumerisePdf() throws IOException {
        try (PDDocument pdf = new PDDocument();
                OutputStream sortie = Files.newOutputStream(FIXTURES.resolve("numerise.pdf"))) {
            PDPage page = new PDPage(PDRectangle.A4);
            pdf.addPage(page);
            BufferedImage scan = new BufferedImage(600, 800, BufferedImage.TYPE_INT_RGB);
            Graphics2D pinceau = scan.createGraphics();
            pinceau.setColor(Color.WHITE);
            pinceau.fillRect(0, 0, 600, 800);
            pinceau.setColor(Color.DARK_GRAY);
            // Des traits, pas des glyphes : rien de ceci n'est du texte pour un PDF.
            for (int i = 0; i < 24; i++) {
                pinceau.fillRect(60, 60 + i * 28, 40 + (i * 37) % 420, 6);
            }
            pinceau.dispose();
            try (PDPageContentStream flux = new PDPageContentStream(pdf, page)) {
                flux.drawImage(LosslessFactory.createFromImage(pdf, scan), 20, 20, 555, 780);
            }
            pdf.save(sortie);
        }
    }

    private static PDPage pageDeTexte(PDDocument pdf, float taille, List<String> lignes) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        pdf.addPage(page);
        try (PDPageContentStream flux = new PDPageContentStream(pdf, page)) {
            float y = 780f;
            for (String texte : lignes) {
                y = ligne(flux, CORPS, taille, y, texte);
            }
        }
        return page;
    }

    /** Écrit une ligne et rend l'ordonnée de la suivante. */
    private static float ligne(PDPageContentStream flux, PDFont police, float taille, float y, String texte)
            throws IOException {
        flux.beginText();
        flux.setFont(police, taille);
        flux.newLineAtOffset(60f, y);
        flux.showText(texte);
        flux.endText();
        return y - taille * 1.8f;
    }

    private static PDOutlineItem signet(String titre, PDPage page) {
        PDOutlineItem item = new PDOutlineItem();
        item.setTitle(titre);
        item.setDestination(page);
        return item;
    }
```

Imports à ajouter : `java.awt.Color`, `java.awt.Graphics2D`, `java.awt.image.BufferedImage`,
`java.util.List`, et sous `org.apache.pdfbox` : `pdmodel.PDDocument`, `pdmodel.PDPage`,
`pdmodel.PDPageContentStream`, `pdmodel.common.PDRectangle`, `pdmodel.font.PDFont`,
`pdmodel.font.PDType1Font`, `pdmodel.font.Standard14Fonts`,
`pdmodel.graphics.image.LosslessFactory`,
`pdmodel.interactive.documentnavigation.outline.PDDocumentOutline`,
`pdmodel.interactive.documentnavigation.outline.PDOutlineItem`.

**Le texte des fixtures PDF est volontairement sans accents.** Les polices Standard 14 de
PDFBox encodent en WinAnsi, qui les accepte, mais les diagnostics d'un test qui échoue sont
plus lisibles sans elles — et ce que ces fixtures vérifient est la structure, pas
l'encodage. C'est `brut.txt` qui porte le cas des accents.

- [ ] **Étape 3 : Fabriquer les trois PDF**

```bash
docker compose down
gtest generateFixtures
ls -l src/test/resources/fixtures/
```

Attendu : `signets.pdf`, `sans-signets.pdf`, `numerise.pdf` en plus de `titres.docx`.
`numerise.pdf` doit être le plus gros des trois — c'est une image.

Contrôle à faire une fois, à l'œil, avant d'écrire le code de lecture : ouvrir les trois
fichiers dans un lecteur de PDF. `signets.pdf` doit montrer un volet de signets à deux
entrées, `sans-signets.pdf` aucun, et `numerise.pdf` ne doit rien laisser sélectionner à la
souris — si un mot s'y surligne, la fixture porte une couche texte et le troisième scénario
du ticket n'est pas exercé.

- [ ] **Étape 4 : Écrire le test de l'heuristique, sans PDF**

L'heuristique se teste sur des lignes fabriquées à la main : c'est là qu'elle est
observable, et un test qui passerait par un PDF mêlerait deux défauts possibles.

`.../extraction/HeadingHeuristicTest.java` :

```java
package xyz.sterenn.secondbrain.knowledge.infrastructure.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class HeadingHeuristicTest {

    private static final String CORPS = "Une ligne de corps de texte, ordinaire et bavarde.";

    @Test
    void prend_pour_le_corps_la_taille_qui_porte_le_plus_de_caracteres() {
        List<Section> sections = HeadingHeuristic.decouper(List.of(
                new TextLine("Rapport annuel", 18f),
                new TextLine(CORPS, 11f),
                new TextLine(CORPS, 11f),
                new TextLine(CORPS, 11f)));

        assertThat(sections).extracting(Section::heading).containsExactly("Rapport annuel");
    }

    @Test
    void range_les_tailles_de_titre_de_la_plus_grande_a_la_plus_petite() {
        List<Section> sections = HeadingHeuristic.decouper(List.of(
                new TextLine("Rapport annuel", 18f),
                new TextLine(CORPS, 11f),
                new TextLine("Premiere partie", 14f),
                new TextLine(CORPS, 11f),
                new TextLine("Seconde partie", 14f),
                new TextLine(CORPS, 11f)));

        assertThat(sections).extracting(Section::level).containsExactly(1, 2, 2);
    }

    @Test
    void garde_hors_section_ce_qui_precede_le_premier_titre() {
        List<Section> sections = HeadingHeuristic.decouper(
                List.of(new TextLine(CORPS, 11f), new TextLine("Un titre", 18f), new TextLine(CORPS, 11f)));

        assertThat(sections).first().satisfies(section -> {
            assertThat(section.heading()).isEmpty();
            assertThat(section.level()).isZero();
        });
    }

    @Test
    void ne_voit_aucun_titre_dans_un_document_d_une_seule_taille() {
        List<Section> sections = HeadingHeuristic.decouper(
                List.of(new TextLine(CORPS, 11f), new TextLine(CORPS, 11f), new TextLine(CORPS, 11f)));

        assertThat(sections).singleElement().satisfies(section -> assertThat(section.heading())
                .isEmpty());
    }

    @Test
    void ne_prend_pas_pour_un_titre_une_phrase_entiere_mise_en_avant() {
        String citation = "C".repeat(200);

        List<Section> sections = HeadingHeuristic.decouper(
                List.of(new TextLine(citation, 18f), new TextLine(CORPS, 11f), new TextLine(CORPS, 11f)));

        assertThat(sections).extracting(Section::heading).containsExactly("");
    }

    @Test
    void ne_rend_aucune_section_quand_il_n_y_a_aucune_ligne() {
        assertThat(HeadingHeuristic.decouper(List.of())).isEmpty();
    }

    @Test
    void borne_le_niveau_a_six_meme_avec_sept_tailles_de_titre() {
        List<TextLine> lignes = new java.util.ArrayList<>();
        for (int taille = 30; taille >= 12; taille -= 3) {
            lignes.add(new TextLine("Titre de " + taille, taille));
            lignes.add(new TextLine(CORPS, 10f));
        }
        lignes.add(new TextLine(CORPS.repeat(5), 10f));

        assertThat(HeadingHeuristic.decouper(lignes)).extracting(Section::level).allMatch(niveau -> niveau <= 6);
    }
}
```

- [ ] **Étape 5 : Écrire le test de l'extracteur PDF**

`.../extraction/PdfBoxTextExtractorTest.java` :

```java
package xyz.sterenn.secondbrain.knowledge.infrastructure.extraction;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.knowledge.Fixtures;
import xyz.sterenn.secondbrain.knowledge.domain.exception.UnextractableDocumentException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.UnreadableDocumentException;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ExtractedText;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.TextBlock;

class PdfBoxTextExtractorTest {

    private final PdfBoxTextExtractor extracteur = new PdfBoxTextExtractor();

    @Test
    void sait_lire_le_format_pdf() {
        assertThat(extracteur.format()).isEqualTo(DocumentFormat.PDF);
    }

    @Test
    void decoupe_un_pdf_a_sommaire_en_une_section_par_signet() {
        ExtractedText texte = extracteur.extract(Fixtures.lire("signets.pdf"));

        assertThat(texte.blocks())
                .extracting(TextBlock::getHeading)
                .containsExactly("", "Premiere partie", "Seconde partie");
    }

    @Test
    void garde_hors_section_la_page_de_garde_qui_precede_le_premier_signet() {
        ExtractedText texte = extracteur.extract(Fixtures.lire("signets.pdf"));

        assertThat(texte.blocks()).first().satisfies(bloc -> {
            assertThat(bloc.getHeadingLevel()).isZero();
            assertThat(bloc.getText()).contains("Page de garde");
        });
    }

    @Test
    void ne_rend_jamais_deux_fois_le_texte_d_une_meme_page() {
        ExtractedText texte = extracteur.extract(Fixtures.lire("signets.pdf"));

        assertThat(texte.blocks().get(1).getText()).doesNotContain("Page de garde");
        assertThat(texte.blocks().get(2).getText()).doesNotContain("premiere partie");
    }

    @Test
    void devine_les_titres_d_un_pdf_sans_sommaire_a_la_taille_de_police() {
        ExtractedText texte = extracteur.extract(Fixtures.lire("sans-signets.pdf"));

        assertThat(texte.blocks())
                .extracting(TextBlock::getHeading)
                .containsExactly("Rapport annuel", "Premiere partie", "Seconde partie");
        assertThat(texte.blocks()).extracting(TextBlock::getHeadingLevel).containsExactly(1, 2, 2);
    }

    @Test
    void refuse_un_pdf_numerise_sans_couche_texte() {
        assertThatExceptionOfType(UnextractableDocumentException.class)
                .isThrownBy(() -> extracteur.extract(Fixtures.lire("numerise.pdf")))
                .withMessageContaining("pas de texte exploitable");
    }

    @Test
    void refuse_un_fichier_qui_n_est_pas_un_pdf() {
        assertThatExceptionOfType(UnreadableDocumentException.class)
                .isThrownBy(() -> extracteur.extract("Ceci n'est pas un PDF.".getBytes(UTF_8)))
                .withMessageContaining("n'a pas pu être lu");
    }
}
```

- [ ] **Étape 6 : Lancer les deux tests, vérifier qu'ils échouent**

```bash
gtest test --tests "…HeadingHeuristicTest" --tests "…PdfBoxTextExtractorTest"
```

Attendu : ÉCHEC de compilation, `cannot find symbol: class HeadingHeuristic`.

- [ ] **Étape 7 : Écrire la ligne mesurée et le stripper**

`.../infrastructure/extraction/TextLine.java` :

```java
package xyz.sterenn.secondbrain.knowledge.infrastructure.extraction;

/**
 * Une ligne rendue par PDFBox, et la plus grande police qu'elle emploie.
 *
 * <p>La plus grande et non la moyenne : un titre dont le premier caractère est une lettrine,
 * ou qui porte un appel de note en petit, reste un titre. La moyenne le noierait.
 */
record TextLine(String text, float fontSize) {}
```

`.../infrastructure/extraction/HeadingFontStripper.java` :

```java
package xyz.sterenn.secondbrain.knowledge.infrastructure.extraction;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

/**
 * Un {@link PDFTextStripper} qui, en plus du texte, retient la taille de police de chaque
 * ligne.
 *
 * <p>C'est la seule façon d'obtenir cette information : {@code getText} rend une chaîne, et
 * une chaîne ne dit rien de la police. {@code writeString} est le point de passage de chaque
 * ligne, avec les positions de ses glyphes.
 *
 * <p>{@code setSortByPosition(true)} parce que l'ordre du flux de contenu d'un PDF n'est pas
 * l'ordre de lecture, et {@code setLineSeparator("\n")} pour que le résultat ne dépende pas
 * du système d'exploitation qui fait tourner la suite.
 */
class HeadingFontStripper extends PDFTextStripper {

    private final List<TextLine> lines = new ArrayList<>();

    HeadingFontStripper() throws IOException {
        setSortByPosition(true);
        setLineSeparator("\n");
    }

    @Override
    protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
        float plusGrande = 0f;
        for (TextPosition position : textPositions) {
            plusGrande = Math.max(plusGrande, position.getFontSizeInPt());
        }
        lines.add(new TextLine(text, plusGrande));
        super.writeString(text, textPositions);
    }

    /**
     * Parcourt le document et rend ses lignes mesurées.
     *
     * <p>Le texte rendu par {@code getText} est jeté : ce qui nous intéresse a été collecté
     * en chemin. Un PDF numérisé n'appelle jamais {@code writeString} — la liste reste vide,
     * et c'est ainsi que le troisième scénario du ticket se solde par un refus.
     */
    List<TextLine> lines(PDDocument pdf) throws IOException {
        getText(pdf);
        return List.copyOf(lines);
    }
}
```

- [ ] **Étape 8 : Écrire l'heuristique**

`.../infrastructure/extraction/HeadingHeuristic.java` :

```java
package xyz.sterenn.secondbrain.knowledge.infrastructure.extraction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.TextBlock;

/**
 * Devine les titres d'un PDF à la taille de police, faute de sommaire. Voir ADR-0027.
 *
 * <p>La règle tient en trois conditions, et aucune ne suffit seule : une ligne est un titre
 * si elle n'est pas vide, si elle est courte, et si elle est écrite nettement plus grand que
 * le corps du document. Le seuil de 15 % écarte les demi-points d'écart d'une même police ;
 * la borne de longueur écarte la citation mise en avant, qui est grande mais bavarde.
 *
 * <p>Le corps, lui, n'est pas la taille la plus fréquente <em>ligne à ligne</em> mais celle
 * qui porte <strong>le plus de caractères</strong> : un document de trente titres et de
 * quarante lignes de corps ferait mentir le décompte par lignes, jamais celui par
 * caractères.
 *
 * <p>Classe utilitaire et non composant : elle n'a aucune dépendance et se teste sur des
 * lignes fabriquées à la main, sans PDF ni Spring.
 */
final class HeadingHeuristic {

    /** Au-delà de 15 % de plus que le corps, c'est un titre. En deçà, un écart de police. */
    private static final float HEADING_RATIO = 1.15f;

    /** Plus long que ça, c'est une phrase mise en avant, pas un titre. */
    private static final int MAX_HEADING_CHARACTERS = 120;

    private HeadingHeuristic() {
        // heuristique, pas un objet
    }

    static List<Section> decouper(List<TextLine> lignes) {
        if (lignes.isEmpty()) {
            return List.of();
        }
        float tailleDuCorps = tailleDuCorps(lignes);
        List<Float> taillesDeTitre = taillesDeTitre(lignes, tailleDuCorps);

        List<Section> sections = new ArrayList<>();
        String titre = "";
        int niveau = 0;
        StringBuilder corps = new StringBuilder();

        for (TextLine ligne : lignes) {
            if (estUnTitre(ligne, tailleDuCorps)) {
                sections.add(new Section(titre, niveau, corps.toString()));
                titre = ligne.text();
                niveau = Math.min(taillesDeTitre.indexOf(arrondie(ligne.fontSize())) + 1, TextBlock.MAX_HEADING_LEVEL);
                corps.setLength(0);
            } else {
                // Une ligne, pas un paragraphe : PDFTextStripper ne sait pas les distinguer,
                // et deviner leur frontière à l'indentation serait un pari sur la mise en
                // page. RAG-5 découpera cette section à la phrase (ADR-0027).
                corps.append(ligne.text()).append('\n');
            }
        }
        sections.add(new Section(titre, niveau, corps.toString()));
        return sections;
    }

    private static float tailleDuCorps(List<TextLine> lignes) {
        Map<Float, Integer> caracteresParTaille = new HashMap<>();
        for (TextLine ligne : lignes) {
            caracteresParTaille.merge(arrondie(ligne.fontSize()), ligne.text().length(), Integer::sum);
        }
        return caracteresParTaille.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(0f);
    }

    /** De la plus grande à la plus petite : l'indice dans cette liste donne le niveau. */
    private static List<Float> taillesDeTitre(List<TextLine> lignes, float tailleDuCorps) {
        return lignes.stream()
                .filter(ligne -> estUnTitre(ligne, tailleDuCorps))
                .map(ligne -> arrondie(ligne.fontSize()))
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    private static boolean estUnTitre(TextLine ligne, float tailleDuCorps) {
        return !ligne.text().isBlank()
                && ligne.text().strip().length() <= MAX_HEADING_CHARACTERS
                && arrondie(ligne.fontSize()) > tailleDuCorps * HEADING_RATIO;
    }

    /** Au demi-point : deux glyphes d'une même police diffèrent parfois de quelques centièmes. */
    private static float arrondie(float taille) {
        return Math.round(taille * 2f) / 2f;
    }
}
```

- [ ] **Étape 9 : Écrire l'extracteur PDF**

`.../infrastructure/extraction/PdfBoxTextExtractor.java` :

```java
package xyz.sterenn.secondbrain.knowledge.infrastructure.extraction;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.exception.UnreadableDocumentException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentTextExtractor;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ExtractedText;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.TextBlock;

/**
 * Le {@code .pdf} : le format le plus fragile, parce qu'il ne porte <em>aucune</em>
 * sémantique de titre. Il n'y a que des glyphes posés à des coordonnées.
 *
 * <p>Deux stratégies, dans cet ordre, et c'est ADR-0027 :
 *
 * <ol>
 *   <li><strong>Le sommaire</strong> quand le document en a un. Il est écrit par l'auteur,
 *       et vaut mieux que n'importe quelle mesure.
 *   <li><strong>La taille de police</strong> sinon, par {@link HeadingHeuristic}.
 * </ol>
 *
 * <p><strong>Limite du chemin par sommaire : la granularité est la page.</strong> PDFBox ne
 * découpe qu'en plages de pages ; deux signets tombant sur la même page sont fusionnés sous
 * le titre du premier, faute de quoi le texte de cette page serait rendu deux fois — et pour
 * un RAG, un texte dupliqué est bien pire qu'un titre manquant.
 */
@Component
public class PdfBoxTextExtractor implements DocumentTextExtractor {

    @Override
    public DocumentFormat format() {
        return DocumentFormat.PDF;
    }

    @Override
    public ExtractedText extract(byte[] content) {
        List<Section> sections;
        try (PDDocument pdf = Loader.loadPDF(content)) {
            PDDocumentOutline sommaire = pdf.getDocumentCatalog().getDocumentOutline();
            List<Bookmark> signets = sommaire == null ? List.of() : signets(pdf, sommaire, 1);
            sections = signets.isEmpty()
                    ? HeadingHeuristic.decouper(new HeadingFontStripper().lines(pdf))
                    : parSignets(pdf, signets);
        } catch (IOException illisible) {
            throw new UnreadableDocumentException(illisible);
        }
        // Hors du try : un PDF numérisé s'ouvre parfaitement, il ne dit simplement rien.
        // C'est un refus métier, pas une panne de lecture.
        return Section.assemble(sections);
    }

    /** Un signet : son titre, sa profondeur dans le sommaire, et la page qu'il vise. */
    private record Bookmark(String title, int level, int pageIndex) {}

    /**
     * Parcours en profondeur du sommaire : son ordre est l'ordre de lecture du document.
     *
     * <p>Un signet sans titre ou dont la destination ne mène à aucune page est écarté : ces
     * deux cas existent dans la nature, et un titre vide ne rattacherait rien à rien.
     */
    private static List<Bookmark> signets(PDDocument pdf, PDOutlineNode noeud, int niveau) throws IOException {
        List<Bookmark> trouves = new ArrayList<>();
        for (PDOutlineItem item : noeud.children()) {
            PDPage page = item.findDestinationPage(pdf);
            if (page != null && item.getTitle() != null && !item.getTitle().isBlank()) {
                trouves.add(new Bookmark(
                        item.getTitle(), Math.min(niveau, TextBlock.MAX_HEADING_LEVEL), pdf.getPages()
                                .indexOf(page)));
            }
            trouves.addAll(signets(pdf, item, niveau + 1));
        }
        return trouves;
    }

    private static List<Section> parSignets(PDDocument pdf, List<Bookmark> signets) throws IOException {
        // Deux signets sur la même page sont fusionnés sous le titre du premier : PDFBox ne
        // découpe qu'en pages, et le texte de cette page serait sinon rendu deux fois.
        List<Bookmark> parPage = new ArrayList<>();
        for (Bookmark signet : signets) {
            if (parPage.isEmpty() || parPage.getLast().pageIndex() != signet.pageIndex()) {
                parPage.add(signet);
            }
        }

        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);
        stripper.setLineSeparator("\n");

        List<Section> sections = new ArrayList<>();
        // Ce qui précède le premier signet — page de garde, résumé — n'appartient à aucune
        // section, et le perdre serait perdre du texte que l'auteur a bien écrit.
        if (parPage.getFirst().pageIndex() > 0) {
            sections.add(Section.untitled(texte(stripper, pdf, 0, parPage.getFirst().pageIndex() - 1)));
        }
        for (int i = 0; i < parPage.size(); i++) {
            Bookmark signet = parPage.get(i);
            int dernierePage =
                    i + 1 < parPage.size() ? parPage.get(i + 1).pageIndex() - 1 : pdf.getNumberOfPages() - 1;
            sections.add(new Section(signet.title(), signet.level(), texte(stripper, pdf, signet.pageIndex(), dernierePage)));
        }
        return sections;
    }

    /**
     * Bornes en index de page, inclusives ; {@link PDFTextStripper} les compte à partir de 1.
     *
     * <p>Un sommaire mal formé, dont les signets ne sont pas dans l'ordre des pages, donne
     * une plage vide plutôt qu'une erreur : la section est alors écartée par
     * {@code Section.assemble}, comme toute section sans corps.
     */
    private static String texte(PDFTextStripper stripper, PDDocument pdf, int premierePage, int dernierePage)
            throws IOException {
        stripper.setStartPage(premierePage + 1);
        stripper.setEndPage(dernierePage + 1);
        return stripper.getText(pdf);
    }
}
```

- [ ] **Étape 10 : Lancer les tests, vérifier qu'ils passent**

```bash
gtest test --tests "xyz.sterenn.secondbrain.knowledge.infrastructure.extraction.*"
```

Attendu : SUCCÈS, 32 tests.

**Si `devine_les_titres_d_un_pdf_sans_sommaire` rend un seul bloc sans titre :** le rapport
entre la taille des titres et celle du corps de `sans-signets.pdf` est passé sous 1,15.
Corriger la **fixture** (écarter davantage les tailles) plutôt que le seuil — le seuil est un
compromis réfléchi, la fixture est un décor.

**Si `refuse_un_pdf_numerise_sans_couche_texte` échoue en `EXTRACTED` :** `numerise.pdf`
porte une couche texte. Vérifier avec un lecteur de PDF qu'aucun mot n'y est sélectionnable,
puis refabriquer.

- [ ] **Étape 11 : Écrire l'ADR-0027**

`docs/decisions/0027-les-titres-d-un-pdf-sans-signets-sont-devines-a-la-taille-de-police.md`,
depuis la **décision 4** de la spec :

- Contexte : un PDF ne porte aucune sémantique de titre ; le ticket demande pourtant un texte
  découpé en sections pour *chacun* des formats acceptés.
- Options : signets seuls · heuristique de police seule · signets puis heuristique en repli.
- Décision : **les deux, dans cet ordre**, parce qu'un sommaire est écrit par l'auteur et
  vaut mieux que toute mesure, mais que la plupart des PDF personnels n'en ont pas.
- Conséquences — Bien : un PDF sans sommaire rend tout de même des sections. Mal : le repli
  a des réglages arbitraires (15 %, 120 caractères) qui produiront des titres fantaisistes
  sur un document exotique, **en silence** ; deux chemins de code à tenir. Mal :
  **granularité page** sur le chemin par signets, deux signets d'une même page fusionnés.
  Mal : **les frontières de paragraphe sont perdues** dans un PDF — `PDFTextStripper` sépare
  les lignes, pas les paragraphes, et sa détection de paragraphes est un pari sur la mise en
  page. Une section de PDF arrive à RAG-5 comme un seul paragraphe, qui la découpera à la
  phrase. Dégradé, pas faux.
- Condition de réouverture : la mesure de qualité de RAG-14, si elle montre que les extraits
  issus de PDF répondent moins bien que les autres. La sortie sera alors une bibliothèque de
  mise en page (analyse de blocs), pas un réglage du seuil.
- Vérification : `HeadingHeuristicTest` et `PdfBoxTextExtractorTest`.

- [ ] **Étape 12 : Mettre `CLAUDE.md` à jour**

```markdown
| [0027](docs/decisions/0027-les-titres-d-un-pdf-sans-signets-sont-devines-a-la-taille-de-police.md) | Les titres d'un PDF sans signets sont devinés à la taille de police |
```

Et « Stack et versions », ligne **Back** : ajouter `PDFBox` à côté de `Apache POI`. La liste
des trois bibliothèques d'extraction est maintenant complète.

- [ ] **Étape 13 : Formater et committer**

```bash
make format-back
gtest test --tests "xyz.sterenn.secondbrain.knowledge.*"
git add src/main/java src/test/java \
        src/test/resources/fixtures/signets.pdf \
        src/test/resources/fixtures/sans-signets.pdf \
        src/test/resources/fixtures/numerise.pdf \
        gradle/libs.versions.toml build.gradle.kts \
        docs/decisions/0027-les-titres-d-un-pdf-sans-signets-sont-devines-a-la-taille-de-police.md \
        CLAUDE.md
git commit -m "feat: extrait le texte d'un .pdf — sommaire d'abord, taille de police ensuite"
```

---

## Tâche 7 : La commande d'extraction et son annonce

Les quatre extracteurs existent ; il manque ce qui les choisit, les fait tourner, range le
résultat et l'annonce.

**Fichiers :**
- Créer : `.../application/command/ExtractDocumentText.java`, `ExtractDocumentTextHandler.java`
- Créer : `.../domain/event/DocumentTextExtracted.java`
- Modifier : `.../infrastructure/messaging/KnowledgeMessagingConfiguration.java`
- Modifier : `CLAUDE.md` (arborescence, flux)
- Tests : `.../application/command/ExtractDocumentTextTest.java`,
  `.../domain/event/DocumentTextExtractedTest.java` (ou l'ajout au test de nommage existant)

**Interfaces :**
- Consomme : `ExtractedText` (T1), `DocumentText`/`DocumentTextRepository` (T3),
  `DocumentTextExtractor` (T4) et ses quatre adapters (T4, T5, T6), plus les ports existants
  `DocumentRepository`, `DocumentStorage`, `DomainEventPublisher`, et `Clock`.
- Produit :
  - `record ExtractDocumentText(UUID documentId, UUID ownerId) implements Command`
  - `record DocumentTextExtracted(UUID documentId, UUID ownerId, int blockCount, Instant occurredAt) implements DomainEvent`
    → clé `knowledge.document-text.extracted`

- [ ] **Étape 1 : Écrire le test de nommage de l'événement**

Ajouter à la classe qui teste déjà `DomainEventNames` (le nom `DocumentTextExtracted` y est
cité en exemple dans la Javadoc — le test doit maintenant porter sur la vraie classe) :

```java
    @Test
    void nomme_l_extraction_du_texte_avec_un_objet_en_deux_mots() {
        assertThat(DomainEventNames.of(DocumentTextExtracted.class))
                .isEqualTo("knowledge.document-text.extracted");
    }
```

- [ ] **Étape 2 : Écrire le test d'intégration de la commande**

`.../application/command/ExtractDocumentTextTest.java`. Il dispatche **par le bus**, jamais
en appelant le handler : c'est le chemin réel de production, et c'est le bus qui ouvre la
transaction.

```java
package xyz.sterenn.secondbrain.knowledge.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.knowledge.Fixtures;
import xyz.sterenn.secondbrain.knowledge.KnowledgeFixture;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DocumentNotFoundException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.UnextractableDocumentException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentTextRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentStatus;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.TextBlock;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;

/**
 * {@code @Transactional} : la base est annulée après chaque test. Le disque, lui, ne l'est
 * pas — d'où le nettoyage explicite en {@code @AfterEach}, comme le fait déjà
 * {@code UploadDocumentTest}.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ExtractDocumentTextTest {

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentTextRepository documentTextRepository;

    @Value("${secondbrain.storage.originals-path}")
    private String cheminDesOriginaux;

    // unCompteVerifie() et le dépôt d'un document par POST : reprendre le montage de
    // UploadDocumentTest, qui dépose déjà par le bus et connaît le compte propriétaire.

    @AfterEach
    void nettoieLesOriginaux() {
        KnowledgeFixture.videLesOriginaux(cheminDesOriginaux);
    }

    @Test
    void range_le_texte_extrait_et_marque_le_document_extrait() {
        Document document = unDocumentDepose("structure.md", Fixtures.STRUCTURE_MD);

        commandBus.dispatch(new ExtractDocumentText(document.getId(), document.getOwnerId()));

        assertThat(documentTextRepository.findByDocumentId(document.getId()))
                .get()
                .satisfies(texte -> assertThat(texte.getBlocks())
                        .extracting(TextBlock::getHeading)
                        .contains("Journal de bord"));
        assertThat(documentRepository
                        .findByIdAndOwnerId(document.getId(), document.getOwnerId())
                        .orElseThrow()
                        .getStatus())
                .isEqualTo(DocumentStatus.EXTRACTED);
    }

    @Test
    void une_seconde_extraction_remplace_la_premiere_sans_la_doubler() {
        Document document = unDocumentDepose("structure.md", Fixtures.STRUCTURE_MD);
        commandBus.dispatch(new ExtractDocumentText(document.getId(), document.getOwnerId()));

        commandBus.dispatch(new ExtractDocumentText(document.getId(), document.getOwnerId()));

        assertThat(documentTextRepository.findByDocumentId(document.getId())).isPresent();
    }

    @Test
    void choisit_l_extracteur_du_format_du_document() {
        Document document = unDocumentDepose("notes.txt", Fixtures.BRUT_TXT);

        commandBus.dispatch(new ExtractDocumentText(document.getId(), document.getOwnerId()));

        assertThat(documentTextRepository.findByDocumentId(document.getId()))
                .get()
                .satisfies(texte -> assertThat(texte.getBlocks()).hasSize(1));
    }

    @Test
    void refuse_un_document_qui_n_appartient_pas_au_demandeur() {
        Document document = unDocumentDepose("notes.txt", Fixtures.BRUT_TXT);

        // Dernier appel du test : le refus marque la transaction englobante rollback-only.
        assertThatExceptionOfType(DocumentNotFoundException.class)
                .isThrownBy(() -> commandBus.dispatch(new ExtractDocumentText(document.getId(), UUID.randomUUID())));
    }

    @Test
    void laisse_remonter_le_refus_d_un_document_inexploitable() {
        Document document = unDocumentDepose("scan.pdf", Fixtures.NUMERISE_PDF);

        // Dernier appel du test, même raison.
        assertThatExceptionOfType(UnextractableDocumentException.class)
                .isThrownBy(() ->
                        commandBus.dispatch(new ExtractDocumentText(document.getId(), document.getOwnerId())));
    }
}
```

**Note d'exécution :** `unDocumentDepose(String filename, String fixture)` est à écrire dans
cette classe. Sa signature dit ce qu'elle fait : le **premier** argument est le nom sous
lequel le document est déposé — c'est lui qui décide du format, par son extension —, le
**second** le nom d'une fixture, dont le contenu est lu par `Fixtures.lire`. Les deux sont
distincts à dessein : `unDocumentDepose("scan.pdf", Fixtures.NUMERISE_PDF)` dépose bien un
PDF, sous un nom qui n'a pas à être celui de la fixture.

Elle dépose **par le bus**, comme `UploadDocumentTest` : `commandBus.dispatch(new
UploadDocument(...))`, puis relit le document par le port. C'est le chemin réel, et c'est lui
qui écrit l'original sur le disque — sans quoi l'extraction n'aurait rien à lire.

- [ ] **Étape 3 : Lancer le test, vérifier qu'il échoue**

```bash
docker compose down
gtest test --tests "…ExtractDocumentTextTest"
```

Attendu : ÉCHEC de compilation, `cannot find symbol: class ExtractDocumentText`.

- [ ] **Étape 4 : Écrire l'événement**

`.../domain/event/DocumentTextExtracted.java` :

```java
package xyz.sterenn.secondbrain.knowledge.domain.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import xyz.sterenn.secondbrain.shared.event.DomainEvent;

/**
 * Le texte d'un document vient d'être extrait et rangé.
 *
 * <p>Comme {@code DocumentUploaded}, il porte des identifiants et non l'état : le
 * consommateur relit, et le texte extrait pèse parfois des centaines de kilo-octets — il n'a
 * rien à faire sur un transport de messages.
 *
 * <p>{@code blockCount} est la seule donnée non identifiante, et elle est là pour une raison
 * précise : elle rend le journal du worker lisible sans requête, et RAG-5 saura d'un coup
 * d'œil s'il a affaire à un document d'une section ou de deux cents.
 *
 * <p>Son nom simple est {@code <Objet><Fait>} : {@code DocumentText} + {@code Extracted},
 * d'où la clé {@code knowledge.document-text.extracted}, qu'un binding
 * {@code knowledge.#} voit comme tous les autres.
 */
public record DocumentTextExtracted(UUID documentId, UUID ownerId, int blockCount, Instant occurredAt)
        implements DomainEvent {

    public DocumentTextExtracted {
        Objects.requireNonNull(documentId, "L'identifiant du document est obligatoire");
        Objects.requireNonNull(ownerId, "Le propriétaire du document est obligatoire");
        Objects.requireNonNull(occurredAt, "L'instant de l'événement est obligatoire");
        if (blockCount <= 0) {
            throw new IllegalArgumentException("Une extraction sans bloc n'a rien à annoncer");
        }
    }
}
```

- [ ] **Étape 5 : Écrire la commande et son handler**

`.../application/command/ExtractDocumentText.java` :

```java
package xyz.sterenn.secondbrain.knowledge.application.command;

import java.util.UUID;
import xyz.sterenn.secondbrain.shared.bus.Command;

/**
 * Extraire le texte d'un document déjà déposé.
 *
 * <p>Elle porte le propriétaire autant que le document : le port de lecture exige que
 * « chaque méthode porte le propriétaire, et qu'aucune lecture ne puisse l'oublier par
 * distraction ». {@code DocumentUploaded} porte déjà les deux ; ajouter un {@code findById}
 * non cloisonné pour le confort du worker ouvrirait la seule lecture de la base qui ignore
 * à qui elle appartient.
 */
public record ExtractDocumentText(UUID documentId, UUID ownerId) implements Command {}
```

`.../application/command/ExtractDocumentTextHandler.java` :

```java
package xyz.sterenn.secondbrain.knowledge.application.command;

import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DocumentText;
import xyz.sterenn.secondbrain.knowledge.domain.event.DocumentTextExtracted;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DocumentNotFoundException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.UnreadableDocumentException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentStorage;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentTextExtractor;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentTextRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ExtractedText;
import xyz.sterenn.secondbrain.shared.bus.CommandHandler;
import xyz.sterenn.secondbrain.shared.event.DomainEventPublisher;

/**
 * Orchestre l'extraction : relecture du document et de son original, choix de l'extracteur,
 * remplacement du texte, changement de statut, puis annonce.
 *
 * <p>Aucun {@code @Transactional} ici — la transaction est ouverte par
 * {@code SpringCommandBus.dispatch}. Une extraction qui échoue n'écrit donc rien : ni texte
 * partiel, ni statut. C'est ce qui oblige le consommateur d'événements à marquer l'échec dans
 * une <em>seconde</em> transaction (ADR-0028).
 *
 * <p><strong>L'effacement avant l'écriture n'est pas une précaution de style.</strong> AMQP
 * livre au moins une fois et {@code document_id} est {@code UNIQUE} : sans lui, une
 * redélivrance de {@code DocumentUploaded} ferait échouer l'écriture sur la contrainte, et le
 * document passerait en {@code FAILED} pour un traitement qui avait réussi.
 *
 * <p>L'annonce en dernier, comme au dépôt : elle ne prend effet qu'au commit, donc sa place
 * n'a aucune portée transactionnelle — elle est dernière pour se lire comme ce qu'elle est.
 */
@Component
public class ExtractDocumentTextHandler implements CommandHandler<ExtractDocumentText> {

    private final DocumentRepository documentRepository;
    private final DocumentStorage documentStorage;
    private final DocumentTextRepository documentTextRepository;
    private final Map<DocumentFormat, DocumentTextExtractor> extractorsByFormat;
    private final DomainEventPublisher domainEventPublisher;
    private final Clock clock;

    public ExtractDocumentTextHandler(
            DocumentRepository documentRepository,
            DocumentStorage documentStorage,
            DocumentTextRepository documentTextRepository,
            List<DocumentTextExtractor> documentTextExtractors,
            DomainEventPublisher domainEventPublisher,
            Clock clock) {
        this.documentRepository = documentRepository;
        this.documentStorage = documentStorage;
        this.documentTextRepository = documentTextRepository;
        this.extractorsByFormat = indexeParFormat(documentTextExtractors);
        this.domainEventPublisher = domainEventPublisher;
        this.clock = clock;
    }

    /**
     * Indexe les extracteurs, et <strong>fait échouer le démarrage</strong> si un format
     * accepté au dépôt n'a pas le sien.
     *
     * <p>C'est la contrepartie du choix d'un extracteur par format (ADR-0026) : ajouter une
     * constante à {@link DocumentFormat} sans écrire son adapter serait, sinon, un document
     * accepté puis irrémédiablement en échec. Même dispositif que la table de routage des
     * bus : le défaut se voit au démarrage, pas en production.
     */
    private static Map<DocumentFormat, DocumentTextExtractor> indexeParFormat(
            List<DocumentTextExtractor> documentTextExtractors) {
        Map<DocumentFormat, DocumentTextExtractor> parFormat = new EnumMap<>(DocumentFormat.class);
        for (DocumentTextExtractor extracteur : documentTextExtractors) {
            DocumentTextExtractor precedent = parFormat.put(extracteur.format(), extracteur);
            if (precedent != null) {
                throw new IllegalStateException("Deux extracteurs revendiquent le format " + extracteur.format()
                        + " : " + precedent.getClass().getName() + " et "
                        + extracteur.getClass().getName());
            }
        }
        for (DocumentFormat format : DocumentFormat.values()) {
            if (!parFormat.containsKey(format)) {
                throw new IllegalStateException("Aucun extracteur ne sait lire " + format
                        + " : un format accepté au dépôt doit être lisible");
            }
        }
        return Map.copyOf(parFormat);
    }

    @Override
    public void handle(ExtractDocumentText command) {
        Document document = documentRepository
                .findByIdAndOwnerId(command.documentId(), command.ownerId())
                .orElseThrow(DocumentNotFoundException::new);

        byte[] contenu = documentStorage
                .read(document.getId())
                // La ligne existe, l'original non : c'est la fuite qu'ADR-0020 assume dans
                // l'autre sens. Illisible est le mot juste — il n'y a rien à lire.
                .orElseThrow(UnreadableDocumentException::new);

        ExtractedText texte = extractorsByFormat.get(document.getFormat()).extract(contenu);

        documentTextRepository.deleteByDocumentId(document.getId());
        documentTextRepository.save(DocumentText.of(document.getId(), texte, clock.instant()));

        document.markTextExtracted();
        documentRepository.save(document);

        domainEventPublisher.publish(new DocumentTextExtracted(
                document.getId(), document.getOwnerId(), texte.blocks().size(), clock.instant()));
    }
}
```

- [ ] **Étape 6 : Déclarer l'événement sur le transport**

Dans `KnowledgeMessagingConfiguration`, la seule ligne à changer :

```java
    @Bean
    public DomainEventRegistration knowledgeDomainEvents() {
        return new DomainEventRegistration(List.of(DocumentUploaded.class, DocumentTextExtracted.class));
    }
```

Le binding `knowledge.#` n'a pas à bouger : c'est tout l'intérêt d'une queue par contexte.

- [ ] **Étape 7 : Lancer les tests, vérifier qu'ils passent**

```bash
gtest test --tests "…ExtractDocumentTextTest" --tests "…DomainEventNamesTest"
```

Attendu : SUCCÈS.

**Si le contexte refuse de démarrer sur « Aucun extracteur ne sait lire … » :** c'est le
garde-fou qui joue son rôle. Un des quatre adapters n'est pas un `@Component`, ou son
`format()` ne rend pas ce qu'on croit.

- [ ] **Étape 8 : Mettre `CLAUDE.md` à jour**

Deux endroits :

1. Arborescence du contexte `knowledge` :

```
│   ├── application/
│   │   ├── command/         UploadDocument, DeleteDocument, ExtractDocumentText
…
│   │   └── event/           DocumentUploaded, DocumentTextExtracted
```

2. Après la section « Le flux du dépôt d'un document », une section nouvelle :

```markdown
### Le flux de l'extraction du texte

Le worker reçoit `DocumentUploaded` et dispatche `ExtractDocumentText`, qui relit le
document, relit son original par le port de stockage, choisit l'extracteur de son format,
remplace le texte extrait, pose `EXTRACTED` et annonce `DocumentTextExtracted`.

**Le format produit est le livrable durable de ce flux** : `ExtractedText`, une suite
ordonnée de `TextBlock` portant chacun le titre de sa section, son niveau et son corps
normalisé. Un bloc est une **section**, pas un paragraphe — un document sans titre rend un
unique bloc (ADR-0024). Il vit dans deux tables cascadées, `knowledge_document_texts` et
`knowledge_document_blocks`.

Quatre extracteurs derrière un port, un par format, et non Apache Tika (ADR-0026) : les
styles `Heading1..9` d'un DOCX et les `#` d'un Markdown sont le livrable, pas du balisage à
traverser. Un PDF, lui, ne porte aucune sémantique de titre : son sommaire d'abord, la
taille de police en repli (ADR-0027). **`ExtractDocumentTextHandler` refuse de démarrer si
une constante de `DocumentFormat` n'a pas son extracteur** — un format accepté au dépôt doit
être lisible.

Un document dont il ne sort pas cinquante caractères **échoue explicitement**
(ADR-0025) : c'est le cas du PDF numérisé, et le vide silencieux ne se verrait qu'à la
première question restée sans réponse.
```

- [ ] **Étape 9 : Formater et committer**

```bash
make format-back
gtest test --tests "xyz.sterenn.secondbrain.knowledge.*"
git add src/main/java src/test/java CLAUDE.md
git commit -m "feat: la commande d'extraction range le texte et annonce ce qu'elle a fait"
```

---

## Tâche 8 : Le worker extrait, et l'échec survit à la transaction annulée

La ligne que `KnowledgeEventListener` réserve en commentaire depuis le socle événementiel, et
le piège que RAG-6 signale : un statut d'erreur écrit dans la transaction annulée disparaît
avec elle.

**Fichiers :**
- Créer : `.../application/command/MarkDocumentExtractionFailed.java`, `MarkDocumentExtractionFailedHandler.java`
- Modifier : `.../infrastructure/messaging/KnowledgeEventListener.java`
- Créer : `docs/decisions/0028-l-echec-d-extraction-s-ecrit-hors-de-la-transaction-annulee.md`
- Modifier : `CLAUDE.md` (index des ADR, section sur les événements et le rôle worker)
- Tests : `.../infrastructure/messaging/KnowledgeEventListenerTest.java` (modifié),
  `.../application/command/MarkDocumentExtractionFailedTest.java`,
  `.../application/command/DeleteDocumentCascadeTest.java`

**Interfaces :**
- Consomme : `ExtractDocumentText` (T7), `Document.markExtractionFailed` (T2),
  `DocumentExtractionException` (T1).
- Produit :
  - `record MarkDocumentExtractionFailed(UUID documentId, UUID ownerId, String reason) implements Command`

- [ ] **Étape 1 : Écrire le test de la commande d'échec**

`.../application/command/MarkDocumentExtractionFailedTest.java` :

```java
    @Test
    void marque_le_document_en_echec_avec_son_motif() {
        Document document = unDocumentDepose("notes.txt", Fixtures.BRUT_TXT);

        commandBus.dispatch(new MarkDocumentExtractionFailed(
                document.getId(), document.getOwnerId(), "Ce document ne contient pas de texte exploitable."));

        assertThat(documentRepository
                        .findByIdAndOwnerId(document.getId(), document.getOwnerId())
                        .orElseThrow())
                .satisfies(relu -> {
                    assertThat(relu.getStatus()).isEqualTo(DocumentStatus.FAILED);
                    assertThat(relu.getErrorMessage()).isEqualTo("Ce document ne contient pas de texte exploitable.");
                });
    }

    @Test
    void reste_silencieux_sur_un_document_disparu() {
        // Dernier appel du test : le refus marque la transaction englobante rollback-only.
        assertThatExceptionOfType(DocumentNotFoundException.class)
                .isThrownBy(() -> commandBus.dispatch(
                        new MarkDocumentExtractionFailed(UUID.randomUUID(), UUID.randomUUID(), "Un motif.")));
    }
```

Même montage que `ExtractDocumentTextTest` : `@SpringBootTest`,
`@Import(TestcontainersConfiguration.class)`, `@Transactional`, nettoyage des originaux en
`@AfterEach`.

- [ ] **Étape 2 : Écrire le test de bout en bout du worker**

Remplacer dans `KnowledgeEventListenerTest` le test qui se contentait de lire le journal.
La classe garde ses annotations — `@ActiveProfiles("worker")` **et**
`webEnvironment = NONE` — et **ne prend pas** `@Transactional` : elle observe des commits.

```java
    @Test
    void extrait_le_texte_du_document_dont_le_depot_est_annonce() {
        Document document = unDocumentDeposeAvec("structure.md", Fixtures.STRUCTURE_MD);

        publie(new DocumentUploaded(document.getId(), document.getOwnerId(), Instant.now()));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(documentTextRepository.findByDocumentId(document.getId()))
                    .get()
                    .satisfies(texte -> assertThat(texte.getBlocks()).isNotEmpty());
            assertThat(statutDe(document)).isEqualTo(DocumentStatus.EXTRACTED);
        });
    }

    @Test
    void marque_le_document_en_echec_quand_l_extraction_refuse() {
        Document document = unDocumentDeposeAvec("scan.pdf", Fixtures.NUMERISE_PDF);

        publie(new DocumentUploaded(document.getId(), document.getOwnerId(), Instant.now()));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(statutDe(document)).isEqualTo(DocumentStatus.FAILED);
            assertThat(motifDe(document)).contains("pas de texte exploitable");
        });
        assertThat(documentTextRepository.findByDocumentId(document.getId())).isEmpty();
    }

    @Test
    void n_expose_pas_le_message_d_une_panne_technique() {
        // L'original est absent du disque : la ligne existe, le fichier non.
        Document document = unDocumentSansOriginal("notes.txt");

        publie(new DocumentUploaded(document.getId(), document.getOwnerId(), Instant.now()));

        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(motifDe(document)).contains("n'a pas pu être lu"));
    }

    @Test
    void ne_double_pas_le_texte_quand_l_evenement_est_livre_deux_fois() {
        Document document = unDocumentDeposeAvec("structure.md", Fixtures.STRUCTURE_MD);
        DocumentUploaded annonce = new DocumentUploaded(document.getId(), document.getOwnerId(), Instant.now());

        publie(annonce);
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(statutDe(document)).isEqualTo(DocumentStatus.EXTRACTED));
        publie(annonce);

        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(statutDe(document)).isEqualTo(DocumentStatus.EXTRACTED);
            assertThat(documentTextRepository.findByDocumentId(document.getId())).isPresent();
        });
    }
```

`publie(...)` envoie sur l'exchange `domain.events` avec la clé et l'en-tête de type dérivés
par `DomainEventNames` — reprendre la méthode que la classe emploie déjà pour son test de
réception, ne pas en écrire une seconde.

**Nettoyage, obligatoire ici :** la classe n'est pas `@Transactional`, donc rien n'annule ce
qu'elle écrit. Un `@AfterEach` doit effacer les documents créés **et** les originaux
(`KnowledgeFixture.videLesOriginaux`), comme le font déjà les tests du socle.

- [ ] **Étape 3 : Écrire le test de la cascade**

`.../application/command/DeleteDocumentCascadeTest.java` — la seule vérification que
`ON DELETE CASCADE` fonctionne réellement. **Pas de `@Transactional` sur la classe** : dans
une transaction, Hibernate rendrait le `DocumentText` depuis son cache de premier niveau sans
jamais interroger la base, et le test passerait au vert quelle que soit la migration.

```java
    @Test
    void la_suppression_d_un_document_emporte_son_texte_extrait() {
        Document document = unDocumentDepose("notes.txt", Fixtures.BRUT_TXT);
        commandBus.dispatch(new ExtractDocumentText(document.getId(), document.getOwnerId()));
        assertThat(documentTextRepository.findByDocumentId(document.getId())).isPresent();

        commandBus.dispatch(new DeleteDocument(document.getId(), document.getOwnerId()));

        assertThat(documentTextRepository.findByDocumentId(document.getId())).isEmpty();
    }
```

Nettoyage explicite en `@AfterEach` : documents restants et originaux.

- [ ] **Étape 4 : Lancer les trois tests, vérifier qu'ils échouent**

```bash
docker compose down
gtest test --tests "…MarkDocumentExtractionFailedTest" --tests "…KnowledgeEventListenerTest" --tests "…DeleteDocumentCascadeTest"
```

Attendu : ÉCHEC de compilation pour les deux premiers
(`cannot find symbol: class MarkDocumentExtractionFailed`). `DeleteDocumentCascadeTest` doit,
lui, **échouer sur une assertion** et non à la compilation : il n'utilise que du code
existant, et c'est la cascade qu'il met à l'épreuve.

- [ ] **Étape 5 : Écrire la commande d'échec et son handler**

`.../application/command/MarkDocumentExtractionFailed.java` :

```java
package xyz.sterenn.secondbrain.knowledge.application.command;

import java.util.UUID;
import xyz.sterenn.secondbrain.shared.bus.Command;

/**
 * Consigner qu'un traitement a échoué, et pourquoi.
 *
 * <p>Une commande à part, et non un appel dans le handler d'extraction : elle est dispatchée
 * <strong>après</strong> que la transaction de l'extraction a été annulée, donc dans une
 * transaction à elle. Un {@code markExtractionFailed} écrit dans la transaction annulée
 * disparaîtrait avec elle, et le document resterait éternellement en attente — voir
 * ADR-0028.
 *
 * <p>{@code reason} est <strong>affichable tel quel</strong> : c'est l'appelant qui garantit
 * qu'aucune trace technique n'y voyage. Voir {@code KnowledgeEventListener}.
 */
public record MarkDocumentExtractionFailed(UUID documentId, UUID ownerId, String reason) implements Command {}
```

`.../application/command/MarkDocumentExtractionFailedHandler.java` :

```java
package xyz.sterenn.secondbrain.knowledge.application.command;

import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DocumentNotFoundException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.shared.bus.CommandHandler;

/**
 * Pose {@code FAILED} et son motif. Rien d'autre : pas d'événement, pas de nettoyage.
 *
 * <p>Le texte partiel n'a pas à être effacé — l'extraction est tout ou rien, et sa
 * transaction annulée n'a rien laissé derrière elle.
 */
@Component
public class MarkDocumentExtractionFailedHandler implements CommandHandler<MarkDocumentExtractionFailed> {

    private final DocumentRepository documentRepository;

    public MarkDocumentExtractionFailedHandler(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    @Override
    public void handle(MarkDocumentExtractionFailed command) {
        Document document = documentRepository
                .findByIdAndOwnerId(command.documentId(), command.ownerId())
                .orElseThrow(DocumentNotFoundException::new);

        document.markExtractionFailed(command.reason());
        documentRepository.save(document);
    }
}
```

- [ ] **Étape 6 : Réécrire le listener**

`.../infrastructure/messaging/KnowledgeEventListener.java`, remplacement complet :

```java
package xyz.sterenn.secondbrain.knowledge.infrastructure.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.application.command.ExtractDocumentText;
import xyz.sterenn.secondbrain.knowledge.application.command.MarkDocumentExtractionFailed;
import xyz.sterenn.secondbrain.knowledge.domain.event.DocumentTextExtracted;
import xyz.sterenn.secondbrain.knowledge.domain.event.DocumentUploaded;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DocumentExtractionException;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;

/**
 * Adapter entrant : consomme la queue du contexte {@code knowledge}.
 *
 * <p>Un listener par contexte, un handler par événement. La queue reçoit tout ce que le
 * contexte annonce ({@code knowledge.#}), et deux classes {@code @RabbitListener} sur la même
 * queue se disputeraient les messages — celle qui ne connaît pas le type rejetterait sans
 * requeue, et l'événement serait perdu. D'où le {@code @RabbitListener} sur la classe et un
 * {@code @RabbitHandler} par type : c'est l'en-tête de type qui choisit la méthode.
 *
 * <p>{@code @Profile("worker")} : l'API publie, elle ne consomme jamais.
 */
@Component
@Profile("worker")
@RabbitListener(queues = KnowledgeMessagingConfiguration.KNOWLEDGE_EVENTS_QUEUE)
public class KnowledgeEventListener {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeEventListener.class);

    /** Ce qu'on montre quand l'échec n'est pas un refus métier : rien de la panne elle-même. */
    private static final String ECHEC_INATTENDU = "Le traitement de ce document a échoué de façon inattendue.";

    private final CommandBus commandBus;

    public KnowledgeEventListener(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    /**
     * Un document vient d'être déposé : on en extrait le texte.
     *
     * <p><strong>Le {@code catch} est la raison d'être de cette méthode.</strong> Le bus
     * ouvre la transaction et l'annule sur la moindre exception ; marquer l'échec depuis le
     * handler d'extraction le ferait disparaître avec le rollback, et le document resterait
     * éternellement en attente. La seconde commande ouvre donc sa <em>propre</em>
     * transaction. Voir ADR-0028.
     *
     * <p>Et l'exception n'est <strong>pas relevée</strong> : rejeter le message n'apporterait
     * rien, l'issue du traitement étant déjà en base. La relever ne produirait qu'une pile de
     * plus dans le journal.
     *
     * <p>Si la seconde commande échoue à son tour, elle, remonte : le message est rejeté sans
     * remise en file ({@code default-requeue-rejected=false}) et le document reste
     * {@code PENDING}. C'est le seul trou, il est journalisé, et il relève du même arbitrage
     * qu'ADR-0023 — on ne construit pas de filet au filet.
     */
    @RabbitHandler
    public void on(DocumentUploaded event) {
        try {
            commandBus.dispatch(new ExtractDocumentText(event.documentId(), event.ownerId()));
        } catch (RuntimeException echec) {
            log.error("Extraction du document {} en échec", event.documentId(), echec);
            commandBus.dispatch(
                    new MarkDocumentExtractionFailed(event.documentId(), event.ownerId(), motif(echec)));
        }
    }

    /**
     * Le texte d'un document vient d'être extrait.
     *
     * <p>Ce handler ne fait rien d'autre que journaliser, et il doit pourtant exister : un
     * type déclaré dans {@code DomainEventRegistration} mais sans {@code @RabbitHandler} est
     * refusé par Spring AMQP et rejeté comme un type inconnu. RAG-5 remplacera cette ligne
     * par {@code commandBus.dispatch(new ChunkDocumentText(...))}.
     */
    @RabbitHandler
    public void on(DocumentTextExtracted event) {
        log.info(
                "Événement knowledge.document-text.extracted reçu pour le document {} : {} blocs",
                event.documentId(),
                event.blockCount());
    }

    /**
     * Un refus métier porte un message affichable tel quel ; le reste n'en porte aucun qu'on
     * puisse montrer. Le message d'une {@code NullPointerException} n'a rien à faire sous les
     * yeux de l'utilisateur — il est dans le journal, où il sert.
     */
    private static String motif(RuntimeException echec) {
        return echec instanceof DocumentExtractionException refusMetier ? refusMetier.getMessage() : ECHEC_INATTENDU;
    }
}
```

- [ ] **Étape 7 : Lancer les tests, vérifier qu'ils passent**

```bash
gtest test --tests "xyz.sterenn.secondbrain.knowledge.*"
```

Attendu : SUCCÈS.

**Si `marque_le_document_en_echec_quand_l_extraction_refuse` laisse le document en
`PENDING` :** la seconde commande a échoué elle aussi. Lire le journal capturé — le plus
probable est un `DocumentNotFoundException`, donc un `ownerId` qui ne correspond pas.

**Si `DeleteDocumentCascadeTest` échoue encore après cette tâche :** la cascade est en cause,
pas le code. Vérifier la migration V7 — les deux `ON DELETE CASCADE` — et non
`DeleteDocumentHandler`, qui n'a pas à connaître le texte.

- [ ] **Étape 8 : Vérifier sur la pile de développement**

Aucun test ne constate que le conteneur `worker` démarre bien sans serveur HTTP et traite un
vrai dépôt de bout en bout. C'est le geste manuel du ticket, et il se fait ici :

```bash
docker compose up --build -d
docker compose logs -f worker
```

Dans un autre terminal : se connecter sur <http://localhost:8080/>, déposer un `.pdf`, un
`.docx` et un `.md` **réels et personnels** depuis l'écran Documents. Le journal du worker
doit montrer un `knowledge.document-text.extracted` par document, et l'écran doit passer de
« En attente de traitement » à « Texte extrait ».

Déposer aussi un document difficile — un scan, un export mal formé. Ce qu'il révèle est un
ticket, pas une correction à glisser ici : le ticket prévient que « c'est le ticket qui peut
remettre en cause les suivants ».

```bash
docker compose down
```

- [ ] **Étape 9 : Écrire l'ADR-0028**

`docs/decisions/0028-l-echec-d-extraction-s-ecrit-hors-de-la-transaction-annulee.md`, depuis
la **décision 5** de la spec :

- Contexte : le bus annule sa transaction sur toute exception ; le statut d'échec écrit
  dedans disparaît avec elle.
- Options : seconde commande dans sa propre transaction, message acquitté · seconde commande
  puis exception relevée pour rejeter le message · dead-letter queue et rejeu ·
  `@Transactional(REQUIRES_NEW)` sur un handler.
- Décision : **seconde commande, message acquitté**, parce que l'issue du traitement est déjà
  en base et que rejeter n'apprend rien de plus. Le `REQUIRES_NEW` est écarté pour une raison
  dure : annoter un handler le fait proxifier en JDK proxy, ce qui casse la résolution de son
  type générique au démarrage — la règle backend l'interdit déjà.
- Conséquences — Bien : un document ne reste jamais en attente sans explication. Mal : si la
  seconde commande échoue, le document reste `PENDING` et le message est perdu ; un
  traitement n'est jamais rejoué, donc une panne passagère du disque se solde par un `FAILED`
  définitif jusqu'à la réextraction de RAG-7.
- Condition de réouverture : le jour où une panne passagère devient fréquente au point que
  les `FAILED` à tort se comptent. La sortie sera alors un rejeu borné côté worker, pas une
  dead-letter queue — ADR-0023 tient toujours.
- Vérification : `KnowledgeEventListenerTest.marque_le_document_en_echec_quand_l_extraction_refuse`.

- [ ] **Étape 10 : Mettre `CLAUDE.md` à jour**

Deux endroits :

1. Table des ADR :

```markdown
| [0028](docs/decisions/0028-l-echec-d-extraction-s-ecrit-hors-de-la-transaction-annulee.md) | L'échec d'extraction s'écrit hors de la transaction annulée |
```

2. Section « Les événements métier (`shared/event`) et le rôle worker », après le paragraphe
sur le rejet sans remise en file — remplacer « Pas de dead-letter queue, pas de retry : un
échec doit finir en `FAILED` sur le document, pas être rejoué. » par :

```markdown
Pas de dead-letter queue, pas de retry : un échec finit en `FAILED` sur le document, pas
rejoué. **Et il y finit depuis une seconde transaction** — `KnowledgeEventListener` rattrape
l'exception, dispatche `MarkDocumentExtractionFailed`, puis acquitte. Un statut d'erreur
écrit dans la transaction que le bus vient d'annuler disparaîtrait avec elle, et le document
resterait éternellement en attente (ADR-0028).
```

- [ ] **Étape 11 : Formater et committer**

```bash
make format-back
gtest test
git add src/main/java src/test/java \
        docs/decisions/0028-l-echec-d-extraction-s-ecrit-hors-de-la-transaction-annulee.md CLAUDE.md
git commit -m "feat: le worker extrait le texte, et l'échec survit à la transaction annulée"
```

---

## Tâche 9 : L'écran ne montre plus `FAILED` en anglais brut

Le ticket ne demande pas d'écran — c'est RAG-11. Mais `DocumentsView` existe déjà et ne
connaît que `PENDING` : sans cette tâche, un document extrait afficherait `EXTRACTED`, et un
document en échec `FAILED`, sans dire pourquoi. Le strict minimum, et rien de plus.

**Fichiers :**
- Modifier : `src/main/java/xyz/sterenn/secondbrain/knowledge/application/query/DocumentView.java`
- Modifier : `frontend/src/views/DocumentsView.vue`
- Test : `.../infrastructure/web/ListDocumentsControllerTest.java` (modifié)

**Interfaces :**
- Consomme : `Document.getErrorMessage()` (T2), `DocumentStatus.EXTRACTED`/`FAILED` (T2).
- Produit : `DocumentView(UUID id, String filename, DocumentStatus status, String errorMessage, Instant createdAt)`

- [ ] **Étape 1 : Écrire le test de la projection**

Ajouter à `ListDocumentsControllerTest`, dans le style de la classe :

```java
    @Test
    void expose_le_motif_d_un_document_en_echec() throws Exception {
        Document document = unDocumentDepose();
        document.markExtractionFailed("Ce document ne contient pas de texte exploitable.");
        documentRepository.save(document);

        mockMvc.perform(get("/api/documents").header(AUTHORIZATION, "Bearer " + jeton))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("FAILED"))
                .andExpect(jsonPath("$[0].errorMessage").value("Ce document ne contient pas de texte exploitable."));
    }

    @Test
    void n_expose_aucun_motif_pour_un_document_en_attente() throws Exception {
        unDocumentDepose();

        mockMvc.perform(get("/api/documents").header(AUTHORIZATION, "Bearer " + jeton))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].errorMessage").doesNotExist());
    }
```

**`doesNotExist()` et non `isEmpty()`** : Jackson 3 sérialise un `null` par défaut, donc le
second test échouera tant que le champ n'est pas annoté. C'est voulu — il force à décider,
et la décision est de ne pas envoyer une clé vide à chaque ligne de la liste.

- [ ] **Étape 2 : Lancer le test, vérifier qu'il échoue**

```bash
docker compose down
gtest test --tests "…ListDocumentsControllerTest"
```

Attendu : ÉCHEC, `No value at JSON path "$[0].errorMessage"`.

- [ ] **Étape 3 : Étendre la projection**

`DocumentView` :

```java
package xyz.sterenn.secondbrain.knowledge.application.query;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentStatus;

/**
 * Projection de lecture d'un document, telle que la liste l'affiche : de quoi reconnaître un
 * dépôt, savoir où il en est, et — quand ça a mal tourné — pourquoi.
 *
 * <p>Ni empreinte ni taille : la première n'apprend rien à un humain, la seconde n'a pas
 * encore d'écran qui la demande. Une projection grandit au rythme des besoins, pas de
 * l'agrégat.
 *
 * <p>{@code errorMessage} est omis quand il est nul plutôt qu'envoyé à {@code null} : la
 * liste porte toutes les lignes d'une base de connaissance, et la quasi-totalité n'a rien à
 * expliquer. C'est le seul champ optionnel de cette projection, d'où l'annotation sur lui et
 * non sur le record — la forme des autres champs ne se négocie pas.
 *
 * <p>Le message vient du serveur et est affichable tel quel : le front ne le réécrit pas.
 */
public record DocumentView(
        UUID id,
        String filename,
        DocumentStatus status,
        @JsonInclude(JsonInclude.Include.NON_NULL) String errorMessage,
        Instant createdAt) {

    /** Seule conversion depuis l'agrégat, partagée par les handlers qui lisent un document. */
    public static DocumentView of(Document document) {
        return new DocumentView(
                document.getId(),
                document.getFilename(),
                document.getStatus(),
                document.getErrorMessage(),
                document.getCreatedAt());
    }
}
```

**Attention à l'import** : Spring Boot 4 est passé à Jackson 3, dont le databind vit sous
`tools.jackson`, mais **les annotations restent sous `com.fasterxml.jackson.annotation`**.

- [ ] **Étape 4 : Lancer le test, vérifier qu'il passe**

```bash
gtest test --tests "xyz.sterenn.secondbrain.knowledge.infrastructure.web.*"
```

Attendu : SUCCÈS.

- [ ] **Étape 5 : Ajouter les deux libellés et le motif à l'écran**

Dans `frontend/src/views/DocumentsView.vue`, le tableau des libellés :

```js
// Le libellé d'une énumération sérialisée par l'API est une affaire d'écran, pas une règle
// du serveur : ADR-0022 assume cette copie. Le motif d'échec, lui, vient du serveur et
// s'affiche tel quel — c'est un message d'erreur, et le front n'en réécrit aucun.
const STATUS_LABELS = {
  PENDING: 'En attente de traitement',
  EXTRACTED: 'Texte extrait',
  FAILED: 'Traitement en échec',
}
```

Et la colonne « Statut » :

```vue
      <Column header="Statut">
        <template #body="{ data }">
          {{ STATUS_LABELS[data.status] ?? data.status }}
          <div v-if="data.errorMessage" class="document-error">{{ data.errorMessage }}</div>
        </template>
      </Column>
```

Le style, dans le `<style scoped>` du fichier — les couleurs passent par les tokens d'Aura,
les espacements par ceux du projet, et **aucun `rem` nu** :

```css
.document-error {
  margin-top: var(--sb-space-xs);
  font-size: var(--sb-text-small);
  color: var(--p-text-muted-color);
}
```

**Attention au nommage :** `DocumentsView` porte déjà une `ref` nommée `errorMessage` pour la
bannière d'erreur de la page. Celle de la ligne est `data.errorMessage`, dans la portée du
slot — les deux cohabitent sans se marcher dessus, mais ne pas déstructurer le slot en
`{ data: { errorMessage } }`, qui masquerait la `ref` et rendrait le template illisible.

- [ ] **Étape 6 : Vérifier le front**

```bash
gfront npm run test:unit
gfront npm run build
```

Attendu : SUCCÈS des deux. `npm run build` est le seul contrôle qui compile les templates —
aucun test ne les rend (ADR-0016).

Puis, à l'œil, sur la pile de développement : un document en `FAILED` doit montrer
« Traitement en échec » et son motif en dessous, en texte atténué.

- [ ] **Étape 7 : Formater et committer**

```bash
make format
gtest test
gfront npm run test:unit
git add src/main/java src/test/java frontend/src/views/DocumentsView.vue
git commit -m "feat: l'écran des documents dit où en est le traitement, et pourquoi il a échoué"
```

---

## Ce que ce plan ne fait pas

À dire explicitement, pour qu'aucune de ces absences ne passe pour un oubli :

- **Aucun vrai document personnel n'entre dans la suite de tests.** Le ticket en demandait
  cinq à dix en intégration continue ; le socle est fabriqué (spec, décision 9). La
  vérification sur documents réels est l'étape 8 de la tâche 8, à la main, et ce qu'elle
  révélera sera un ticket.
- **Les frontières de paragraphe sont perdues dans un PDF** (ADR-0027). RAG-5 découpera ces
  sections à la phrase.
- **Un titre immédiatement suivi d'un autre titre est perdu** (ADR-0024). `headingLevel`
  existe pour qu'un ticket ultérieur reconstruise le chemin de section sans réextraire.
- **Pas d'OCR, pas de tableaux, pas d'images** — hors périmètre du ticket.
- **Pas de découpage en extraits** (RAG-5), **pas de vectorisation ni de statut `READY`**
  (RAG-6), **pas de réextraction d'un document modifié** (RAG-7). `deleteByDocumentId` existe
  au port pour l'idempotence de la redélivrance ; RAG-7 s'en servira sans le créer.
- **Pas d'écran de suivi** (RAG-11). La tâche 9 se borne à ce qui empêcherait l'écran existant
  d'afficher de l'anglais brut.
- **Pas de détection de jeu de caractères** : UTF-8, puis ISO-8859-1 en repli, et c'est tout.
