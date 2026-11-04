package com.jobscraper.job_platform.resume;

import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CoverLetterService {
    private final GeminiClient geminiClient;

    public CoverLetterResult generate(String candidateData, String jobData, String candidateName, Path resumePath) {
        String prompt = """
                Write a tailored cover letter for this candidate and role.
                Use only factual candidate details in CANDIDATE DATA. Use job information to explain relevant fit.
                Do not invent experience, achievements, skills, metrics, or contact details.
                Keep it concise (250-350 words), professional, and first person. Return plain text only.

                CANDIDATE DATA:
                %s

                JOB DATA:
                %s
                """.formatted(safe(candidateData), safe(jobData));
        String body = geminiClient.generate(prompt).trim();
        if (body.isBlank()) throw new IllegalStateException("The generated cover letter is empty.");
        try {
            Path output = resumePath.toAbsolutePath().normalize().getParent();
            if (output == null) throw new IOException("Resume output directory is unavailable.");
            Files.createDirectories(output);
            Path pdf = output.resolve("cover-letter.pdf");
            writePdf(body, candidateName, pdf);
            return new CoverLetterResult(body, pdf.toString());
        } catch (IOException e) {
            throw new IllegalStateException("Unable to save the generated cover letter PDF.", e);
        }
    }

    private void writePdf(String body, String name, Path path) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.TIMES_BOLD);
            List<String> lines = wrap(ascii(body), 92);
            int lineIndex = 0;
            boolean firstPage = true;
            while (lineIndex < lines.size() || firstPage) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    float y = 735;
                stream.beginText();
                    if (firstPage) {
                        stream.setFont(bold, 15);
                        stream.newLineAtOffset(60, y);
                        stream.showText(ascii(safe(name)));
                        stream.newLineAtOffset(0, -34);
                        y -= 34;
                    } else {
                        stream.newLineAtOffset(60, y);
                    }
                stream.setFont(font, 11);
                stream.setLeading(15);
                    while (lineIndex < lines.size() && y >= 72) {
                        String line = lines.get(lineIndex++);
                    stream.showText(line);
                    stream.newLine();
                    y -= 15;
                }
                stream.endText();
                }
                firstPage = false;
            }
            document.save(path.toFile());
        }
    }

    private List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\\R")) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.trim().split("\\s+")) {
                if (word.isBlank()) continue;
                if (line.length() > 0 && line.length() + word.length() + 1 > width) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                if (line.length() > 0) line.append(' ');
                line.append(word);
            }
            lines.add(line.toString());
            lines.add("");
        }
        return lines;
    }

    private String ascii(String value) {
        return safe(value).replaceAll("[^\\x20-\\x7E]", " ");
    }
    private String safe(String value) { return value == null ? "" : value; }

    public record CoverLetterResult(String body, String pdfPath) { }
}
