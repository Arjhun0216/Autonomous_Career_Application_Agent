package com.jobscraper.job_platform.recommendation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class EmbeddingClient {

    private final HttpClient httpClient;
    private final String embeddingServiceUrl;

    public EmbeddingClient(
            @Value("${recommendation.embedding-service.url}")
            String embeddingServiceUrl) {

        this.embeddingServiceUrl = embeddingServiceUrl;
        this.httpClient = HttpClient.newHttpClient();
    }

    public List<Double> generateEmbedding(String text) {

        System.out.println();
        System.out.println("########################################");
        System.out.println("### EMBEDDING CLIENT - GET METHOD ###");
        System.out.println("########################################");

        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }

        try {

            String encodedText = URLEncoder
                    .encode(text, StandardCharsets.UTF_8);

            String url =
                    embeddingServiceUrl
                            + "/embed?text="
                            + encodedText;

            System.out.println("Embedding URL:");
            System.out.println(url);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            System.out.println(
                    "Embedding HTTP Status: "
                            + response.statusCode()
            );

            System.out.println(
                    "Embedding Response Length: "
                            + response.body().length()
            );

            if (response.statusCode() != 200) {

                System.err.println(
                        "Embedding service error:"
                );

                System.err.println(response.body());

                return Collections.emptyList();
            }

            String responseBody = response.body();

            int start = responseBody.indexOf("[");
            int end = responseBody.lastIndexOf("]");

            if (start == -1 || end == -1) {

                System.err.println(
                        "Embedding array not found."
                );

                return Collections.emptyList();
            }

            String array =
                    responseBody.substring(
                            start + 1,
                            end
                    );

            String[] values = array.split(",");

            List<Double> embedding =
                    new ArrayList<>(values.length);

            for (String value : values) {

                embedding.add(
                        Double.parseDouble(
                                value.trim()
                        )
                );
            }

            System.out.println(
                    "USER EMBEDDING DIMENSION: "
                            + embedding.size()
            );

            return embedding;

        } catch (Exception e) {

            System.err.println(
                    "Embedding service connection failed:"
            );

            e.printStackTrace();

            return Collections.emptyList();
        }
    }
}