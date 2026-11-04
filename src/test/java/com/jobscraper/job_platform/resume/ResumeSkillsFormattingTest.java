package com.jobscraper.job_platform.resume;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ResumeSkillsFormattingTest {
    @Test
    void replacesGeneratedSkillsWithProfileSkillsGroupedLikeTheReferenceAndHighlightsJobMatches() throws Exception {
        ResumeService service = new ResumeService(null, null, null);
        Method formatter = ResumeService.class.getDeclaredMethod(
                "addConfirmedSkillsSection", String.class, List.class, List.class);
        formatter.setAccessible(true);

        String latex = "\\begin{document}\n\\section*{SKILLS}\nold generated list\\par\n"
                + "\\section*{EXPERIENCE}\nWork\\par\n\\end{document}";
        String result = (String) formatter.invoke(service, latex,
                List.of("Python", ".NET", "CI/CD", "Pandas", "REST APIs", "AWS", "python"),
                List.of(".NET", "CI/CD"));

        assertTrue(result.contains("\\begin{tabular}"));
        assertTrue(result.contains("Programming Language"));
        assertTrue(result.contains("Machine Learning"));
        assertTrue(result.contains("Core Concepts"));
        assertTrue(result.contains("Cloud \\& DevOps"));
        assertTrue(result.contains("\\textbf{.NET}"));
        assertTrue(result.contains("\\textbf{CI/CD}"));
        assertTrue(result.contains("Python"));
        assertTrue(result.contains("Pandas"));
        assertTrue(result.contains("\\section*{EXPERIENCE}"));
        assertTrue(result.contains("\\end{document}"));
        assertTrue(!result.contains("old generated list"));
    }
}
