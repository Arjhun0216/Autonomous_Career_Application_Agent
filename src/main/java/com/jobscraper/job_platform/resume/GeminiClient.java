package com.jobscraper.job_platform.resume;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.UnknownHostException;
import java.nio.channels.UnresolvedAddressException;
import java.time.Duration;

@Component
@RequiredArgsConstructor
public class GeminiClient {

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.url}")
    private String apiUrl;

    @Value("${gemini.model}")
    private String model;

    private final ObjectMapper objectMapper;

    private final HttpClient httpClient =
            HttpClient.newBuilder()
                    .connectTimeout(
                            Duration.ofSeconds(20)
                    )
                    .build();

    private static final int DEFAULT_MAX_OUTPUT_TOKENS = 3000;

    private static final int MAX_ATTEMPTS = 4;

    public String generate(String prompt) {

        return generate(
                prompt,
                DEFAULT_MAX_OUTPUT_TOKENS
        );
    }

    public String generate(
            String prompt,
            int maxOutputTokens
    ) {

        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException(
                    "Gemini prompt cannot be empty."
            );
        }

        if (maxOutputTokens <= 0) {
            throw new IllegalArgumentException(
                    "maxOutputTokens must be greater than zero."
            );
        }

        Exception lastException = null;

        for (int attempt = 1;
             attempt <= MAX_ATTEMPTS;
             attempt++) {

            try {

                return executeRequest(
                        prompt,
                        maxOutputTokens,
                        attempt
                );

            } catch (Exception e) {

                lastException = e;

                boolean retryable =
                        isRetryable(e);

                System.err.println(
                        "GEMINI ATTEMPT "
                                + attempt
                                + "/"
                                + MAX_ATTEMPTS
                                + " FAILED"
                );

                System.err.println(
                        "RETRYABLE: "
                                + retryable
                );

                System.err.println(
                        "ERROR: "
                                + describeFailure(e)
                );

                if (!retryable
                        || attempt == MAX_ATTEMPTS
                        || (isNameResolutionFailure(e)
                        && attempt >= 2)) {

                    break;
                }

                sleepBeforeRetry(
                        attempt,
                        isNameResolutionFailure(e)
                );
            }
        }

        throw new RuntimeException(
                "Failed to generate content using Gemini after "
                        + MAX_ATTEMPTS
                        + " attempts: "
                        + (
                        lastException == null
                                ? "Unknown error"
                                : describeFailure(lastException)
                ),
                lastException
        );
    }

    private String executeRequest(
            String prompt,
            int maxOutputTokens,
            int attempt
    ) throws Exception {

        long startTime =
                System.currentTimeMillis();

        GeminiRequest requestBody =
                new GeminiRequest(
                        new Content[]{
                                new Content(
                                        new Part[]{
                                                new Part(prompt)
                                        }
                                )
                        },
                        new GenerationConfig(
                                new ThinkingConfig("minimal"),
                                maxOutputTokens,
                                "text/plain"
                        )
                );

        String jsonBody =
                objectMapper.writeValueAsString(
                        requestBody
                );

        String endpoint =
                apiUrl
                        + "/"
                        + model
                        + ":generateContent";

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(
                                URI.create(endpoint)
                        )
                        .timeout(
                                Duration.ofMinutes(2)
                        )
                        .header(
                                "Content-Type",
                                "application/json"
                        )
                        .header(
                                "x-goog-api-key",
                                apiKey
                        )
                        .POST(
                                HttpRequest.BodyPublishers
                                        .ofString(
                                                jsonBody
                                        )
                        )
                        .build();

        System.out.println(
                "=========================================="
        );

        System.out.println(
                "GEMINI REQUEST"
        );

        System.out.println(
                "MODEL: " + model
        );

        System.out.println(
                "ATTEMPT: "
                        + attempt
                        + "/"
                        + MAX_ATTEMPTS
        );

        System.out.println(
                "THINKING: MINIMAL"
        );

        System.out.println(
                "MAX OUTPUT TOKENS: "
                        + maxOutputTokens
        );

        System.out.println(
                "=========================================="
        );

        HttpResponse<String> response =
                httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

        long elapsed =
                System.currentTimeMillis()
                        - startTime;

        int status =
                response.statusCode();

        System.out.println(
                "GEMINI STATUS: "
                        + status
        );

        System.out.println(
                "GENERATION TIME: "
                        + elapsed
                        + " ms"
        );

        if (status < 200 || status >= 300) {

            throw new GeminiHttpException(
                    status,
                    response.body()
            );
        }

        JsonNode root =
                objectMapper.readTree(
                        response.body()
                );

        JsonNode candidates =
                root.get("candidates");

        if (candidates == null
                || !candidates.isArray()
                || candidates.isEmpty()) {

            throw new RuntimeException(
                    "Gemini returned no candidates."
            );
        }

        JsonNode content =
                candidates
                        .get(0)
                        .get("content");

        if (content == null) {

            throw new RuntimeException(
                    "Gemini returned no content."
            );
        }

        JsonNode parts =
                content.get("parts");

        if (parts == null
                || !parts.isArray()) {

            throw new RuntimeException(
                    "Gemini returned no response parts."
            );
        }

        StringBuilder result =
                new StringBuilder();

        for (JsonNode part : parts) {

            JsonNode text =
                    part.get("text");

            if (text != null
                    && !text.isNull()) {

                result.append(
                        text.asText()
                );
            }
        }

        String generatedText =
                result.toString().trim();

        if (generatedText.isBlank()) {

            throw new RuntimeException(
                    "Gemini returned an empty response."
            );
        }

        System.out.println(
                "GEMINI RESPONSE RECEIVED"
        );

        System.out.println(
                "GENERATED CHARACTERS: "
                        + generatedText.length()
        );

        System.out.println(
                "=========================================="
        );

        return generatedText;
    }

    private boolean isRetryable(
            Exception exception
    ) {

        if (exception instanceof GeminiHttpException httpException) {

            int status =
                    httpException.statusCode();

            return status == 408
                    || status == 429
                    || status == 500
                    || status == 502
                    || status == 503
                    || status == 504;
        }

        /*
         * Network problems such as:
         *
         * EOFException
         * connection reset
         * connection closed
         * timeout
         *
         * are transient and should be retried.
         */
        if (exception instanceof IOException) {
            return true;
        }

        if (exception instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            return false;
        }

        Throwable cause =
                exception.getCause();

        while (cause != null) {

            if (cause instanceof IOException) {
                return true;
            }

            cause = cause.getCause();
        }

        return false;
    }

    private boolean isNameResolutionFailure(Throwable failure) {
        Throwable current = failure;

        while (current != null) {
            if (current instanceof UnknownHostException
                    || current instanceof UnresolvedAddressException) {
                return true;
            }
            current = current.getCause();
        }

        return false;
    }

    private String describeFailure(Throwable failure) {
        Throwable root = failure;

        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }

        String detail = root.getMessage();
        if (detail == null || detail.isBlank()) {
            detail = root.getClass().getSimpleName();
        }

        String host;
        try {
            host = URI.create(apiUrl).getHost();
        } catch (IllegalArgumentException ignored) {
            host = "unknown";
        }

        return "Gemini host " + host + ": " + detail;
    }

    private void sleepBeforeRetry(
            int attempt,
            boolean nameResolutionFailure
    ) {

        long baseDelay =
                nameResolutionFailure
                        ? 12_000L
                        : 1000L * (1L << (attempt - 1));

        long jitter =
                (long)
                        (Math.random() * 500);

        long delay =
                baseDelay + jitter;

        System.out.println(
                "GEMINI RETRYING IN "
                        + delay
                        + " ms..."
                        + (nameResolutionFailure
                        ? " (allowing Java DNS cache to expire)"
                        : "")
        );

        try {

            Thread.sleep(delay);

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            throw new RuntimeException(
                    "Gemini retry interrupted.",
                    e
            );
        }
    }

    private record GeminiRequest(
            Content[] contents,
            GenerationConfig generationConfig
    ) {
    }

    private record Content(
            Part[] parts
    ) {
    }

    private record Part(
            String text
    ) {
    }

    private record GenerationConfig(
            ThinkingConfig thinkingConfig,
            int maxOutputTokens,
            String responseMimeType
    ) {
    }

    private record ThinkingConfig(
            String thinkingLevel
    ) {
    }

    private static class GeminiHttpException
            extends RuntimeException {

        private final int statusCode;

        GeminiHttpException(
                int statusCode,
                String body
        ) {

            super(
                    "Gemini API request failed. HTTP "
                            + statusCode
                            + ": "
                            + body
            );

            this.statusCode =
                    statusCode;
        }

        int statusCode() {
            return statusCode;
        }
    }
}
