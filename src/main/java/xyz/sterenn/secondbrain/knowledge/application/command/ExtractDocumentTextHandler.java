package xyz.sterenn.secondbrain.knowledge.application.command;

import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DocumentText;
import xyz.sterenn.secondbrain.knowledge.domain.event.DocumentTextExtracted;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DocumentNotFoundException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.UnreadableDocumentException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentStorage;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentTextExtractor;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentTextRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ExtractedText;
import xyz.sterenn.secondbrain.shared.bus.CommandHandler;
import xyz.sterenn.secondbrain.shared.event.DomainEventPublisher;

/**
 * Orchestre l'extraction : relecture du document et de son original, choix de l'extracteur,
 * remplacement du texte, changement de statut, puis annonce.
 *
 * <p>Aucun {@code @Transactional} ici — la transaction est ouverte par
 * {@code SpringCommandBus.dispatch}. Une extraction qui échoue n'écrit donc rien : ni texte
 * partiel, ni statut. C'est ce qui oblige le consommateur d'événements à marquer l'échec dans
 * une <em>seconde</em> transaction (ADR-0028).
 *
 * <p><strong>L'effacement avant l'écriture n'est pas une précaution de style.</strong> AMQP
 * livre au moins une fois et {@code document_id} est {@code UNIQUE} : sans lui, une
 * redélivrance de {@code DocumentUploaded} ferait échouer l'écriture sur la contrainte, et le
 * document passerait en {@code FAILED} pour un traitement qui avait réussi.
 *
 * <p>L'annonce en dernier, comme au dépôt : elle ne prend effet qu'au commit, donc sa place
 * n'a aucune portée transactionnelle — elle est dernière pour se lire comme ce qu'elle est.
 */
@Component
public class ExtractDocumentTextHandler implements CommandHandler<ExtractDocumentText> {

    private final DocumentRepository documentRepository;
    private final DocumentStorage documentStorage;
    private final DocumentTextRepository documentTextRepository;
    private final Map<DocumentFormat, DocumentTextExtractor> extractorsByFormat;
    private final DomainEventPublisher domainEventPublisher;
    private final Clock clock;

    public ExtractDocumentTextHandler(
            DocumentRepository documentRepository,
            DocumentStorage documentStorage,
            DocumentTextRepository documentTextRepository,
            List<DocumentTextExtractor> documentTextExtractors,
            DomainEventPublisher domainEventPublisher,
            Clock clock) {
        this.documentRepository = documentRepository;
        this.documentStorage = documentStorage;
        this.documentTextRepository = documentTextRepository;
        this.extractorsByFormat = indexeParFormat(documentTextExtractors);
        this.domainEventPublisher = domainEventPublisher;
        this.clock = clock;
    }

    /**
     * Indexe les extracteurs, et <strong>fait échouer le démarrage</strong> si un format
     * accepté au dépôt n'a pas le sien.
     *
     * <p>C'est la contrepartie du choix d'un extracteur par format (ADR-0026) : ajouter une
     * constante à {@link DocumentFormat} sans écrire son adapter serait, sinon, un document
     * accepté puis irrémédiablement en échec. Même dispositif que la table de routage des
     * bus : le défaut se voit au démarrage, pas en production.
     */
    private static Map<DocumentFormat, DocumentTextExtractor> indexeParFormat(
            List<DocumentTextExtractor> documentTextExtractors) {
        Map<DocumentFormat, DocumentTextExtractor> parFormat = new EnumMap<>(DocumentFormat.class);
        for (DocumentTextExtractor extracteur : documentTextExtractors) {
            DocumentTextExtractor precedent = parFormat.put(extracteur.format(), extracteur);
            if (precedent != null) {
                throw new IllegalStateException("Deux extracteurs revendiquent le format " + extracteur.format() + " : "
                        + precedent.getClass().getName() + " et "
                        + extracteur.getClass().getName());
            }
        }
        for (DocumentFormat format : DocumentFormat.values()) {
            if (!parFormat.containsKey(format)) {
                throw new IllegalStateException(
                        "Aucun extracteur ne sait lire " + format + " : un format accepté au dépôt doit être lisible");
            }
        }
        return Map.copyOf(parFormat);
    }

    @Override
    public void handle(ExtractDocumentText command) {
        Document document = documentRepository
                .findByIdAndOwnerId(command.documentId(), command.ownerId())
                .orElseThrow(DocumentNotFoundException::new);

        byte[] contenu = documentStorage
                .read(document.getId())
                // La ligne existe, l'original non : c'est la fuite qu'ADR-0020 assume dans
                // l'autre sens. Illisible est le mot juste — il n'y a rien à lire.
                .orElseThrow(UnreadableDocumentException::new);

        ExtractedText texte = extractorsByFormat.get(document.getFormat()).extract(contenu);

        documentTextRepository.deleteByDocumentId(document.getId());
        documentTextRepository.save(DocumentText.of(document.getId(), texte, clock.instant()));

        document.markTextExtracted();
        documentRepository.save(document);

        domainEventPublisher.publish(new DocumentTextExtracted(
                document.getId(), document.getOwnerId(), texte.blocks().size(), clock.instant()));
    }
}
