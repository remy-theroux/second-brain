package xyz.sterenn.secondbrain.knowledge.infrastructure.persistence;

import java.util.UUID;

interface ChunkMatchRow {

    UUID getDocumentId();

    String getFilename();

    int getChunkPosition();

    String getHeading();

    String getChunkText();

    double getSimilarity();
}
