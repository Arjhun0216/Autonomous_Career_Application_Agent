package com.jobscraper.job_platform.resume;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CoverLetterServiceTest {
    @TempDir Path tempDir;

    @Test
    void generatesAndSavesCoverLetterPdfAlongsideResume() throws Exception {
        GeminiClient gemini = mock(GeminiClient.class);
        when(gemini.generate(org.mockito.ArgumentMatchers.anyString())).thenReturn(
                "Dear Hiring Team,\n\nI am interested in this role. My verified project experience is relevant.\n\nSincerely,\nArjun");
        Path resume = tempDir.resolve("resume.pdf");
        Files.createFile(resume);

        CoverLetterService.CoverLetterResult result = new CoverLetterService(gemini)
                .generate("Candidate details", "Software Engineer role", "Arjun M", resume);

        assertEquals("cover-letter.pdf", Path.of(result.pdfPath()).getFileName().toString());
        assertTrue(Files.isRegularFile(Path.of(result.pdfPath())));
        try (PDDocument pdf = Loader.loadPDF(Path.of(result.pdfPath()).toFile())) {
            assertEquals(1, pdf.getNumberOfPages());
        }
    }
}
