package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SourceCatalogueTest {

    private static final UUID REPORT = UUID.randomUUID();
    private static final UUID NOTES = UUID.randomUUID();

    private static SourceCandidate chunk(UUID document, int position) {
        return new SourceCandidate(document, "rapport.pdf", position, "Introduction", "texte " + position);
    }

    @Test
    void starts_empty() {
        assertThat(SourceCatalogue.empty().sources()).isEmpty();
        assertThat(SourceCatalogue.empty().contains(1)).isFalse();
    }

    @Test
    void numbers_the_chunks_from_one_in_the_order_received() {
        Absorption absorption = SourceCatalogue.empty().absorb(List.of(chunk(REPORT, 0), chunk(REPORT, 1)));

        assertThat(absorption.newSources()).extracting(Source::number).containsExactly(1, 2);
        assertThat(absorption.alreadySeen()).isZero();
        assertThat(absorption.catalogue().sources()).hasSize(2);
    }

    @Test
    void gives_back_its_original_number_to_an_already_seen_chunk() {
        SourceCatalogue first = SourceCatalogue.empty()
                .absorb(List.of(chunk(REPORT, 0), chunk(REPORT, 1)))
                .catalogue();

        Absorption second = first.absorb(List.of(chunk(REPORT, 1), chunk(NOTES, 0)));

        assertThat(second.newSources()).extracting(Source::number).containsExactly(3);
        assertThat(second.alreadySeen()).isEqualTo(1);
        assertThat(second.catalogue().sources()).extracting(Source::number).containsExactly(1, 2, 3);
    }

    @Test
    void distinguishes_two_chunks_by_their_document_as_much_as_by_their_position() {
        Absorption absorption = SourceCatalogue.empty().absorb(List.of(chunk(REPORT, 0), chunk(NOTES, 0)));

        assertThat(absorption.newSources()).hasSize(2);
    }

    @Test
    void learns_nothing_from_a_search_that_brings_back_only_already_seen_chunks() {
        SourceCatalogue first =
                SourceCatalogue.empty().absorb(List.of(chunk(REPORT, 0))).catalogue();

        Absorption second = first.absorb(List.of(chunk(REPORT, 0)));

        assertThat(second.newSources()).isEmpty();
        assertThat(second.alreadySeen()).isEqualTo(1);
        assertThat(second.catalogue().sources()).hasSize(1);
    }

    @Test
    void does_not_modify_the_catalogue_it_absorbs_into() {
        SourceCatalogue start = SourceCatalogue.empty();

        start.absorb(List.of(chunk(REPORT, 0)));

        assertThat(start.sources()).isEmpty();
    }

    @Test
    void returns_only_the_sources_actually_cited_and_known() {
        SourceCatalogue catalogue = SourceCatalogue.empty()
                .absorb(List.of(chunk(REPORT, 0), chunk(REPORT, 1), chunk(REPORT, 2)))
                .catalogue();

        assertThat(catalogue.cited(List.of(3, 9, 1))).extracting(Source::number).containsExactly(3, 1);
    }
}
