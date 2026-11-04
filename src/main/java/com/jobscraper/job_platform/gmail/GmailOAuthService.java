package com.jobscraper.job_platform.gmail;

import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.ClientParametersAuthentication;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;

import com.jobscraper.job_platform.user.User;
import com.jobscraper.job_platform.user.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class GmailOAuthService {

    private final UserRepository userRepository;

    @Value("${gmail.oauth.client-id}")
    private String clientId;

    @Value("${gmail.oauth.client-secret}")
    private String clientSecret;

    @Value("${gmail.oauth.redirect-uri}")
    private String redirectUri;

    private static final GsonFactory JSON_FACTORY =
            GsonFactory.getDefaultInstance();

    /*
     * Gmail permission required for sending email.
     */
    private static final String GMAIL_SEND_SCOPE =
            GmailScopes.GMAIL_SEND;

    /*
     * Temporary OAuth state storage.
     *
     * state -> userId
     *
     * Used to identify which logged-in platform user
     * initiated the Gmail connection.
     */
    private final Map<String, Long> pendingStates =
            new ConcurrentHashMap<>();


    /**
     * ============================================================
     * CREATE GOOGLE AUTHORIZATION URL
     * ============================================================
     */
    public String createAuthorizationUrl(Long userId) {

        if (userId == null) {
            throw new RuntimeException(
                    "User ID is required"
            );
        }

        try {

            /*
             * Generate a unique OAuth state.
             */
            String state =
                    UUID.randomUUID().toString();

            /*
             * Remember which platform user
             * started the OAuth process.
             */
            pendingStates.put(
                    state,
                    userId
            );

            /*
             * Create HTTPS transport.
             */
            NetHttpTransport transport =
                    GoogleNetHttpTransport
                            .newTrustedTransport();

            /*
             * Create OAuth flow.
             */
            GoogleAuthorizationCodeFlow flow =
                    new GoogleAuthorizationCodeFlow.Builder(
                            transport,
                            JSON_FACTORY,
                            clientId,
                            clientSecret,
                            Collections.singleton(
                                    GMAIL_SEND_SCOPE
                            )
                    )
                    .setAccessType("offline")
                    .setApprovalPrompt("force")
                    .build();

            /*
             * Generate Google's authorization URL.
             */
            return flow
                    .newAuthorizationUrl()
                    .setRedirectUri(
                            redirectUri
                    )
                    .setState(
                            state
                    )
                    .build();

        } catch (
                GeneralSecurityException |
                IOException e) {

            throw new RuntimeException(
                    "Failed to create Gmail authorization URL",
                    e
            );
        }
    }


    /**
     * ============================================================
     * HANDLE GOOGLE OAUTH CALLBACK
     * ============================================================
     */
    public String handleCallback(
            String code,
            String state) {

        if (code == null ||
                code.isBlank()) {

            throw new RuntimeException(
                    "Authorization code is missing"
            );
        }

        if (state == null ||
                state.isBlank()) {

            throw new RuntimeException(
                    "OAuth state is missing"
            );
        }

        /*
         * Retrieve the platform user who initiated
         * the OAuth request.
         */
        Long userId =
                pendingStates.remove(state);

        if (userId == null) {

            throw new RuntimeException(
                    "Invalid or expired OAuth state"
            );
        }

        /*
         * Find platform user.
         */
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "User not found"
                                )
                        );

        try {

            /*
             * Create HTTPS transport.
             */
            NetHttpTransport transport =
                    GoogleNetHttpTransport
                            .newTrustedTransport();

            /*
             * Create OAuth flow.
             */
            GoogleAuthorizationCodeFlow flow =
                    new GoogleAuthorizationCodeFlow.Builder(
                            transport,
                            JSON_FACTORY,
                            clientId,
                            clientSecret,
                            Collections.singleton(
                                    GMAIL_SEND_SCOPE
                            )
                    )
                    .setAccessType("offline")
                    .setApprovalPrompt("force")
                    .build();

            /*
             * Exchange authorization code
             * for access + refresh tokens.
             */
            TokenResponse tokenResponse =
                    flow
                            .newTokenRequest(code)
                            .setRedirectUri(
                                    redirectUri
                            )
                            .execute();

            /*
             * Get refresh token.
             */
            String refreshToken =
                    tokenResponse.getRefreshToken();

            if (refreshToken == null ||
                    refreshToken.isBlank()) {

                throw new RuntimeException(
                        "Google did not return a refresh token. "
                                + "Please authorize Gmail again."
                );
            }

            /*
             * Create credential directly from
             * the received token response.
             *
             * We do not use Google's credential datastore.
             * The refresh token is stored in our MySQL database.
             */
            Credential credential =
                    new Credential.Builder(
                            BearerToken.authorizationHeaderAccessMethod()
                    )
                    .setTransport(
                            transport
                    )
                    .setJsonFactory(
                            JSON_FACTORY
                    )
                    .setClientAuthentication(
                            new ClientParametersAuthentication(
                                    clientId,
                                    clientSecret
                            )
                    )
                    .setTokenServerEncodedUrl(
                            "https://oauth2.googleapis.com/token"
                    )
                    .build();

            credential.setFromTokenResponse(
                    tokenResponse
            );

            /*
             * Create Gmail API client.
             *
             * This verifies that the OAuth token
             * can be used with Gmail.
             */
            Gmail gmail =
                    new Gmail.Builder(
                            transport,
                            JSON_FACTORY,
                            credential
                    )
                    .setApplicationName(
                            "Job Platform"
                    )
                    .build();

            /*
             * IMPORTANT:
             *
             * We only request gmail.send permission.
             * Therefore, we do NOT call:
             *
             * gmail.users().getProfile("me")
             *
             * because that would require additional
             * Gmail permissions.
             *
             * The platform user's Google email is already
             * known from our users table.
             */
            String gmailEmail =
                    user.getEmail();

            if (gmailEmail == null ||
                    gmailEmail.isBlank()) {

                throw new RuntimeException(
                        "User email is missing"
                );
            }

            /*
             * Save Gmail information
             * into our users table.
             */
            user.setGmailConnected(
                    true
            );

            user.setGmailRefreshToken(
                    refreshToken
            );

            user.setGmailEmail(
                    gmailEmail
            );

            userRepository.save(user);

            return gmailEmail;

        } catch (
                GeneralSecurityException |
                IOException e) {

            throw new RuntimeException(
                    "Failed to connect Gmail account: "
                            + e.getMessage(),
                    e
            );
        }
    }


    /**
     * ============================================================
     * CREATE AUTHORIZED GMAIL SERVICE
     * ============================================================
     *
     * Uses the refresh token stored in MySQL.
     *
     * We do NOT store Gmail access tokens in the database.
     * A fresh access token is obtained when required.
     */
    public Gmail getGmailService(
            User user) {

        if (user == null) {

            throw new RuntimeException(
                    "User is required"
            );
        }

        if (!user.isGmailConnected()) {

            throw new RuntimeException(
                    "Gmail account is not connected"
            );
        }

        if (user.getGmailRefreshToken() == null ||
                user.getGmailRefreshToken().isBlank()) {

            throw new RuntimeException(
                    "Gmail refresh token is missing"
            );
        }

        try {

            /*
             * Create HTTPS transport.
             */
            NetHttpTransport transport =
                    GoogleNetHttpTransport
                            .newTrustedTransport();

            /*
             * Create credential directly from
             * the refresh token stored in MySQL.
             */
            Credential credential =
                    new Credential.Builder(
                            BearerToken
                                    .authorizationHeaderAccessMethod()
                    )
                    .setTransport(
                            transport
                    )
                    .setJsonFactory(
                            JSON_FACTORY
                    )
                    .setClientAuthentication(
                            new ClientParametersAuthentication(
                                    clientId,
                                    clientSecret
                            )
                    )
                    .setTokenServerEncodedUrl(
                            "https://oauth2.googleapis.com/token"
                    )
                    .build();

            /*
             * Attach stored refresh token.
             */
            credential.setRefreshToken(
                    user.getGmailRefreshToken()
            );

            /*
             * Get a fresh access token.
             */
            boolean refreshed =
                    credential.refreshToken();

            if (!refreshed) {

                throw new RuntimeException(
                        "Unable to refresh Gmail access token"
                );
            }

            /*
             * Create authorized Gmail client.
             */
            return new Gmail.Builder(
                    transport,
                    JSON_FACTORY,
                    credential
            )
            .setApplicationName(
                    "Job Platform"
            )
            .build();

        } catch (
                GeneralSecurityException |
                IOException e) {

            throw new RuntimeException(
                    "Failed to create Gmail service: "
                            + e.getMessage(),
                    e
            );
        }
    }
}