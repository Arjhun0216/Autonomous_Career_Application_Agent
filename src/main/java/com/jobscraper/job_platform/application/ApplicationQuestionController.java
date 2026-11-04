package com.jobscraper.job_platform.application;

import com.jobscraper.job_platform.user.User;
import com.jobscraper.job_platform.user.UserRepository;
import com.jobscraper.job_platform.application.automation.ApplicationProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class ApplicationQuestionController {

    private final ApplicationQuestionRepository questionRepository;
    private final JobApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final ApplicationProfileService profileService;

    @GetMapping("/{applicationId}/questions")
    public ResponseEntity<?> getQuestions(@PathVariable Long applicationId, Authentication authentication) {
        try {
            User user = currentUser(authentication);
            JobApplication application = ownApplication(user.getUserId(), applicationId);
            return ResponseEntity.ok(questionRepository.findByApplicationIdOrderByQuestionIdAsc(application.getApplicationId()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{applicationId}/questions/{questionId}")
    public ResponseEntity<?> answerQuestion(
            @PathVariable Long applicationId,
            @PathVariable Long questionId,
            @RequestBody AnswerRequest request,
            Authentication authentication) {
        try {
            User user = currentUser(authentication);
            ownApplication(user.getUserId(), applicationId);
            ApplicationQuestion question = questionRepository.findById(questionId)
                    .orElseThrow(() -> new RuntimeException("Question not found."));

            if (!question.getApplicationId().equals(applicationId)) {
                throw new RuntimeException("Question does not belong to this application.");
            }
            if (request == null || request.answer() == null || request.answer().isBlank()) {
                throw new RuntimeException("Answer is required.");
            }

            String answer = request.answer().trim();
            question.setFinalAnswer(answer);
            question.setStatus("ANSWERED");
            question.setAnsweredAt(LocalDateTime.now());
            questionRepository.save(question);

            // Resolve duplicate records for the same visible question in this application.
            // Zoho may expose an opaque DOM ID on one attempt and only a generated fallback
            // key on another; the readable label remains stable.
            String normalizedQuestion = normalizeQuestion(question.getQuestion());
            for (ApplicationQuestion duplicate : questionRepository
                    .findByApplicationIdOrderByQuestionIdAsc(applicationId)) {
                if (java.util.Objects.equals(duplicate.getQuestionId(), questionId)) continue;
                if (!normalizedQuestion.isBlank()
                        && normalizedQuestion.equals(normalizeQuestion(duplicate.getQuestion()))
                        && "PENDING".equalsIgnoreCase(duplicate.getStatus())) {
                    duplicate.setFinalAnswer(answer);
                    duplicate.setStatus("ANSWERED");
                    duplicate.setAnsweredAt(LocalDateTime.now());
                    questionRepository.save(duplicate);
                }
            }

            String key = question.getFieldId();
            if (key == null || key.isBlank()) {
                key = question.getQuestion();
            }
            profileService.saveAnswer(user, key, question.getQuestion(), answer);

            return ResponseEntity.ok(question);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new RuntimeException("Authentication required.");
        }
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found."));
    }

    private JobApplication ownApplication(Long userId, Long applicationId) {
        JobApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new RuntimeException("Application not found."));
        if (!application.getUserId().equals(userId)) {
            throw new RuntimeException("You are not authorized to access this application.");
        }
        return application;
    }

    private String normalizeQuestion(String value) {
        return value == null ? "" : value.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim();
    }

    public record AnswerRequest(String answer) {}
}
