package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.util.List;

/**
 * Everything one run of the change feed brought, and the token the next run starts from. The
 * token is handed back rather than kept along the way: written page by page, it would lose the
 * changes of a page whose processing failed.
 */
public record DriveChangePage(List<DriveChange> changes, String newStartPageToken) {

    public DriveChangePage {
        changes = changes == null ? List.of() : List.copyOf(changes);
    }
}
