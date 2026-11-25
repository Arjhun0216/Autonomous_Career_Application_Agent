package com.jobscraper.job_platform.college;

import com.jobscraper.job_platform.gmail.GmailSendService;
import com.google.api.services.gmail.Gmail;
import com.jobscraper.job_platform.hr.HrContact;
import com.jobscraper.job_platform.hr.HrContactRepository;
import com.jobscraper.job_platform.user.User;
import com.jobscraper.job_platform.user.UserRepository;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CollegeMailService {

    private static final String SUBJECT = "Entry-Level Software Development Opportunities | Arjun M";
    private static final String BODY_TEMPLATE = """
            Hi %s,

            I hope you’re doing well. I’m a final-semester B.E. Computer Science student at Government College of Engineering, Bargur, graduating in 2027. I’m reaching out to ask whether %s has any entry-level software development opportunities that may suit my background.

            I’ve built projects across full-stack development, AI/LLM integration, and test automation. These include a career application platform, an AI evaluation framework, and the DOTE 7.5 Management Portal, which I developed as a sole developer. My experience includes Java, Spring Boot, React, Python, SQL, Playwright, Docker, AWS, and GitHub Actions.

            If you think my background could be a fit for an opening at %s, would you kindly consider my application or point me toward the appropriate role? I’ve attached my resume for your review.

            Thank you for your time.

            Kind regards,
            Arjun M
            +91 6381771885
            arjhungautam0246@gmail.com
            """;

    private final HrContactRepository hrContactRepository;
    private final UserRepository userRepository;
    private final GmailSendService gmailSendService;

    @Value("${college.mailing.resume-path:uploads/Arjun_M_Resume.pdf}")
    private String resumePath;

    public DraftResponse getDraft(String authenticatedEmail) {
        User user = findUser(authenticatedEmail);
        Path resume = resolveResumePath();
        return new DraftResponse(SUBJECT,
                String.format(BODY_TEMPLATE, "[HR name]", "[company name]", "[company name]"),
                uniqueRecipientCount(), Files.isRegularFile(resume), resume.getFileName().toString(),
                user.isGmailConnected(), user.getGmailEmail() == null ? "" : user.getGmailEmail());
    }

    public BrochureResponse uploadResume(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Choose your resume PDF to upload.");
        if (file.getSize() > 15L * 1024 * 1024) throw new IllegalArgumentException("The resume must be 15 MB or smaller.");
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new IllegalArgumentException("The resume must be a PDF file.");
        }
        Path destination = resolveResumePath();
        Path temporary = destination.resolveSibling(destination.getFileName() + ".uploading");
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length < 5 || bytes[0] != '%' || bytes[1] != 'P' || bytes[2] != 'D'
                    || bytes[3] != 'F' || bytes[4] != '-') throw new IllegalArgumentException("The uploaded file is not a valid PDF.");
            Files.createDirectories(destination.getParent());
            Files.write(temporary, bytes);
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            return new BrochureResponse(true, destination.getFileName().toString(), file.getSize());
        } catch (IOException e) {
            try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
            throw new IllegalStateException("Could not save the resume.", e);
        }
    }

    public SendResponse sendToAll(String authenticatedEmail) {
        User user = findUser(authenticatedEmail);
        if (!user.isGmailConnected()) throw new IllegalStateException("Connect Gmail before sending.");
        Path resume = resolveResumePath();
        if (!Files.isRegularFile(resume)) throw new IllegalStateException("Upload your resume before sending.");
        Gmail gmail = gmailSendService.getAuthorizedGmail(user);

        Map<String, HrContact> recipients = new LinkedHashMap<>();
        for (HrContact contact : hrContactRepository.findAll()) {
            if (contact.getMailId() != null && !contact.getMailId().isBlank()) {
                recipients.putIfAbsent(contact.getMailId().trim().toLowerCase(Locale.ROOT), contact);
            }
        }
        if (recipients.isEmpty()) throw new IllegalStateException("There are no HR email addresses in the contacts list.");

        List<RecipientFailure> failures = new ArrayList<>();
        int sent = 0;
        for (HrContact contact : recipients.values()) {
            String address = contact.getMailId().trim();
            try {
                new InternetAddress(address, true).validate();
                String name = contact.getHrName() == null || contact.getHrName().isBlank()
                        ? "there" : contact.getHrName().trim();
                String company = contact.getCompanyName() == null || contact.getCompanyName().isBlank()
                        ? "your company" : contact.getCompanyName().trim();
                gmailSendService.sendEmail(gmail, user, address, SUBJECT,
                        String.format(BODY_TEMPLATE, name, company, company), resume.toString());
                sent++;
            } catch (AddressException e) {
                failures.add(new RecipientFailure(address, "Invalid email address"));
            } catch (RuntimeException e) {
                failures.add(new RecipientFailure(address, safeMessage(e)));
            }
        }
        return new SendResponse(recipients.size(), sent, failures.size(), List.copyOf(failures));
    }

    private int uniqueRecipientCount() {
        return (int) hrContactRepository.findAll().stream().map(HrContact::getMailId)
                .filter(address -> address != null && !address.isBlank())
                .map(address -> address.trim().toLowerCase(Locale.ROOT)).distinct().count();
    }

    private User findUser(String email) {
        if (email == null || email.isBlank()) throw new IllegalStateException("Authenticated user is missing.");
        return userRepository.findByEmail(email).orElseThrow(() -> new IllegalStateException("User account was not found."));
    }

    private Path resolveResumePath() { return Path.of(resumePath).toAbsolutePath().normalize(); }
    private String safeMessage(RuntimeException e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? "Gmail could not send this message." : message;
    }

    public record DraftResponse(String subject, String body, int recipientCount, boolean resumeUploaded,
                                String resumeName, boolean gmailConnected, String gmailEmail) {}
    public record BrochureResponse(boolean uploaded, String fileName, long sizeBytes) {}
    public record SendResponse(int recipientCount, int sentCount, int failedCount, List<RecipientFailure> failures) {}
    public record RecipientFailure(String email, String reason) {}
}
