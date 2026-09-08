package xyz.sterenn.secondbrain.knowledge.domain;

public final class ImportPolicy {

    /**
     * The same ceiling as {@code spring.servlet.multipart.max-file-size}: two ways into one base
     * must not have two ceilings.
     */
    public static final long MAX_FILE_SIZE = 20L * 1024 * 1024;

    private static final long MEGABYTE = 1024L * 1024;

    private ImportPolicy() {}

    public static boolean isTooLarge(long sizeBytes) {
        return sizeBytes > MAX_FILE_SIZE;
    }

    /** Displayable as it stands: it is what the owner reads next to the file left out. */
    public static String tooLargeReason() {
        return "Ce fichier dépasse " + MAX_FILE_SIZE / MEGABYTE + " Mo.";
    }
}
