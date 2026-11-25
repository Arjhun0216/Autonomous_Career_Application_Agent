package com.jobscraper.job_platform.gmail;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;

import com.jobscraper.job_platform.user.User;

import jakarta.mail.Message.RecipientType;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Properties;

@Service
@RequiredArgsConstructor
public class GmailSendService {

    private final GmailOAuthService gmailOAuthService;

public String sendApplicationEmail(
        User user,
        String to,
        String subject,
        String body,
        String resumePath
) {
    sendEmail(
            user,
            to,
            subject,
            body,
            resumePath
    );

    return "EMAIL_SENT";
}
    /**
     * ============================================================
     * SEND EMAIL WITH OPTIONAL RESUME ATTACHMENT
     * ============================================================
     */
    public void sendEmail(
            User user,
            String to,
            String subject,
            String body,
            String resumePath) {

        sendEmail(gmailOAuthService.getGmailService(user), user, to, subject, body, resumePath);
    }

    public Gmail getAuthorizedGmail(User user) {
        return gmailOAuthService.getGmailService(user);
    }

    public void sendEmail(
            Gmail gmail,
            User user,
            String to,
            String subject,
            String body,
            String resumePath) {

        if (user == null) {
            throw new RuntimeException(
                    "User is required"
            );
        }

        if (to == null || to.isBlank()) {
            throw new RuntimeException(
                    "Recipient email is required"
            );
        }

        if (subject == null || subject.isBlank()) {
            throw new RuntimeException(
                    "Email subject is required"
            );
        }

        if (body == null || body.isBlank()) {
            throw new RuntimeException(
                    "Email body is required"
            );
        }

        try {

            /*
             * Create MIME email.
             */
            MimeMessage mimeMessage =
                    createMimeMessage(
                            user,
                            to,
                            subject,
                            body,
                            resumePath
                    );

            /*
             * Convert MIME message to bytes.
             */
            ByteArrayOutputStream buffer =
                    new ByteArrayOutputStream();

            mimeMessage.writeTo(buffer);

            /*
             * Gmail API requires Base64 URL-safe encoding.
             */
            String encodedEmail =
                    Base64.getUrlEncoder()
                            .withoutPadding()
                            .encodeToString(
                                    buffer.toByteArray()
                            );

            /*
             * Create Gmail API Message.
             */
            Message gmailMessage =
                    new Message();

            gmailMessage.setRaw(
                    encodedEmail
            );

            /*
             * Send email.
             *
             * "me" means the authenticated Gmail account.
             */
            gmail.users()
                    .messages()
                    .send(
                            "me",
                            gmailMessage
                    )
                    .execute();

        } catch (Exception e) {

            throw new RuntimeException(
                    "Failed to send Gmail message: "
                            + e.getMessage(),
                    e
            );
        }
    }

    


    /**
     * ============================================================
     * CREATE MIME MESSAGE
     * ============================================================
     */
    private MimeMessage createMimeMessage(
            User user,
            String to,
            String subject,
            String body,
            String resumePath
    ) throws Exception {

        /*
         * Create mail session.
         *
         * We are NOT using SMTP here.
         *
         * This session is only used to construct
         * the MIME message before sending it
         * through Gmail API.
         */
        Properties properties =
                new Properties();

        Session session =
                Session.getInstance(
                        properties
                );

        MimeMessage message =
                new MimeMessage(
                        session
                );


        /*
         * --------------------------------------------------------
         * FROM
         * --------------------------------------------------------
         */
        String fromEmail =
                user.getGmailEmail();

        if (fromEmail == null ||
                fromEmail.isBlank()) {

            throw new RuntimeException(
                    "Connected Gmail email is missing"
            );
        }

        message.setFrom(
                new InternetAddress(
                        fromEmail
                )
        );


        /*
         * --------------------------------------------------------
         * TO
         * --------------------------------------------------------
         */
        message.setRecipient(
                RecipientType.TO,
                new InternetAddress(
                        to
                )
        );


        /*
         * --------------------------------------------------------
         * SUBJECT
         * --------------------------------------------------------
         */
        message.setSubject(
                subject,
                "UTF-8"
        );


        /*
         * --------------------------------------------------------
         * EMAIL BODY
         * --------------------------------------------------------
         */
        MimeBodyPart textPart =
                new MimeBodyPart();

        textPart.setText(
                body,
                "UTF-8"
        );


        /*
         * --------------------------------------------------------
         * MULTIPART
         * --------------------------------------------------------
         */
        MimeMultipart multipart =
                new MimeMultipart();

        multipart.addBodyPart(
                textPart
        );


        /*
         * --------------------------------------------------------
         * RESUME ATTACHMENT
         * --------------------------------------------------------
         */
        if (resumePath != null &&
                !resumePath.isBlank()) {

            Path path =
                    Path.of(resumePath);

            if (!Files.exists(path)) {

                throw new RuntimeException(
                        "Resume file not found: "
                                + resumePath
                );
            }

            if (!Files.isRegularFile(path)) {

                throw new RuntimeException(
                        "Resume path is not a file: "
                                + resumePath
                );
            }

            byte[] pdfBytes =
                    Files.readAllBytes(
                            path
                    );

            MimeBodyPart attachment =
                    new MimeBodyPart();

            attachment.setContent(
                    pdfBytes,
                    "application/pdf"
            );

            attachment.setFileName(
                    path.getFileName()
                            .toString()
            );

            multipart.addBodyPart(
                    attachment
            );
        }


        /*
         * Attach multipart content
         * to the email.
         */
        message.setContent(
                multipart
        );

        /*
         * Finalize MIME headers.
         */
        message.saveChanges();

        return message;
    }
}
