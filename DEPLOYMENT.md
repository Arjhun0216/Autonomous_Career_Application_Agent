# Deployment plan

## Staging services

1. Build the React app with `VITE_API_BASE_URL` set to the Cloud Run API URL.
2. Deploy `Dockerfile` to Cloud Run with `SPRING_PROFILES_ACTIVE=prod`. The image binds to the `PORT` supplied by Cloud Run.
3. Create Cloud SQL for MySQL in the same region and grant the Cloud Run service identity Cloud SQL Client. Set `INSTANCE_CONNECTION_NAME`, `DB_NAME`, `DB_USERNAME`, and `DB_PASSWORD` as runtime configuration/secrets.
4. Set `APP_FRONTEND_URL`, `GOOGLE_OAUTH_REDIRECT_URI`, `GMAIL_OAUTH_REDIRECT_URI`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GEMINI_API_KEY`, and a newly generated `JWT_SECRET`. Keep secrets in Secret Manager.
5. Set the Google OAuth callback to `https://<api-domain>/login/oauth2/code/google`; set the Gmail callback to `https://<api-domain>/api/gmail/oauth2/callback`. Register the production frontend domain in OAuth authorized origins.
6. Build and host the frontend with Firebase Hosting; add its domain to Google OAuth and deploy only after a custom domain and consent-screen configuration are ready.

## Production gates (not yet complete)

- Create and review versioned database migrations for a clean MySQL 8 database; production profile uses Hibernate validation, not schema creation. Decide separately whether any local user data should be migrated.
- Move uploaded PDFs and generated résumé files from local disk to private object storage.
- Protect Gmail refresh tokens at rest with application-level encryption; users must authorize their own Gmail account. Keep human approval before an email is sent and add rate limits.
- Replace the current OAuth success redirect that puts the application JWT in a URL query string with a one-time code or secure session-cookie flow before public production.
- Configure OAuth consent branding, privacy policy, verified domain, and Google verification for the sensitive `gmail.send` scope before serving 200 external users.
- Configure database backups/restore checks, Cloud Run instance limits and connection pool size, error monitoring, quotas, and budget alerts.
- Run end-to-end acceptance and a load test against staging with synthetic data.

For development, use the `local` default profile and an ignored local `config/application-local.properties`. Production must explicitly set `SPRING_PROFILES_ACTIVE=prod` and supply all secrets and database values.
