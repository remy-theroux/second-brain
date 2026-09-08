package xyz.sterenn.secondbrain.knowledge.infrastructure.drive;

import java.util.List;

/** The union of the {@code fields} this project asks for; a field left out comes back null. */
record GoogleFileResponse(
        String id,
        String name,
        String mimeType,
        Boolean trashed,
        List<String> parents,
        String size,
        String webViewLink,
        String modifiedTime) {}
