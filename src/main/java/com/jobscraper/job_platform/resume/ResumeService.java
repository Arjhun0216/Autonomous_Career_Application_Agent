package com.jobscraper.job_platform.resume;

import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ResumeService {

    private final GeminiClient geminiClient;
    private final ResumePromptBuilder resumePromptBuilder;
    private final LatexCompiler latexCompiler;

    public ResumeGenerationResult generateResume(
            String userData,
            String jobData,
            String userName,
            String jobId
    ) {
        return generateResume(userData, jobData, userName, jobId, null, null);
    }

    public ResumeGenerationResult generateResume(
            String userData,
            String jobData,
            String userName,
            String jobId,
            List<String> candidateSkills,
            List<String> roleConfirmedSkills
    ) {

        require(userData, "Candidate information is required");
        require(jobData, "Job information is required");
        require(userName, "User name is required");
        require(jobId, "Job ID is required");

        System.out.println("==========================================");
        System.out.println("STARTING RESUME GENERATION");
        System.out.println("USER: " + userName);
        System.out.println("JOB ID: " + jobId);
        System.out.println("==========================================");

        /*
         * ---------------------------------------------------------
         * 1. BUILD GEMINI PROMPT
         * ---------------------------------------------------------
         */

        String prompt =
                resumePromptBuilder.buildPrompt(
                        userData,
                        jobData
                );

        /*
         * ---------------------------------------------------------
         * 2. GENERATE LATEX
         * ---------------------------------------------------------
         */

        long start =
                System.currentTimeMillis();

        String latex =
                cleanLatexResponse(
                        geminiClient.generate(
                                prompt,
                                5000
                        )
                );

        System.out.println(
                "LLM LATEX GENERATION TIME: "
                        + (System.currentTimeMillis() - start)
                        + " ms"
        );

        /*
         * ---------------------------------------------------------
         * 3. VALIDATE GENERATED LATEX
         * ---------------------------------------------------------
         */

        validateLatex(latex);

        /*
         * ---------------------------------------------------------
         * 4. NORMALIZE LATEX
         * ---------------------------------------------------------
         *
         * IMPORTANT:
         *
         * DO NOT globally escape &, %, #, _, etc.
         *
         * The document is already complete LaTeX.
         * Global escaping would corrupt commands/comments.
         *
         * Example:
         *
         * % Section formatting
         *
         * must remain:
         *
         * % Section formatting
         *
         * and must NOT become:
         *
         * \% Section formatting
         *
         * ---------------------------------------------------------
         */

        latex =
                normalizeLatex(latex);

        latex = addConfirmedSkillsSection(latex, candidateSkills, roleConfirmedSkills, jobData);

        latex =
                addSkillsLineSpacing(latex);

        /*
         * Gemini sometimes returns valid-looking visible text containing
         * raw LaTeX special characters. The most common failure is:
         *
         *     Databases & Cloud
         *
         * which makes pdflatex treat & as an alignment character. Repair
         * special characters in the document body before compilation.
         */
        latex =
                sanitizeBodyLatexCharacters(latex);

        /*
         * ---------------------------------------------------------
         * 5. VALIDATE AGAIN AFTER NORMALIZATION
         * ---------------------------------------------------------
         */

        validateLatex(latex);

        /*
         * ---------------------------------------------------------
         * 6. OUTPUT FILE CONFIGURATION
         * ---------------------------------------------------------
         */

        Path outputDirectory =
                Path.of("generated-resumes");

        String baseFileName =
                sanitizeFileName(userName)
                        + "_"
                        + sanitizeFileName(jobId);

        /*
         * ---------------------------------------------------------
         * 7. COMPILE LATEX → PDF
         * ---------------------------------------------------------
         */

        Path pdf =
                latexCompiler.compile(
                        latex,
                        outputDirectory,
                        baseFileName
                );

        /*
         * ---------------------------------------------------------
         * 8. COUNT PDF PAGES
         * ---------------------------------------------------------
         */

        int pageCount = countPages(pdf);

        if (pageCount > 1) {
            System.out.println("Resume is " + pageCount + " pages; requesting a compact one-page version.");

            String compactedLatex = cleanLatexResponse(
                    geminiClient.generate(
                            resumePromptBuilder.buildCompactionPrompt(latex),
                            5000
                    )
            );
            validateLatex(compactedLatex);
            compactedLatex = normalizeLatex(compactedLatex);
            compactedLatex = addConfirmedSkillsSection(compactedLatex, candidateSkills, roleConfirmedSkills, jobData);
            compactedLatex = addSkillsLineSpacing(compactedLatex);
            compactedLatex = sanitizeBodyLatexCharacters(compactedLatex);
            validateLatex(compactedLatex);

            pdf = latexCompiler.compile(compactedLatex, outputDirectory, baseFileName);
            pageCount = countPages(pdf);
            latex = compactedLatex;
        }

        if (pageCount > 2) {
            throw new RuntimeException(
                    "Generated resume exceeds the allowed maximum of 2 pages. "
                            + "Actual pages: " + pageCount
            );
        }

        /*
         * ---------------------------------------------------------
         * 10. TEX FILE PATH
         * ---------------------------------------------------------
         */

        Path tex =
                outputDirectory.resolve(
                        baseFileName + ".tex"
                );

        /*
         * ---------------------------------------------------------
         * 11. SUCCESS LOG
         * ---------------------------------------------------------
         */

        System.out.println("==========================================");
        System.out.println("RESUME GENERATED SUCCESSFULLY");
        System.out.println("PAGES: " + pageCount);
        System.out.println(
                "TEX: "
                        + tex.toAbsolutePath()
        );
        System.out.println(
                "PDF: "
                        + pdf.toAbsolutePath()
        );
        System.out.println("==========================================");

        /*
         * ---------------------------------------------------------
         * 12. RETURN RESULT
         * ---------------------------------------------------------
         */

        return new ResumeGenerationResult(
                tex.toAbsolutePath().toString(),
                pdf.toAbsolutePath().toString(),
                latex
        );
    }

    /**
     * Count the number of pages in the generated PDF.
     */
    private int countPages(Path pdf) {

        try (
                PDDocument document =
                        Loader.loadPDF(pdf.toFile())
        ) {

            return document.getNumberOfPages();

        } catch (Exception e) {

            throw new RuntimeException(
                    "Unable to verify generated resume page count.",
                    e
            );
        }
    }

    /** Keeps only candidate-confirmed skills that match this role, so the section stays focused and compact. */
    private String addConfirmedSkillsSection(String latex, List<String> candidateSkills, List<String> roleConfirmedSkills, String jobData) {
        if (candidateSkills == null && roleConfirmedSkills == null) return latex;
        java.util.Map<String, String> candidate = new java.util.LinkedHashMap<>();
        if (candidateSkills != null) candidateSkills.stream().filter(v -> v != null && !v.isBlank() && !isSkillCategoryHeader(v))
                .forEach(skill -> candidate.putIfAbsent(normalizeSkill(skill), skill.trim()));
        java.util.Set<String> role = new java.util.LinkedHashSet<>();
        if (roleConfirmedSkills != null) roleConfirmedSkills.stream().filter(v -> v != null && !v.isBlank() && !isSkillCategoryHeader(v))
                .forEach(skill -> { role.add(skill.trim()); candidate.putIfAbsent(normalizeSkill(skill), skill.trim()); });
        String normalizedJob = normalizeSkill(jobData);
        List<String> relevantSkills = new java.util.ArrayList<>();
        java.util.Set<String> selectedSkills = new java.util.LinkedHashSet<>();

        // Preserve every confirmed job skill first.
        for (String skill : candidate.values()) {
            if (isJobConfirmed(skill, role) && selectedSkills.add(normalizeSkill(skill))) {
                relevantSkills.add(skill);
            }
        }

        // Add directly matching user skills, then include more user-confirmed
        // profile skills for a deeper, balanced technical skills section.
        for (String skill : candidate.values()) {
            String normalized = normalizeSkill(skill);
            if (relevantSkills.size() >= 14) break;
            if (normalized.length() >= 3 && normalizedJob.contains(normalized)
                    && selectedSkills.add(normalized)) {
                relevantSkills.add(skill);
            }
        }
        for (String skill : candidate.values()) {
            if (relevantSkills.size() >= 14) break;
            if (selectedSkills.add(normalizeSkill(skill))) relevantSkills.add(skill);
        }
        String categorizedSkills = categorizedSkillTable(relevantSkills, role);
        java.util.regex.Pattern section = java.util.regex.Pattern.compile("(?i)\\\\section\\*?\\{\\s*(?:technical\\s+)?skills\\s*\\}");
        java.util.regex.Matcher matcher = section.matcher(latex);
        if (matcher.find()) {
            int documentEnd = latex.lastIndexOf("\\end{document}");
            int contentEnd = documentEnd >= matcher.end() ? documentEnd : latex.length();
            java.util.regex.Matcher nextSection = java.util.regex.Pattern.compile("(?i)\\\\section\\*?\\{[^}]+}").matcher(latex);
            nextSection.region(matcher.end(), latex.length());
            if (nextSection.find() && nextSection.start() < contentEnd) contentEnd = nextSection.start();
            if (relevantSkills.isEmpty()) return latex.substring(0, matcher.start()) + latex.substring(contentEnd);
            return latex.substring(0, matcher.end()) + "\n" + categorizedSkills + "\n" + latex.substring(contentEnd);
        }
        if (relevantSkills.isEmpty()) return latex;
        int end = latex.lastIndexOf("\\end{document}");
        if (end < 0) return latex;
        String addition = "\\section*{SKILLS}\n" + categorizedSkills;
        return latex.substring(0, end) + addition + latex.substring(end);
    }

    private String categorizedSkillTable(List<String> skills, java.util.Set<String> jobSkills) {
        java.util.Map<String, List<String>> grouped = new java.util.LinkedHashMap<>();
        for (String skill : skills) grouped.computeIfAbsent(skillCategory(skill), ignored -> new java.util.ArrayList<>()).add(skill);
        StringBuilder table = new StringBuilder("{\\renewcommand{\\arraystretch}{1.12}\n\\begin{tabular}{@{}p{0.25\\linewidth}p{0.72\\linewidth}@{}}\n");
        for (String category : List.of("Programming Languages", "Frameworks & Libraries", "Web Development", "Databases", "Machine Learning & Data", "Data & Streaming", "Tools", "Cloud & DevOps", "Monitoring & Observability", "Core Concepts", "Support & Communication", "Other Skills")) {
            List<String> values = grouped.get(category);
            if (values == null || values.isEmpty()) continue;
            values.sort((left, right) -> Boolean.compare(isJobConfirmed(right, jobSkills), isJobConfirmed(left, jobSkills)));
            String skillsText = values.stream().map(skill -> isJobConfirmed(skill, jobSkills)
                    ? "\\textbf{" + escapeLatexText(skill) + "}" : escapeLatexText(skill)).collect(Collectors.joining(", "));
            table.append("\\textbf{").append(escapeLatexText(category)).append("} & ").append(skillsText).append(" \\\\\n");
        }
        return table.append("\\end{tabular}}\n").toString();
    }

    private boolean isJobConfirmed(String skill, java.util.Set<String> jobSkills) {
        return jobSkills.stream().anyMatch(jobSkill -> sameSkill(skill, jobSkill));
    }

    private String skillCategory(String skill) {
        String value = normalizeSkill(skill);
        if (List.of("java", "javascript", "typescript", "python", "c", "cpp", "csharp", "go", "golang", "ruby", "php", "scala", "kotlin", "rust", "r", "bash", "shell", "powershell").contains(value)) return "Programming Languages";
        if (List.of("react", "reactjs", "bootstrap", "node", "nodejs", "express", "expressjs", "flask", "angular", "vue", "asp", "aspnet", "net", "dotnet", "mvc", "spring", "springboot", "hibernate", "django", "fastapi", "nextjs").contains(value)) return "Frameworks & Libraries";
        if (List.of("html", "css", "webapi", "userinterfaces", "webapplication", "webapplicationdevelopment", "frontendarchitecture", "frontend", "backend", "fullstack").contains(value)) return "Web Development";
        if (List.of("sql", "mysql", "postgresql", "postgres", "mongodb", "sqlserver", "cosmosdb", "redis", "oracle", "couchdb", "database", "databases", "databaseoperations", "databasesystems").contains(value)) return "Databases";
        if (List.of("machinelearning", "ml", "rag", "pandas", "numpy", "matplotlib", "seaborn", "scikitlearn", "tensorflow", "pytorch", "neuralnetworks", "dataanalysis", "featureengineering", "hyperparametertuning", "modelbuilding", "datacleaning", "computervision", "artificialintelligence").contains(value)) return "Machine Learning & Data";
        if (List.of("spark", "apachespark", "kafka", "apachekafka", "hadoop", "airflow", "pyspark", "etl", "bigdata", "datapipelines", "streaming").contains(value)) return "Data & Streaming";
        if (List.of("git", "github", "gitlab", "postman", "maven", "gradle", "linux", "jira", "phpmyadmin", "vscode", "jenkins").contains(value)) return "Tools";
        if (List.of("aws", "docker", "kubernetes", "k8s", "cicd", "devops", "terraform", "azure", "gcp", "cloud", "ansible", "openshift", "argocd", "helm").contains(value)) return "Cloud & DevOps";
        if (List.of("instana", "newrelic", "grafana", "prometheus", "opentelemetry", "datadog", "splunk", "elasticsearch", "observability", "monitoring").contains(value)) return "Monitoring & Observability";
        if (List.of("oop", "oops", "objectorientedprogramming", "datastructures", "algorithms", "restapi", "restapis", "api", "apis", "designpatterns", "microservicesarchitecture", "testing", "unittesting", "networking", "problemsolving", "applicationprocessing", "scalableapplications", "applicationarchitecture", "systemdesign", "distributedsystems").contains(value)) return "Core Concepts";
        if (List.of("communication", "technicalcommunication", "customercommunication", "customersupport", "voicebasedsupport", "troubleshooting").contains(value)) return "Support & Communication";
        return "Other Skills";
    }

    private boolean isSkillCategoryHeader(String skill) {
        return List.of("programminglanguages", "programminglanguage", "languages", "webdevelopment", "database", "databases", "tools", "coreconcepts", "frameworks", "frameworksandlibraries", "clouddevops", "machinelearningdata", "dataandstreaming", "monitoringandobservability", "supportandcommunication", "otherskills")
                .contains(normalizeSkill(skill));
    }

    private boolean sameSkill(String left, String right) {
        String a = normalizeSkill(left);
        String b = normalizeSkill(right);
        return a.equals(b) || (List.of("oop", "oops").contains(a) && List.of("oop", "oops").contains(b));
    }

    private String normalizeSkill(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
    }

    private String escapeLatexText(String value) {
        return value.replace("\\", "\\textbackslash{}")
                .replace("&", "\\&").replace("%", "\\%").replace("$", "\\$")
                .replace("#", "\\#").replace("_", "\\_")
                .replace("{", "\\{").replace("}", "\\}")
                .replace("~", "\\textasciitilde{}").replace("^", "\\textasciicircum{}");
    }

    /**
     * Clean the raw response returned by Gemini.
     *
     * Gemini may return:
     *
     * RESUME_LATEX:
     *
     * ```latex
     * \documentclass...
     * ...
     * \end{document}
     * ```
     *
     * This method extracts only the actual LaTeX document.
     */
    private String cleanLatexResponse(String response) {

        if (response == null || response.isBlank()) {

            throw new IllegalArgumentException(
                    "LLM returned empty LaTeX response"
            );
        }

        String latex =
                response.trim();

        /*
         * Remove opening Markdown code fence.
         */

        latex =
                latex.replaceFirst(
                        "(?is)^\\s*```(?:latex|tex)?\\s*",
                        ""
                );

        /*
         * Remove closing Markdown code fence.
         */

        latex =
                latex.replaceFirst(
                        "(?is)\\s*```\\s*$",
                        ""
                );

        latex =
                latex.trim();

        /*
         * If Gemini returned a wrapper such as:
         *
         * RESUME_LATEX:
         *
         * ```latex
         * \documentclass...
         *
         * start exactly from \documentclass.
         */

        int documentStart =
                latex.indexOf(
                        "\\documentclass"
                );

        if (documentStart >= 0) {

            latex =
                    latex.substring(
                            documentStart
                    );
        }

        /*
         * Keep only the complete LaTeX document.
         *
         * Anything after \end{document}
         * is discarded.
         */

        int documentEnd =
                latex.lastIndexOf(
                        "\\end{document}"
                );

        if (documentEnd >= 0) {

            latex =
                    latex.substring(
                            0,
                            documentEnd
                                    + "\\end{document}".length()
                    );
        }

        latex =
                latex.trim();

        /*
         * Final validation.
         */

        if (!latex.contains("\\documentclass")) {

            throw new IllegalArgumentException(
                    "LLM response does not contain a valid LaTeX document"
            );
        }

        if (!latex.contains("\\begin{document}")) {

            throw new IllegalArgumentException(
                    "Generated LaTeX is missing \\begin{document}"
            );
        }

        if (!latex.contains("\\end{document}")) {

            throw new IllegalArgumentException(
                    "Generated LaTeX is missing \\end{document}"
            );
        }

        return latex;
    }

    /**
     * Validate the minimum required LaTeX document structure.
     */
    private void validateLatex(String latex) {

        if (latex == null || latex.isBlank()) {

            throw new RuntimeException(
                    "Generated LaTeX is empty."
            );
        }

        if (!latex.contains("\\documentclass")) {

            throw new RuntimeException(
                    "Generated LaTeX is missing documentclass."
            );
        }

        if (!latex.contains("\\begin{document}")) {

            throw new RuntimeException(
                    "Generated LaTeX is missing begin{document}."
            );
        }

        if (!latex.contains("\\end{document}")) {

            throw new RuntimeException(
                    "Generated LaTeX is missing end{document}."
            );
        }

        /*
         * Basic structural ordering check.
         */

        int documentClass =
                latex.indexOf(
                        "\\documentclass"
                );

        int beginDocument =
                latex.indexOf(
                        "\\begin{document}"
                );

        int endDocument =
                latex.lastIndexOf(
                        "\\end{document}"
                );

        if (documentClass > beginDocument) {

            throw new RuntimeException(
                    "Invalid LaTeX structure: "
                            + "documentclass appears after begin{document}."
            );
        }

        if (beginDocument > endDocument) {

            throw new RuntimeException(
                    "Invalid LaTeX structure: "
                            + "begin{document} appears after end{document}."
            );
        }
    }

    /**
     * Normalize generated LaTeX.
     *
     * Removes geometry package usage and adds a compact
     * standard page layout when the model has not already
     * specified one.
     */
    private String normalizeLatex(String latex) {

        /*
         * ---------------------------------------------------------
         * Remove geometry package
         * ---------------------------------------------------------
         */

        String geometryPackage =
                "\\\\usepackage\\s*(?:\\[[^\\]]*\\])?\\s*\\{geometry\\}\\s*";

        latex =
                latex.replaceAll(
                        geometryPackage,
                        ""
                );

        /*
         * ---------------------------------------------------------
         * Remove \geometry{...} commands
         * ---------------------------------------------------------
         */

        String geometryCommand =
                "\\\\geometry\\s*\\{[^}]*\\}\\s*";

        latex =
                latex.replaceAll(
                        geometryCommand,
                        ""
                );

        /*
         * ---------------------------------------------------------
         * Add compact page layout if margins are not specified.
         * ---------------------------------------------------------
         */

        if (!latex.contains("\\oddsidemargin")) {

            int documentClassPosition =
                    latex.indexOf(
                            "\\documentclass"
                    );

            if (documentClassPosition >= 0) {

                int lineEnd =
                        latex.indexOf(
                                '\n',
                                documentClassPosition
                        );

                if (lineEnd >= 0) {

                    String margins =
                            "\n"
                                    + "% Compact one-page layout\n"
                                    + "\\setlength{\\oddsidemargin}{-0.25in}\n"
                                    + "\\setlength{\\evensidemargin}{-0.25in}\n"
                                    + "\\setlength{\\textwidth}{7.0in}\n"
                                    + "\\setlength{\\topmargin}{-0.45in}\n"
                                    + "\\setlength{\\textheight}{9.5in}\n"
                                    + "\\setlength{\\parindent}{0pt}\n"
                                    + "\\setlength{\\parskip}{2pt}\n\n";

                    latex =
                            latex.substring(
                                    0,
                                    lineEnd + 1
                            )
                                    + margins
                                    + latex.substring(
                                    lineEnd + 1
                            );
                }
            }
        }

        return applyDefaultResumeStyles(latex);
    }

    /** Applies a consistent, readable style even when the model omits formatting commands. */
    private String applyDefaultResumeStyles(String latex) {
        int documentStart = latex.indexOf("\\begin{document}");
        if (documentStart < 0) return latex;

        String preamble = latex.substring(0, documentStart);
        String body = latex.substring(documentStart);
        StringBuilder additions = new StringBuilder("\n% AI Job Navigator resume typography\n")
                .append("\\renewcommand{\\familydefault}{\\sfdefault}\n")
                .append("\\emergencystretch=2em\n")
                .append("\\hyphenpenalty=10000\n")
                .append("\\exhyphenpenalty=10000\n")
                .append("\\pagestyle{empty}\n");
        if (!preamble.contains("\\titleformat") && !preamble.contains("\\renewcommand{\\section}")) {
            additions.append("\\makeatletter\n")
                    .append("\\renewcommand\\section{\\@startsection{section}{1}{\\z@}{1.5ex plus .4ex minus .2ex}{.6ex}{\\normalfont\\large\\bfseries\\sffamily}}\n")
                    .append("\\makeatother\n");
        }

        return preamble + additions + body;
    }

    /** Adds breathing room between skill categories without changing other sections. */
    private String addSkillsLineSpacing(String latex) {
        java.util.regex.Pattern sectionPattern = java.util.regex.Pattern.compile(
                "(?i)\\\\section\\*?\\s*\\{\\s*skills\\s*\\}"
        );
        java.util.regex.Matcher matcher = sectionPattern.matcher(latex);
        if (!matcher.find()) return latex;

        int skillsStart = matcher.end();
        java.util.regex.Matcher nextSection = java.util.regex.Pattern
                .compile("\\\\section\\*?\\s*\\{")
                .matcher(latex);
        int skillsEnd = latex.length();
        if (nextSection.find(skillsStart)) skillsEnd = nextSection.start();

        String skills = latex.substring(skillsStart, skillsEnd);
        String itemize = "\\begin{itemize}";
        if (skills.contains(itemize)) {
            skills = skills.replaceFirst(
                    java.util.regex.Pattern.quote(itemize),
                    java.util.regex.Matcher.quoteReplacement(itemize + "\n    \\setlength{\\itemsep}{3pt}")
            );
        } else {
            skills = skills.replace("\\\\", "\\\\[3pt]");
        }
        return latex.substring(0, skillsStart) + skills + latex.substring(skillsEnd);
    }

    /**
     * Repair common LaTeX special characters in visible document text.
     *
     * This intentionally operates only on the document body so that
     * LaTeX commands in the preamble are never modified.
     *
     * The most important repair is an unescaped ampersand (&), which
     * otherwise causes pdflatex to interpret normal text as an alignment
     * tab. Existing escaped characters such as \& are left unchanged.
     */
    private String sanitizeBodyLatexCharacters(String latex) {

        String begin = "\\begin{document}";
        String end = "\\end{document}";

        int bodyStart = latex.indexOf(begin);
        int bodyEnd = latex.lastIndexOf(end);

        if (bodyStart < 0 || bodyEnd <= bodyStart) {
            return latex;
        }

        int contentStart = bodyStart + begin.length();
        String preamble = latex.substring(0, contentStart);
        String body = latex.substring(contentStart, bodyEnd);
        // Models occasionally emit this pseudo-command when they mean a list item.
        body = body.replace("\\removeItem", "\\item");
        String documentEnd = latex.substring(bodyEnd);

        StringBuilder repaired = new StringBuilder(body.length() + 64);

        int alignmentEnvironmentDepth = 0;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);

            if (body.startsWith("\\begin{tabular", i)
                    || body.startsWith("\\begin{tabularx", i)
                    || body.startsWith("\\begin{array}", i)
                    || body.startsWith("\\begin{align", i)
                    || body.startsWith("\\begin{aligned}", i)
                    || body.startsWith("\\begin{cases}", i)) {
                alignmentEnvironmentDepth++;
            } else if (alignmentEnvironmentDepth > 0
                    && (body.startsWith("\\end{tabular", i)
                    || body.startsWith("\\end{tabularx}", i)
                    || body.startsWith("\\end{array}", i)
                    || body.startsWith("\\end{align", i)
                    || body.startsWith("\\end{aligned}", i)
                    || body.startsWith("\\end{cases}", i))) {
                alignmentEnvironmentDepth--;
            }

            if (c == '&') {
                int slashCount = 0;
                for (int j = i - 1; j >= 0 && body.charAt(j) == '\\'; j--) {
                    slashCount++;
                }

                if (alignmentEnvironmentDepth > 0) {
                    repaired.append(c);
                } else if (slashCount % 2 == 0) {
                    repaired.append("\\&");
                } else {
                    repaired.append(c);
                }
                continue;
            }

            if (c == '#') {
                int slashCount = 0;
                for (int j = i - 1; j >= 0 && body.charAt(j) == '\\'; j--) {
                    slashCount++;
                }

                if (slashCount % 2 == 0) {
                    repaired.append("\\#");
                } else {
                    repaired.append(c);
                }
                continue;
            }

            if (c == '_') {
                int slashCount = 0;
                for (int j = i - 1; j >= 0 && body.charAt(j) == '\\'; j--) {
                    slashCount++;
                }

                if (slashCount % 2 == 0) {
                    repaired.append("\\_");
                } else {
                    repaired.append(c);
                }
                continue;
            }

            if (c == '%') {
                int slashCount = 0;
                for (int j = i - 1; j >= 0 && body.charAt(j) == '\\'; j--) {
                    slashCount++;
                }

                if (slashCount % 2 != 0) {
                    repaired.append(c);
                    continue;
                }

                /*
                 * A line beginning with % is a legitimate LaTeX comment.
                 * Preserve it. For ordinary visible text such as 100%,
                 * escape the percent sign.
                 */
                int lineStart = body.lastIndexOf('\n', i - 1) + 1;
                String beforePercent = body.substring(lineStart, i).trim();

                if (beforePercent.isEmpty()) {
                    repaired.append(c);
                } else if (beforePercent.matches(".*\\\\[A-Za-z@]+.*")
                        && !beforePercent.matches(".*\\\\textbf\\s*\\{[^}]*$")) {
                    /* Keep command comments conservative; the percent is
                       more likely to be an intentional inline comment. */
                    repaired.append(c);
                } else {
                    repaired.append("\\%");
                }
                continue;
            }

            repaired.append(c);
        }

        return preamble + repaired + documentEnd;
    }

    /**
     * Validate required input.
     */
    private void require(
            String value,
            String message
    ) {

        if (value == null || value.isBlank()) {

            throw new IllegalArgumentException(
                    message
            );
        }
    }

    /**
     * Create a safe filename from user/job identifiers.
     */
    private String sanitizeFileName(String value) {

        String sanitized =
                value.trim()
                        .replaceAll(
                                "[\\\\/:*?\"<>|]",
                                "_"
                        )
                        .replaceAll(
                                "\\s+",
                                "_"
                        );

        if (sanitized.isBlank()) {

            throw new RuntimeException(
                    "Invalid file name value"
            );
        }

        return sanitized;
    }

    public record ResumeGenerationResult(
            String texPath,
            String pdfPath,
            String latex
    ) {
    }
}
