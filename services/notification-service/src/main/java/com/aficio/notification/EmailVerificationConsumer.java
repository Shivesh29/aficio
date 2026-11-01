package com.aficio.notification;

import com.aficio.kafka.EmailVerificationEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "aficio.kafka.enabled", havingValue = "true")
public class EmailVerificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationConsumer.class);
    
    private final ObjectMapper objectMapper;
    private final String resendApiKey;
    private final String sender;
    private final String frontendUrl;

    public EmailVerificationConsumer(
            ObjectMapper objectMapper,
            @Value("${aficio.resend.api-key:}") String resendApiKey,
            @Value("${aficio.resend.from:Aficio <onboarding@resend.dev>}") String sender,
            @Value("${aficio.frontend.url:http://localhost:5174}") String frontendUrl
    ) {
        this.objectMapper = objectMapper;
        this.resendApiKey = resendApiKey;
        this.sender = sender;
        this.frontendUrl = frontendUrl;
    }

    @KafkaListener(
            topics = "aficio.auth.events",
            groupId = "${spring.kafka.consumer.group-id:aficio}"
    )
    public void consumeAuthEvent(String payload) {
        try {
            EmailVerificationEvent event = objectMapper.readValue(payload, EmailVerificationEvent.class);
            log.info("Received email verification event for: {}", event.email());

            String verificationUrl = frontendUrl + "/verify-email?token=" + event.token();
            String body = "Welcome to Aficio!\n\nPlease verify your email by clicking the link below:\n\n" + verificationUrl;

            sendDirect(event.email(), "Verify your Aficio account", body);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize auth event, payload: {}, error: {}", payload, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to process auth event, payload: {}, error: {}", payload, e.getMessage());
        }
    }

    private void sendDirect(String toEmail, String subject, String body) {
        CreateEmailOptions params = CreateEmailOptions.builder()
                .from(sender)
                .to(toEmail)
                .subject(subject)
                .html("<p>" + body.replace("\n", "<br/>") + "</p>")
                .build();

        try {
            if (resendApiKey.isBlank()) {
                throw new IllegalStateException("RESEND_API_KEY is not configured");
            }
            new Resend(resendApiKey).emails().send(params);
            log.info("Verification email sent directly to: {}", toEmail);
        } catch (ResendException e) {
            log.error("Failed to send verification email to: {}, error: {}", toEmail, e.getMessage());
            throw new IllegalStateException("Failed to send verification email to " + toEmail + ": " + e.getMessage(), e);
        }
    }
}
