package com.jobscraper.job_platform.application.automation;

import com.jobscraper.job_platform.resume.GeminiClient;
import com.jobscraper.job_platform.user.User;
import com.jobscraper.job_platform.user.UserRepository;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApplicationProfileServiceTest {

    @Test
    void savesZohoAnswersIntoUsersOtherDetailsEvenWhenFieldIdUsesHyphens() throws Exception {
        UserRepository users = (UserRepository) Proxy.newProxyInstance(
                UserRepository.class.getClassLoader(), new Class<?>[]{UserRepository.class},
                (proxy, method, args) -> method.getName().equals("save") ? args[0] : null);
        ApplicationProfileService service = new ApplicationProfileService(
                users, new GeminiClient(new ObjectMapper()), new ObjectMapper());
        User user = new User();
        user.setOtherDetails("""
                {"application_profile":{"pending_questions":{"rec-form_123":{"fieldId":"rec-form_123","question":"Preferred Location","answer":"","status":"PENDING"},"frame0_preferred_location":{"fieldId":"frame0_preferred_location","question":"Preferred Location","answer":"","status":"PENDING"}}}}
                """);

        service.saveAnswer(user, "frame0_preferred_location", "Preferred Location", "Coimbatore");

        JsonNode details = new ObjectMapper().readTree(user.getOtherDetails());
        JsonNode pending = details.path("application_profile").path("pending_questions").path("rec-form_123");
        assertEquals("Coimbatore", pending.path("answer").asText());
        assertEquals("ANSWERED", pending.path("status").asText());
        JsonNode fallbackPending = details.path("application_profile").path("pending_questions").path("frame0_preferred_location");
        assertEquals("Coimbatore", fallbackPending.path("answer").asText());
        assertEquals("ANSWERED", fallbackPending.path("status").asText());
        assertEquals("Coimbatore", details.path("application_profile").path("answers").path("frame0_preferred_location").asText());
        assertEquals("Coimbatore", details.path("application_profile").path("answers").path("preferred_location").asText());
    }
}
