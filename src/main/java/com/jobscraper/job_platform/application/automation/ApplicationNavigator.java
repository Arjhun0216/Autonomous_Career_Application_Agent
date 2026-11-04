package com.jobscraper.job_platform.application.automation;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class ApplicationNavigator {

    private static final String[] APPLY_TEXTS = {
            "apply now",
            "apply for this job",
            "apply for job",
            "apply"
    };

    public ApplicationNavigationResult openApplicationPage(
            Page page
    ) {

        if (page == null) {
            return ApplicationNavigationResult.failed(
                    "Page is missing"
            );
        }

        try {

            System.out.println("======================================");
            System.out.println("APPLICATION NAVIGATOR");
            System.out.println("Current URL: " + safeUrl(page));
            System.out.println("Searching for Apply button...");
            System.out.println("======================================");

            Locator applyElement =
                    findApplyElement(page);

            if (applyElement == null) {

                return ApplicationNavigationResult.failed(
                        "Could not find an Apply button or link."
                );
            }

            String applyText =
                    safeText(applyElement);

            String href =
                    safeAttribute(
                            applyElement,
                            "href"
                    );

            System.out.println(
                    "Apply element found: "
                            + applyText
            );

            System.out.println(
                    "Apply href: "
                            + href
            );

            String originalUrl =
                    safeUrl(page);

            List<Page> pagesBefore =
                    new ArrayList<>(
                            page.context().pages()
                    );

            /*
             * IMPORTANT:
             *
             * Do NOT wait for scheduled navigation.
             *
             * The previous implementation timed out after the click
             * had already succeeded.
             */

            System.out.println(
                    "Clicking Apply..."
            );

            try {

                applyElement.click(
                        new Locator.ClickOptions()
                                .setTimeout(10000)
                                .setNoWaitAfter(true)
                );

                System.out.println(
                        "Apply click completed."
                );

            } catch (Exception e) {

                System.out.println(
                        "Apply click warning: "
                                + e.getMessage()
                );
            }

            /*
             * Allow the page/new tab to settle.
             */

            page.waitForTimeout(3500);

            Page applicationPage =
                    findNewPage(
                            pagesBefore,
                            page
                    );

            if (applicationPage != null) {

                System.out.println(
                        "NEW APPLICATION TAB DETECTED."
                );

                try {
                    applicationPage.bringToFront();
                } catch (Exception ignored) {
                }

            } else {

                applicationPage =
                        page;
            }

            /*
             * If the click did not navigate and the Apply element
             * contains a real href, navigate directly.
             */

            String currentUrl =
                    safeUrl(applicationPage);

            System.out.println(
                    "Original URL: "
                            + originalUrl
            );

            System.out.println(
                    "Current URL: "
                            + currentUrl
            );

            if (sameUrl(
                    originalUrl,
                    currentUrl
            )
                    && href != null
                    && !href.isBlank()) {

                String absoluteHref =
                        makeAbsoluteUrl(
                                originalUrl,
                                href
                        );

                System.out.println(
                        "Navigating directly to Apply URL:"
                );

                System.out.println(
                        absoluteHref
                );

                try {

                    applicationPage.navigate(
                            absoluteHref,
                            new Page.NavigateOptions()
                                    .setTimeout(30000)
                                    .setWaitUntil(
                                            com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED
                                    )
                    );

                } catch (Exception e) {

                    System.out.println(
                            "Direct Apply navigation warning: "
                                    + e.getMessage()
                    );
                }

                applicationPage.waitForTimeout(
                        3000
                );
            }

            /*
             * Wait for DOM to settle.
             */

            try {

                applicationPage.waitForLoadState(
                        com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED
                );

            } catch (Exception ignored) {
            }

            applicationPage.waitForTimeout(
                    1500
            );

            if (containsAuthenticationRequired(
                    applicationPage
            )) {

                return ApplicationNavigationResult.authenticationRequired(
                        "Google/application authentication is required. "
                                + "Complete authentication manually."
                );
            }

            if (containsCaptcha(
                    applicationPage
            )) {

                return ApplicationNavigationResult.blocked(
                        "CAPTCHA or human verification detected."
                );
            }

            System.out.println(
                    "======================================"
            );

            System.out.println(
                    "APPLICATION PAGE READY"
            );

            System.out.println(
                    "URL: "
                            + safeUrl(
                            applicationPage
                    )
            );

            System.out.println(
                    "TITLE: "
                            + safeTitle(
                            applicationPage
                    )
            );

            System.out.println(
                    "======================================"
            );

            return ApplicationNavigationResult.success(
                    "Application page opened successfully.",
                    applicationPage
            );

        } catch (Exception e) {

            e.printStackTrace();

            return ApplicationNavigationResult.failed(
                    "Unable to open application page: "
                            + e.getMessage()
            );
        }
    }

    private Locator findApplyElement(
            Page page
    ) {

        Locator links =
                page.locator(
                        "a:visible"
                );

        for (int i = 0;
             i < links.count();
             i++) {

            Locator element =
                    links.nth(i);

            String text =
                    safeText(element)
                            .toLowerCase(
                                    Locale.ROOT
                            )
                            .replaceAll(
                                    "\\s+",
                                    " "
                            )
                            .trim();

            String aria =
                    safeAttribute(
                            element,
                            "aria-label"
                    )
                            .toLowerCase(
                                    Locale.ROOT
                            );

            if (isApplyText(text)
                    || isApplyText(aria)) {

                return element;
            }
        }

        Locator buttons =
                page.locator(
                        "button:visible, [role='button']:visible"
                );

        for (int i = 0;
             i < buttons.count();
             i++) {

            Locator element =
                    buttons.nth(i);

            String text =
                    safeText(element)
                            .toLowerCase(
                                    Locale.ROOT
                            )
                            .replaceAll(
                                    "\\s+",
                                    " "
                            )
                            .trim();

            String aria =
                    safeAttribute(
                            element,
                            "aria-label"
                    )
                            .toLowerCase(
                                    Locale.ROOT
                            );

            if (isApplyText(text)
                    || isApplyText(aria)) {

                return element;
            }
        }

        return null;
    }

    private boolean isApplyText(
            String text
    ) {

        if (text == null
                || text.isBlank()) {

            return false;
        }

        for (String candidate :
                APPLY_TEXTS) {

            if (text.equals(candidate)
                    || text.startsWith(
                    candidate + " ")) {

                return true;
            }
        }

        return false;
    }

    private Page findNewPage(
            List<Page> pagesBefore,
            Page original
    ) {

        for (Page page :
                original.context().pages()) {

            if (!pagesBefore.contains(page)) {
                return page;
            }
        }

        return null;
    }

    private boolean sameUrl(
            String first,
            String second
    ) {

        if (first == null
                || second == null) {

            return true;
        }

        return first.equals(
                second
        );
    }

    private String makeAbsoluteUrl(
            String base,
            String href
    ) {

        try {

            return java.net.URI
                    .create(base)
                    .resolve(href)
                    .toString();

        } catch (Exception e) {

            return href;
        }
    }

    private boolean containsAuthenticationRequired(
            Page page
    ) {

        String text =
                safeBody(page)
                        .toLowerCase(
                                Locale.ROOT
                        );

        return text.contains(
                "sign in to continue"
        )
                || text.contains(
                "sign in"
        ) && text.contains(
                "google account"
        );
    }

    private boolean containsCaptcha(
            Page page
    ) {

        try {

            if (page.locator(
                    ".g-recaptcha:visible"
            ).count() > 0) {

                return true;
            }

            if (page.locator(
                    "iframe[src*='captcha']:visible"
            ).count() > 0) {

                return true;
            }

            String text =
                    safeBody(page)
                            .toLowerCase(
                                    Locale.ROOT
                            );

            return text.contains(
                    "verify you are human"
            )
                    || text.contains(
                    "i'm not a robot"
            )
                    || text.contains(
                    "human verification"
            );

        } catch (Exception e) {

            return false;
        }
    }

    private String safeBody(
            Page page
    ) {

        try {

            return page.locator(
                    "body"
            ).innerText();

        } catch (Exception e) {

            return "";
        }
    }

    private String safeText(
            Locator locator
    ) {

        try {

            return locator.innerText()
                    .trim();

        } catch (Exception e) {

            return "";
        }
    }

    private String safeAttribute(
            Locator locator,
            String attribute
    ) {

        try {

            String value =
                    locator.getAttribute(
                            attribute
                    );

            return value == null
                    ? ""
                    : value;

        } catch (Exception e) {

            return "";
        }
    }

    private String safeUrl(
            Page page
    ) {

        try {

            return page.url();

        } catch (Exception e) {

            return "";
        }
    }

    private String safeTitle(
            Page page
    ) {

        try {

            return page.title();

        } catch (Exception e) {

            return "";
        }
    }
}