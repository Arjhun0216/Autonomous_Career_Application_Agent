package com.jobscraper.job_platform.auth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;

@Component
public class GoogleOAuth2FailureHandler
        extends SimpleUrlAuthenticationFailureHandler {

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception)
            throws IOException, ServletException {

        String message =
                exception.getMessage();

        if (message == null || message.isBlank()) {
            message = "Google authentication failed.";
        }

        String redirectUrl =
                frontendUrl + "/register"
                        + "?googleError="
                        + java.net.URLEncoder.encode(
                                message,
                                java.nio.charset.StandardCharsets.UTF_8
                        );

        getRedirectStrategy().sendRedirect(
                request,
                response,
                redirectUrl
        );
    }
}