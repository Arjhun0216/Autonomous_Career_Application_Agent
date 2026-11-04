import { useNavigate } from "react-router-dom";

const steps = [
  { name: "Account", path: "/register" },
  { name: "Personal", path: "/personal-details" },
  { name: "Education", path: "/education" },
  { name: "Skills", path: "/skills" },
  { name: "Experience", path: "/experience" },
  { name: "Projects", path: "/projects" },
  { name: "More", path: "/additional-details" },
];

function ProgressBar({ currentStep }) {
  const navigate = useNavigate();

  return (
    <div className="progress-wrapper">
      {steps.map((step, index) => {
        const stepNumber = index + 1;

        return (
          <div className="progress-step" key={step.name}>
            <button
              type="button"
              className={`progress-circle ${
                stepNumber <= currentStep ? "active" : ""
              }`}
              onClick={() => {
                if (stepNumber < currentStep) {
                  navigate(step.path);
                }
              }}
            >
              {stepNumber}
            </button>

            <span
              className={
                stepNumber <= currentStep
                  ? "progress-label active"
                  : "progress-label"
              }
            >
              {step.name}
            </span>

            {index < steps.length - 1 && (
              <div
                className={`progress-line ${
                  stepNumber < currentStep ? "active" : ""
                }`}
              />
            )}
          </div>
        );
      })}
    </div>
  );
}

export default ProgressBar;