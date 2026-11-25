package com.jobscraper.job_platform.hr;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HrContactRepository
        extends JpaRepository<HrContact, Long> {

    List<HrContact> findByCompanyNameIgnoreCase(
            String companyName
    );

    java.util.Optional<HrContact> findByMailIdIgnoreCaseAndCompanyNameIgnoreCase(
            String mailId, String companyName
    );
}
