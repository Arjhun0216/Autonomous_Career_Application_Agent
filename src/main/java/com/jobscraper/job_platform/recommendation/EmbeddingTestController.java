package com.jobscraper.job_platform.recommendation;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/test/embedding")
@RequiredArgsConstructor
public class EmbeddingTestController {

    private final EmbeddingClient embeddingClient;

    @GetMapping
    public Object testEmbedding(
            @RequestParam String text) {

        List<Double> embedding =
                embeddingClient.generateEmbedding(text);

        return new EmbeddingTestResponse(
                embedding.size(),
                embedding
        );
    }

    public record EmbeddingTestResponse(
            int dimension,
            List<Double> embedding
    ) {
    }
}