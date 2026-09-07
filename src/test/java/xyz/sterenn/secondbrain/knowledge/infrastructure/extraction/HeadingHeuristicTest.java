package xyz.sterenn.secondbrain.knowledge.infrastructure.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class HeadingHeuristicTest {

    private static final String BODY = "Une ligne de corps de texte, ordinaire et bavarde.";

    @Test
    void takes_as_the_body_the_size_that_carries_the_most_characters() {
        List<Section> sections = HeadingHeuristic.split(List.of(
                new TextLine("Rapport annuel", 18f),
                new TextLine(BODY, 11f),
                new TextLine(BODY, 11f),
                new TextLine(BODY, 11f)));

        assertThat(sections).extracting(Section::heading).containsExactly("Rapport annuel");
    }

    @Test
    void orders_the_heading_sizes_from_the_largest_to_the_smallest() {
        List<Section> sections = HeadingHeuristic.split(List.of(
                new TextLine("Rapport annuel", 18f),
                new TextLine(BODY, 11f),
                new TextLine("Premiere partie", 14f),
                new TextLine(BODY, 11f),
                new TextLine("Seconde partie", 14f),
                new TextLine(BODY, 11f)));

        assertThat(sections).extracting(Section::level).containsExactly(1, 2, 2);
    }

    @Test
    void keeps_outside_any_section_what_precedes_the_first_heading() {
        List<Section> sections = HeadingHeuristic.split(
                List.of(new TextLine(BODY, 11f), new TextLine("Un titre", 18f), new TextLine(BODY, 11f)));

        assertThat(sections).first().satisfies(section -> {
            assertThat(section.heading()).isEmpty();
            assertThat(section.level()).isZero();
        });
    }

    @Test
    void sees_no_heading_in_a_document_with_a_single_size() {
        List<Section> sections = HeadingHeuristic.split(
                List.of(new TextLine(BODY, 11f), new TextLine(BODY, 11f), new TextLine(BODY, 11f)));

        assertThat(sections).singleElement().satisfies(section -> assertThat(section.heading())
                .isEmpty());
    }

    @Test
    void does_not_take_a_whole_highlighted_sentence_for_a_heading() {
        String quote = "C".repeat(200);

        List<Section> sections = HeadingHeuristic.split(
                List.of(new TextLine(quote, 18f), new TextLine(BODY, 11f), new TextLine(BODY, 11f)));

        assertThat(sections).extracting(Section::heading).containsExactly("");
    }

    @Test
    void returns_no_section_when_there_is_no_line() {
        assertThat(HeadingHeuristic.split(List.of())).isEmpty();
    }

    @Test
    void caps_the_level_at_six_even_with_seven_heading_sizes() {
        List<TextLine> lines = new ArrayList<>();
        for (int size = 30; size >= 12; size -= 3) {
            lines.add(new TextLine("Titre de " + size, size));
            lines.add(new TextLine(BODY, 10f));
        }
        lines.add(new TextLine(BODY.repeat(5), 10f));

        assertThat(HeadingHeuristic.split(lines)).extracting(Section::level).allMatch(level -> level <= 6);
    }
}
