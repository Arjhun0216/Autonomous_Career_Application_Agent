package com.jobscraper.job_platform.hr;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class HrContactService {

    private final HrContactRepository hrContactRepository;

    public List<HrContact> getRecruitersForCompany(
            String companyName) {

        if (companyName == null ||
                companyName.isBlank()) {

            return List.of();
        }

        return hrContactRepository
                .findByCompanyNameIgnoreCase(
                        companyName.trim()
                );
    }

    public HrContact addRecruiter(
            String mailId,
            String companyName) {

        if (mailId == null || mailId.isBlank()) {
            throw new RuntimeException(
                    "Recruiter email is required"
            );
        }

        if (companyName == null ||
                companyName.isBlank()) {

            throw new RuntimeException(
                    "Company name is required"
            );
        }

        String normalizedMailId = mailId.trim();
        try {
            new InternetAddress(normalizedMailId, true).validate();
        } catch (AddressException e) {
            throw new IllegalArgumentException(
                    "Enter a valid recruiter email address.",
                    e
            );
        }

        HrContact contact =
                HrContact.builder()
                        .mailId(normalizedMailId)
                        .companyName(companyName.trim())
                        .build();

        return hrContactRepository.save(contact);
    }

    public List<HrContact> getAllRecruiters() {
        return hrContactRepository.findAll();
    }
}
