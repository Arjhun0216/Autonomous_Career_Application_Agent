package com.jobscraper.job_platform.recommendation;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RecommendationResponse {

    private String jobId;

    private String title;

    private String company;

    private String location;

    private String salary;

    private String jobUrl;

    private double matchScore;
}