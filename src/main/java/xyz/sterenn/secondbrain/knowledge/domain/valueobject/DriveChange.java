package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.util.List;
import java.util.Optional;

/**
 * One change as Drive hands it back. Two ways of being gone, and they are not the same fact:
 * {@code removed} says the file left the visible perimeter — deleted, or lost with a share — and
 * carries <strong>no file at all</strong>, hence no parents; {@code trashed} says it sits in the
 * bin, and Drive still describes it. Both lead to the same removal, and nothing may dereference
 * the first.
 */
public record DriveChange(String fileId, boolean removed, boolean trashed, DriveFile file, List<String> parents) {

    public DriveChange {
        if (fileId == null || fileId.isBlank()) {
            throw new IllegalArgumentException("The Drive identifier of a change is required");
        }
        parents = parents == null ? List.of() : List.copyOf(parents);
    }

    public static DriveChange removed(String fileId) {
        return new DriveChange(fileId, true, false, null, List.of());
    }

    public static DriveChange trashed(String fileId) {
        return new DriveChange(fileId, false, true, null, List.of());
    }

    public static DriveChange of(String fileId, DriveFile file, List<String> parents) {
        return new DriveChange(fileId, false, false, file, parents);
    }

    public boolean isGone() {
        return removed || trashed;
    }

    /** Empty for a file that is gone, and for one in a format this base could never open. */
    public Optional<DriveFile> readableFile() {
        return Optional.ofNullable(file);
    }
}
