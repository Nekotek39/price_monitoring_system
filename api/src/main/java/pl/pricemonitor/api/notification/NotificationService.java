package pl.pricemonitor.api.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import pl.pricemonitor.api.config.ApiProperties;
import pl.pricemonitor.core.pricing.Money;

import java.math.BigDecimal;

/**
 * E-mail notifications. Without a configured mail server (spring.mail.host) messages are only logged,
 * so the system also works in development without SMTP.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final ApiProperties properties;

    public NotificationService(ObjectProvider<JavaMailSender> mailSender, ApiProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    public void priceChanged(String to, String product, BigDecimal oldPrice, BigDecimal newPrice, String reason) {
        send(to, "Zmieniono cenę: " + product,
                "Moduł dynamicznego dostosowywania cen zmienił cenę produktu „" + product + "” z "
                        + Money.format(oldPrice) + " na " + Money.format(newPrice) + ".\n\n" + reason);
    }

    public void priceRecommended(String to, String product, BigDecimal currentPrice, BigDecimal recommended,
                                 String reason) {
        send(to, "Rekomendowana cena: " + product,
                "Dla produktu „" + product + "” rekomendujemy cenę " + Money.format(recommended) + " (obecnie "
                        + Money.format(currentPrice) + ").\n\n" + reason);
    }

    public void competitorBelowMinimum(String to, String product, BigDecimal lowestCompetitor, BigDecimal minPrice,
                                       String reason) {
        send(to, "Konkurent poniżej Twojej ceny minimalnej: " + product,
                "Najniższa cena konkurencji dla produktu „" + product + "” to " + Money.format(lowestCompetitor)
                        + ", czyli mniej niż Twoja cena minimalna " + Money.format(minPrice)
                        + ". Cena nie została zmieniona - decyzję o ewentualnej obniżce podejmujesz sam.\n\n" + reason);
    }

    public void adminAlert(String subject, String body) {
        send(properties.adminEmail(), subject, body);
    }

    void send(String to, String subject, String body) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || to == null || to.isBlank()) {
            log.info("[e-mail -> {}] {}\n{}", to, subject, body);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.mailFrom());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        try {
            sender.send(message);
        } catch (MailException e) {
            log.warn("Could not send e-mail '{}' to {}: {}", subject, to, e.getMessage());
        }
    }
}
