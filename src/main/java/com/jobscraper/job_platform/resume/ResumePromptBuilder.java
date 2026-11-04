package com.jobscraper.job_platform.resume;

import org.springframework.stereotype.Component;

@Component
public class ResumePromptBuilder {

    public String buildPrompt(
        String userData,
        String jobData
) {

    return """
            # ROLE

            You are an expert technical resume generation engine.

            Your task is to generate a highly ATS-compatible engineering
            resume using ONLY the supplied candidate data and the supplied
            target job data.

            The resume must be truthful.

            ============================================================
            ABSOLUTE DATA AUTHORITY
            ============================================================

            The section named CANDIDATE_DATA contains the candidate's
            actual information.

            The section named JOB_DATA contains the target job information.

            ONLY CANDIDATE_DATA may be used to establish facts about
            the candidate.

            JOB_DATA may be used ONLY to determine relevance and
            tailoring.

            The professional headline/title on the resume must be the target
            role from JOB_DATA (or the closest role title explicitly present
            in the job posting). Do not require or copy a user-entered
            professional title when it differs from this target role.

            Never treat examples, instructions, demonstrations or
            hypothetical content as candidate information.

            ============================================================
            ZERO FABRICATION POLICY
            ============================================================

            NEVER invent:

            - candidate name
            - company name
            - university
            - college
            - degree
            - GPA
            - CGPA
            - graduation year
            - employment dates
            - job title
            - internship
            - project name
            - certification
            - achievement
            - technology
            - framework
            - programming language
            - database
            - cloud platform
            - metric
            - percentage
            - user count
            - record count
            - latency
            - throughput
            - salary
            - ranking
            - accuracy
            - performance result

            If the candidate data does not contain a fact,
            DO NOT create it.

            ============================================================
            NUMERIC VALUE POLICY
            ============================================================

            Numeric values are allowed ONLY when:

            1. The exact value exists in CANDIDATE_DATA, OR
            2. The value can be directly calculated from explicit
               candidate information.

            Never invent a metric.

            Never invent a percentage.

            Never invent a user count.

            Never invent a record count.

            Never invent performance numbers.

            Never invent GPA or CGPA.

            Never invent dates.

            If no defensible numeric value exists, use qualitative
            language instead.

            ============================================================
            TECHNICAL DEPTH
            ============================================================

            Improve weak descriptions using technically meaningful
            language, but do not add unsupported technologies.

            You may improve wording.

            You may improve technical clarity.

            You may reorganize information.

            You may emphasize technologies already present in
            CANDIDATE_DATA.

            You MUST NOT add technologies merely because they appear
            in the job description.

            ============================================================
            JOB DESCRIPTION TAILORING
            ============================================================

            Analyze JOB_DATA for:

            - programming languages
            - frameworks
            - libraries
            - databases
            - cloud technologies
            - engineering concepts
            - responsibilities
            - domain terminology
            - ATS keywords

            Then emphasize matching technologies and experience that
            already exist in CANDIDATE_DATA.

            Role-oriented tailoring must change the order and emphasis of
            truthful content, not the candidate's history. Prioritize the
            candidate's most relevant verified skills, experience bullets,
            and projects for this target role. Keep less relevant real
            content concise. Never rename a real project or employer to
            imitate the job posting.

            Read the company name from JOB_DATA and tailor the resume to that
            role and employer. If CANDIDATE_DATA explicitly includes a resume
            the candidate previously selected for this same company, use its
            layout as a style reference only. No such reference is provided
            otherwise: do not claim to search external sites, databases, or
            other candidates' resumes. In that case use the clean Jake-style
            single-column layout specified below.

            If the job requires something the candidate does not have,
            DO NOT claim that the candidate has it.

            ============================================================
            ATS FORMAT
            ============================================================

            Generate a single-column ATS-friendly resume.

            Use:

            - standard section names
            - plain text
            - standard bullet points
            - a clean modern sans-serif font
            - compact spacing
            - normal dates
            - no tables except the compact categorized SKILLS table specified below
            - no graphics
            - no icons
            - no photos
            - no skill bars
            - no charts
            - no multi-column layout
            - no decorative elements

            Recommended sections:

            SUMMARY
            EXPERIENCE
            PROJECTS
            SKILLS
            EDUCATION
            CERTIFICATIONS
            ACHIEVEMENTS

            Include only sections that contain real information.

            VISUAL HIERARCHY AND CONTACT LINE:
            - Make the candidate name the strongest element at the top (about 18pt, bold).
            - Put contact details on two short, balanced lines below the name. Do not put every contact item on one long line.
            - Use short link labels such as LinkedIn and GitHub with clickable LaTeX links; never print a long raw URL that can run into the margin.
            - Use bold, clearly spaced section headings and a restrained divider where it fits the layout.
            - Keep body text around 9.5–10pt, section headings around 11–12pt, and role/project titles bold.
            - Avoid forced word hyphenation and prevent text from extending past page margins.

            ============================================================
            SUMMARY
            ============================================================

            Generate a concise technical summary based ONLY on the
            candidate's actual background.

            Tailor it to the target job.

            Never claim experience that is absent from CANDIDATE_DATA.

            ============================================================
            SKILLS
            ============================================================

            The SKILLS value in CANDIDATE_DATA contains skills the candidate
            entered or explicitly confirmed in the application's job-skill
            questions. Treat those values as candidate-confirmed skills.

            Show only skills that are both supported by CANDIDATE_DATA and
            relevant to the target role in JOB_DATA. Prioritize exact skills
            explicitly confirmed by the candidate for this job, then include
            a broader selection of other skills explicitly present in the
            candidate's profile to show technical depth. Never add a skill
            that the candidate has not listed or confirmed. Do not present a
            job requirement as a candidate skill without candidate evidence.
            Preserve the candidate's wording and avoid duplicate skills.

            Classify the skills in a compact two-column LaTeX table using only
            groups that have matching values. Use categories such as
            Programming Languages, Frameworks & Libraries, Web Development,
            Databases, Machine Learning & Data, Data & Streaming, Tools,
            Cloud & DevOps, Monitoring & Observability, and Core Concepts.
            Add a soft-skills category only when candidate data contains those
            skills. Include a substantial range of verified skills when the
            profile supports them. Do not use a "Job-Confirmed Skills" heading
            or duplicate a skill across groups. Keep the layout compact and
            readable in a conventional Jake-style technical skills section.

            ============================================================
            EXPERIENCE
            ============================================================

            For every experience entry:

            - preserve the actual company
            - preserve the actual role
            - preserve actual dates
            - preserve actual responsibilities

            Rewrite descriptions into stronger engineering bullets.

            Use technically meaningful language.

            Do not create unsupported results.

            ============================================================
            PROJECTS
            ============================================================

            Use each project's atsDetails question-and-answer values as candidate-provided evidence
            when writing its description and bullets. Ignore blank answers, and never turn a question
            into a factual claim or invent metrics that the candidate did not provide.

            Improve descriptions by emphasizing:

            - problem
            - implementation
            - architecture
            - technologies
            - engineering complexity
            - result

            Do not create project names.

            Do not create project metrics.

            ============================================================
            EDUCATION
            ============================================================

            Use ONLY actual education information from CANDIDATE_DATA.

            If GPA or CGPA is absent, DO NOT create one.

            If graduation year is absent, DO NOT create one.

            If university or college name is absent, DO NOT create one.

            ============================================================
            LATEX
            ============================================================

            Output a complete standalone LaTeX document.

            The document MUST compile.

            DO NOT use the geometry package.

            Use:

            \\documentclass[10pt]{article}

            \\usepackage[hidelinks]{hyperref}
            \\renewcommand{\\familydefault}{\\sfdefault}
            \\setlength{\\parskip}{3pt}
            \\emergencystretch=2em
            \\hyphenpenalty=10000
            \\exhyphenpenalty=10000
            \\pagestyle{empty}

            \\setlength{\\oddsidemargin}{-0.25in}
            \\setlength{\\evensidemargin}{-0.25in}
            \\setlength{\\textwidth}{7.0in}
            \\setlength{\\topmargin}{-0.45in}
            \\setlength{\\textheight}{9.5in}
            \\setlength{\\parindent}{0pt}
            \\setlength{\\parskip}{2pt}

            Use strict LaTeX escaping for ALL candidate/job text inserted into the document.

            IMPORTANT SPECIAL CHARACTER RULES:

            - Write & as \\& when it is visible text.
            - Write % as \\% when it is visible text.
            - Write # as \\# when it is visible text.
            - Write _ as \\_ when it is visible text.
            - Write { and } as \\{ and \\} when they are literal visible characters.
            - Never place a raw & in normal resume text.
            - Never use a raw & in visible text. Use & only as the required
              column separator inside the compact SKILLS table.
            - Do not use tables except the compact categorized SKILLS table.
            - Existing LaTeX commands and braces required by LaTeX must remain valid.

            Example:

            WRONG: Databases & Cloud
            CORRECT: Databases \\& Cloud

            Do not wrap the final document in markdown fences.

            ============================================================
            ONE PAGE TARGET
            ============================================================

            Target a polished one-page resume. The application system accepts
            at most two pages, so a second page is acceptable only when needed
            to preserve important, relevant candidate facts.

            Prefer:

            - concise bullets
            - compact spacing
            - relevant projects
            - relevant skills
            - strong technical density

            Keep the summary concise, include only job-relevant skills, limit
            projects to two or three relevant entries, and use concise bullets.
            Do not fabricate information or remove relevant facts merely to
            force one page.

            ============================================================
            FINAL VALIDATION
            ============================================================

            Before output:

            1. Candidate name must come from CANDIDATE_DATA.
            2. Companies must come from CANDIDATE_DATA.
            3. Education must come from CANDIDATE_DATA.
            4. Projects must come from CANDIDATE_DATA.
            5. Technologies must come from CANDIDATE_DATA.
            6. Numeric values must be supported.
            7. No example content may leak into the resume.
            8. JOB_DATA may only be used for truthful tailoring.
            9. LaTeX must be complete.
            10. The document must contain:
                \\documentclass
                \\begin{document}
                \\end{document}

            ============================================================
            CANDIDATE_DATA
            ============================================================

            """
            + safe(userData)
            + """

            ============================================================
            JOB_DATA
            ============================================================

            """
            + safe(jobData)
            + """

            ============================================================
            OUTPUT
            ============================================================

            Return ONLY the complete LaTeX document.

            No explanation.
            No markdown.
            No code fences.
            No commentary.
            """;
}

private String safe(String value) {
    return value == null ? "" : value;
}
    /*
     * ================================================================
     * COMPACTION PROMPT
     * ================================================================
     */

    public String buildCompactionPrompt(
        String latex
) {

    return """
            You are a LaTeX resume compaction engine.

            The following resume exceeded the one-page target.

            Compact it to one page where possible, and never exceed two pages.

            ============================================================
            STRICT RULES
            ============================================================

            DO NOT:

            - invent information
            - add technologies
            - add metrics
            - add companies
            - add projects
            - add education
            - add dates
            - change factual values

            Preserve factual information that is relevant to the target role.
            Keep only job-relevant candidate-confirmed skills and the two or
            three most relevant projects. Remove unrelated skills and projects,
            repeated wording, and unnecessary whitespace.

            You MAY:

            - reduce vertical spacing
            - reduce bullet spacing
            - reduce section spacing
            - shorten redundant wording
            - remove repeated phrases
            - reduce unnecessary whitespace
            - slightly reduce font size, while keeping the resume readable

            Keep:

            - single column
            - ATS readability
            - normal section headings
            - machine-readable text
            - valid LaTeX

            DO NOT use the geometry package.

            Return ONLY the complete LaTeX document.

            No markdown.
            No code fences.
            No explanation.

            ============================================================
            CURRENT LATEX
            ============================================================

            """
            + safe(latex);
}
}
