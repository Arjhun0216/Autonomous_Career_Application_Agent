package com.jobscraper.job_platform.recommendation;

import com.jobscraper.job_platform.job.Job;
import com.jobscraper.job_platform.job.JobRepository;
import com.jobscraper.job_platform.user.User;
import com.jobscraper.job_platform.user.UserRepository;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecommendationServiceTest {

    @Test
    void keepsValidJobVisibleWhenItsMatchScoreIsBelowTheOldCutoff() {
        User user = new User();
        user.setEmail("candidate@example.test");
        user.setProfessionalTitle("Software Developer");
        user.setSkills("[]");
        user.setUserExperience(0);

        Job job = new Job();
        job.setJobId("job-1");
        job.setTitle("Junior Software Developer");
        job.setSkills("");
        job.setExperienceLevel("5");

        UserRepository users = (UserRepository) Proxy.newProxyInstance(
                UserRepository.class.getClassLoader(), new Class<?>[]{UserRepository.class},
                (proxy, method, args) -> method.getName().equals("findByEmail")
                        ? Optional.of(user) : defaultValue(method.getReturnType()));
        JobRepository jobs = (JobRepository) Proxy.newProxyInstance(
                JobRepository.class.getClassLoader(), new Class<?>[]{JobRepository.class},
                (proxy, method, args) -> method.getName().equals("findAll")
                        ? List.of(job) : defaultValue(method.getReturnType()));
        EmbeddingClient embeddings = new EmbeddingClient("http://unused") {
            @Override
            public List<Double> generateEmbedding(String text) { return List.of(); }
        };

        RecommendationService service = new RecommendationService(users, jobs, embeddings, new ObjectMapper());

        List<RecommendationResponse> results = service.getRecommendations("candidate@example.test");

        assertEquals(1, results.size());
        assertEquals("job-1", results.get(0).getJobId());
        assertEquals(0.0, results.get(0).getMatchScore());
    }

    private Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        return 0;
    }
}
