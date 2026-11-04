package com.jobscraper.job_platform.gmail;

import com.jobscraper.job_platform.user.User;
import com.jobscraper.job_platform.user.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.security.core.Authentication;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/gmail")
@RequiredArgsConstructor
public class GmailController {

    private final GmailOAuthService gmailOAuthService;
    private final UserRepository userRepository;

    @Value("${gmail.oauth.frontend-url}")
    private String frontendUrl;


    /*
     * ============================================================
     * GET GMAIL CONNECT URL
     * ============================================================
     *
     * React calls:
     *
     * GET /api/gmail/connect-url
     *
     * Backend generates Google's OAuth URL.
     *
     * ============================================================
     */
    @GetMapping("/connect-url")
    public ResponseEntity<?> getConnectUrl(
            Authentication authentication) {

        try {

            if (authentication == null ||
                    !authentication.isAuthenticated()) {

                return ResponseEntity
                        .status(HttpStatus.UNAUTHORIZED)
                        .body(
                                Map.of(
                                        "message",
                                        "User is not authenticated"
                                )
                        );
            }

            String email =
                    authentication.getName();

            User user =
                    userRepository
                            .findByEmail(email)
                            .orElseThrow(() ->
                                    new RuntimeException(
                                            "User not found"
                                    )
                            );

            String url =
                    gmailOAuthService
                            .createAuthorizationUrl(
                                    user.getUserId()
                            );

            return ResponseEntity.ok(
                    Map.of(
                            "url",
                            url
                    )
            );

        } catch (Exception e) {

            e.printStackTrace();

            return ResponseEntity
                    .status(
                            HttpStatus.INTERNAL_SERVER_ERROR
                    )
                    .body(
                            Map.of(
                                    "message",
                                    e.getMessage() == null
                                            ? "Unable to create Gmail authorization URL"
                                            : e.getMessage()
                            )
                    );
        }
    }


    /*
     * ============================================================
     * GMAIL STATUS
     * ============================================================
     *
     * React calls:
     *
     * GET /api/gmail/status
     *
     * ============================================================
     */
    @GetMapping("/status")
    public ResponseEntity<?> getGmailStatus(
            Authentication authentication) {

        try {

            if (authentication == null ||
                    !authentication.isAuthenticated()) {

                return ResponseEntity
                        .status(HttpStatus.UNAUTHORIZED)
                        .body(
                                Map.of(
                                        "connected",
                                        false,
                                        "message",
                                        "User is not authenticated"
                                )
                        );
            }

            String email =
                    authentication.getName();

            User user =
                    userRepository
                            .findByEmail(email)
                            .orElseThrow(() ->
                                    new RuntimeException(
                                            "User not found"
                                    )
                            );

            return ResponseEntity.ok(
                    Map.of(
                            "connected",
                            user.isGmailConnected(),
                            "gmailEmail",
                            user.getGmailEmail() == null
                                    ? ""
                                    : user.getGmailEmail()
                    )
            );

        } catch (Exception e) {

            e.printStackTrace();

            return ResponseEntity
                    .status(
                            HttpStatus.INTERNAL_SERVER_ERROR
                    )
                    .body(
                            Map.of(
                                    "connected",
                                    false,
                                    "message",
                                    e.getMessage() == null
                                            ? "Unable to check Gmail status"
                                            : e.getMessage()
                            )
                    );
        }
    }


    /*
     * ============================================================
     * GOOGLE OAUTH CALLBACK
     * ============================================================
     *
     * Google redirects here after the user grants Gmail permission.
     *
     * ============================================================
     */
    @GetMapping("/oauth2/callback")
    public ResponseEntity<?> oauthCallback(
            @RequestParam(required = false)
            String code,

            @RequestParam(required = false)
            String state,

            @RequestParam(required = false)
            String error) {

        try {

            /*
             * User denied permission.
             */
            if (error != null &&
                    !error.isBlank()) {

                String redirect =
                        frontendUrl
                                + "/pending-approvals"
                                + "?gmail=error";

                return ResponseEntity
                        .status(HttpStatus.FOUND)
                        .header(
                                "Location",
                                redirect
                        )
                        .build();
            }

            if (code == null ||
                    code.isBlank()) {

                throw new RuntimeException(
                        "Google authorization code is missing"
                );
            }

            if (state == null ||
                    state.isBlank()) {

                throw new RuntimeException(
                        "OAuth state is missing"
                );
            }

            /*
             * Complete OAuth process.
             */
            String gmailEmail =
                    gmailOAuthService.handleCallback(
                            code,
                            state
                    );

            /*
             * Redirect React.
             */
            String redirect =
                    frontendUrl
                            + "/pending-approvals"
                            + "?gmail=connected"
                            + "&email="
                            + java.net.URLEncoder
                                    .encode(
                                            gmailEmail,
                                            java.nio.charset.StandardCharsets.UTF_8
                                    );

            return ResponseEntity
                    .status(HttpStatus.FOUND)
                    .header(
                            "Location",
                            redirect
                    )
                    .build();

        } catch (Exception e) {

            e.printStackTrace();

            String redirect =
                    frontendUrl
                            + "/pending-approvals"
                            + "?gmail=error";

            return ResponseEntity
                    .status(HttpStatus.FOUND)
                    .header(
                            "Location",
                            redirect
                    )
                    .build();
        }
    }
}