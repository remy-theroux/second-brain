package xyz.sterenn.secondbrain.knowledge.domain.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.AnswerVerdict;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.CitedSource;

@Entity
@Table(name = "knowledge_agent_runs")
public class AgentRun {

    public static final int MAX_AGENT_NAME_LENGTH = 64;
    public static final int MAX_AGENT_VERSION_LENGTH = 16;
    public static final int MAX_VERDICT_LENGTH = 32;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "owner_id", nullable = false, columnDefinition = "uuid")
    private UUID ownerId;

    @Column(name = "agent_name", nullable = false, length = MAX_AGENT_NAME_LENGTH)
    private String agentName;

    @Column(name = "agent_version", nullable = false, length = MAX_AGENT_VERSION_LENGTH)
    private String agentVersion;

    // columnDefinition: without it, Hibernate expects a varchar(255) and `ddl-auto: validate`
    // fails at startup on "wrong column type". Same reason as TextChunk.text.
    @Column(nullable = false, columnDefinition = "text")
    private String question;

    @Column(nullable = false, columnDefinition = "text")
    private String answer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = MAX_VERDICT_LENGTH)
    private AnswerVerdict verdict;

    @Column(nullable = false)
    private int turns;

    @Column(name = "duration_millis", nullable = false)
    private long durationMillis;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "knowledge_agent_run_searches",
            joinColumns = @JoinColumn(name = "agent_run_id", nullable = false))
    @OrderColumn(name = "search_position")
    @Column(name = "search_query", nullable = false, columnDefinition = "text")
    private List<String> searches = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "knowledge_agent_run_sources",
            joinColumns = @JoinColumn(name = "agent_run_id", nullable = false))
    @OrderColumn(name = "source_position")
    private List<CitedSource> sources = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AgentRun() {}

    private AgentRun(
            UUID ownerId,
            String agentName,
            String agentVersion,
            String question,
            String answer,
            AnswerVerdict verdict,
            int turns,
            long durationMillis,
            List<String> searches,
            List<CitedSource> sources,
            Instant createdAt) {
        this.ownerId = ownerId;
        this.agentName = agentName;
        this.agentVersion = agentVersion;
        this.question = question;
        this.answer = answer;
        this.verdict = verdict;
        this.turns = turns;
        this.durationMillis = durationMillis;
        this.searches = searches;
        this.sources = sources;
        this.createdAt = createdAt;
    }

    public static AgentRun of(
            UUID ownerId,
            String agentName,
            String agentVersion,
            String question,
            String answer,
            AnswerVerdict verdict,
            int turns,
            long durationMillis,
            List<String> searches,
            List<CitedSource> sources,
            Instant createdAt) {
        return new AgentRun(
                ownerId,
                agentName,
                agentVersion,
                question,
                answer,
                verdict,
                turns,
                durationMillis,
                new ArrayList<>(searches),
                new ArrayList<>(sources),
                createdAt);
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getAgentName() {
        return agentName;
    }

    public String getAgentVersion() {
        return agentVersion;
    }

    public String getQuestion() {
        return question;
    }

    public String getAnswer() {
        return answer;
    }

    public AnswerVerdict getVerdict() {
        return verdict;
    }

    public int getTurns() {
        return turns;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    public List<String> getSearches() {
        return List.copyOf(searches);
    }

    public List<CitedSource> getSources() {
        return List.copyOf(sources);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
