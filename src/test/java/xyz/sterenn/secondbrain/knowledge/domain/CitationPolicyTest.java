package xyz.sterenn.secondbrain.knowledge.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.OptionalInt;
import java.util.function.IntPredicate;
import org.junit.jupiter.api.Test;

class CitationPolicyTest {

    private static final IntPredicate EIGHT_CHUNKS = number -> number >= 1 && number <= 8;

    @Test
    void finds_no_citation_in_a_text_that_carries_none() {
        assertThat(CitationPolicy.citations("Le délai est de quatorze jours.")).isEmpty();
    }

    @Test
    void returns_the_citations_in_order_of_appearance_without_repetition() {
        assertThat(CitationPolicy.citations("un [3] deux [1] trois [3] quatre [12]"))
                .containsExactly(3, 1, 12);
    }

    @Test
    void ignores_what_looks_like_a_citation_without_being_one() {
        assertThat(CitationPolicy.citations("[abc] [ 3 ] [] [3.5]")).isEmpty();
    }

    @Test
    void ignores_the_chunk_tag_that_surrounds_the_data() {
        assertThat(CitationPolicy.citations("<extrait numero=\"1\" document=\"a.pdf\">texte</extrait>"))
                .isEmpty();
    }

    @Test
    void locates_the_end_of_the_first_known_citation() {
        String text = "Le délai est de quatorze jours [2] selon vos documents.";

        OptionalInt end = CitationPolicy.endOfFirstValidCitation(text, EIGHT_CHUNKS);

        assertThat(end).hasValue(text.indexOf("[2]") + "[2]".length());
    }

    @Test
    void locates_nothing_when_the_only_citation_is_unknown() {
        assertThat(CitationPolicy.endOfFirstValidCitation("Le délai [9] est long.", EIGHT_CHUNKS))
                .isEmpty();
    }

    @Test
    void skips_an_unknown_citation_to_find_the_next_one() {
        String text = "Faux [9] mais vrai [4] ensuite.";

        assertThat(CitationPolicy.endOfFirstValidCitation(text, EIGHT_CHUNKS))
                .hasValue(text.indexOf("[4]") + "[4]".length());
    }

    @Test
    void locates_nothing_in_a_citation_still_split_in_two() {
        assertThat(CitationPolicy.endOfFirstValidCitation("Le délai [", EIGHT_CHUNKS))
                .isEmpty();
        assertThat(CitationPolicy.endOfFirstValidCitation("Le délai [2", EIGHT_CHUNKS))
                .isEmpty();
    }
}
