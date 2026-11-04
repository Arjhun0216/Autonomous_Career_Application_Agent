package com.jobscraper.job_platform.application.automation;

import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class ApplicationFieldMapper {

    public FieldType map(FormField field) {

        String text = (
                field.name() + " " +
                field.id() + " " +
                field.placeholder() + " " +
                field.label()
        ).toLowerCase(Locale.ROOT);

        /*
         * Email
         */
        if (containsAny(
                text,
                "email",
                "e-mail"
        )) {
            return FieldType.EMAIL;
        }

        /*
         * Phone
         */
        if (containsAny(
                text,
                "phone",
                "mobile",
                "telephone",
                "contact number"
        )) {
            return FieldType.PHONE;
        }

        /*
         * First name
         */
        if (containsAny(
                text,
                "first name",
                "firstname",
                "given name",
                "fname"
        )) {
            return FieldType.FIRST_NAME;
        }

        /*
         * Last name
         */
        if (containsAny(
                text,
                "last name",
                "lastname",
                "surname",
                "family name",
                "lname"
        )) {
            return FieldType.LAST_NAME;
        }

        /*
         * LinkedIn
         */
        if (containsAny(
                text,
                "linkedin"
        )) {
            return FieldType.LINKEDIN;
        }

        /*
         * GitHub
         */
        if (containsAny(
                text,
                "github"
        )) {
            return FieldType.GITHUB;
        }

        /*
         * Portfolio
         */
        if (containsAny(
                text,
                "portfolio",
                "personal website",
                "website"
        )) {
            return FieldType.PORTFOLIO;
        }

        /*
         * Location
         */
        if (containsAny(
                text,
                "location",
                "city",
                "current city",
                "address"
        )) {
            return FieldType.LOCATION;
        }

        /*
         * Professional title
         */
        if (containsAny(
                text,
                "job title",
                "professional title",
                "current title",
                "designation"
        )) {
            return FieldType.PROFESSIONAL_TITLE;
        }

        /*
         * Resume upload
         */
        if (containsAny(
                text,
                "resume",
                "cv",
                "curriculum vitae"
        )
                && field.type() != null
                && field.type().equalsIgnoreCase("file")) {

            return FieldType.RESUME;
        }

        return FieldType.UNKNOWN;
    }

    private boolean containsAny(
            String text,
            String... values) {

        for (String value : values) {

            if (text.contains(
                    value.toLowerCase(Locale.ROOT)
            )) {
                return true;
            }
        }

        return false;
    }

    public enum FieldType {

        FIRST_NAME,
        LAST_NAME,
        EMAIL,
        PHONE,
        LINKEDIN,
        GITHUB,
        PORTFOLIO,
        LOCATION,
        PROFESSIONAL_TITLE,
        RESUME,
        UNKNOWN
    }
}