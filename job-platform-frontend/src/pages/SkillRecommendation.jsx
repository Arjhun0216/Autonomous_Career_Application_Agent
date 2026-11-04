import { API_BASE_URL } from "../apiBase.js";
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import axios from "axios";
import "./SkillRecommendation.css";
import { useProfile } from "../context/ProfileContext";

const KNOWLEDGE_OPTIONS = [
  { value: "UNKNOWN", label: "I don't know it" },
  { value: "CONCEPT", label: "I know what it is" },
  { value: "USE", label: "I know how to use it" },
  { value: "IMPLEMENTED", label: "I have implemented it" },
];

export default function SkillRecommendation() {
  const navigate = useNavigate();
  const { updateSection } = useProfile();
  const token = localStorage.getItem("token");

  const [skills, setSkills] = useState([]);
  const [answers, setAnswers] = useState({});
  const [stacks, setStacks] = useState({});
  const [resources, setResources] = useState({});
  const [loading, setLoading] = useState(true);
  const [resourceLoading, setResourceLoading] = useState({});
  const [error, setError] = useState("");
  const [savingImplemented, setSavingImplemented] = useState({});
  const [savedImplemented, setSavedImplemented] = useState({});

  useEffect(() => {
    if (!token) {
      navigate("/login");
      return;
    }

    const load = async () => {
      try {
        setLoading(true);
        const response = await axios.get(
          API_BASE_URL + "/api/recommendations/skills",
          { headers: { Authorization: `Bearer ${token}` } }
        );
        setSkills(Array.isArray(response.data) ? response.data : []);
      } catch (err) {
        console.error("Skill recommendations failed:", err);
        setError("Unable to load skill recommendations.");
      } finally {
        setLoading(false);
      }
    };

    load();
  }, [navigate, token]);

  const getResources = async (skill) => {
    const answer = answers[skill];
    if (!answer || answer === "IMPLEMENTED") return;

    try {
      setResourceLoading((previous) => ({ ...previous, [skill]: true }));

      const stack = (stacks[skill] || "").trim();
      const experience =
        answer === "UNKNOWN"
          ? `Beginner. The candidate does not know this skill yet. Existing stack provided by candidate: ${stack || "Not provided"}.`
          : answer === "CONCEPT"
            ? `Knows what the skill is, but needs practical understanding. Existing stack: ${stack || "Not provided"}.`
            : `Knows how to use the skill. Existing stack: ${stack || "Not provided"}.`;

      const response = await axios.post(
        API_BASE_URL + "/api/skills/learning-resources",
        { skill, experience },
        { headers: { Authorization: `Bearer ${token}` } }
      );

      setResources((previous) => ({ ...previous, [skill]: response.data }));
    } catch (err) {
      console.error("Skill resources failed:", err);
      setError(`Unable to generate learning resources for ${skill}.`);
    } finally {
      setResourceLoading((previous) => ({ ...previous, [skill]: false }));
    }
  };

  const answerSkill = async (skill, value) => {
    setAnswers((previous) => ({ ...previous, [skill]: value }));

    if (value === "IMPLEMENTED") {
      setResources((previous) => {
        const copy = { ...previous };
        delete copy[skill];
        return copy;
      });

      // An implemented recommended skill is now a real user skill.
      if (!savedImplemented[skill] && !savingImplemented[skill]) {
        try {
          setSavingImplemented((previous) => ({
            ...previous,
            [skill]: true,
          }));

          const response = await axios.post(
            API_BASE_URL + "/api/skills/confirm",
            { skill },
            {
              headers: {
                Authorization: `Bearer ${token}`,
                "Content-Type": "application/json",
              },
            }
          );

          // Keep the in-memory and cached profile aligned with the persisted
          // users.skills value so a later profile save cannot restore stale data.
          if (Array.isArray(response.data?.skills)) {
            updateSection("skills", response.data.skills);
          }

          setSavedImplemented((previous) => ({
            ...previous,
            [skill]: true,
          }));

          setSkills((previous) =>
            previous.map((item) =>
              item.skill === skill
                ? { ...item, known: true }
                : item
            )
          );
        } catch (err) {
          console.error("Failed to save implemented skill:", err);
          setError(`Unable to add ${skill} to your profile.`);
        } finally {
          setSavingImplemented((previous) => ({
            ...previous,
            [skill]: false,
          }));
        }
      }
    }
  };

  return (
    <div className="skill-recommendation-page">
      <nav className="skill-recommendation-navbar">
        <button onClick={() => navigate("/home")}>← Back to Jobs</button>
        <div className="skill-recommendation-brand">
          <span className="brand-icon">AI</span>
          <span>Skill Recommendations</span>
        </div>
      </nav>

      <main className="skill-recommendation-content">
        <div className="skill-recommendation-header">
          <div>
            <span className="skill-recommendation-badge">AI SKILL ROADMAP</span>
            <h1>Skills that can improve your job opportunities</h1>
            <p>
              These skills are derived from the same recommendation service used
              for your job recommendations. A skill is added to your profile only when
              you confirm that you have implemented it.
            </p>
          </div>
        </div>

        {loading && <div className="skill-state-card">Loading skill recommendations...</div>}
        {error && <div className="skill-error-card">{error}</div>}

        {!loading && !skills.length && (
          <div className="skill-state-card">
            No skill recommendations are available yet.
          </div>
        )}

        <section className="skill-recommendation-grid">
          {skills.map((item) => {
            const answer = answers[item.skill];
            const resource = resources[item.skill];

            return (
              <article className="skill-recommendation-card" key={item.skill}>
                <div className="skill-card-top">
                  <div>
                    <h2>{item.skill}</h2>
                    <p>
                      {item.jobCount} job{item.jobCount === 1 ? "" : "s"} · relevance {Math.round(item.relevanceScore)}%
                    </p>
                  </div>
                  {item.known && <span className="known-badge">IN PROFILE</span>}
                </div>

                <div className="skill-question">
                  <strong>How much do you know about {item.skill}?</strong>
                  <div className="knowledge-options">
                    {KNOWLEDGE_OPTIONS.map((option) => (
                      <button
                        key={option.value}
                        className={answer === option.value ? "selected" : ""}
                        onClick={() => answerSkill(item.skill, option.value)}
                      >
                        {option.label}
                      </button>
                    ))}
                  </div>
                </div>

                {answer === "IMPLEMENTED" && (
                  <div className="implemented-message">
                    {savingImplemented[item.skill]
                      ? "Saving this implemented skill to your profile..."
                      : savedImplemented[item.skill]
                        ? "✓ Implemented skill added to your profile and will be used for future resume generation."
                        : "✓ Saved to your profile and users.skills."}
                  </div>
                )}

                {answer && answer !== "IMPLEMENTED" && (
                  <div className="stack-section">
                    <label>Your current stack</label>
                    <input
                      value={stacks[item.skill] || ""}
                      onChange={(e) =>
                        setStacks((previous) => ({
                          ...previous,
                          [item.skill]: e.target.value,
                        }))
                      }
                      placeholder="Example: Java, Spring Boot, MySQL"
                    />
                    <button
                      className="learn-button"
                      onClick={() => getResources(item.skill)}
                      disabled={resourceLoading[item.skill]}
                    >
                      {resourceLoading[item.skill]
                        ? "Generating..."
                        : answer === "UNKNOWN"
                          ? "Give me a beginner path"
                          : "Give me a practical learning path"}
                    </button>
                  </div>
                )}

                {resource && (
                  <div className="resource-list">
                    <h3>Learning path</h3>
                    {(resource.resources || []).map((r, index) => (
                      <div className="resource-item" key={`${item.skill}-${index}`}>
                        <strong>{index + 1}. {r.title}</strong>
                        <p>{r.purpose}</p>
                        {r.url && (
                          <a href={r.url} target="_blank" rel="noopener noreferrer">
                            Open resource →
                          </a>
                        )}
                      </div>
                    ))}
                  </div>
                )}
              </article>
            );
          })}
        </section>
      </main>
    </div>
  );
}
