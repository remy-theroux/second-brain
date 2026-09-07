package xyz.sterenn.secondbrain.knowledge.infrastructure.extraction;

/**
 * The font size kept is the largest on the line, never the average: a drop cap or a small
 * footnote marker must not make a heading look like body text.
 */
record TextLine(String text, float fontSize) {}
