package com.jobscraper.job_platform.application.automation;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Zoho-specific application entry workflow.
 *
 * Zoho job pages do not necessarily expose a normal "Apply" button.
 * The job page uses an "I'm Interested" entry point.
 *
 * RESPONSIBILITY:
 *  1. Detect Zoho job pages.
 *  2. Find the visible "I'm Interested" entry control.
 *  3. Click it without assuming Playwright navigation.
 *  4. Detect a new tab if Zoho opens one.
 *  5. Follow a real href if the click did not navigate.
 *  6. Return the resulting application page.
 *
 * IMPORTANT:
 * This class does NOT fill the application form.
 * After the application page is opened, GeminiApplicationAgent handles
 * the common form analysis/filling workflow.
 */
@Component
public class ZohoApplicationAdapter {

    private static final String COOKIE_CONTAINERS =
            "[id*='cookie' i]:visible, [class*='cookie' i]:visible, " +
            "[id*='consent' i]:visible, [class*='consent' i]:visible, " +
            "[id*='privacy' i]:visible, [class*='privacy' i]:visible, " +
            "[role='dialog']:visible, [aria-modal='true']:visible";

    private static final String CLICKABLE_CONTROLS =
            "button:visible, a:visible, [role='button']:visible, " +
            "input[type='button']:visible, input[type='submit']:visible";

    private static final String[] ENTRY_TEXTS = {
            "i'm interested",
            "i’m interested",
            "im interested",
            "i am interested"
    };

    public boolean supports(Page page) {

        if (page == null) {
            return false;
        }

        String url = safeUrl(page).toLowerCase(Locale.ROOT);
        String title = safeTitle(page).toLowerCase(Locale.ROOT);

        return url.contains("careers.zohocorp.com")
                || url.contains("zohocorp.com/jobs")
                || title.contains("zoho corporation");
    }

