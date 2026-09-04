package xyz.sterenn.secondbrain.knowledge.domain.exception;

public class UnsupportedDocumentFormatException extends RuntimeException {

    public UnsupportedDocumentFormatException(String acceptedExtensions) {
        super("Ce format de fichier n'est pas pris en charge. Formats acceptés : " + acceptedExtensions + ".");
    }
}
