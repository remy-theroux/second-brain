package xyz.sterenn.secondbrain.knowledge.infrastructure.drive;

/** A removed change carries no {@code file} at all: Drive says the identifier and nothing more. */
record GoogleChangeResponse(String fileId, Boolean removed, GoogleFileResponse file) {}
