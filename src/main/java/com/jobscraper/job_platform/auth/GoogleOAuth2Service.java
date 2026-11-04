package com.jobscraper.job_platform.auth;

import com.jobscraper.job_platform.user.User;
import com.jobscraper.job_platform.user.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GoogleOAuth2Service {

    private final UserRepository userRepository;

    @Transactional
    public User processGoogleUser(
            String googleId,
            String email,
            String name) {

        /*
         * ---------------------------------------------------------
         * CASE 1:
         * Google account is already linked
         * ---------------------------------------------------------
         */
        var existingByProvider =
                userRepository.findByProviderUserId(googleId);

        if (existingByProvider.isPresent()) {

            User user = existingByProvider.get();

            // Keep name updated from Google if available
            if (name != null && !name.isBlank()) {
                user.setName(name);
            }

            return userRepository.save(user);
        }


        /*
         * ---------------------------------------------------------
         * CASE 2:
         * Email already exists in our database
         *
         * Login to the EXISTING account instead of throwing
         * "email already exists".
         * ---------------------------------------------------------
         */
        var existingByEmail =
                userRepository.findByEmail(email);

        if (existingByEmail.isPresent()) {

            User user = existingByEmail.get();

            /*
             * Link this Google account to the existing user.
             */
            user.setProviderUserId(googleId);

            /*
             * Mark authentication provider as GOOGLE.
             *
             * The account can still contain all the existing
             * profile information.
             */
            user.setAuthProvider("GOOGLE");

            /*
             * Update name from Google only if available.
             */
            if (name != null && !name.isBlank()) {
                user.setName(name);
            }

            return userRepository.save(user);
        }


        /*
         * ---------------------------------------------------------
         * CASE 3:
         * Completely new Google user
         * ---------------------------------------------------------
         */
        User user =
                User.builder()
                        .email(email)
                        .name(name)
                        .authProvider("GOOGLE")
                        .providerUserId(googleId)
                        .passwordHash(null)
                        .userExperience(0)
                        .registrationCompleted(false)
                        .build();

        return userRepository.save(user);
    }
}