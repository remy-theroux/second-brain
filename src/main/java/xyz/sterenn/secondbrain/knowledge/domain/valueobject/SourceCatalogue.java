package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SourceCatalogue {

    private record Key(UUID documentId, int position) {}

    private final Map<Key, Source> byKey;

    private SourceCatalogue(Map<Key, Source> byKey) {
        this.byKey = byKey;
    }

    public static SourceCatalogue empty() {
        return new SourceCatalogue(Map.of());
    }

    public Absorption absorb(List<SourceCandidate> candidates) {
        Map<Key, Source> merged = new LinkedHashMap<>(byKey);
        List<Source> newSources = new ArrayList<>();
        int alreadySeen = 0;
        for (SourceCandidate candidate : candidates) {
            Key key = new Key(candidate.documentId(), candidate.position());
            if (merged.containsKey(key)) {
                alreadySeen++;
                continue;
            }
            Source source = Source.numbered(merged.size() + 1, candidate);
            merged.put(key, source);
            newSources.add(source);
        }
        return new Absorption(new SourceCatalogue(Map.copyOf(merged)), newSources, alreadySeen);
    }

    public List<Source> sources() {
        return byKey.values().stream()
                .sorted(Comparator.comparingInt(Source::number))
                .toList();
    }

    public boolean contains(int number) {
        return number >= 1 && number <= byKey.size();
    }

    public List<Source> cited(List<Integer> numbers) {
        List<Source> all = sources();
        return numbers.stream()
                .filter(this::contains)
                .map(number -> all.get(number - 1))
                .toList();
    }
}
