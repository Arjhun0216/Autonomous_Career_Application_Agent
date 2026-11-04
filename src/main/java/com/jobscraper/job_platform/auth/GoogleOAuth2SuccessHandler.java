package com.jobscraper.job_platform.auth;

import com.jobscraper.job_platform.security.JwtService;
import com.jobscraper.job_platform.user.User;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.Authentication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.user.OAuth2User;

import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class GoogleOAuth2SuccessHandler
        extends SimpleUrlAuthenticationSuccessHandler {

    private final GoogleOAuth2Service googleOAuth2Service;
    private final JwtService jwtService;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication)
            throws IOException, ServletException {

        OAuth2User oauth2User =
                (OAuth2User) authentication.getPrincipal();

        String googleId =
                oauth2User.getAttribute("sub");

        String email =
                oauth2User.getAttribute("email");

        String name =
                oauth2User.getAttribute("name");

        if (googleId == null || email == null) {

            redirectWithError(
                    response,
                    "Google account information is incomplete."
            );

            return;
        }

        /*
         * Find existing user OR create new Google user.
         */
        User user =
                googleOAuth2Service.processGoogleUser(
                        googleId,
                        email,
                        name
                );

        /*
         * Generate our application's JWT.
         */
        String token =
                jwtService.generateToken(
                        user.getEmail()
                );

        /*
         * Send token + profile status to React.
         */
        String redirectUrl =
                frontendUrl + "/oauth2/success"
                        + "?token="
                        + URLEncoder.encode(
                                token,
                                StandardCharsets.UTF_8
                        )
                        + "&profileCompleted="
                        + user.isRegistrationCompleted();

        getRedirectStrategy().sendRedirect(
                request,
                response,
                redirectUrl
        );
    }

    private void redirectWithError(
            HttpServletResponse response,
            String message)
            throws IOException {

        String redirectUrl =
                frontendUrl + "/register"
                        + "?googleError="
                        + URLEncoder.encode(
                                message,
                                StandardCharsets.UTF_8
                        );

        response.sendRedirect(
                response.encodeRedirectURL(redirectUrl)
        );
    }
}