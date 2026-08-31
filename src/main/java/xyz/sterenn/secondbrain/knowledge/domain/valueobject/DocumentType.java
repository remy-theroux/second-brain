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
