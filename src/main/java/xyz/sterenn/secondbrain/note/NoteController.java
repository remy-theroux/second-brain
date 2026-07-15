package xyz.sterenn.secondbrain.note;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/notes")
public class NoteController {

    private final NoteRepository notes;

    public NoteController(NoteRepository notes) {
        this.notes = notes;
    }

    @GetMapping
    public List<NoteResponse> list() {
        return notes.findAll().stream().map(NoteResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<NoteResponse> get(@PathVariable UUID id) {
        return notes.findById(id)
            .map(NoteResponse::from)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<NoteResponse> create(@Valid @RequestBody CreateNoteRequest request) {
        Note saved = notes.save(new Note(request.title(), request.content()));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(saved.getId())
            .toUri();
        return ResponseEntity.created(location).body(NoteResponse.from(saved));
    }

    public record CreateNoteRequest(@NotBlank String title, String content) {
    }

    public record NoteResponse(UUID id, String title, String content, Instant createdAt) {
        static NoteResponse from(Note note) {
            return new NoteResponse(note.getId(), note.getTitle(), note.getContent(), note.getCreatedAt());
        }
    }
}
