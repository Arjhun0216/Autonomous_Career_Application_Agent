import { API_BASE_URL } from "../apiBase.js";
import GoogleButton from "../components/GoogleButton";

export default function Login() {
  const handleGoogleLogin = () => {
    window.location.href = API_BASE_URL + "/oauth2/authorization/google";
  };

  return (
    <main className="auth-page">
      <section className="auth-container">
        <header className="brand-section">
          <h1>AI Job Navigator</h1>
          <p>Find opportunities that fit your next move.</p>
        </header>

        <div className="auth-card">
          <div className="auth-heading">
            <h2>Welcome back</h2>
            <p>Sign in securely with your Google account.</p>
          </div>

          <GoogleButton onClick={handleGoogleLogin} />

          <div className="google-login-note">
            New to AI Job Navigator? Your profile starts after Google sign-in.
          </div>

        </div>

        <p className="footer-text">By continuing, you agree to our Terms and Privacy Policy.</p>
      </section>
    </main>
  );
}
