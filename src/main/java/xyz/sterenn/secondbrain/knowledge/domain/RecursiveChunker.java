package xyz.sterenn.secondbrain.knowledge.domain;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import xyz.sterenn.secondbrain.knowledge.domain.port.TokenCounter;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Chunk;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ExtractedText;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.TextBlock;

public final class RecursiveChunker {

    private static final String PARAGRAPH_SEPARATOR = "\n\n";
    private static final String SENTENCE_SEPARATOR = " ";
    private static final String PARAGRAPH_BOUNDARY = "\n{2,}";
    private static final String WHITESPACE = "\\s+";

    private final TokenCounter tokenCounter;

    public RecursiveChunker(TokenCounter tokenCounter) {
        this.tokenCounter = Objects.requireNonNull(tokenCounter, "Le compteur de tokens est obligatoire");
    }

    public List<Chunk> chunk(ExtractedText text) {
        Objects.requireNonNull(text, "Le texte extrait est obligatoire");
        List<Chunk> extraits = new ArrayList<>();
        for (TextBlock bloc : text.blocks()) {
            extraits.addAll(chunkSection(bloc.getHeading(), bloc.getText()));
        }
        return List.copyOf(extraits);
    }

    private List<Chunk> chunkSection(String heading, String body) {
        if (tokenCounter.count(body) <= ChunkingPolicy.MAX_TOKENS) {
            return List.of(new Chunk(heading, body));
        }
        List<Chunk> extraits = new ArrayList<>();
        String courant = "";
        for (Unit unite : units(body)) {
            if (courant.isEmpty()) {
                courant = unite.text();
                continue;
            }
            String candidat = courant + unite.separator() + unite.text();
            if (tokenCounter.count(candidat) <= ChunkingPolicy.TARGET_TOKENS) {
                courant = candidat;
                continue;
            }
            List<String> phrases = sentences(courant);
            List<String> reprises = keptOverlap(phrases, unite);
            if (reprises.size() < phrases.size()) {
                extraits.add(new Chunk(heading, courant));
            }
            courant = reprises.isEmpty() ? unite.text() : join(reprises) + unite.separator() + unite.text();
        }
        if (!courant.isEmpty()) {
            extraits.add(new Chunk(heading, courant));
        }
        return extraits;
    }

    private List<String> keptOverlap(List<String> phrases, Unit suivante) {
        List<String> reprises = new ArrayList<>(trailingSentences(phrases));
        while (!reprises.isEmpty()
                && tokenCounter.count(join(reprises) + suivante.separator() + suivante.text())
                        > ChunkingPolicy.MAX_TOKENS) {
            reprises.removeFirst();
        }
        return reprises;
    }

    private List<String> trailingSentences(List<String> phrases) {
        List<String> reprises = new ArrayList<>();
        int total = 0;
        for (int index = phrases.size() - 1; index >= 0; index--) {
            int cout = tokenCounter.count(phrases.get(index));
            if (total + cout > ChunkingPolicy.OVERLAP_TOKENS) {
                break;
            }
            reprises.addFirst(phrases.get(index));
            total += cout;
        }
        return reprises;
    }

    private List<Unit> units(String body) {
        List<Unit> unites = new ArrayList<>();
        for (String paragraphe : body.split(PARAGRAPH_BOUNDARY)) {
            String bloc = paragraphe.strip();
            if (bloc.isEmpty()) {
                continue;
            }
            if (tokenCounter.count(bloc) <= ChunkingPolicy.MAX_TOKENS) {
                unites.add(new Unit(bloc, PARAGRAPH_SEPARATOR));
                continue;
            }
            String separateur = PARAGRAPH_SEPARATOR;
            for (String phrase : sentences(bloc)) {
                for (String morceau : forceSplit(phrase)) {
                    unites.add(new Unit(morceau, separateur));
                    separateur = SENTENCE_SEPARATOR;
                }
            }
        }
        return unites;
    }

    private List<String> forceSplit(String phrase) {
        if (tokenCounter.count(phrase) <= ChunkingPolicy.MAX_TOKENS) {
            return List.of(phrase);
        }
        List<String> morceaux = new ArrayList<>();
        String courant = "";
        for (String mot : phrase.split(WHITESPACE)) {
            if (tokenCounter.count(mot) > ChunkingPolicy.MAX_TOKENS) {
                if (!courant.isEmpty()) {
                    morceaux.add(courant);
                    courant = "";
                }
                morceaux.addAll(splitOnCharacters(mot));
                continue;
            }
            String candidat = courant.isEmpty() ? mot : courant + SENTENCE_SEPARATOR + mot;
            if (tokenCounter.count(candidat) > ChunkingPolicy.MAX_TOKENS) {
                morceaux.add(courant);
                courant = mot;
            } else {
                courant = candidat;
            }
        }
        if (!courant.isEmpty()) {
            morceaux.add(courant);
        }
        return morceaux;
    }

    private List<String> splitOnCharacters(String mot) {
        List<String> morceaux = new ArrayList<>();
        // La densité de tokens est mesurée une fois : la recompter à chaque tour rendrait
        // le découpage quadratique.
        long tokens = Math.max(1, tokenCounter.count(mot));
        // (long) : le produit dépasse int dès 2,7 millions de caractères. L'entier négatif
        // qui en sortait ramenait la coupe à un caractère par tour.
        int estimation = (int) Math.max(1L, (long) mot.length() * ChunkingPolicy.MAX_TOKENS / tokens);
        String reste = mot;
        while (!reste.isEmpty()) {
            int taille = Math.min(estimation, reste.length());
            while (taille > 1 && tokenCounter.count(reste.substring(0, taille)) > ChunkingPolicy.MAX_TOKENS) {
                taille = taille * 3 / 4;
            }
            // Ne pas couper une paire de substituts en deux : la moitié orpheline n'est plus
            // de l'UTF-8 valide, et PostgreSQL refuse de l'écrire.
            if (taille > 1 && taille < reste.length() && Character.isHighSurrogate(reste.charAt(taille - 1))) {
                taille--;
            }
            morceaux.add(reste.substring(0, taille));
            reste = reste.substring(taille);
        }
        return morceaux;
    }

    /** Une nouvelle instance à chaque appel : {@code BreakIterator} n'est pas sûr en accès concurrent. */
    private static List<String> sentences(String texte) {
        BreakIterator frontieres = BreakIterator.getSentenceInstance(Locale.FRENCH);
        frontieres.setText(texte);
        List<String> phrases = new ArrayList<>();
        int debut = frontieres.first();
        for (int fin = frontieres.next(); fin != BreakIterator.DONE; debut = fin, fin = frontieres.next()) {
            String phrase = texte.substring(debut, fin).strip();
            if (!phrase.isEmpty()) {
                phrases.add(phrase);
            }
        }
        return phrases;
    }

    private static String join(List<String> phrases) {
        return String.join(SENTENCE_SEPARATOR, phrases);
    }

    private record Unit(String text, String separator) {}
}
