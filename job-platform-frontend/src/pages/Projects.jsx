import { API_BASE_URL } from "../apiBase.js";
import { useState } from "react";
import { useNavigate } from "react-router-dom";
import axios from "axios";
import ProfileLayout from "../components/ProfileLayout";
import { useProfile } from "../context/ProfileContext";
import "./Projects.css";

const emptyProject = {
  name: "",
  organization: "",
  techStack: "",
  description: "",
  githubUrl: "",
  liveUrl: "",
  contributions: ["", "", ""],
};

function Projects() {
  const navigate = useNavigate();
  const { profile, updateSection } = useProfile();

  const [projects, setProjects] = useState(
    profile.projects || []
  );
  const [questionLoading, setQuestionLoading] = useState({});
  const [questionErrors, setQuestionErrors] = useState({});

  const addProject = () => {
    setProjects([
      ...projects,
      {
        ...emptyProject,
        contributions: ["", "", ""],
      },
    ]);
  };

  const removeProject = (index) => {
    setProjects(
      projects.filter((_, i) => i !== index)
    );
  };

  const updateField = (index, field, value) => {
    const updated = [...projects];

    updated[index][field] = value;

    setProjects(updated);
  };

  const generateAtsQuestions = async (index, force = false) => {
    const project = projects[index];
    const title = String(project?.name || "").trim();
    if (title.length < 3) return;
    if (!force && project.atsQuestions?.length && project.atsQuestionTitle === title) return;

    setQuestionLoading((current) => ({ ...current, [index]: true }));
    setQuestionErrors((current) => ({ ...current, [index]: "" }));
    try {
      const token = localStorage.getItem("token");
      const response = await axios.post(
        API_BASE_URL + "/api/projects/ats-questions",
        { projectTitle: title },
        { headers: { Authorization: `Bearer ${token}` } }
      );
      const updated = [...projects];
      updated[index] = {
        ...updated[index],
        atsQuestions: response.data || [],
        atsQuestionTitle: title,
        atsDetails: updated[index].atsDetails || {},
      };
      setProjects(updated);
    } catch (error) {
      setQuestionErrors((current) => ({
        ...current,
        [index]: error.response?.data || "Could not create ATS project questions. Try again.",
      }));
    } finally {
      setQuestionLoading((current) => ({ ...current, [index]: false }));
    }
  };

  const updateAtsAnswer = (projectIndex, key, value) => {
    const updated = [...projects];
    const project = { ...updated[projectIndex] };
    project.atsDetails = { ...(project.atsDetails || {}), [key]: value };
    updated[projectIndex] = project;
    setProjects(updated);
  };

  const updateContribution = (
    projectIndex,
    bulletIndex,
    value
  ) => {
    const updated = [...projects];

    updated[projectIndex].contributions[
      bulletIndex
    ] = value;

    setProjects(updated);
  };

  const handleNext = () => {
    updateSection("projects", projects);
    navigate("/additional-details");
  };

  return (
    <ProfileLayout
      step={6}
      title="Projects"
      subtitle="Showcase projects that demonstrate your skills."
      onNext={handleNext}
      onBack={() => navigate("/experience")}
    >

      {projects.map((project, index) => (
        <div className="dynamic-card" key={index}>

          <div className="dynamic-card-header">
            <h3>Project {index + 1}</h3>

            <button
              type="button"
              className="remove-button"
              onClick={() =>
                removeProject(index)
              }
            >
              Remove
            </button>
          </div>

          <div className="form-grid">

            <div className="form-group">
              <label>Project Name *</label>
              <input
                value={project.name}
                onChange={(e) =>
                  updateField(
                    index,
                    "name",
                    e.target.value
                  )
                }
                placeholder="Project name"
                onBlur={() => generateAtsQuestions(index)}
              />
            </div>

            <div className="form-group full-width ats-project-prompts">
              <div className="ats-prompts-heading">
                <div>
                  <strong>Build a stronger project story</strong>
                  <span>We’ll ask project-specific questions to capture ATS-ready details.</span>
                </div>
                <button
                  type="button"
                  className="ats-refresh-button"
                  disabled={!project.name?.trim() || questionLoading[index]}
                  onClick={() => generateAtsQuestions(index, true)}
                >
                  {questionLoading[index] ? "Preparing…" : project.atsQuestions?.length ? "Refresh questions" : "Create questions"}
                </button>
              </div>
              {questionErrors[index] && <p className="ats-question-error">{questionErrors[index]}</p>}
              {!!project.atsQuestions?.length && (
                <div className="ats-question-list">
                  {project.atsQuestions.map((item) => (
                    <div className="form-group" key={item.key}>
                      <label>{item.question}</label>
                      {item.type === "text" ? (
                        <input
                          value={project.atsDetails?.[item.key] || ""}
                          onChange={(event) => updateAtsAnswer(index, item.key, event.target.value)}
                          placeholder={item.placeholder || "Your answer"}
                        />
                      ) : (
                        <textarea
                          rows="3"
                          value={project.atsDetails?.[item.key] || ""}
                          onChange={(event) => updateAtsAnswer(index, item.key, event.target.value)}
                          placeholder={item.placeholder || "Your answer"}
                        />
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>

            <div className="form-group">
              <label>Organization</label>
              <input
                value={project.organization}
                onChange={(e) =>
                  updateField(
                    index,
                    "organization",
                    e.target.value
                  )
                }
                placeholder="College / Company / Personal"
              />
            </div>

            <div className="form-group full-width">
              <label>Tech Stack *</label>
              <input
                value={project.techStack}
                onChange={(e) =>
                  updateField(
                    index,
                    "techStack",
                    e.target.value
                  )
                }
                placeholder="React, Spring Boot, MySQL..."
              />
            </div>

            <div className="form-group full-width">
              <label>Project Description</label>
              <textarea
                rows="4"
                value={project.description}
                onChange={(e) =>
                  updateField(
                    index,
                    "description",
                    e.target.value
                  )
                }
                placeholder="Briefly describe the project..."
              />
            </div>

            <div className="form-group">
              <label>GitHub URL</label>
              <input
                value={project.githubUrl}
                onChange={(e) =>
                  updateField(
                    index,
                    "githubUrl",
                    e.target.value
                  )
                }
                placeholder="https://github.com/..."
              />
            </div>

            <div className="form-group">
              <label>Live URL</label>
              <input
                value={project.liveUrl}
                onChange={(e) =>
                  updateField(
                    index,
                    "liveUrl",
                    e.target.value
                  )
                }
                placeholder="https://..."
              />
            </div>

          </div>

          <div className="form-group">
            <label>Key Contributions</label>

            {project.contributions.map(
              (item, bulletIndex) => (
                <input
                  key={bulletIndex}
                  className="bullet-input"
                  value={item}
                  onChange={(e) =>
                    updateContribution(
                      index,
                      bulletIndex,
                      e.target.value
                    )
                  }
                  placeholder={`Contribution ${
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
        onClick={addProject}
      >
        + Add Project
      </button>

    </ProfileLayout>
  );
}

export default Projects;