    public ApplicationNavigationResult openApplicationPage(
            Page page
    ) {

        if (page == null) {
            return ApplicationNavigationResult.failed(
                    "Zoho job page is missing."
            );
        }

        if (!supports(page)) {
            return ApplicationNavigationResult.failed(
                    "The current page is not recognized as a Zoho job page."
            );
        }

        try {

            System.out.println("======================================");
            System.out.println("ZOHO APPLICATION ADAPTER");
            System.out.println("======================================");
            System.out.println("Current URL: " + safeUrl(page));
            System.out.println("Title: " + safeTitle(page));
            System.out.println("Searching for \"I'm Interested\"...");
            System.out.println("======================================");

            String cookieError = acceptCookieConsent(page);
            if (cookieError != null) {
                return ApplicationNavigationResult.failed(cookieError);
            }

            Locator entry =
                    findInterestedElement(page);

            if (entry == null) {

                /*
                 * Give dynamically-rendered Zoho content a little more time
                 * before declaring that the entry point is unavailable.
                 */
                page.waitForTimeout(2500);

                entry =
                        findInterestedElement(page);
            }

            if (entry == null) {

                return ApplicationNavigationResult.failed(
                        "Zoho \"I'm Interested\" button/link was not found."
                );
            }

            String entryText =
                    safeText(entry);

            String href =
                    safeAttribute(entry, "href");

            String originalUrl =
                    safeUrl(page);

            System.out.println(
                    "Zoho entry element found: "
                            + entryText
            );

            System.out.println(
                    "Zoho entry href: "
                            + href
            );

            /*
             * Remember all currently open pages. Zoho may open the
             * application form in another tab.
             */
            List<Page> pagesBefore =
                    new ArrayList<>(
                            page.context().pages()
                    );

            System.out.println(
                    "Clicking \"I'm Interested\"..."
            );

            try {

                entry.click(
                        new Locator.ClickOptions()
                                .setTimeout(15000)
                                .setNoWaitAfter(true)
                );

                System.out.println(
                        "Zoho entry click completed."
                );

            } catch (Exception e) {

                System.out.println(
                        "Zoho entry click warning: "
                                + e.getMessage()
                );
            }

            /*
             * Do not wait for scheduled navigation. The click may have
             * already succeeded, opened a new tab, or changed the DOM
             * without changing the URL.
             */
            page.waitForTimeout(3000);

            Page applicationPage =
                    findNewPage(
                            pagesBefore,
                            page
                    );

            if (applicationPage != null) {

                System.out.println(
                        "NEW ZOHO APPLICATION TAB DETECTED."
                );

                try {
                    applicationPage.bringToFront();
                } catch (Exception ignored) {
                }

            } else {

                applicationPage = page;
            }

            /*
             * If Zoho exposed a real href but the click did not navigate,
             * follow the href directly.
             */
            String currentUrl =
                    safeUrl(applicationPage);

            if (sameUrl(originalUrl, currentUrl)
                    && href != null
                    && !href.isBlank()
                    && !href.startsWith("javascript:")
                    && !href.startsWith("#")) {

                String absoluteHref =
                        makeAbsoluteUrl(
                                originalUrl,
                                href
                        );

                System.out.println(
                        "Zoho click did not navigate."
                );

                System.out.println(
                        "Following Zoho entry URL: "
                                + absoluteHref
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
                            "Zoho direct navigation warning: "
                                    + e.getMessage()
                    );
                }

                applicationPage.waitForTimeout(2500);
            }

            /*
             * Allow SPA/modal content to render.
             */
            try {

                applicationPage.waitForLoadState(
                        com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED
                );

            } catch (Exception ignored) {
            }

            applicationPage.waitForTimeout(1500);

            if (containsAuthenticationRequired(applicationPage)) {

                return ApplicationNavigationResult.authenticationRequired(
                        "Zoho authentication is required before the application can continue."
                );
            }

            if (containsCaptcha(applicationPage)) {

                return ApplicationNavigationResult.blocked(
                        "Zoho CAPTCHA or human verification detected."
                );
            }

            /*
             * Make sure the click actually exposed an application form.
             * We intentionally use broad signals because Zoho may render
             * the form dynamically rather than changing the URL.
             */
            if (!looksLikeApplicationPage(applicationPage)) {

                /*
                 * One additional wait helps with slow client-side rendering.
                 */
                applicationPage.waitForTimeout(2500);

                if (containsAuthenticationRequired(applicationPage)) {

                    return ApplicationNavigationResult.authenticationRequired(
                            "Zoho authentication is required before the application can continue."
                    );
                }

                if (containsCaptcha(applicationPage)) {

                    return ApplicationNavigationResult.blocked(
                            "Zoho CAPTCHA or human verification detected."
                    );
                }

                if (!looksLikeApplicationPage(applicationPage)) {

                    return ApplicationNavigationResult.failed(
                            "Zoho \"I'm Interested\" was clicked, but the application form did not appear."
                    );
                }
            }

            System.out.println("======================================");
            System.out.println("ZOHO APPLICATION PAGE READY");
            System.out.println("URL: " + safeUrl(applicationPage));
            System.out.println("TITLE: " + safeTitle(applicationPage));
            System.out.println("======================================");

            return ApplicationNavigationResult.success(
                    "Zoho application page opened successfully.",
                    applicationPage
            );

        } catch (Exception e) {

            e.printStackTrace();

            return ApplicationNavigationResult.failed(
                    "Unable to open Zoho application page: "
                            + (
                            e.getMessage() == null
                                    ? "Unknown error"
                                    : e.getMessage()
                    )
            );
        }
    }

    /**
     * Accepts a visible cookie/consent banner before touching the job page.
     * Returns an error if a banner is present but no clear accept action is
     * available, so the workflow never clicks through an unresolved overlay.
     */
    private String acceptCookieConsent(Page page) {
        Locator containers = page.locator(COOKIE_CONTAINERS);
        Locator consentContainer = null;
        Locator acceptButton = null;
        boolean bannerDetected = false;

        for (int i = 0; i < containers.count(); i++) {
            Locator candidate = containers.nth(i);
            String text = safeText(candidate).toLowerCase(Locale.ROOT);
            if (!isCookieConsentText(text)) {
                continue;
            }

            bannerDetected = true;
            consentContainer = candidate;
            Locator controls = candidate.locator(CLICKABLE_CONTROLS);
            for (int j = 0; j < controls.count(); j++) {
                Locator control = controls.nth(j);
                String label = firstNonBlank(
                        safeText(control),
                        safeAttribute(control, "aria-label"),
                        safeAttribute(control, "title"),
                        safeAttribute(control, "value")
                );
                if (isCookieAcceptLabel(label)) {
                    acceptButton = control;
                    break;
                }
            }
            if (acceptButton != null) {
                break;
            }
        }

        if (!bannerDetected) {
            System.out.println("No visible cookie consent banner detected.");
            return null;
        }

        if (acceptButton == null) {
            return "Zoho cookie consent is visible, but no clear Accept/Agree action was found.";
        }

        try {
            System.out.println("Accepting Zoho cookie consent: " + safeText(acceptButton));
            acceptButton.click(new Locator.ClickOptions().setTimeout(8000));
            page.waitForTimeout(3000);
        } catch (Exception e) {
            return "Unable to accept Zoho cookie consent: " +
                    (e.getMessage() == null ? "Browser click failed." : e.getMessage());
        }

        if (consentContainer != null) {
            try {
                if (consentContainer.isVisible()
                        && isCookieConsentText(safeText(consentContainer).toLowerCase(Locale.ROOT))) {
                    return "Zoho cookie consent remained visible after clicking its accept action.";
                }
            } catch (Exception ignored) {
                // The banner may have been removed from the DOM after acceptance.
            }
        }

        System.out.println("Zoho cookie consent accepted.");
        return null;
    }

    static boolean isCookieAcceptLabel(String label) {
        String normalized = normalize(label);
        return normalized.equals("accept")
                || normalized.equals("accept all")
                || normalized.equals("accept cookies")
                || normalized.equals("accept all cookies")
                || normalized.equals("allow")
                || normalized.equals("allow all")
                || normalized.equals("allow all cookies")
                || normalized.equals("i agree")
                || normalized.equals("i accept")
                || normalized.equals("agree");
    }

    private boolean isCookieConsentText(String text) {
        return text.contains("cookie")
                || text.contains("consent")
                || text.contains("privacy preference");
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private Locator findInterestedElement(
            Page page
    ) {

        /*
         * Check buttons, links and role=button elements.
         */
        String selector =
                "button:visible, " +
                        "a:visible, " +
                        "[role='button']:visible, " +
                        "input[type='button']:visible, " +
                        "input[type='submit']:visible";

        Locator elements =
                page.locator(selector);

        for (int i = 0; i < elements.count(); i++) {

            Locator element =
                    elements.nth(i);

            String text =
                    normalize(
                            safeText(element)
                    );

            String aria =
                    normalize(
                            safeAttribute(
                                    element,
                                    "aria-label"
                            )
                    );

            String title =
                    normalize(
                            safeAttribute(
                                    element,
                                    "title"
                            )
                    );

            if (isInterestedText(text)
                    || isInterestedText(aria)
                    || isInterestedText(title)) {

                return element;
            }
        }

        /*
         * Some Zoho controls may render the text inside a child span.
         * Search visible elements containing the phrase as a fallback.
         */
        Locator textCandidates =
                page.locator(
                        "button:visible *, " +
                                "a:visible *, " +
                                "[role='button']:visible *"
                );

        for (int i = 0; i < textCandidates.count(); i++) {

            Locator child =
                    textCandidates.nth(i);

            String text =
                    normalize(
                            safeText(child)
                    );

            if (isInterestedText(text)) {

                Locator clickable =
                        child.locator(
                                "xpath=ancestor::*[" +
                                        "self::button or " +
                                        "self::a or " +
                                        "@role='button'" +
                                        "][1]"
                        );

                if (clickable.count() > 0) {

                    return clickable.first();
                }
            }
        }

        return null;
    }

    private boolean isInterestedText(
            String text
    ) {

        if (text == null || text.isBlank()) {
            return false;
        }

        String normalized =
                normalize(text);

        for (String candidate : ENTRY_TEXTS) {

            if (normalized.equals(
                    normalize(candidate)
            )) {
                return true;
            }
        }

        return false;
    }

    private boolean looksLikeApplicationPage(
            Page page
    ) {

        try {

            /*
             * A visible file input is a strong application-form signal.
             */
            if (page.locator(
                    "input[type='file']:visible"
            ).count() > 0) {

                return true;
            }

            /*
             * Common application controls.
             */
            if (page.locator(
                    "input:visible, textarea:visible, select:visible"
            ).count() >= 3) {

                return true;
            }

            String body =
                    safeBody(page)
                            .toLowerCase(Locale.ROOT);

            int signals = 0;

            if (body.contains("apply")) {
                signals++;
            }

            if (body.contains("resume")
                    || body.contains("cv")) {
                signals++;
            }

            if (body.contains("email")) {
                signals++;
            }

            if (body.contains("phone")
                    || body.contains("mobile")) {
                signals++;
            }

            if (body.contains("experience")
                    || body.contains("education")) {
                signals++;
            }

            return signals >= 2;

        } catch (Exception e) {

            return false;
        }
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

        if (first == null || second == null) {
            return true;
        }

        return first.equals(second);
    }

    private String makeAbsoluteUrl(
            String base,
            String href
    ) {

        try {

            return URI
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
                        .toLowerCase(Locale.ROOT);

        return text.contains("sign in to continue")
                || text.contains("login to continue")
                || text.contains("log in to continue");
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
                            .toLowerCase(Locale.ROOT);

            return text.contains("verify you are human")
                    || text.contains("i'm not a robot")
                    || text.contains("human verification")
                    || text.contains("captcha");

        } catch (Exception e) {

            return false;
        }
    }

    private String safeBody(
            Page page
    ) {

        try {

            return page.locator("body").innerText();

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
                    locator.getAttribute(attribute);

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

    private static String normalize(
            String value
    ) {

        return value == null
                ? ""
                : value
                .replace('\u2019', '\'')
                .replace('\u2018', '\'')
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }
}
