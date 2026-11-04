package com.jobscraper.job_platform.hr;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/hr")
@RequiredArgsConstructor
public class HrContactController {

    private final HrContactService hrContactService;

    @GetMapping
    public ResponseEntity<?> getAllRecruiters() {

        return ResponseEntity.ok(
                hrContactService.getAllRecruiters()
        );
    }

    @GetMapping("/company/{companyName}")
    public ResponseEntity<?> getRecruitersByCompany(
            @PathVariable String companyName) {

        return ResponseEntity.ok(
                hrContactService
                        .getRecruitersForCompany(companyName)
        );
    }

    @PostMapping
    public ResponseEntity<?> addRecruiter(
            @RequestBody HrContactRequest request) {

        try {

            return ResponseEntity.ok(
                    hrContactService.addRecruiter(
                            request.mailId(),
                            request.companyName()
                    )
            );

        } catch (RuntimeException e) {

            return ResponseEntity
                    .badRequest()
                    .body(e.getMessage());
        }
    }

    public record HrContactRequest(
            String mailId,
            String companyName
    ) {}
}