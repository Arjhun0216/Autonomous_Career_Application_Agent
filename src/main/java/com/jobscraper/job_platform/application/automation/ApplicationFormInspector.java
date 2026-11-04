package com.jobscraper.job_platform.application.automation;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ApplicationFormInspector {

    public ApplicationForm inspect(Page page) {

        List<FormField> fields =
                new ArrayList<>();

        Locator inputs =
                page.locator(
                        "input:visible, textarea:visible, select:visible"
                );

        int count =
                inputs.count();

        System.out.println(
                "Found "
                        + count
                        + " form controls."
        );

        for (int i = 0; i < count; i++) {

            Locator element =
                    inputs.nth(i);

            /*
             * Playwright evaluate() returns Object,
             * so explicitly convert it to String.
             */
            String tag =
                    String.valueOf(
                            element.evaluate(
                                    "el => el.tagName.toLowerCase()"
                            )
                    );

            String type =
                    safeAttribute(
                            element,
                            "type"
                    );

            String name =
                    safeAttribute(
                            element,
                            "name"
                    );

            String id =
                    safeAttribute(
                            element,
                            "id"
                    );

            String placeholder =
                    safeAttribute(
                            element,
                            "placeholder"
                    );

            String label =
                    findLabel(
                            page,
                            element,
                            id
                    );

            boolean required =
                    element.getAttribute(
                            "required"
                    ) != null;

            FormField field =
                    new FormField(
                            tag,
                            type,
                            name,
                            id,
                            placeholder,
                            label,
                            required
                    );

            fields.add(field);

            System.out.println(
                    "--------------------------------"
            );

            System.out.println(
                    "Tag        : " + tag
            );

            System.out.println(
                    "Type       : " + type
            );

            System.out.println(
                    "Name       : " + name
            );

            System.out.println(
                    "ID         : " + id
            );

            System.out.println(
                    "Placeholder: " + placeholder
            );

            System.out.println(
                    "Label      : " + label
            );

            System.out.println(
                    "Required   : " + required
            );
        }

        return new ApplicationForm(
                fields
        );
    }

    private String safeAttribute(
            Locator element,
            String attribute) {

        String value =
                element.getAttribute(
                        attribute
                );

        return value == null
                ? ""
                : value;
    }

    private String findLabel(
            Page page,
            Locator element,
            String id) {

        try {

            if (id != null &&
                    !id.isBlank()) {

                Locator label =
                        page.locator(
                                "label[for='" + id + "']"
                        );

                if (label.count() > 0) {

                    return label
                            .first()
                            .innerText()
                            .trim();
                }
            }

            return "";

        } catch (Exception e) {

            return "";
        }
    }
}