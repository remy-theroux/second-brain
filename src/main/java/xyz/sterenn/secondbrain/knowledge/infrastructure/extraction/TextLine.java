package xyz.sterenn.secondbrain.knowledge.infrastructure.extraction;

/**
 * Une ligne rendue par PDFBox, et la plus grande police qu'elle emploie.
 *
 * <p>La plus grande et non la moyenne : un titre dont le premier caractère est une lettrine,
 * ou qui porte un appel de note en petit, reste un titre. La moyenne le noierait.
 */
record TextLine(String text, float fontSize) {}
