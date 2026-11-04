package com.jobscraper.job_platform.application.automation;

import com.jobscraper.job_platform.application.JobApplication;
import com.jobscraper.job_platform.application.JobApplicationRepository;
import com.jobscraper.job_platform.user.User;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class ApplicationAgentService {

    private final ApplicationBrowserService browserService;

    private final ApplicationNavigator applicationNavigator;

    private final CompanyWorkflowRegistry companyWorkflowRegistry;

    private final ZohoApplicationAdapter zohoApplicationAdapter;

    private final GreenhouseApplicationAdapter greenhouseApplicationAdapter;

    private final IBMApplicationAdapter ibmApplicationAdapter;

    private final NvidiaApplicationFlow nvidiaApplicationFlow;

    private final EmployerLoginHandoffAdapter employerLoginHandoffAdapter;

    private final GeminiApplicationAgent geminiApplicationAgent;

    private final ZohoFormAutomationService zohoFormAutomationService;

    private final KekaFormAutomationService kekaFormAutomationService;

    private final JobApplicationRepository applicationRepository;

    public ApplicationResult inspectApplicationPage(
            User user,
            JobApplication application
    ) {

        if (user == null) {

            return fail(
                    application,
                    "User information is missing."
            );
        }

        if (application == null) {

            return ApplicationResult.failed(
                    "Application information is missing."
            );
        }

        if (application.getJobUrl() == null
                || application.getJobUrl().isBlank()) {

            return fail(
                    application,
                    "Job URL is missing."
            );
        }

        if (application.getResumePath() == null
                || application.getResumePath().isBlank()) {

            return fail(
                    application,
                    "Generated resume is missing."
            );
        }

        if (isGreenhouseUrl(application.getJobUrl().toLowerCase(java.util.Locale.ROOT))) {
            String message = "Greenhouse automation is skipped because this application is protected by reCAPTCHA. Please apply manually on the employer's career page.";
            application.setStatus("MANUAL_ACTION_REQUIRED");
            application.setErrorMessage(message);
            applicationRepository.save(application);
            return ApplicationResult.blocked(message);
        }

        ApplicationBrowser browser =
                null;
        boolean keepBrowserOpenForManualLogin = false;

        try {

            application.setStatus(
                    "APPLYING"
            );

            application.setErrorMessage(
                    null
            );

            applicationRepository.save(
                    application
            );

            /*
             * ============================================================
             * CREATE BROWSER
             * ============================================================
             */

            browser =
                    browserService.createBrowser();

            BrowserContext context =
                    browser.context();

            Page page =
                    context.newPage();

            page.setDefaultTimeout(
                    30000
            );

            /*
             * ============================================================
             * START
             * ============================================================
             */

            logStart(
                    application
            );

            /*
             * ============================================================
             * 1. OPEN GOOGLE JOB PAGE
             * ============================================================
             */

            System.out.println(
                    "Opening job page..."
            );

            page.navigate(
                    application.getJobUrl(),
                    new Page.NavigateOptions()
                            .setTimeout(30000)
                            .setWaitUntil(
                                    com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED
                            )
            );

            String openedJobUrl = safeUrl(page).toLowerCase(java.util.Locale.ROOT);
            page.waitForTimeout(initialPageWaitMillis(application, openedJobUrl));

            System.out.println(
                    "Job page loaded."
            );

            System.out.println(
                    "URL: "
                            + safeUrl(page)
            );

            System.out.println(
                    "Title: "
                            + safeTitle(page)
            );

            /*
             * ============================================================
             * 2. SELECT COMPANY WORKFLOW
             * ============================================================
             *
             * Company-specific workflows provide Gemini with the
             * website/application rules. They do NOT hard-code browser
             * actions. Gemini observes the live page and decides what
             * Playwright should execute.
             */

            CompanyWorkflow workflow =
                    companyWorkflowRegistry.resolve(
                            page,
                            application
                    );

            Page applicationPage;

            if (workflow != null) {

                System.out.println(
                        "======================================"
                );
                System.out.println(
                        "COMPANY WORKFLOW SELECTED: "
                                + workflow.getCompany()
                );
                System.out.println("Company workflow adapter selected: " + workflow.getCompany());
                System.out.println(
                        "======================================"
                );

                applicationPage = page;

                if (workflow instanceof NvidiaWorkflow
                        || isNvidiaApplication(application, openedJobUrl)) {
                    ApplicationNavigationResult nvidiaResult =
                            nvidiaApplicationFlow.openApplyAndCheckWorkdaySignIn(
                                    page,
                                    application.getJobTitle()
                            );
                    if (nvidiaResult.authenticationRequired()) {
                        application.setStatus("AUTHENTICATION_REQUIRED");
                        application.setErrorMessage(nvidiaResult.message());
                        applicationRepository.save(application);
                        keepBrowserOpenForManualLogin = true;
                        return ApplicationResult.authenticationRequired(
                                nvidiaResult.message(),
                                application.getJobUrl()
                        );
                    }
                    return fail(application, nvidiaResult.message());
                }

                if (isManualLoginEmployer(application)) {
                    ApplicationNavigationResult loginHandoff =
                            employerLoginHandoffAdapter.openApplyAndDetectLogin(page, application);
                    if (loginHandoff.authenticationRequired()) {
                        application.setStatus("AUTHENTICATION_REQUIRED");
                        application.setErrorMessage(loginHandoff.message());
                        applicationRepository.save(application);
                        keepBrowserOpenForManualLogin = true;
                        return ApplicationResult.authenticationRequired(
                                loginHandoff.message(),
                                application.getJobUrl()
                        );
                    }
                    return fail(application, loginHandoff.message());
                }

                if (workflow instanceof IBMWorkflow) {
                    ApplicationNavigationResult ibmResult = ibmApplicationAdapter.clickApplyAndCheckLogin(page);
                    if (ibmResult.authenticationRequired()) {
                        application.setStatus("AUTHENTICATION_REQUIRED");
                        application.setErrorMessage(ibmResult.message());
                        applicationRepository.save(application);
                        return ApplicationResult.failed(ibmResult.message());
                    }
                    if (!ibmResult.success()) return fail(application, ibmResult.message());
                    applicationPage = ibmResult.page();
                }

                // Zoho uses its visible "I'm Interested" entry point for this
                // workflow. Do not invoke the Easy Apply / candidate sign-in
                // control; open the actual application form before starting
                // the resume-first, database-backed form workflow.
                if (workflow instanceof ZohoWorkflow
                        && zohoApplicationAdapter.supports(page)) {

                    ApplicationNavigationResult zohoNavigation =
                            zohoApplicationAdapter.openApplicationPage(page);

                    if (zohoNavigation.authenticationRequired()) {
                        application.setStatus("AUTHENTICATION_REQUIRED");
                        application.setErrorMessage(zohoNavigation.message());
                        applicationRepository.save(application);
                        return ApplicationResult.failed(zohoNavigation.message());
                    }

                    if (zohoNavigation.blocked()) {
                        return fail(application, zohoNavigation.message());
                    }

                    if (!zohoNavigation.success() || zohoNavigation.page() == null) {
                        return fail(application, zohoNavigation.message());
                    }

                    applicationPage = zohoNavigation.page();
                }

                if (workflow instanceof GreenhouseWorkflow
                        && greenhouseApplicationAdapter.supports(page)) {
                    ApplicationNavigationResult greenhouseNavigation = greenhouseApplicationAdapter.openApplicationForm(page);
                    if (!greenhouseNavigation.success() || greenhouseNavigation.page() == null) {
                        return fail(application, greenhouseNavigation.message());
                    }
                    applicationPage = greenhouseNavigation.page();
                }

            } else {

                /*
                 * Existing generic/Google navigation remains unchanged.
                 */

                System.out.println(
                        "Opening application page..."
                );

                ApplicationNavigationResult navigation =
                        applicationNavigator
                                .openApplicationPage(
                                        page
                                );

                if (navigation.blocked()) {
                    return fail(application, navigation.message());
                }

                if (navigation.authenticationRequired()) {
                    application.setStatus("AUTHENTICATION_REQUIRED");
                    application.setErrorMessage(navigation.message());
                    applicationRepository.save(application);
                    return ApplicationResult.failed(navigation.message());
                }

                if (!navigation.success()) {
                    return fail(application, navigation.message());
                }

                applicationPage = navigation.page();
            }

            if (applicationPage == null) {
                return fail(
                        application,
                        "Application page is unavailable."
                );
            }

            /*
             * ============================================================
             * 3. APPLICATION PAGE
             * ============================================================
             */

            applicationPage.bringToFront();

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

            /*
             * ============================================================
             * 4. HAND OVER TO GOOGLE FORM AGENT
             * ============================================================
             *
             * The agent is responsible for:
             *
             * - text
             * - textarea
             * - native select
             * - custom dropdown
             * - autocomplete
             * - radio
             * - checkbox
             * - file upload
             * - next/continue buttons
             * - dynamically rendered fields
             * - page changes
             * - final submit protection
             */

            GeminiApplicationAgent.Result result;
            if (workflow instanceof ZohoWorkflow) {
                result = zohoFormAutomationService.apply(
                        applicationPage,
                        user,
                        application.getResumePath(),
                        application.getApplicationId()
                );
            } else if (workflow instanceof KekaWorkflow) {
                result = kekaFormAutomationService.apply(
                        applicationPage,
                        user,
                        application.getResumePath(),
                        application.getApplicationId()
                );
            } else {
                result = geminiApplicationAgent.run(
                        applicationPage,
                        user,
                        application.getResumePath(),
                        workflow,
                        application.getApplicationId(),
                        application.getCoverLetterPath()
                );
            }

            /*
             * ============================================================
             * 5. SUCCESS
             * ============================================================
             */

            if (result.success()) {

                application.setStatus(
                        "APPLIED"
                );

                application.setAppliedAt(
                        LocalDateTime.now()
                );

                application.setErrorMessage(
                        null
                );

                applicationRepository.save(
                        application
                );

                System.out.println(
                        "======================================"
                );

                System.out.println(
                        "APPLICATION SUBMITTED SUCCESSFULLY"
                );

                System.out.println(
                        "Application ID: "
                                + application.getApplicationId()
                );

                System.out.println(
                        "Submitted URL: "
                                + result.submittedUrl()
                );

                System.out.println(
                        "======================================"
                );

                return ApplicationResult.success(
                        result.message(),
                        result.submittedUrl()
                );
            }

            /*
             * ============================================================
             * 6. USER INPUT REQUIRED
             * ============================================================
             */

            if (result.userInputRequired()) {

                application.setStatus(
                        "USER_INPUT_REQUIRED"
                );

                application.setErrorMessage(
                        result.message()
                );

                applicationRepository.save(
                        application
                );

                System.out.println(
                        "======================================"
                );

                System.out.println(
                        "USER INPUT REQUIRED"
                );

                System.out.println(
                        result.message()
                );

                System.out.println(
                        "======================================"
                );

                return ApplicationResult.failed(
                        result.message()
                );
            }

            /*
             * ============================================================
             * 7. AUTHENTICATION
             * ============================================================
             */

            if (result.authenticationRequired()) {

                application.setStatus(
                        "AUTHENTICATION_REQUIRED"
                );

                application.setErrorMessage(
                        result.message()
                );

                applicationRepository.save(
                        application
                );

                return ApplicationResult.failed(
                        result.message()
                );
            }

            /*
             * ============================================================
             * 8. BLOCKED / CAPTCHA
             * ============================================================
             */

            if (result.blocked()) {

                application.setStatus(
                        "MANUAL_ACTION_REQUIRED"
                );

                application.setErrorMessage(
                        result.message()
                );

                applicationRepository.save(
                        application
                );

                if (workflow instanceof KekaWorkflow) {
                    keepBrowserOpenForManualLogin = true;
                    ApplicationBrowser browserToClose = browser;
                    CompletableFuture.delayedExecutor(3, TimeUnit.MINUTES).execute(() -> {
                        try {
                            browserService.closeBrowser(browserToClose);
                        } catch (Exception ignored) {
                        }
                    });
                    return ApplicationResult.blocked(result.message());
                }

                keepBrowserOpenForManualLogin = true;

                return ApplicationResult.blocked(
                        "Manual verification is required in the open Chromium window. "
                                + result.message()
                );
            }

            /*
             * ============================================================
             * 9. FAILURE
             * ============================================================
             */

            return fail(
                    application,
                    result.message()
            );

        } catch (Exception e) {

            e.printStackTrace();

            return fail(
                    application,
                    "Application automation failed: "
                            + (
                            e.getMessage() == null
                                    ? "Unknown error"
                                    : e.getMessage()
                    )
            );

        } finally {

            if (browser != null && !keepBrowserOpenForManualLogin) {

                try {

                    browserService.closeBrowser(
                            browser
                    );

                } catch (Exception ignored) {
                }
            }
        }
    }

    private ApplicationResult fail(
            JobApplication application,
            String message
    ) {

        if (application != null) {

            application.setStatus(
                    "FAILED"
            );

            application.setErrorMessage(
                    message
            );

            applicationRepository.save(
                    application
            );
        }

        return ApplicationResult.failed(
                message
        );
    }

    private void logStart(
            JobApplication application
    ) {

        System.out.println(
                "======================================"
        );

        System.out.println(
                "APPLICATION AGENT STARTED"
        );

        System.out.println(
                "Application ID: "
                        + application.getApplicationId()
        );

        System.out.println(
                "Company: "
                        + application.getCompanyName()
        );

        System.out.println(
                "Job Title: "
                        + application.getJobTitle()
        );

        System.out.println(
                "Job URL: "
                        + application.getJobUrl()
        );

        System.out.println(
                "Resume: "
                        + application.getResumePath()
        );

        System.out.println(
                "======================================"
        );
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

    private boolean isGreenhouseUrl(String url) {
        return url.contains("greenhouse.io/") || url.contains("greenhouse.com/");
    }

    private long initialPageWaitMillis(JobApplication application, String url) {
        if (isGreenhouseUrl(url)) return 5000L;
        if (isIbmUrl(url)) return 7000L;
        if (isManualLoginEmployer(application)) {
            String company = application.getCompanyName().toLowerCase(java.util.Locale.ROOT);
            return company.contains("boeing") || company.contains("nvidia") ? 3000L : 4000L;
        }
        return 3000L;
    }

    private boolean isManualLoginEmployer(JobApplication application) {
        if (application == null || application.getCompanyName() == null) return false;
        String company = application.getCompanyName().toLowerCase(java.util.Locale.ROOT);
        return company.contains("hcl") || company.contains("accenture")
                || company.contains("amazon") || company.contains("boeing")
                || company.contains("nvidia");
    }

    private boolean isIbmUrl(String url) {
        return url.contains("ibm.com/careers") || url.contains("careers.ibm.com")
                || url.contains("ibmglobal.avature.net") || url.contains("ibm.wd1.myworkdayjobs.com")
                || url.contains("ibm.wd5.myworkdayjobs.com");
    }

    private boolean isNvidiaUrl(String url) {
        return url.contains("nvidia.wd5.myworkdayjobs.com")
                || url.contains("myworkdayjobs.com") && url.contains("nvidia")
                || url.contains("nvidia.com");
    }

    private boolean isNvidiaApplication(JobApplication application, String url) {
        String company = application == null || application.getCompanyName() == null
                ? ""
                : application.getCompanyName().toLowerCase(java.util.Locale.ROOT);
        return isNvidiaUrl(url) || company.contains("nvidia");
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
