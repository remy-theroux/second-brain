package xyz.sterenn.secondbrain.knowledge.domain.exception;

import xyz.sterenn.secondbrain.knowledge.domain.ExtractionPolicy;

/**
 * Le fichier s'est ouvert, mais il n'en sort pas assez de texte pour être exploitable.
 *
 * <p>C'est le cas du PDF issu d'une numérisation : la couche texte est absente, et
 * l'extraction ne rend qu'un numéro de page ou une mention de scanner. Le ticket exige que
 * ce cas <strong>échoue</strong> plutôt que de produire du vide en silence — le vide ne se
 * verrait qu'à la première question restée sans réponse, trois tickets plus loin.
 *
 * <p>Le seuil est {@link ExtractionPolicy#MINIMUM_USEFUL_CHARACTERS}. Voir ADR-0025.
 */
public class UnextractableDocumentException extends DocumentExtractionException {

    public UnextractableDocumentException() {
        super("Ce document ne contient pas de texte exploitable :"
                + " s'il s'agit d'une numérisation, il faudra le repasser par une reconnaissance de caractères.");
    }
}
