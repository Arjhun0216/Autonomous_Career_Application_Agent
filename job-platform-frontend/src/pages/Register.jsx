import { API_BASE_URL } from "../apiBase.js";
import { Link, useSearchParams } from "react-router-dom";
import GoogleButton from "../components/GoogleButton";

export default function Register() {
  const [searchParams] = useSearchParams();
  const googleError = searchParams.get("googleError");

  const handleGoogleSignup = () => {
    window.location.href = API_BASE_URL + "/oauth2/authorization/google";
  };

  return (
    <main className="auth-page">
      <section className="auth-container">
        <header className="brand-section">
          <h1>AI Job Navigator</h1>
          <p>Build your profile. Discover what’s next.</p>
        </header>

        <div className="auth-card">
          <div className="auth-heading">
            <h2>Create your account</h2>
            <p>Continue with Google, then add your professional details.</p>
          </div>

          {googleError && <div className="error-text">{googleError}</div>}
          <GoogleButton onClick={handleGoogleSignup} />

          <div className="switch-auth">
            Already have an account? <Link to="/login">Sign in with Google</Link>
          </div>
        </div>

        <p className="footer-text">Your Google sign-in creates or opens your AI Job Navigator profile.</p>
      </section>
    </main>
  );
}
