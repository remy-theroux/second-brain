package xyz.sterenn.secondbrain.shared.event;

/**
 * Port sortant : annonce un événement métier au reste du système.
 *
 * <p>Depuis une transaction — toujours le cas depuis {@code SpringCommandBus} — l'annonce
 * ne part qu'au commit, et un rollback n'annonce rien. Hors transaction, elle part
 * immédiatement. C'est le handler qui publie, en dernière étape de son orchestration : la
 * place de l'appel dans la séquence n'a aucune importance transactionnelle, elle est
 * dernière pour se lire comme ce qu'elle est, une annonce.
 *
 * <p>Ce qui arrive à l'annonce après le commit n'est pas garanti par ce port (spec,
 * décision 3) : un événement que le transport n'a pas reçu est perdu.
 */
public interface DomainEventPublisher {

    void publish(DomainEvent event);
}
