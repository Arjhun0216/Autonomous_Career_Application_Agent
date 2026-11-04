package com.jobscraper.job_platform.college;

import com.jobscraper.job_platform.gmail.GmailSendService;
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

    private static final String SUBJECT =
            "Invitation to Conduct Campus Recruitment Drive at Government College of Engineering, Bargur – CSE Department, Batch 2027";

    private static final String BODY = """
            Dear Sir/Madam,

            Greetings from the Career Development Cell, Government College of Engineering, Bargur, Tamil Nadu.

            We are pleased to invite your organization to conduct a campus recruitment drive at our institution for the 2027 graduating batch of Computer Science and Engineering students.

            Government College of Engineering, Bargur, is an autonomous institution affiliated with Anna University, providing quality technical education and industry-oriented training. Our CSE students have a strong foundation in programming, software development, database management, data analytics, and emerging technologies, along with practical problem-solving skills.

            We kindly request you to share your recruitment requirements, eligible qualifications and branches, job roles, selection process, salary package, and proposed schedule for the campus recruitment drive. We would be happy to coordinate the necessary arrangements based on your organization's requirements.

            Kindly refer to the attached placement brochure for further information. We look forward to your positive response and hope to establish a strong association with your organization.

            Thank you for your time and consideration.

            Best regards,
            Dr. T. Mohandoss, M.E., Ph.D.
            CDC Officer
            Government College of Engineering, Bargur
            Krishnagiri, Tamil Nadu, India
            Contact no.: 9994811902
            """;

    private final HrContactRepository hrContactRepository;
    private final UserRepository userRepository;
    private final GmailSendService gmailSendService;

    @Value("${college.mailing.brochure-path:uploads/college-brochure.pdf}")
    private String brochurePath;

    public DraftResponse getDraft(String authenticatedEmail) {
        User user = findUser(authenticatedEmail);
        Path brochure = resolveBrochurePath();
        return new DraftResponse(
                SUBJECT,
                BODY,
                uniqueRecipientCount(),
                Files.isRegularFile(brochure),
                brochure.getFileName().toString(),
                user.isGmailConnected(),
                user.getGmailEmail() == null ? "" : user.getGmailEmail()
        );
    }

    public BrochureResponse uploadBrochure(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Choose a PDF brochure to upload.");
        }
        if (file.getSize() > 15L * 1024 * 1024) {
            throw new IllegalArgumentException("The brochure must be 15 MB or smaller.");
        }
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new IllegalArgumentException("The brochure must be a PDF file.");
        }

        Path destination = resolveBrochurePath();
        Path temporary = destination.resolveSibling(destination.getFileName() + ".uploading");
        try {
            byte[] header = file.getBytes();
            if (header.length < 5 || header[0] != '%' || header[1] != 'P'
                    || header[2] != 'D' || header[3] != 'F' || header[4] != '-') {
                throw new IllegalArgumentException("The uploaded file is not a valid PDF.");
            }
            Files.createDirectories(destination.getParent());
            Files.write(temporary, header);
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            return new BrochureResponse(true, destination.getFileName().toString(), file.getSize());
        } catch (IOException e) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // Keep the original upload failure as the useful error.
            }
            throw new IllegalStateException("Could not save the college brochure.", e);
        }
    }

    public SendResponse sendToAll(String authenticatedEmail) {
        User user = findUser(authenticatedEmail);
        if (!user.isGmailConnected()) {
            throw new IllegalStateException("Connect Gmail before sending the college email.");
        }

        Path brochure = resolveBrochurePath();
        if (!Files.isRegularFile(brochure)) {
            throw new IllegalStateException("Upload the college brochure before sending.");
        }

        Map<String, String> recipients = new LinkedHashMap<>();
        for (HrContact contact : hrContactRepository.findAll()) {
            if (contact.getMailId() == null || contact.getMailId().isBlank()) {
                continue;
            }
            String address = contact.getMailId().trim();
            recipients.putIfAbsent(address.toLowerCase(Locale.ROOT), address);
        }
        if (recipients.isEmpty()) {
            throw new IllegalStateException("There are no HR email addresses in the contacts list.");
        }

        List<RecipientFailure> failures = new ArrayList<>();
        int sent = 0;
        for (String address : recipients.values()) {
            try {
                new InternetAddress(address, true).validate();
                gmailSendService.sendEmail(user, address, SUBJECT, BODY, brochure.toString());
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
        return (int) hrContactRepository.findAll().stream()
                .map(HrContact::getMailId)
                .filter(address -> address != null && !address.isBlank())
                .map(address -> address.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .count();
    }

    private User findUser(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalStateException("Authenticated user is missing.");
        }
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User account was not found."));
    }

    private Path resolveBrochurePath() {
        return Path.of(brochurePath).toAbsolutePath().normalize();
    }

    private String safeMessage(RuntimeException e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? "Gmail could not send this message." : message;
    }

    public record DraftResponse(
            String subject,
            String body,
            int recipientCount,
            boolean brochureUploaded,
            String brochureName,
            boolean gmailConnected,
            String gmailEmail
    ) {}

    public record BrochureResponse(boolean uploaded, String fileName, long sizeBytes) {}

    public record SendResponse(
            int recipientCount,
            int sentCount,
            int failedCount,
            List<RecipientFailure> failures
    ) {}

    public record RecipientFailure(String email, String reason) {}
}
