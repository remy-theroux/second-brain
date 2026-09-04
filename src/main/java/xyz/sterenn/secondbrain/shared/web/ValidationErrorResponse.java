package xyz.sterenn.secondbrain.shared.web;

import java.util.Map;

public record ValidationErrorResponse(Map<String, String> errors) {}
