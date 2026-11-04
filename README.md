# Career Agent

A React + Spring Boot career platform with MySQL storage and optional, user-authorized Gmail sending.

## Local development

- Java 21 and Maven
- Node.js 22 and npm
- MySQL/MariaDB local database named `job_platform`
- Google OAuth and Gemini credentials supplied through environment variables

Copy `job-platform-frontend/.env.example` to a local `.env` there and keep it out of Git. The backend loads its local-only JWT key from `config/application-local.properties`; create that file locally if it is missing. Never commit that file, credentials, uploaded résumés, or generated documents.

## Deployment shape

- React frontend: Firebase Hosting
- Spring Boot API: Google Cloud Run (`Dockerfile`)
- MySQL: Cloud SQL for MySQL via the Cloud SQL Java Connector
- Uploaded/generated files: private Cloud Storage bucket (app storage migration required before production)
- Credentials: Secret Manager / Cloud Run secrets

See `DEPLOYMENT.md` for production configuration and launch gates.
