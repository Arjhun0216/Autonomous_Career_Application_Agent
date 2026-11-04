import { useState } from "react";
import { useNavigate } from "react-router-dom";
import ProfileLayout from "../components/ProfileLayout";
import { useProfile } from "../context/ProfileContext";

function AdditionalDetails() {
  const navigate = useNavigate();

  const { profile, updateSection } = useProfile();

  const [data, setData] = useState(
    profile.additional || {
      certifications: [],
      achievements: [],
      leadership: [],
      publications: [],
      volunteering: [],
      languages: [],
      other: "",
    }
  );

  const [certification, setCertification] = useState({
    name: "",
    issuer: "",
    year: "",
    credentialUrl: "",
  });

  const [achievement, setAchievement] = useState({
    title: "",
    organization: "",
    year: "",
    description: "",
  });

  // --------------------------------------------------
  // ADD CERTIFICATION
  // --------------------------------------------------

  const addCertification = () => {
    if (!certification.name.trim()) return;

    setData((previous) => ({
      ...previous,
      certifications: [
        ...(previous.certifications || []),
        certification,
      ],
    }));

    setCertification({
      name: "",
      issuer: "",
      year: "",
      credentialUrl: "",
    });
  };

  // --------------------------------------------------
  // ADD ACHIEVEMENT
  // --------------------------------------------------

  const addAchievement = () => {
    if (!achievement.title.trim()) return;

    setData((previous) => ({
      ...previous,
      achievements: [
        ...(previous.achievements || []),
        achievement,
      ],
    }));

    setAchievement({
      title: "",
      organization: "",
      year: "",
      description: "",
    });
  };

  // --------------------------------------------------
  // CONTINUE TO PROFILE COMPLETE
  // --------------------------------------------------

  const handleNext = () => {
    // Save the latest Additional Details
    // into the global ProfileContext.
    updateSection("additional", data);

    // Move to the final profile review page.
    navigate("/profile-complete");
  };

  // --------------------------------------------------
  // RENDER
  // --------------------------------------------------

  return (
    <ProfileLayout
      step={7}
      title="Certifications & Achievements"
      subtitle="Add additional information that strengthens your profile."
      onNext={handleNext}
      onBack={() => navigate("/projects")}
      nextText="Complete Profile"
    >
      {/* ==================================================
          CERTIFICATIONS
          ================================================== */}

      <div className="section-block">
        <h3>Certifications</h3>

        <div className="form-grid">

          {/* Certification Name */}
          <div className="form-group">
            <label>Certification Name</label>

            <input
              value={certification.name}
              onChange={(e) =>
                setCertification({
                  ...certification,
                  name: e.target.value,
                })
              }
              placeholder="AWS Cloud Practitioner"
            />
          </div>

          {/* Issuing Organization */}
          <div className="form-group">
            <label>Issuing Organization</label>

            <input
              value={certification.issuer}
              onChange={(e) =>
                setCertification({
                  ...certification,
                  issuer: e.target.value,
                })
              }
              placeholder="Amazon Web Services"
            />
          </div>

          {/* Year */}
          <div className="form-group">
            <label>Year</label>

            <input
              value={certification.year}
              onChange={(e) =>
                setCertification({
                  ...certification,
                  year: e.target.value,
                })
              }
              placeholder="2026"
            />
          </div>

          {/* Credential URL */}
          <div className="form-group">
            <label>Credential URL</label>

            <input
              value={certification.credentialUrl}
              onChange={(e) =>
                setCertification({
                  ...certification,
                  credentialUrl: e.target.value,
                })
              }
              placeholder="https://..."
            />
          </div>

        </div>

        <button
          type="button"
          className="add-button"
          onClick={addCertification}
        >
          + Add Certification
        </button>

        {/* Saved Certifications */}
        {(data.certifications || []).map(
          (item, index) => (
            <div
              className="saved-item"
              key={index}
            >
              <strong>
                {item.name}
              </strong>

              <span>
                {item.issuer}

                {item.year &&
                  ` • ${item.year}`}
              </span>
            </div>
          )
        )}
      </div>

      {/* ==================================================
          ACHIEVEMENTS
          ================================================== */}

      <div className="section-block">
        <h3>Achievements</h3>

        <div className="form-grid">

          {/* Achievement */}
          <div className="form-group">
            <label>Achievement</label>

            <input
              value={achievement.title}
              onChange={(e) =>
                setAchievement({
                  ...achievement,
                  title: e.target.value,
                })
              }
              placeholder="Hackathon Winner"
            />
          </div>

          {/* Organization */}
          <div className="form-group">
            <label>
              Organization / Event
            </label>

            <input
              value={achievement.organization}
              onChange={(e) =>
                setAchievement({
                  ...achievement,
                  organization: e.target.value,
                })
              }
              placeholder="Hackathon / Organization"
            />
          </div>

          {/* Year */}
          <div className="form-group">
            <label>Year</label>

            <input
              value={achievement.year}
              onChange={(e) =>
                setAchievement({
                  ...achievement,
                  year: e.target.value,
                })
              }
              placeholder="2026"
            />
          </div>

        </div>

        {/* Description */}
        <div className="form-group">
          <label>Description</label>

          <textarea
            rows="3"
            value={achievement.description}
            onChange={(e) =>
              setAchievement({
                ...achievement,
                description: e.target.value,
              })
            }
            placeholder="Describe your achievement..."
          />
        </div>

        <button
          type="button"
          className="add-button"
          onClick={addAchievement}
        >
          + Add Achievement
        </button>

        {/* Saved Achievements */}
        {(data.achievements || []).map(
          (item, index) => (
            <div
              className="saved-item"
              key={index}
            >
              <strong>
                {item.title}
              </strong>

              <span>
                {item.organization}

                {item.year &&
                  ` • ${item.year}`}
              </span>
            </div>
          )
        )}
      </div>

      {/* ==================================================
          OTHER INFORMATION
          ================================================== */}

      <div className="section-block">
        <h3>Other Information</h3>

        {/* Languages */}
        <div className="form-group">
          <label>Languages</label>

          <input
            value={(data.languages || []).join(", ")}
            onChange={(e) =>
              setData((previous) => ({
                ...previous,

                languages: e.target.value
                  .split(",")
                  .map((x) => x.trim())
                  .filter(Boolean),
              }))
            }
            placeholder="English, Tamil, Hindi"
          />
        </div>

        {/* Other Information */}
        <div className="form-group">
          <label>
            Leadership / Volunteering / Other
          </label>

          <textarea
            rows="4"
            value={data.other || ""}
            onChange={(e) =>
              setData((previous) => ({
                ...previous,
                other: e.target.value,
              }))
            }
            placeholder="Any other information relevant to your professional profile..."
          />
        </div>
      </div>

    </ProfileLayout>
  );
}

export default AdditionalDetails;