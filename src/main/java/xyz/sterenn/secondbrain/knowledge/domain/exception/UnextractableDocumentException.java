package xyz.sterenn.secondbrain.knowledge.domain.exception;

public class UnextractableDocumentException extends DocumentExtractionException {

    public UnextractableDocumentException() {
        super("Ce document ne contient pas de texte exploitable :"
                + " s'il s'agit d'une numérisation, il faudra le repasser par une reconnaissance de caractères.");
    }
}
