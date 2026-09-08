package xyz.sterenn.secondbrain.knowledge.infrastructure.drive;

import java.util.List;

record GoogleChangeListResponse(List<GoogleChangeResponse> changes, String nextPageToken, String newStartPageToken) {}
