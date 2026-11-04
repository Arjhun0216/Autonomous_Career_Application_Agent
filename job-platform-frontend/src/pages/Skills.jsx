import { useState } from "react";
import { useNavigate } from "react-router-dom";
import ProfileLayout from "../components/ProfileLayout";
import { useProfile } from "../context/ProfileContext";

const skillDatabase = [
  // Programming Languages
  "Java",
  "Python",
  "JavaScript",
  "TypeScript",
  "C",
  "C++",
  "C#",
  "Go",
  "Rust",
  "Kotlin",
  "Swift",
  "PHP",
  "Ruby",
  "Scala",
  "R",
  "Dart",

  // Frontend
  "HTML",
  "CSS",
  "React",
  "Angular",
  "Vue.js",
  "Next.js",
  "Nuxt.js",
  "Vite",
  "Webpack",
  "Tailwind CSS",
  "Bootstrap",
  "Redux",
  "jQuery",

  // Backend
  "Node.js",
  "Express.js",
  "Spring",
  "Spring Boot",
  "Spring MVC",
  "Spring Security",
  "Django",
  "Flask",
  "FastAPI",
  "Laravel",
  "ASP.NET",
  "ASP.NET Core",

  // Databases
  "MySQL",
  "PostgreSQL",
  "MongoDB",
  "SQL",
  "Oracle",
  "SQL Server",
  "SQLite",
  "Redis",
  "Cassandra",
  "DynamoDB",
  "Elasticsearch",

  // Cloud & DevOps
  "AWS",
  "Microsoft Azure",
  "Google Cloud",
  "Docker",
  "Kubernetes",
  "Terraform",
  "Jenkins",
  "GitHub Actions",
  "GitLab CI/CD",
  "Ansible",
  "Linux",
  "Nginx",
  "Apache",

  // AI / ML
  "Artificial Intelligence",
  "Machine Learning",
  "Deep Learning",
  "TensorFlow",
  "PyTorch",
  "Scikit-learn",
  "Pandas",
  "NumPy",
  "Matplotlib",
  "OpenCV",
  "Natural Language Processing",
  "Computer Vision",
  "Generative AI",
  "Large Language Models",
  "LangChain",

  // Data Engineering
  "Apache Kafka",
  "Apache Spark",
  "Hadoop",
  "Apache Airflow",
  "Databricks",
  "ETL",
  "Data Warehousing",
  "Data Engineering",

  // Tools
  "Git",
  "GitHub",
  "GitLab",
  "Bitbucket",
  "Postman",
  "Jira",
  "Maven",
  "Gradle",
  "npm",
  "pnpm",

  // Other Technical Skills
  "REST API",
  "GraphQL",
  "Microservices",
  "System Design",
  "Data Structures",
  "Algorithms",
  "Object Oriented Programming",
  "Operating Systems",
  "Database Management",
  "Computer Networks",
  "Cybersecurity",
  "Software Testing",
  "Unit Testing",
  "CI/CD",
];

function Skills() {
  const navigate = useNavigate();
  const { profile, updateSection } = useProfile();

  /*
   * Normalize skills loaded from localStorage.
   *
   * This also protects us if older profile data contains:
   * Java
   * java
   * JAVA
   */
  const initialSkills = Array.from(
    new Set(
      (profile.skills || [])
        .map((skill) => String(skill).trim().toLowerCase())
        .filter(Boolean)
    )
  );

  const [skills, setSkills] = useState(initialSkills);
  const [search, setSearch] = useState("");

  /*
   * Normalize the search text.
   */
  const normalizedSearch = search.trim().toLowerCase();

  /*
   * Autocomplete suggestions.
   *
   * The database can contain:
   * Java
   * Python
   * React
   *
   * But the comparison is case-insensitive.
   */
  const suggestions = skillDatabase
    .filter((skill) => {
      const normalizedSkill = skill.trim().toLowerCase();

      return (
        normalizedSkill.includes(normalizedSearch) &&
        !skills.includes(normalizedSkill)
      );
    })
    .slice(0, 8);

  /*
   * Add a skill.
   *
   * This works for BOTH:
   *
   * 1. Suggested skills
   * 2. Completely custom skills
   */
  const addSkill = (skill) => {
    const normalizedSkill = String(skill)
      .trim()
      .toLowerCase();

    if (!normalizedSkill) {
      return;
    }

    /*
     * Prevent duplicate skills.
     */
    if (skills.includes(normalizedSkill)) {
      setSearch("");
      return;
    }

    setSkills((previous) => [
      ...previous,
      normalizedSkill,
    ]);

    setSearch("");
  };

  /*
   * Add custom skill when user presses Enter.
   */
  const handleKeyDown = (e) => {
    if (e.key === "Enter") {
      e.preventDefault();

      if (normalizedSearch) {
        addSkill(normalizedSearch);
      }
    }
  };

  /*
   * Remove skill.
   */
  const removeSkill = (skill) => {
    setSkills((previous) =>
      previous.filter((item) => item !== skill)
    );
  };

  /*
   * Save skills into ProfileContext.
   */
  const handleNext = () => {
    /*
     * Final normalization before saving.
     *
     * This guarantees:
     * - lowercase
     * - trimmed
     * - no duplicates
     */
    const normalizedSkills = Array.from(
      new Set(
        skills
          .map((skill) =>
            String(skill).trim().toLowerCase()
          )
          .filter(Boolean)
      )
    );

    updateSection("skills", normalizedSkills);

    navigate("/experience");
  };

  return (
    <ProfileLayout
      step={4}
      title="Skills"
      subtitle="Select your existing skills or add your own."
      onNext={handleNext}
      onBack={() => navigate("/education")}
    >
      <div className="form-group">

        <label>Search Skills</label>

        <input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="Start typing a skill..."
        />

        {/* Suggestions */}
        {normalizedSearch && suggestions.length > 0 && (
          <div className="suggestion-box">
            {suggestions.map((skill) => (
              <button
                type="button"
                key={skill}
                onClick={() => addSkill(skill)}
              >
                {skill}
              </button>
            ))}
          </div>
        )}

        {/* Custom skill message */}
        {normalizedSearch &&
          suggestions.length === 0 &&
          !skills.includes(normalizedSearch) && (
            <div className="custom-skill-hint">
              Press <strong>Enter</strong> to add{" "}
              <strong>{normalizedSearch}</strong>
            </div>
          )}

        {/* Add custom skill even when suggestions exist */}
        {normalizedSearch &&
          suggestions.length > 0 &&
          !skills.includes(normalizedSearch) && (
            <div className="custom-skill-hint">
              Can't find your skill? Press{" "}
              <strong>Enter</strong> to add{" "}
              <strong>{normalizedSearch}</strong>
            </div>
          )}
      </div>

      {/* Selected Skills */}
      <div className="selected-skills">

        {skills.map((skill) => (
          <div
            className="skill-chip"
            key={skill}
          >
            {skill}

            <button
              type="button"
              onClick={() => removeSkill(skill)}
              aria-label={`Remove ${skill}`}
            >
              ×
            </button>
          </div>
        ))}

      </div>

      {!skills.length && (
        <div className="empty-message">
          No skills selected yet.
        </div>
      )}
    </ProfileLayout>
  );
}

export default Skills;