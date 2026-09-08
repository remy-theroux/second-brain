package xyz.sterenn.secondbrain.knowledge.domain;

public final class ImportPolicy {

    /**
     * The same ceiling as {@code spring.servlet.multipart.max-file-size}: two ways into one base
     * must not have two ceilings.
     */
    public static final long MAX_FILE_SIZE = 20L * 1024 * 1024;

    /**
     * Google's ceiling on an export, not this project's: it is suffered, not chosen, and it
     * cannot be anticipated either — {@code files.list} tells no size for a native Doc.
     */
    public static final long MAX_GOOGLE_EXPORT_SIZE = 10L * 1024 * 1024;

    private static final long MEGABYTE = 1024L * 1024;

    private ImportPolicy() {}

    public static boolean isTooLarge(long sizeBytes) {
        return sizeBytes > MAX_FILE_SIZE;
    }

    /** Displayable as it stands: it is what the owner reads next to the file left out. */
    public static String tooLargeReason() {
        return "Ce fichier dépasse " + MAX_FILE_SIZE / MEGABYTE + " Mo.";
    }

    public static String exportRefusedReason() {
        return "Google refuse d'exporter ce document, qui dépasse " + MAX_GOOGLE_EXPORT_SIZE / MEGABYTE + " Mo.";
    }
}
