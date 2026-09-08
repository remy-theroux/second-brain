package xyz.sterenn.secondbrain.knowledge.infrastructure.persistence;

import java.util.UUID;

interface WatchedFolderDocumentCountRow {

    UUID getWatchedFolderId();

    long getDocumentCount();
}
