package xyz.sterenn.secondbrain.knowledge.infrastructure.extraction;

import java.util.List;
import xyz.sterenn.secondbrain.knowledge.domain.exception.UnextractableDocumentException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.UnreadableDocumentException;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ExtractedText;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ExtractedTextBuilder;

/**
 * Une section telle qu'un extracteur la lit, avant toute normalisation.
 *
 * <p>Elle existe pour une raison de structure, pas de confort : un extracteur parcourt son
 * fichier <strong>dans un {@code try}</strong> qui traduit les pannes de la bibliothèque en
 * {@link UnreadableDocumentException}. Construire un type du domaine à l'intérieur de ce
 * {@code try} y ferait passer {@link UnextractableDocumentException} — un refus métier —
 * pour une panne de lecture. Les sections brutes sortent donc du {@code try}, et
 * {@link #assemble} les convertit après.
 */
record Section(String heading, int level, String body) {

    /** Une section sans titre : avant le premier, ou dans un document qui n'en a pas. */
    static Section untitled(String body) {
        return new Section("", 0, body);
    }

    /**
     * @throws UnextractableDocumentException si rien n'en ressort, ou si le total reste sous
     *     le plancher
     */
    static ExtractedText assemble(List<Section> sections) {
        ExtractedTextBuilder blocs = new ExtractedTextBuilder();
        sections.forEach(section -> blocs.section(section.heading(), section.level(), section.body()));
        return blocs.build();
    }
}
