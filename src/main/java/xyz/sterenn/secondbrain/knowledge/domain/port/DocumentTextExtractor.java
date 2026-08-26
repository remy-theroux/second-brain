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
