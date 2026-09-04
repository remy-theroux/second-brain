package xyz.sterenn.secondbrain.knowledge.infrastructure.extraction;

/**
 * La police retenue est la plus grande de la ligne, jamais la moyenne : une lettrine ou un
 * appel de note en petit ne doit pas faire passer un titre pour du corps.
 */
record TextLine(String text, float fontSize) {}
