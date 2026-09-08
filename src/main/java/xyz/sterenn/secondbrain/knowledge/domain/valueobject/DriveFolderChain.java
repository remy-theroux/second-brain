package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.util.List;

/**
 * The folders above one folder, nearest first, and whether the climb reached the top of the Drive.
 * A chain that stopped short — a parent Drive no longer hands back, a link of a shared chain
 * nothing may read — must never be read as "under no watched folder": that reading is a removal,
 * and it would erase the mirror on a hiccup.
 */
public record DriveFolderChain(List<String> folders, boolean reachedTheTop) {

    public DriveFolderChain {
        folders = folders == null ? List.of() : List.copyOf(folders);
    }

    /** Every folder up to the top: what is not named here is above nothing this Drive holds. */
    public static DriveFolderChain upToTheTop(List<String> folders) {
        return new DriveFolderChain(folders, true);
    }

    /** What was read before the climb broke: a folder found in it is still certain, its absence is not. */
    public static DriveFolderChain stoppedShort(List<String> folders) {
        return new DriveFolderChain(folders, false);
    }
}
