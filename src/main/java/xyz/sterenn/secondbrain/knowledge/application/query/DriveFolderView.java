package xyz.sterenn.secondbrain.knowledge.application.query;

import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveFolder;

public record DriveFolderView(String id, String name) {

    public static DriveFolderView of(DriveFolder folder) {
        return new DriveFolderView(folder.id(), folder.name());
    }
}
