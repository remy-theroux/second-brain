package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SourceCatalogue {

    private record Cle(UUID documentId, int position) {}

    private final Map<Cle, Source> parCle;

    private SourceCatalogue(Map<Cle, Source> parCle) {
        this.parCle = parCle;
    }

    public static SourceCatalogue empty() {
        return new SourceCatalogue(Map.of());
    }

    public Absorption absorb(List<SourceCandidate> candidats) {
        Map<Cle, Source> fusionne = new LinkedHashMap<>(parCle);
        List<Source> nouveaux = new ArrayList<>();
        int dejaVus = 0;
        for (SourceCandidate candidat : candidats) {
            Cle cle = new Cle(candidat.documentId(), candidat.position());
            if (fusionne.containsKey(cle)) {
                dejaVus++;
                continue;
            }
            Source source = Source.numerote(fusionne.size() + 1, candidat);
            fusionne.put(cle, source);
            nouveaux.add(source);
        }
        return new Absorption(new SourceCatalogue(Map.copyOf(fusionne)), nouveaux, dejaVus);
    }

    public List<Source> sources() {
        return parCle.values().stream()
                .sorted(Comparator.comparingInt(Source::number))
                .toList();
    }

    public boolean contains(int numero) {
        return numero >= 1 && numero <= parCle.size();
    }

    public List<Source> cited(List<Integer> numeros) {
        List<Source> toutes = sources();
        return numeros.stream()
                .filter(this::contains)
                .map(numero -> toutes.get(numero - 1))
                .toList();
    }
}
