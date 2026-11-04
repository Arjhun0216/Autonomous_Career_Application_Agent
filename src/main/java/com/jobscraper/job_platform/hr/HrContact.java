package com.jobscraper.job_platform.hr;

import jakarta.persistence.*;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "hr_contacts",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "unique_hr_company",
            columnNames = {"mail_id", "company_name"}
        )
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HrContact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "hr_id")
    private Long hrId;

    @Column(name = "mail_id", nullable = false)
    private String mailId;

    @Column(name = "company_name", nullable = false)
    private String companyName;
}