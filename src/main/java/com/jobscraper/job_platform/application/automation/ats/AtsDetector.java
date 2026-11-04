package com.jobscraper.job_platform.application.automation.ats;

import com.microsoft.playwright.Page;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class AtsDetector {

    public AtsType detect(Page page) {

        if (page == null) {
            return AtsType.UNKNOWN;
        }

        String url =
                safe(page.url())
                        .toLowerCase(Locale.ROOT);

        String content = "";

        try {

            content =
                    page.content()
                            .toLowerCase(Locale.ROOT);

        } catch (Exception ignored) {
        }


        /*
         * ============================================
         * WORKDAY
         * ============================================
         */

        if (url.contains("myworkdayjobs.com")) {

            return AtsType.WORKDAY;
        }

        /*
         * Some Workday pages redirect to a workday.com
         * domain while still presenting a recruiting flow.
         */

        if (url.contains("workday.com")
                && isRecruitingPage(content)) {

            return AtsType.WORKDAY;
        }


        /*
         * ============================================
         * GREENHOUSE
         * ============================================
         */

        if (url.contains("greenhouse.io")
                || content.contains("greenhouse")) {

            return AtsType.GREENHOUSE;
        }


        /*
         * ============================================
         * LEVER
         * ============================================
         */

        if (url.contains("lever.co")
                || content.contains("lever-jobs")) {

            return AtsType.LEVER;
        }


        /*
         * ============================================
         * LINKEDIN
         * ============================================
         */

        if (url.contains("linkedin.com")) {

            return AtsType.LINKEDIN;
        }


        /*
         * ============================================
         * INDEED
         * ============================================
         */

        if (url.contains("indeed.com")) {

            return AtsType.INDEED;
        }


        return AtsType.UNKNOWN;
    }


    private boolean isRecruitingPage(
            String content
    ) {

        return content.contains("candidate")
                || content.contains("job application")
                || content.contains("apply")
                || content.contains("careers");
    }


    private String safe(String value) {

        return value == null
                ? ""
                : value;
    }
}