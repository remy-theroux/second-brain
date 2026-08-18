package xyz.sterenn.secondbrain.knowledge.application.command;

import java.util.UUID;
import xyz.sterenn.secondbrain.shared.bus.Command;

/**
 * Dépôt d'un document dans la base de connaissance d'un compte.
 *
 * <p>Le contenu voyage en octets bruts, tel que reçu : c'est le handler qui en déduit le
 * format et l'empreinte. Une commande transporte l'intention, elle ne la valide pas.
 *
 * <p>{@link #toString()} est redéfini pour rendre le nom et la taille, jamais le contenu :
 * un log ou un message d'échec d'assertion qui déverserait vingt mégaoctets d'octets
 * serait illisible, et pourrait rendre en clair un document privé.
 *
 * @param ownerId  compte propriétaire, tel que le jeton d'accès le désigne
 * @param filename nom du fichier tel que déposé, non normalisé
 * @param content  octets du fichier
 */
public record UploadDocument(UUID ownerId, String filename, byte[] content) implements Command {

    @Override
    public String toString() {
        return "UploadDocument[ownerId=" + ownerId + ", filename=" + filename + ", content=" + content.length
                + " octets]";
    }
}
