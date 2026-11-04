package com.jobscraper.job_platform.application.automation.ats;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class WorkdayAdapter {

    public boolean canHandle(Page page) {

        if (page == null) {
            return false;
        }

        try {

            String url =
                    safe(page.url())
                            .toLowerCase(Locale.ROOT);

            String title =
                    safe(page.title())
                            .toLowerCase(Locale.ROOT);

            String body = "";

            try {

                body =
                        page.locator("body")
                                .innerText()
                                .toLowerCase(Locale.ROOT);

            } catch (Exception ignored) {
            }


            /*
             * Strongest Workday indicator.
             */

            if (url.contains(
                    "myworkdayjobs.com"
            )) {

                return true;
            }


            /*
             * Workday domain with recruiting content.
             */

            if (url.contains("workday.com")
                    && (
                    body.contains("apply")
                            || body.contains("candidate")
                            || body.contains("application")
                            || title.contains("workday")
            )) {

                return true;
            }


            /*
             * Workday text indicator.
             */

            return body.contains("workday")
                    && (
                    body.contains("apply")
                            || body.contains("candidate")
                            || body.contains("application")
            );

        } catch (Exception e) {

            return false;
        }
    }


    public String instructions(
            Page page
    ) {

        return """
                ================================
                WORKDAY RECRUITING MODE
                ================================

                The current page appears to use Workday Recruiting.

                Workday application pages can vary between employers.
                Never assume that one employer's selectors work for another.

                GENERAL WORKDAY BEHAVIOUR:

                1. Find the actual Apply control from the current page.

                2. Clicking Apply may:
                   - navigate to another Workday page;
                   - open another page;
                   - redirect to authentication;
                   - show an application form immediately.

                3. After EVERY browser action, inspect the new state.

                4. Workday frequently uses custom comboboxes and searchable
                   dropdowns.

                5. For dropdowns:
                   - inspect actual visible options;
                   - select an existing option;
                   - never invent an option;
                   - verify the selection after choosing it.

                6. Workday can reveal new questions after previous questions
                   are answered.

                7. Therefore the current DOM is always more important than
                   an earlier DOM snapshot.

                8. Use the candidate profile as the source of truth.

                9. If a required question cannot be answered from the profile,
                   use ASK_USER.

                10. Upload the supplied resume when requested.

                11. Do not upload unrelated files.

                12. If authentication is required, return:

                {
                  "status":"AUTHENTICATION_REQUIRED",
                  "action":"WAIT",
                  "message":"Authentication is required."
                }

                13. Do not enter passwords, OTPs, MFA codes, security codes,
                    recovery codes, or other authentication secrets.

                14. If CAPTCHA/human verification appears, return:

                {
                  "status":"CAPTCHA_BLOCKED",
                  "action":"WAIT",
                  "message":"Human verification is required."
                }

                15. Do not report SUCCESS merely because a Submit button was
                    clicked.

                16. Require visible confirmation that the application was
                    actually submitted.

                17. Do not navigate to another job while completing the
                    current application.

                18. Prefer semantic controls, labels, roles, accessible names
                    and visible text over coordinates.

                ================================
                END WORKDAY MODE
                ================================
                """;
    }


    public String inspect(
            Page page
    ) {

        StringBuilder result =
                new StringBuilder();


        result.append(
                "\n========== WORKDAY INSPECTION ==========\n"
        );


        result.append(
                "URL: "
        )
                .append(
                        safe(page.url())
                )
                .append("\n");


        result.append(
                "TITLE: "
        )
                .append(
                        safe(page.title())
                )
                .append("\n");


        try {

            result.append(
                    "VISIBLE BUTTONS: "
            )
                    .append(
                            page.getByRole(
                                    com.microsoft.playwright.options.AriaRole.BUTTON
                            ).allInnerTexts()
                    )
                    .append("\n");

        } catch (Exception ignored) {
        }


        try {

            result.append(
                    "VISIBLE LINKS: "
            )
                    .append(
                            page.getByRole(
                                    com.microsoft.playwright.options.AriaRole.LINK
                            ).allInnerTexts()
                    )
                    .append("\n");

        } catch (Exception ignored) {
        }


        try {

            Locator controls =
                    page.locator(
                            "input:visible,"
                                    + "textarea:visible,"
                                    + "select:visible,"
                                    + "button:visible"
                    );


            result.append(
                    "VISIBLE CONTROLS: "
            )
                    .append(
                            controls.count()
                    )
                    .append("\n");

        } catch (Exception ignored) {
        }


        result.append(
                "=========================================\n"
        );


        return result.toString();
    }


    private String safe(
            String value
    ) {

        return value == null
                ? ""
                : value;
    }
}