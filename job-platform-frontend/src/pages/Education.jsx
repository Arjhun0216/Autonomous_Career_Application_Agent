import { useState } from "react";
import { useNavigate } from "react-router-dom";
import ProfileLayout from "../components/ProfileLayout";
import { useProfile } from "../context/ProfileContext";

const emptyEducation = {
  degree: "",
  institution: "",
  location: "",
  field: "",
  startYear: "",
  endYear: "",
  score: "",
  scoreType: "CGPA",
};

function Education() {
  const navigate = useNavigate();
  const { profile, updateSection } = useProfile();

  const [education, setEducation] = useState(
    profile.education.length
      ? profile.education
      : [{ ...emptyEducation }]
  );

  const addEducation = () => {
    if (education.length < 5) {
      setEducation([
        ...education,
        { ...emptyEducation },
      ]);
    }
  };

  const removeEducation = (index) => {
    setEducation(
      education.filter((_, i) => i !== index)
    );
  };

  const updateEducation = (index, field, value) => {
    const updated = [...education];

    updated[index][field] = value;

    setEducation(updated);
  };

  const handleNext = () => {
    updateSection("education", education);
    navigate("/skills");
  };

  return (
    <ProfileLayout
      step={3}
      title="Education"
      subtitle="Add up to five educational qualifications."
      onNext={handleNext}
      onBack={() => navigate("/personal-details")}
    >

      {education.map((item, index) => (
        <div className="dynamic-card" key={index}>

          <div className="dynamic-card-header">
            <h3>Education {index + 1}</h3>

            {education.length > 1 && (
              <button
                type="button"
                className="remove-button"
                onClick={() => removeEducation(index)}
              >
                Remove
              </button>
            )}
          </div>

          <div className="form-grid">

            <div className="form-group">
              <label>Degree / Qualification *</label>
              <input
                value={item.degree}
                onChange={(e) =>
                  updateEducation(
                    index,
                    "degree",
                    e.target.value
                  )
                }
                placeholder="B.E. Computer Science"
              />
            </div>

            <div className="form-group">
              <label>Institution *</label>
              <input
                value={item.institution}
                onChange={(e) =>
                  updateEducation(
                    index,
                    "institution",
                    e.target.value
                  )
                }
                placeholder="University / College / School"
              />
            </div>

            <div className="form-group">
              <label>Location</label>
              <input
                value={item.location}
                onChange={(e) =>
                  updateEducation(
                    index,
                    "location",
                    e.target.value
                  )
                }
                placeholder="City, State"
              />
            </div>

            <div className="form-group">
              <label>Field of Study</label>
              <input
                value={item.field}
                onChange={(e) =>
                  updateEducation(
                    index,
                    "field",
                    e.target.value
                  )
                }
                placeholder="Computer Science"
              />
            </div>

            <div className="form-group">
              <label>Start Year</label>
              <input
                type="number"
                value={item.startYear}
                onChange={(e) =>
                  updateEducation(
                    index,
                    "startYear",
                    e.target.value
                  )
                }
                placeholder="2023"
              />
            </div>

            <div className="form-group">
              <label>End Year / Expected</label>
              <input
                type="number"
                value={item.endYear}
                onChange={(e) =>
                  updateEducation(
                    index,
                    "endYear",
                    e.target.value
                  )
                }
                placeholder="2027"
              />
            </div>

            <div className="form-group">
              <label>Score Type</label>
              <select
                value={item.scoreType}
                onChange={(e) =>
                  updateEducation(
                    index,
                    "scoreType",
                    e.target.value
                  )
                }
              >
                <option value="CGPA">CGPA</option>
                <option value="Percentage">
                  Percentage
                </option>
                <option value="Marks">
                  Marks
                </option>
              </select>
            </div>

            <div className="form-group">
              <label>Score</label>
              <input
                value={item.score}
                onChange={(e) =>
                  updateEducation(
                    index,
                    "score",
                    e.target.value
                  )
                }
                placeholder="8.6 / 92%"
              />
            </div>

          </div>

        </div>
      ))}

      {education.length < 5 && (
        <button
          type="button"
          className="add-button"
          onClick={addEducation}
        >
          + Add Education
        </button>
      )}

    </ProfileLayout>
  );
}

export default Education;