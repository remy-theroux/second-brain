package xyz.sterenn.secondbrain.knowledge.domain.exception;

/**
 * Le fichier déposé n'est pas d'un format que la base de connaissance sait accueillir.
 *
 * <p>Le message énonce les formats acceptés : un refus qui se contenterait de dire non
 * obligerait l'utilisateur à deviner, ou à ouvrir la documentation.
 */
public class UnsupportedDocumentFormatException extends RuntimeException {

    public UnsupportedDocumentFormatException(String acceptedExtensions) {
        super("Ce format de fichier n'est pas pris en charge. Formats acceptés : " + acceptedExtensions + ".");
    }
}
