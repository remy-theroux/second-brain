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

/**
 * Découpe un texte extrait en extraits vectorisables. <strong>Logique de domaine pure</strong> :
 * ni Spring, ni base, ni réseau — sa seule dépendance est le port {@link TokenCounter}, et
 * c'est le handler qui l'instancie.
 *
 * <p>Quatre niveaux de repli, dans cet ordre :
 *
 * <ol>
 *   <li><strong>Une section qui tient sous le plafond donne un extrait.</strong> On ne coupe
 *       pas un bloc de 700 tokens pour se rapprocher de la cible.
 *   <li><strong>Sinon, découpe en paragraphes</strong>, sur la double ligne vide. Ce n'est pas
 *       une heuristique : {@code TextBlock.normalise} garantit qu'une frontière de paragraphe
 *       survit sous la forme d'exactement deux sauts de ligne.
 *   <li><strong>Un paragraphe seul au-dessus du plafond descend aux phrases</strong>, par
 *       {@link BreakIterator} en français — le JDK, zéro dépendance. Une expression régulière
 *       sur {@code [.!?]} couperait « 3.14 », « etc. » et « M. Dupont » ; {@code BreakIterator}
 *       se trompe aussi, moins souvent, et sa panne est bénigne : une fausse frontière produit
 *       un extrait un peu court, jamais un extrait cassé.
 *   <li><strong>Une phrase seule au-dessus du plafond est coupée net</strong>, aux mots puis,
 *       s'il n'y a même plus de mot (un blob sans espace), aux caractères. C'est le seul
 *       endroit où la promesse « jamais au milieu d'une phrase » cède, et elle y est forcée :
 *       un texte qui n'offre aucune frontière ne peut pas en imposer une.
 * </ol>
 *
 * <p><strong>Le recouvrement ne franchit jamais une frontière de section</strong>, et il cède
 * devant le plafond : c'est un confort, le plafond est un invariant. Sans cette dernière
 * règle, un recouvrement suivi d'une longue phrase produirait l'unique extrait hors plafond
 * de tout l'algorithme.
 *
 * <p>Un cas limite tombe tout seul : une section vide ne peut pas arriver ici, {@code
 * TextBlock.of} refusant un corps vide et {@code ExtractedTextBuilder} écartant les sections
 * sans corps.
 */
public final class RecursiveChunker {

    private static final String PARAGRAPH_SEPARATOR = "\n\n";
    private static final String SENTENCE_SEPARATOR = " ";

    /** Deux sauts de ligne ou plus : ce que {@code TextBlock.normalise} laisse d'un paragraphe. */
    private static final String PARAGRAPH_BOUNDARY = "\n{2,}";

    private static final String WHITESPACE = "\\s+";

    private final TokenCounter tokenCounter;

    public RecursiveChunker(TokenCounter tokenCounter) {
        this.tokenCounter = Objects.requireNonNull(tokenCounter, "Le compteur de tokens est obligatoire");
    }

    /**
     * @return au moins un extrait par bloc, dans l'ordre du document, aucun au-dessus du
     *     plafond. Jamais vide : {@link ExtractedText} garantit au moins un bloc.
     */
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
            // Un extrait dont le recouvrement reprend TOUTES les phrases serait un doublon exact de
            // son successeur : une ligne de vecteur pour rien, et une réponse de recherche qui ne
            // montrerait rien de plus que l'extrait suivant. Il ne se publie pas — son texte n'est
            // pas perdu pour autant, il ouvre le suivant.
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

    /**
     * Les phrases de fin du précédent extrait à reprendre en tête du suivant — et moins
     * encore, jusqu'à zéro s'il le faut, plutôt que de franchir le plafond une fois
     * rattachées à {@code suivante}.
     *
     * <p>Prend {@code phrases} déjà découpées plutôt que le texte du précédent extrait : son
     * appelant en a déjà besoin pour décider si ce précédent extrait mérite d'être publié, et
     * les redécouper ici referait le même travail de {@link BreakIterator} pour rien.
     */
    private List<String> keptOverlap(List<String> phrases, Unit suivante) {
        List<String> reprises = new ArrayList<>(trailingSentences(phrases));
        while (!reprises.isEmpty()
                && tokenCounter.count(join(reprises) + suivante.separator() + suivante.text())
                        > ChunkingPolicy.MAX_TOKENS) {
            reprises.removeFirst();
        }
        return reprises;
    }

    /** Les dernières phrases entières qui tiennent dans le recouvrement, dans l'ordre. */
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

    /**
     * Les morceaux insécables d'une section, chacun avec le séparateur qui le raccroche au
     * précédent : deux sauts de ligne pour un début de paragraphe, une espace pour une phrase
     * au sein d'un paragraphe. Sans ce séparateur porté par le morceau, recoller deux unités
     * effacerait la frontière de paragraphe que l'extraction a pris soin de conserver.
     */
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

    /** Dernier recours : au mot, puis au caractère pour un mot qui pèse à lui seul plus que le plafond. */
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

    /**
     * Un « mot » plus lourd que le plafond — une chaîne encodée, un fichier collé dans un
     * document. Il n'y a plus aucune frontière : on estime la longueur à couper par la
     * densité de tokens du mot, puis on la resserre tant qu'elle dépasse. C'est le seul
     * chemin qui ne préserve rien du tout, et c'est ce qui garantit qu'<em>aucun</em> extrait
     * ne franchit le plafond.
     */
    private List<String> splitOnCharacters(String mot) {
        List<String> morceaux = new ArrayList<>();
        // La densité de tokens est mesurée UNE fois, et la coupe ne vérifie ensuite que le
        // préfixe qu'elle publie : recompter le reste à chaque tour rendrait le découpage
        // quadratique, et un blob de deux mégaoctets ferait passer des centaines de
        // mégaoctets dans le tokenizer pour un seul document.
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

    /**
     * Les phrases d'un texte, par le {@link BreakIterator} du JDK en français. Une nouvelle
     * instance à chaque appel : {@code BreakIterator} porte l'état de son parcours, il n'est
     * pas sûr en accès concurrent, et le chunker est instancié une fois pour tout le worker.
     */
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

    /** Un morceau insécable et ce qui le raccroche au précédent. */
    private record Unit(String text, String separator) {}
}
