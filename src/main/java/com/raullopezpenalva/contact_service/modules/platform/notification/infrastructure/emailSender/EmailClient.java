package com.raullopezpenalva.contact_service.modules.platform.notification.infrastructure.emailSender;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
public class EmailClient {
    
    private final JavaMailSender mailSender;

    @Value("${email.recipient}")
    private String recipient;

    @Value("${spring.mail.username}")
    private String sender;

    public EmailClient(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendEmail(String replyTo, String subject, String body) {
        try {
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true);
            helper.setFrom(sender);
            helper.setTo(recipient);
            helper.setReplyTo(replyTo);
            helper.setSubject(subject);
            helper.setText(body, false);
            mailSender.send(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
