package com.jobscraper.job_platform.resume;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

@Component
public class LatexCompiler {

    public Path compile(
            String latexContent,
            Path outputDirectory,
            String baseFileName
    ) {

        try {

            Files.createDirectories(outputDirectory);

            Path texFile =
                    outputDirectory.resolve(
                            baseFileName + ".tex"
                    );

            Files.writeString(
                    texFile,
                    latexContent,
                    StandardCharsets.UTF_8
            );

            ProcessBuilder processBuilder =
                    new ProcessBuilder(
                            "pdflatex",
                            "-interaction=nonstopmode",
                            "-halt-on-error",
                            "-output-directory",
                            outputDirectory.toAbsolutePath().toString(),
                            texFile.toAbsolutePath().toString()
                    );

            processBuilder.redirectErrorStream(true);

            Process process =
                    processBuilder.start();

            String compilerOutput;

            try (BufferedReader reader =
                         new BufferedReader(
                                 new InputStreamReader(
                                         process.getInputStream(),
                                         StandardCharsets.UTF_8
                                 )
                         )) {

                compilerOutput =
                        reader.lines()
                                .collect(
                                        Collectors.joining(
                                                System.lineSeparator()
                                        )
                                );
            }

            int exitCode =
                    process.waitFor();

            Path pdfFile =
                    outputDirectory.resolve(
                            baseFileName + ".pdf"
                    );

            if (exitCode != 0 || !Files.exists(pdfFile)) {

                throw new RuntimeException(
                        "LaTeX compilation failed.\n\n"
                        + compilerOutput
                );
            }

            return pdfFile;

        } catch (IOException e) {

            throw new RuntimeException(
                    "Unable to execute pdflatex. "
                    + "Make sure MiKTeX/pdflatex is installed "
                    + "and available in PATH.",
                    e
            );

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            throw new RuntimeException(
                    "LaTeX compilation was interrupted.",
                    e
            );
        }
    }
}