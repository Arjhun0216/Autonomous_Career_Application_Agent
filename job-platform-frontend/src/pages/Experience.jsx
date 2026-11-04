import { useState } from "react";
import { useNavigate } from "react-router-dom";
import ProfileLayout from "../components/ProfileLayout";
import { useProfile } from "../context/ProfileContext";

const emptyExperience = {
  company: "",
  role: "",
  location: "",
  employmentType: "Full-time",
  startDate: "",
  endDate: "",
  current: false,
  skills: "",
  responsibilities: ["", "", ""],
};

function Experience() {
  const navigate = useNavigate();
  const { profile, updateSection } = useProfile();

  const [experience, setExperience] = useState(
    profile.experience || []
  );

  const addExperience = () => {
    setExperience([
      ...experience,
      {
        ...emptyExperience,
        responsibilities: ["", "", ""],
      },
    ]);
  };

  const removeExperience = (index) => {
    setExperience(
      experience.filter((_, i) => i !== index)
    );
  };

  const updateField = (index, field, value) => {
    const updated = [...experience];

    updated[index][field] = value;

    setExperience(updated);
  };

  const updateResponsibility = (
    experienceIndex,
    bulletIndex,
    value
  ) => {
    const updated = [...experience];

    updated[experienceIndex].responsibilities[
      bulletIndex
    ] = value;

    setExperience(updated);
  };

  const handleNext = () => {
    updateSection("experience", experience);
    navigate("/projects");
  };

  return (
    <ProfileLayout
      step={5}
      title="Experience"
      subtitle="Add your internships and professional experience."
      onNext={handleNext}
      onBack={() => navigate("/skills")}
    >

      {experience.map((item, index) => (
        <div className="dynamic-card" key={index}>

          <div className="dynamic-card-header">
            <h3>Experience {index + 1}</h3>

            <button
              type="button"
              className="remove-button"
              onClick={() =>
                removeExperience(index)
              }
            >
              Remove
            </button>
          </div>

          <div className="form-grid">

            <div className="form-group">
              <label>Company</label>
              <input
                value={item.company}
                onChange={(e) =>
                  updateField(
                    index,
                    "company",
                    e.target.value
                  )
                }
              />
            </div>

            <div className="form-group">
              <label>Role</label>
              <input
                value={item.role}
                onChange={(e) =>
                  updateField(
                    index,
                    "role",
                    e.target.value
                  )
                }
              />
            </div>

            <div className="form-group">
              <label>Location</label>
              <input
                value={item.location}
                onChange={(e) =>
                  updateField(
                    index,
                    "location",
                    e.target.value
                  )
                }
              />
            </div>

            <div className="form-group">
              <label>Employment Type</label>
              <select
                value={item.employmentType}
                onChange={(e) =>
                  updateField(
                    index,
                    "employmentType",
                    e.target.value
                  )
                }
              >
                <option>Full-time</option>
                <option>Part-time</option>
                <option>Internship</option>
                <option>Contract</option>
                <option>Freelance</option>
              </select>
            </div>

            <div className="form-group">
              <label>Start Date</label>
              <input
                type="month"
                value={item.startDate}
                onChange={(e) =>
                  updateField(
                    index,
                    "startDate",
                    e.target.value
                  )
                }
              />
            </div>

            {!item.current && (
              <div className="form-group">
                <label>End Date</label>
                <input
                  type="month"
                  value={item.endDate}
                  onChange={(e) =>
                    updateField(
                      index,
                      "endDate",
                      e.target.value
                    )
                  }
                />
              </div>
            )}

          </div>

          <label className="checkbox-row">
            <input
              type="checkbox"
              checked={item.current}
              onChange={(e) =>
                updateField(
                  index,
                  "current",
                  e.target.checked
                )
              }
            />
            Currently working here
          </label>

          <div className="form-group">
            <label>Skills / Technologies Used</label>
            <input
              value={item.skills}
              onChange={(e) =>
                updateField(
                  index,
                  "skills",
                  e.target.value
                )
              }
              placeholder="Java, Spring Boot, MySQL..."
            />
          </div>

          <div className="form-group">
            <label>Responsibilities / Achievements</label>

            {item.responsibilities.map(
              (bullet, bulletIndex) => (
                <input
                  key={bulletIndex}
                  className="bullet-input"
                  value={bullet}
                  onChange={(e) =>
                    updateResponsibility(
                      index,
                      bulletIndex,
                      e.target.value
                    )
                  }
                  placeholder={`Bullet point ${
                    bulletIndex + 1
                  }`}
                />
              )
            )}
          </div>

        </div>
      ))}

      <button
        type="button"
        className="add-button"
        onClick={addExperience}
      >
        + Add Experience
      </button>

      {!experience.length && (
        <p className="optional-note">
          No experience? That's completely fine.
          You can continue as a fresher.
        </p>
      )}

    </ProfileLayout>
  );
}

export default Experience;