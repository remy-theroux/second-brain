package xyz.sterenn.secondbrain.knowledge.infrastructure.web;

import java.util.UUID;

public record DuplicateDocumentResponse(String message, UUID existingDocumentId) {}
