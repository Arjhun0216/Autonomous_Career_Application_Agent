import { useNavigate } from "react-router-dom";
import ProgressBar from "./ProgressBar";

function ProfileLayout({
  step,
  title,
  subtitle,
  children,
  onNext,
  onBack,
  nextText = "Save & Continue",
}) {
  const navigate = useNavigate();

  return (
    <div className="profile-page">
      <div className="profile-container">

        <div className="profile-brand">
          <h1>AI Job Navigator</h1>
          <p>Build your professional profile</p>
        </div>

        <ProgressBar currentStep={step} />

        <div className="profile-card">

          <div className="profile-heading">
            <h2>{title}</h2>
            <p>{subtitle}</p>
          </div>

          <div className="profile-content">
            {children}
          </div>

          <div className="profile-actions">

            {step > 2 ? (
              <button
                type="button"
                className="secondary-button"
                onClick={onBack}
              >
                ← Back
              </button>
            ) : (
              <div />
            )}

            <button
              type="button"
              className="primary-button"
              onClick={onNext}
            >
              {nextText} →
            </button>

          </div>

        </div>

      </div>
    </div>
  );
}

export default ProfileLayout;
