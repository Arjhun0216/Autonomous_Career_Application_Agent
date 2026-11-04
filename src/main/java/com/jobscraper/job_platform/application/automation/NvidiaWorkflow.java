package com.jobscraper.job_platform.application.automation;

import com.microsoft.playwright.Page;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class NvidiaWorkflow implements CompanyWorkflow {
    @Override
    public String getCompany() {
        return "NVIDIA";
    }

    @Override
    public boolean supports(Page page) {
        if (page == null) return false;
        try {
            String url = page.url().toLowerCase(Locale.ROOT);
            return url.contains("nvidia.wd5.myworkdayjobs.com")
                    || url.contains("nvidia.com");
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override
    public String getInstructions() {
        return "NVIDIA application setup stops for manual Google sign-in. Do not enter Google credentials or submit the application.";
    }
}
