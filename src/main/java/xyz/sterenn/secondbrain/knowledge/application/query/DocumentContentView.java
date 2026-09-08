package xyz.sterenn.secondbrain.knowledge.application.query;

import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;

public record DocumentContentView(String filename, DocumentFormat format, byte[] content) {}
