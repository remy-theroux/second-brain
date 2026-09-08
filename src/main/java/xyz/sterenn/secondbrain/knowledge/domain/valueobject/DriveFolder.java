package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

/** A folder as Drive hands it back: an identifier that survives a rename, and a name to show. */
public record DriveFolder(String id, String name) {

    /** Drive names the top of a Drive by a keyword, not by an empty parent. */
    public static final String ROOT = "root";

    public DriveFolder {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("The Drive identifier of a folder is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("The name of a Drive folder is required");
        }
    }
}
