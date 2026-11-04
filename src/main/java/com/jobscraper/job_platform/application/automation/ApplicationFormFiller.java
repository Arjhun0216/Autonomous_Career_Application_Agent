package com.jobscraper.job_platform.application.automation;

import com.jobscraper.job_platform.user.User;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
public class ApplicationFormFiller {

    private final ApplicationFieldMapper fieldMapper;

    public void fill(
            Page page,
            ApplicationForm form,
            User user,
            String resumePath) {

        String firstName =
                extractFirstName(user.getName());

        String lastName =
                extractLastName(user.getName());

        for (FormField field : form.fields()) {

            ApplicationFieldMapper.FieldType type =
                    fieldMapper.map(field);

            String value =
                    getValue(
                            type,
                            user,
                            firstName,
                            lastName
                    );

            System.out.println(
                    "Mapped field: "
                            + type
                            + " | "
                            + field.label()
                            + " | "
                            + value
            );

            /*
             * Resume is handled separately.
             */
            if (type ==
                    ApplicationFieldMapper.FieldType.RESUME) {

                uploadResume(
                        page,
                        field,
                        resumePath
                );

                continue;
            }

            /*
             * Unknown fields are NOT touched.
             */
            if (type ==
                    ApplicationFieldMapper.FieldType.UNKNOWN) {

                System.out.println(
                        "Skipping unknown field: "
                                + field.name()
                );

                continue;
            }

            /*
             * Don't fill an empty value.
             */
            if (value == null ||
                    value.isBlank()) {

                System.out.println(
                        "No value available for: "
                                + type
                );

                continue;
            }

            fillField(
                    page,
                    field,
                    value
            );
        }
    }

    private void fillField(
            Page page,
            FormField field,
            String value) {

        Locator locator =
                findLocator(
                        page,
                        field
                );

        if (locator == null) {

            System.out.println(
                    "Could not locate field: "
                            + field.label()
            );

            return;
        }

        try {

            locator.fill(value);

            System.out.println(
                    "Filled: "
                            + field.label()
                            + " = "
                            + value
            );

        } catch (Exception e) {

            System.out.println(
                    "Unable to fill field "
                            + field.label()
                            + ": "
                            + e.getMessage()
            );
        }
    }

    private Locator findLocator(
            Page page,
            FormField field) {

        /*
         * Prefer ID.
         */
        if (field.id() != null &&
                !field.id().isBlank()) {

            Locator locator =
                    page.locator(
                            "#" + escapeCss(field.id())
                    );

            if (locator.count() > 0) {
                return locator.first();
            }
        }

        /*
         * Then name.
         */
        if (field.name() != null &&
                !field.name().isBlank()) {

            Locator locator =
                    page.locator(
                            "[name=\""
                                    + field.name()
                                    + "\"]"
                    );

            if (locator.count() > 0) {
                return locator.first();
            }
        }

        return null;
    }

    private void uploadResume(
            Page page,
            FormField field,
            String resumePath) {

        if (resumePath == null ||
                resumePath.isBlank()) {

            System.out.println(
                    "Resume path is empty."
            );

            return;
        }

        Path path =
                Path.of(resumePath);

        if (!Files.exists(path)) {

            System.out.println(
                    "Resume does not exist: "
                            + resumePath
            );

            return;
        }

        try {

            Locator locator =
                    findLocator(
                            page,
                            field
                    );

            if (locator == null) {

                System.out.println(
                        "Resume input not found."
                );

                return;
            }

            locator.setInputFiles(
                    path
            );

            System.out.println(
                    "Resume uploaded: "
                            + resumePath
            );

        } catch (Exception e) {

            System.out.println(
                    "Resume upload failed: "
                            + e.getMessage()
            );
        }
    }

    private String getValue(
            ApplicationFieldMapper.FieldType type,
            User user,
            String firstName,
            String lastName) {

        return switch (type) {

            case FIRST_NAME ->
                    firstName;

            case LAST_NAME ->
                    lastName;

            case EMAIL ->
                    user.getEmail();

            case PHONE ->
                    user.getPhone();

            case LINKEDIN ->
                    user.getLinkedinUrl();

            case GITHUB ->
                    user.getGithubUrl();

            case PORTFOLIO ->
                    user.getPortfolioUrl();

            case LOCATION ->
                    user.getLocation();

            case PROFESSIONAL_TITLE ->
                    user.getProfessionalTitle();

            default ->
                    null;
        };
    }

    private String extractFirstName(
            String name) {

        if (name == null ||
                name.isBlank()) {

            return "";
        }

        String[] parts =
                name.trim().split("\\s+");

        return parts[0];
    }

    private String extractLastName(
            String name) {

        if (name == null ||
                name.isBlank()) {

            return "";
        }

        String[] parts =
                name.trim().split("\\s+");

        if (parts.length <= 1) {
            return "";
        }

        return parts[parts.length - 1];
    }

    private String escapeCss(
            String value) {

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}