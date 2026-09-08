package xyz.sterenn.secondbrain.knowledge.infrastructure.drive;

import java.util.List;

/** The nested {@code reason} of a Drive error body: on a 403, it alone tells a quota from a refusal. */
record GoogleApiErrorResponse(ErrorBody error) {

    record ErrorBody(List<ErrorDetail> errors) {}

    record ErrorDetail(String reason) {}
}
