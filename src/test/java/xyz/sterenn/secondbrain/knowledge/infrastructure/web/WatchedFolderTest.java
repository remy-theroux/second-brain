package xyz.sterenn.secondbrain.knowledge.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static xyz.sterenn.secondbrain.knowledge.FakeGoogleDriveConfiguration.FakeGoogleDrive.folder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.knowledge.FakeGoogleDriveConfiguration;
import xyz.sterenn.secondbrain.knowledge.FakeGoogleDriveConfiguration.FakeGoogleDrive;
import xyz.sterenn.secondbrain.knowledge.KnowledgeFixture;
import xyz.sterenn.secondbrain.knowledge.application.command.RecordDriveImportOutcome;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveConnection;
import xyz.sterenn.secondbrain.knowledge.domain.entity.WatchedFolder;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DriveNotConnectedException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.WatchedFolderNotFoundException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.DriveConnectionRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.WatchedFolderRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Checksum;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveConnectionStatus;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveFile;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveFolder;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveImportRejection;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveImportStatus;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveProvenance;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.RefreshToken;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.users.AccountFixture;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration.RecordingNotificationSender;
import xyz.sterenn.secondbrain.users.domain.port.AccessTokenIssuer;

@Import({
    TestcontainersConfiguration.class,
    RecordingNotificationSenderConfiguration.class,
    FakeGoogleDriveConfiguration.class
})
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WatchedFolderTest {

    private static final String PASSWORD = "chevalpile42";

    private static final String WATCHED_FOLDERS = "/api/drive/watched-folders";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private RecordingNotificationSender recordingNotificationSender;

    @Autowired
    private FakeGoogleDrive fakeGoogleDrive;

    @Autowired
    private AccessTokenIssuer accessTokenIssuer;

    @Autowired
    private DriveConnectionRepository driveConnectionRepository;

    @Autowired
    private WatchedFolderRepository watchedFolderRepository;

    @Autowired
    private DocumentRepository documentRepository;

    private UUID alice;
    private String aliceToken;

    @BeforeEach
    void prepare_a_signed_in_account() {
        recordingNotificationSender.clear();
        fakeGoogleDrive.clear();
        alice = AccountFixture.registerVerified(commandBus, recordingNotificationSender, "alice@exemple.fr", PASSWORD);
        aliceToken = KnowledgeFixture.token(accessTokenIssuer, alice);
    }

    private DriveConnection connectADrive() {
        return connectADriveFor(alice, "alice@gmail.com", "1//jeton-alice");
    }

    private DriveConnection connectADriveFor(UUID owner, String googleEmail, String refreshToken) {
        return driveConnectionRepository.save(
                DriveConnection.connect(owner, googleEmail, new RefreshToken(refreshToken), Instant.now()));
    }

    private void aDriveHolding(DriveFolder... topLevelFolders) {
        fakeGoogleDrive.put(DriveFolder.ROOT, topLevelFolders);
    }

    private void watch(String folderId) throws Exception {
        mockMvc.perform(post(WATCHED_FOLDERS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"folderId\": \"" + folderId + "\"}")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isCreated());
    }

    @Test
    void watches_a_folder_of_my_drive() throws Exception {
        connectADrive();
        aDriveHolding(folder("a1", "Notes"), folder("a2", "Factures"));

        watch("a1");

        mockMvc.perform(get(WATCHED_FOLDERS).header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].driveFolderId").value("a1"))
                .andExpect(jsonPath("$[0].name").value("Notes"))
                .andExpect(jsonPath("$[0].watchedAt").exists());
    }

    @Test
    void watches_a_second_folder_alongside_the_first() throws Exception {
        connectADrive();
        aDriveHolding(folder("a1", "Notes"), folder("a2", "Factures"));

        watch("a1");
        watch("a2");

        mockMvc.perform(get(WATCHED_FOLDERS).header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].name", containsInAnyOrder("Notes", "Factures")));
    }

    @Test
    void announces_a_folder_never_imported_without_an_outcome() throws Exception {
        connectADrive();
        aDriveHolding(folder("a1", "Notes"));

        watch("a1");

        mockMvc.perform(get(WATCHED_FOLDERS).header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lastImportAt").doesNotExist())
                .andExpect(jsonPath("$[0].lastImportStatus").doesNotExist())
                .andExpect(jsonPath("$[0].lastImportError").doesNotExist())
                .andExpect(jsonPath("$[0].documentCount").value(0))
                .andExpect(jsonPath("$[0].rejections").isEmpty());
    }

    @Test
    void announces_the_outcome_of_the_last_import_and_the_files_it_left_out() throws Exception {
        DriveConnection connection = connectADrive();
        aDriveHolding(folder("a1", "Notes"));
        watch("a1");
        recordAnImport(
                onlyWatchedFolderOf(connection).getId(),
                DriveImportStatus.SUCCEEDED,
                null,
                DriveImportRejection.of(aDriveFile("f1", "archive.pdf"), "Ce fichier dépasse 20 Mo."));

        mockMvc.perform(get(WATCHED_FOLDERS).header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lastImportAt").exists())
                .andExpect(jsonPath("$[0].lastImportStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$[0].lastImportError").doesNotExist())
                .andExpect(jsonPath("$[0].rejections.length()").value(1))
                .andExpect(jsonPath("$[0].rejections[0].filename").value("archive.pdf"))
                .andExpect(jsonPath("$[0].rejections[0].reason").value("Ce fichier dépasse 20 Mo."))
                .andExpect(jsonPath("$[0].rejections[0].driveFileId").doesNotExist());
    }

    @Test
    void announces_the_reason_a_failed_import_gave() throws Exception {
        DriveConnection connection = connectADrive();
        aDriveHolding(folder("a1", "Notes"));
        watch("a1");
        recordAnImport(
                onlyWatchedFolderOf(connection).getId(),
                DriveImportStatus.FAILED,
                "Google Drive est momentanément injoignable.");

        mockMvc.perform(get(WATCHED_FOLDERS).header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lastImportStatus").value("FAILED"))
                .andExpect(jsonPath("$[0].lastImportError").value("Google Drive est momentanément injoignable."));
    }

    @Test
    void counts_the_documents_each_watched_folder_brought() throws Exception {
        DriveConnection connection = connectADrive();
        aDriveHolding(folder("a1", "Notes"), folder("a2", "Factures"));
        watch("a1");
        watch("a2");
        UUID notes = watchedFolderNamed(connection, "Notes").getId();
        UUID invoices = watchedFolderNamed(connection, "Factures").getId();
        importADocument(notes, "compte-rendu.md", "f1");
        importADocument(notes, "journal.md", "f2");
        importADocument(invoices, "facture.md", "f3");
        // A manual upload belongs to no folder, and must not be counted by any of them.
        documentRepository.save(Document.upload(
                alice, "a-la-main.md", DocumentFormat.MARKDOWN, Checksum.of("à la main".getBytes()), 9L));

        mockMvc.perform(get(WATCHED_FOLDERS).header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='Notes')].documentCount", contains(2)))
                .andExpect(jsonPath("$[?(@.name=='Factures')].documentCount", contains(1)));
    }

    @Test
    void refuses_a_folder_already_covered_by_a_watched_ancestor() throws Exception {
        connectADrive();
        aDriveHolding(folder("a1", "Notes"));
        fakeGoogleDrive.put("a1", folder("b1", "2026"));
        fakeGoogleDrive.put("b1", folder("c1", "Janvier"));

        watch("a1");

        mockMvc.perform(post(WATCHED_FOLDERS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"folderId\": \"c1\"}")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Ce dossier est déjà couvert par « Notes »."));
    }

    @Test
    void unwatches_a_folder_without_touching_the_connection() throws Exception {
        DriveConnection connection = connectADrive();
        aDriveHolding(folder("a1", "Notes"));
        watch("a1");
        UUID watchedFolderId = onlyWatchedFolderOf(connection).getId();

        mockMvc.perform(delete(WATCHED_FOLDERS + "/" + watchedFolderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(WATCHED_FOLDERS).header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/drive/connection").header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(DriveConnectionStatus.ACTIVE.name()));
    }

    @Test
    void unwatches_a_folder_without_removing_the_documents_it_brought() throws Exception {
        DriveConnection connection = connectADrive();
        aDriveHolding(folder("a1", "Notes"));
        watch("a1");
        documentRepository.save(
                Document.upload(alice, "rapport.md", DocumentFormat.MARKDOWN, Checksum.of("# Rapport".getBytes()), 9L));

        mockMvc.perform(delete(WATCHED_FOLDERS + "/"
                                + onlyWatchedFolderOf(connection).getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/documents").header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].filename").value("rapport.md"));
    }

    @Test
    void accepts_the_import_of_a_watched_folder_without_waiting_for_it() throws Exception {
        DriveConnection connection = connectADrive();
        aDriveHolding(folder("a1", "Notes"));
        watch("a1");

        // 202 and not 201: the walk has not started when the answer leaves, and nothing was created.
        mockMvc.perform(post(WATCHED_FOLDERS + "/"
                                + onlyWatchedFolderOf(connection).getId() + "/import")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isAccepted());
    }

    @Test
    void refuses_to_import_a_folder_of_another_account() throws Exception {
        DriveConnection connection = connectADrive();
        aDriveHolding(folder("a1", "Notes"));
        watch("a1");
        UUID watchedFolderId = onlyWatchedFolderOf(connection).getId();
        UUID bob = AccountFixture.registerVerified(commandBus, recordingNotificationSender, "bob@exemple.fr", PASSWORD);
        connectADriveFor(bob, "bob@gmail.com", "1//jeton-bob");

        // The message matters as much as the code: it says the refusal comes from the folder being
        // another account's, not from a route that does not exist.
        mockMvc.perform(post(WATCHED_FOLDERS + "/" + watchedFolderId + "/import")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + KnowledgeFixture.token(accessTokenIssuer, bob)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(WatchedFolderNotFoundException.MESSAGE));
    }

    @Test
    void lists_no_watched_folder_without_a_connected_account() throws Exception {
        mockMvc.perform(get(WATCHED_FOLDERS).header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void refuses_to_unwatch_a_folder_of_another_account() throws Exception {
        DriveConnection connection = connectADrive();
        aDriveHolding(folder("a1", "Notes"));
        watch("a1");
        UUID watchedFolderId = onlyWatchedFolderOf(connection).getId();
        UUID bob = AccountFixture.registerVerified(commandBus, recordingNotificationSender, "bob@exemple.fr", PASSWORD);
        // Bob has his own Drive: without it the refusal would come from the missing connection,
        // and the test would stay green even if the folder were looked up unscoped.
        connectADriveFor(bob, "bob@gmail.com", "1//jeton-bob");

        mockMvc.perform(delete(WATCHED_FOLDERS + "/" + watchedFolderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + KnowledgeFixture.token(accessTokenIssuer, bob)))
                .andExpect(status().isNotFound());
    }

    @Test
    void refuses_a_folder_the_drive_does_not_know() throws Exception {
        connectADrive();
        aDriveHolding(folder("a1", "Notes"));

        mockMvc.perform(post(WATCHED_FOLDERS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"folderId\": \"inconnu\"}")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void refuses_to_watch_without_a_connected_account() throws Exception {
        aDriveHolding(folder("a1", "Notes"));

        mockMvc.perform(post(WATCHED_FOLDERS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"folderId\": \"a1\"}")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(DriveNotConnectedException.MESSAGE));
    }

    @Test
    void refuses_an_anonymous_request() throws Exception {
        mockMvc.perform(post(WATCHED_FOLDERS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"folderId\": \"a1\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(WATCHED_FOLDERS)).andExpect(status().isUnauthorized());
        mockMvc.perform(delete(WATCHED_FOLDERS + "/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
        mockMvc.perform(post(WATCHED_FOLDERS + "/" + UUID.randomUUID() + "/import"))
                .andExpect(status().isUnauthorized());
    }

    private void recordAnImport(
            UUID watchedFolderId, DriveImportStatus status, String error, DriveImportRejection... rejections) {
        commandBus.dispatch(new RecordDriveImportOutcome(alice, watchedFolderId, status, error, List.of(rejections)));
    }

    private static DriveFile aDriveFile(String driveFileId, String filename) {
        return DriveFile.downloaded(driveFileId, filename, DocumentFormat.PDF, 42L, null, null);
    }

    private void importADocument(UUID watchedFolderId, String filename, String driveFileId) {
        byte[] content = ("contenu de " + driveFileId).getBytes(StandardCharsets.UTF_8);
        documentRepository.save(Document.importedFromDrive(
                alice,
                filename,
                DocumentFormat.fromFilename(filename),
                Checksum.of(content),
                content.length,
                new DriveProvenance(driveFileId, "https://drive.google.com/file/d/" + driveFileId + "/view", null),
                watchedFolderId));
    }

    private WatchedFolder watchedFolderNamed(DriveConnection connection, String name) {
        return watchedFolderRepository.findAllByConnectionId(connection.getId()).stream()
                .filter(watchedFolder -> watchedFolder.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private WatchedFolder onlyWatchedFolderOf(DriveConnection connection) {
        List<WatchedFolder> watchedFolders = watchedFolderRepository.findAllByConnectionId(connection.getId());
        assertThat(watchedFolders).hasSize(1);
        return watchedFolders.getFirst();
    }
}
