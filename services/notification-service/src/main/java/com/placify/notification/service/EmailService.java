package com.placify.notification.service;

import com.placify.notification.NotificationProperties;
import com.placify.notification.domain.EmailLog;
import com.placify.notification.repository.EmailLogRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;

/** Renders the HTML e-mail template and sends it (or logs it when mail is disabled). */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final ObjectProvider<JavaMailSender> mailSender;
    private final TemplateEngine templates;
    private final NotificationProperties properties;
    private final EmailLogRepository emailLog;

    public record Mail(String to, String name, String subject, String title, String message, String link) {
    }

    @Async
    public void send(Mail mail) {
        if (mail.to() == null || mail.to().isBlank()) {
            return;
        }
        String status = "SENT";
        String error = null;
        try {
            JavaMailSender sender = mailSender.getIfAvailable();
            if (!properties.isMailEnabled() || sender == null) {
                log.info("[mail disabled] to={} subject='{}' message='{}'", mail.to(), mail.subject(), mail.message());
                status = "LOGGED";
            } else {
                Context ctx = new Context();
                ctx.setVariable("name", mail.name());
                ctx.setVariable("title", mail.title());
                ctx.setVariable("message", mail.message());
                ctx.setVariable("link", absolute(mail.link()));
                String html = templates.process("email/notification", ctx);

                MimeMessage message = sender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
                helper.setFrom(properties.getMailFrom());
                helper.setTo(mail.to());
                helper.setSubject(mail.subject());
                helper.setText(plain(mail), html);
                sender.send(message);
            }
        } catch (Exception ex) {
            status = "FAILED";
            error = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            log.warn("E-mail to {} failed: {}", mail.to(), error);
        }
        emailLog.save(EmailLog.builder().recipient(mail.to()).subject(truncate(mail.subject(), 255)).status(status)
                .error(truncate(error, 500)).build());
    }

    private String plain(Mail mail) {
        String link = absolute(mail.link());
        return mail.title() + "\n\n" + mail.message() + (link == null ? "" : "\n\n" + link);
    }

    private String absolute(String link) {
        if (link == null || link.isBlank()) {
            return null;
        }
        if (link.startsWith("http://") || link.startsWith("https://")) {
            return link;
        }
        return properties.getPublicBaseUrl() + (link.startsWith("/") ? link : "/" + link);
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
