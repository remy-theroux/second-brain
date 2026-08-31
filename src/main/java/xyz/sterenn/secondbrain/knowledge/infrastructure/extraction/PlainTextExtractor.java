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
