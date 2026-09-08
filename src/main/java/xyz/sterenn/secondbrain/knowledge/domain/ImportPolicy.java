package xyz.sterenn.secondbrain.knowledge.domain;

public final class ImportPolicy {

    /**
     * The same ceiling as {@code spring.servlet.multipart.max-file-size}: two ways into one base
     * must not have two ceilings.
     */
    public static final long MAX_FILE_SIZE = 20L * 1024 * 1024;

    private ImportPolicy() {}
}
