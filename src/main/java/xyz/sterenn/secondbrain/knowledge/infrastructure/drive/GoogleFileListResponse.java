package xyz.sterenn.secondbrain.knowledge.infrastructure.drive;

import java.util.List;

record GoogleFileListResponse(List<GoogleFileResponse> files, String nextPageToken) {}
