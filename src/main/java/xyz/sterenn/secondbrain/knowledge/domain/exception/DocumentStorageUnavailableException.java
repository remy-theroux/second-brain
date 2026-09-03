package xyz.sterenn.secondbrain.knowledge.domain.exception;

/**
 * Le stockage des originaux n'a pas répondu.
 *
 * <p>Ce n'est <strong>pas un refus métier</strong> : rien n'est reproché au document ni à
 * celui qui le dépose. Un stockage objet est un service distant dont la disponibilité change
 * dans le temps — le même geste, refait dix minutes plus tard, peut très bien aboutir. C'est
 * ce qui la distingue des autres exceptions de ce package, qui énoncent toutes une règle que
 * la demande enfreint.
 *
 * <p>{@code RuntimeException} et non checked : une exception checked ne déclencherait pas le
 * rollback promis par le {@code CommandBus} avec les réglages Spring par défaut. La ligne
 * serait committée sans que son original ait été écrit.
 *
 * <p><strong>Le message de la {@code SdkException} ne remonte jamais dans celui-ci.</strong>
 * Le SDK dit « The specified bucket does not exist » et un identifiant de requête : c'est du
 * jargon, en anglais, et les messages d'exception de ce projet sont affichables tels quels à
 * l'utilisateur. La cause porte tout cela et part au journal, où elle sert celui qui répare.
 *
 * <p><strong>Elle n'hérite pas de {@link DocumentExtractionException}, et ce n'est pas un
 * oubli.</strong> Conséquence directe : quand le stockage tombe pendant une extraction,
 * {@code KnowledgeEventListener} ne reconnaît pas le type et pose le motif générique « Le
 * traitement de ce document a échoué de façon inattendue. » C'est juste — une panne de
 * stockage ne dit rien de <em>ce</em> document-là, et lui coller un motif nominatif ferait
 * croire à l'utilisateur que son fichier est en cause. À ne pas « corriger ».
 */
public class DocumentStorageUnavailableException extends RuntimeException {

    public DocumentStorageUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
