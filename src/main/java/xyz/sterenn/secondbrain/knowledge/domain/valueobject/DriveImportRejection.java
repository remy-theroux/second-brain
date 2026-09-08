package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** See ADR-0002: the deviation that allows JPA annotations in the domain. */
@Embeddable
public class DriveImportRejection {

    public static final int MAX_FILENAME_LENGTH = 255;

    public static final int MAX_REASON_LENGTH = 255;

    @Column(name = "drive_file_id", nullable = false, length = DriveProvenance.MAX_FILE_ID_LENGTH)
    private String driveFileId;

    @Column(nullable = false, length = MAX_FILENAME_LENGTH)
    private String filename;

    @Column(nullable = false, length = MAX_REASON_LENGTH)
    private String reason;

    protected DriveImportRejection() {}

    private DriveImportRejection(String driveFileId, String filename, String reason) {
        this.driveFileId = driveFileId;
        this.filename = filename;
        this.reason = reason;
    }

    public static DriveImportRejection of(DriveFile file, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A file left out without a reason is not a reason to leave it out");
        }
        return new DriveImportRejection(
                file.id(), bounded(file.name(), MAX_FILENAME_LENGTH), bounded(reason.strip(), MAX_REASON_LENGTH));
    }

    private static String bounded(String value, int maxLength) {
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    public String getDriveFileId() {
        return driveFileId;
    }

    public String getFilename() {
        return filename;
    }

    public String getReason() {
        return reason;
    }
}
