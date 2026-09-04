package xyz.sterenn.secondbrain.users.infrastructure.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import xyz.sterenn.secondbrain.users.domain.port.NotificationSender;
import xyz.sterenn.secondbrain.users.domain.valueobject.Notification;
import xyz.sterenn.secondbrain.users.domain.valueobject.VerificationNotification;

@Component
public class EmailNotificationSender implements NotificationSender {

    private final JavaMailSender mailSender;
    private final String baseUrl;
    private final String from;

    EmailNotificationSender(
            JavaMailSender mailSender,
            @Value("${secondbrain.base-url}") String baseUrl,
            @Value("${secondbrain.notification.from}") String from) {
        this.mailSender = mailSender;
        this.baseUrl = baseUrl;
        this.from = from;
    }

    @Override
    public void send(Notification notification) {
        mailSender.send(buildMessage(notification));
    }

    SimpleMailMessage buildMessage(Notification notification) {
        return switch (notification) {
            case VerificationNotification verification -> verificationMessage(verification);
        };
    }

    private SimpleMailMessage verificationMessage(VerificationNotification notification) {
        String lien = UriComponentsBuilder.fromUriString(baseUrl)
                .path("/verification")
                .queryParam("compte", notification.accountId())
                .queryParam("jeton", notification.rawToken().value())
                .build()
                .toUriString();

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(notification.recipient().value());
        message.setSubject("Vérifiez votre adresse email");
        message.setText("""
            Bonjour,

            Votre compte Second Brain a bien été créé. Il reste à vérifier votre adresse
            email en suivant ce lien :

            %s

            Ce lien est valable 24 heures et ne fonctionne qu'une fois.

            Si vous n'êtes pas à l'origine de cette création de compte, ignorez ce message.
            """.formatted(lien));
        return message;
    }
}
