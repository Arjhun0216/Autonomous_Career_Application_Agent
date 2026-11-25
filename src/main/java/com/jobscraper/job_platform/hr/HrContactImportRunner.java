package com.jobscraper.job_platform.hr;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class HrContactImportRunner implements ApplicationRunner {

    private final HrContactRepository repository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        try (var reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("db/hr_contacts.csv").getInputStream(), StandardCharsets.UTF_8))) {
            reader.readLine(); // CSV header
            String line;
            Map<String, HrContact> imported = new LinkedHashMap<>();
            while ((line = reader.readLine()) != null) {
                List<String> fields = parseCsvLine(line);
                if (fields.size() != 5 || fields.get(2).isBlank() || fields.get(4).isBlank()) continue;
                String email = fields.get(2).trim();
                String company = fields.get(4).trim();
                String key = email.toLowerCase(Locale.ROOT) + "|" + company.toLowerCase(Locale.ROOT);
                HrContact contact = imported.computeIfAbsent(key, ignored ->
                        repository.findByMailIdIgnoreCaseAndCompanyNameIgnoreCase(email, company)
                                .orElseGet(HrContact::new));
                contact.setMailId(email);
                contact.setHrName(fields.get(1).trim());
                contact.setDesignation(fields.get(3).trim());
                contact.setCompanyName(company);
            }
            repository.saveAll(imported.values());
            log.info("Loaded {} HR contacts from the bundled contact list", imported.size());
        }
    }

    private List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    value.append('"');
                    i++;
                } else quoted = !quoted;
            } else if (ch == ',' && !quoted) {
                fields.add(value.toString());
                value.setLength(0);
            } else value.append(ch);
        }
        fields.add(value.toString());
        return fields;
    }
}
