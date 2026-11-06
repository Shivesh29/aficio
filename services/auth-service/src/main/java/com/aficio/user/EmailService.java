package com.aficio.user;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final String frontendUrl;
    private final String resendApiKey;
    private final String sender;

    public EmailService(
            @Value("${aficio.frontend.url:http://localhost:5174}") String frontendUrl,
            @Value("${aficio.resend.api-key:}") String resendApiKey,
            @Value("${aficio.resend.from:Aficio <onboarding@resend.dev>}") String sender
    ) {
        this.frontendUrl = frontendUrl;
        this.resendApiKey = resendApiKey;
        this.sender = sender;
    }

    public String sendVerificationEmail(Long userId, String toEmail, String token) {
        String verificationUrl = frontendUrl + "/verify-email?token=" + token;

        if (resendApiKey != null && !resendApiKey.isBlank()) {
            try {
                String body = "Welcome to Aficio!\n\nPlease verify your email by clicking the link below:\n\n" + verificationUrl;
                CreateEmailOptions params = CreateEmailOptions.builder()
                        .from(sender)
                        .to(toEmail)
                        .subject("Verify your Aficio account")
                        .html("<p>" + body.replace("\n", "<br/>") + "</p>")
                        .build();

                new Resend(resendApiKey).emails().send(params);
                log.info("Verification email sent directly to: {}", toEmail);
            } catch (ResendException e) {
                log.error("Failed to send verification email to: {}, error: {}", toEmail, e.getMessage());
            } catch (Exception e) {
                log.error("Unexpected error sending verification email to: {}, error: {}", toEmail, e.getMessage());
            }
        } else {
            log.info("RESEND_API_KEY is not configured; local verification link: {}", verificationUrl);
        }

        return verificationUrl;
    }
}
