import { API_BASE_URL } from "../apiBase.js";
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import axios from "axios";
import "./JobDetails.css";

function JobDetails() {

  const navigate = useNavigate();

  const [job, setJob] = useState(null);
  const [loadingJob, setLoadingJob] = useState(true);

  const [applying, setApplying] = useState(false);
  const [applyMessage, setApplyMessage] = useState("");

  // Job-specific skill verification
  const [missingSkills, setMissingSkills] = useState([]);
  const [skillCheckLoading, setSkillCheckLoading] = useState(false);
  const [skillCheckError, setSkillCheckError] = useState("");
  const [skillQuestionIndex, setSkillQuestionIndex] = useState(0);
  const [skillModalOpen, setSkillModalOpen] = useState(false);
  const [savingSkill, setSavingSkill] = useState(false);
  const [savedJobSkills, setSavedJobSkills] = useState([]);

  const token =
    localStorage.getItem("token");


  // ============================================================
  // LOAD JOB
  // ============================================================

  useEffect(() => {

    const loadJob = async () => {

      const stored =
        localStorage.getItem("selectedJob");

      if (!stored) {
        navigate("/home");
        return;
      }

      try {

        const selected =
          JSON.parse(stored);

        const jobId =
          selected?.jobId ??
          selected?.job_id;

        if (!jobId) {
          throw new Error(
            "Selected job does not contain a job ID."
          );
        }

        /*
         * Always fetch the real job from the backend.
         *
         * This fixes the JD problem because recommendation
         * cards do not contain the full JD.
         */
        const response =
          await axios.get(
            `${API_BASE_URL}/api/jobs/${encodeURIComponent(jobId)}`,
            {
              headers: {
                Authorization:
                  `Bearer ${token}`
              }
            }
          );

        const fullJob =
          response.data || {};

        const resolvedJob = {
          ...selected,
          ...fullJob,
          jobId:
            fullJob.jobId ??
            jobId
        };

        setJob(resolvedJob);

        // Before the user applies, ask only about skills missing for THIS job.
        await checkJobSkills(resolvedJob.jobId);

      } catch (error) {

        console.error(
          "Failed to load job:",
          error
        );

        /*
         * Fall back to the selected recommendation if
         * the details endpoint fails.
         */
        try {

          const fallback =
            JSON.parse(stored);

          setJob(fallback);

          await checkJobSkills(
            fallback?.jobId ??
            fallback?.job_id
          );

        } catch (_) {

          localStorage.removeItem(
            "selectedJob"
          );

          navigate("/home");
        }

      } finally {

        setLoadingJob(false);
      }
    };

    loadJob();

  }, [navigate, token]);


  // ============================================================
  // CHECK SKILLS REQUIRED FOR THIS JOB
  // ============================================================

  const checkJobSkills = async (jobId) => {

    if (!token || !jobId) {
      return;
    }

    try {

      setSkillCheckLoading(true);
      setSkillCheckError("");

      const response = await axios.get(
        `${API_BASE_URL}/api/skills/verify?jobId=${encodeURIComponent(jobId)}`,
        {
          headers: {
            Authorization: `Bearer ${token}`
          }
        }
      );

      const missing =
        Array.isArray(response.data?.missingSkills)
          ? response.data.missingSkills
          : [];

      setMissingSkills(missing);
      setSkillQuestionIndex(0);
      setSkillModalOpen(missing.length > 0);

    } catch (error) {

      console.error(
        "Failed to verify job skills:",
        error
      );

      // Skill verification must never prevent the user from viewing/applying.
      setSkillCheckError(
        "We could not check the job-specific skill gaps right now. You can continue without adding any skills."
      );

    } finally {

      setSkillCheckLoading(false);
    }
  };


  // ============================================================
  // HANDLE JOB-SPECIFIC SKILL ANSWER
  // ============================================================

  const handleJobSkillAnswer = async (knowsSkill) => {

    const skill = missingSkills[skillQuestionIndex];

    if (!skill) {
      setSkillModalOpen(false);
      return;
    }

    if (!knowsSkill) {
      // Unknown skill is deliberately not added to the profile.
      moveToNextSkill();
      return;
    }

    try {

      setSavingSkill(true);

      await axios.post(
        API_BASE_URL + "/api/skills/confirm",
        { skill },
        {
          headers: {
            Authorization: `Bearer ${token}`,
            "Content-Type": "application/json"
          }
        }
      );

      setSavedJobSkills((previous) =>
        previous.includes(skill)
          ? previous
          : [...previous, skill]
      );

      moveToNextSkill();

    } catch (error) {

      console.error(
        "Failed to save job skill:",
        error
      );

      setApplyMessage(
        `Unable to save ${skill} to your profile. Please try again.`
      );

    } finally {

      setSavingSkill(false);
    }
  };


  const moveToNextSkill = () => {

    if (skillQuestionIndex >= missingSkills.length - 1) {
      setSkillModalOpen(false);
      return;
    }

    setSkillQuestionIndex((previous) => previous + 1);
  };


  // ============================================================
  // SAFE VALUE
  // ============================================================

  const isAvailable = (value) => {

    if (
      value === null ||
      value === undefined ||
      String(value).trim() === "" ||
      String(value).toLowerCase() === "null" ||
      String(value).toLowerCase() === "unknown"
    ) {
      return false;
    }

    return true;
  };


  const displayValue = (
    value,
    fallback = "Not Available"
  ) => {

    return isAvailable(value)
      ? value
      : fallback;
  };


  // ============================================================
  // SCORE
  // ============================================================

  const getScore = () => {

    if (!job) {
      return 0;
    }

    let score =
      Number(
        job.matchScore ??
        job.finalScore ??
        job.score ??
        0
      );

    if (Number.isNaN(score)) {
      return 0;
    }

    if (score > 0 && score <= 1) {
      score *= 100;
    }

    return Math.round(score);
  };


  // ============================================================
  // JOB HELPERS
  // ============================================================

  const getJobId = () =>
    job?.jobId ??
    job?.job_id;

  const getTitle = () =>
    job?.title ??
    job?.jobTitle;

  const getCompany = () =>
    job?.company ??
    job?.companyName;

  const getExperience = () =>
    job?.experienceLevel ??
    job?.experience_level;


  const getSkills = () => {

    const value =
      job?.skills;

    if (!value) {
      return [];
    }

    if (Array.isArray(value)) {
      return value;
    }

    if (typeof value === "string") {

      try {

        const parsed =
          JSON.parse(value);

        if (Array.isArray(parsed)) {
          return parsed;
        }

      } catch (_) {

        return value
          .split(",")
          .map(
            skill => skill.trim()
          )
          .filter(Boolean);
      }
    }

    return [];
  };


  // ============================================================
  // APPLY
  // ============================================================

  const handleApply = async () => {

    if (!token) {
      navigate("/login");
      return;
    }

    if (!job) {
      return;
    }

    const jobId =
      getJobId();

    if (!jobId) {

      setApplyMessage(
        "Unable to apply because the job ID is missing."
      );

      return;
    }

    try {

      setApplying(true);
      setApplyMessage("");

      const response =
        await axios.post(
          API_BASE_URL + "/api/applications",
          {
            jobId,
            companyName:
              getCompany(),
            jobTitle:
              getTitle(),
            jobUrl:
              job?.jobUrl ??
              job?.job_url ??
              null
          },
          {
            headers: {
              Authorization:
                `Bearer ${token}`,
              "Content-Type":
                "application/json"
            }
          }
        );

      console.log(
        "Application created:",
        response.data
      );

      const appliedJobs =
        JSON.parse(
          localStorage.getItem(
            "appliedJobIds"
          ) || "[]"
        );

      if (!appliedJobs.includes(jobId)) {

        appliedJobs.push(jobId);

        localStorage.setItem(
          "appliedJobIds",
          JSON.stringify(appliedJobs)
        );
      }

      localStorage.removeItem(
        "selectedJob"
      );

      navigate("/home", {
        replace: true,
        state: {
          appliedNotice: `Applied to ${getTitle()} at ${getCompany()}. Resume and application preparation is in progress.`,
        },
      });

    } catch (error) {

      console.error(
        "Failed to create application:",
        error
      );

      if (
        error.response?.status === 401
      ) {

        localStorage.removeItem(
          "token"
        );

        navigate("/login");

        return;
      }

      const message =
        error.response?.data;

      setApplyMessage(
        typeof message === "string"
          ? message
          : "Unable to submit your application. Please try again."
      );

    } finally {

      setApplying(false);
    }
  };


  // ============================================================
  // LOADING
  // ============================================================

  if (loadingJob) {

    return (
      <div className="job-details-page">
        <div className="job-details-state">
          <div className="job-details-loader"></div>
          <h3>Loading job details...</h3>
        </div>
      </div>
    );
  }


  const skills =
    getSkills();



  return (
    <div className="job-details-page">

      {skillModalOpen && missingSkills.length > 0 && (
        <div className="job-skill-modal-backdrop">
          <div className="job-skill-modal" role="dialog" aria-modal="true">

            <div className="job-skill-modal-badge">JOB-SPECIFIC SKILL CHECK</div>

            <h2>Do you know this required skill?</h2>

            <p className="job-skill-modal-description">
              We found skills required by this job that are not currently in your profile.
              Answer only for this job. Skills you know will be added to your profile and used
              for the personalized resume. Unknown skills are simply skipped.
            </p>

            <div className="job-skill-progress">
              Skill {skillQuestionIndex + 1} of {missingSkills.length}
            </div>

            <div className="job-skill-question">
              {missingSkills[skillQuestionIndex]}
            </div>

            <div className="job-skill-actions">
              <button
                className="job-skill-known-button"
                onClick={() => handleJobSkillAnswer(true)}
                disabled={savingSkill}
              >
                {savingSkill ? "Saving..." : "I know this"}
              </button>

              <button
                className="job-skill-unknown-button"
                onClick={() => handleJobSkillAnswer(false)}
                disabled={savingSkill}
              >
                I don't know it — Skip
              </button>
            </div>

            {savedJobSkills.length > 0 && (
              <p className="job-skill-saved-message">
                ✓ Added to your profile: {savedJobSkills.join(", ")}
              </p>
            )}

          </div>
        </div>
      )}

      <nav className="job-details-navbar">

        <button
          className="back-button"
          onClick={() =>
            navigate("/home")
          }
        >
          ← Back to Jobs
        </button>

        <div className="job-details-brand">
          <span className="brand-icon">
            AI
          </span>

          <span>
            AI Job Navigator
          </span>
        </div>

      </nav>


      <main className="job-details-content">

        <section className="job-details-header">

          <div>

            <div className="job-details-badge">
              RECOMMENDED JOB
            </div>

            <h1>
              {displayValue(
                getTitle()
              )}
            </h1>

            <h2>
              {displayValue(
                getCompany()
              )}
            </h2>

          </div>

          <div className="job-score-card">

            <span>
              Match Score
            </span>

            <strong>
              {getScore()}%
            </strong>

          </div>

        </section>

        <section className="apply-section top-apply-section" aria-label="Apply to this job">
          <button className="apply-now-button" onClick={handleApply} disabled={applying}>
            {applying ? "Submitting..." : "Apply Now"}
          </button>
          <p>Start your application now. Your tailored resume and cover letter will be prepared automatically.</p>
        </section>


        <section className="job-info-grid">

          <div className="job-info-card">
            <span className="job-info-icon">📍</span>
            <div>
              <span>Location</span>
              <strong>
                {displayValue(job.location)}
              </strong>
            </div>
          </div>

          <div className="job-info-card">
            <span className="job-info-icon">💰</span>
            <div>
              <span>Salary</span>
              <strong>
                {displayValue(job.salary)}
              </strong>
            </div>
          </div>

          <div className="job-info-card">
            <span className="job-info-icon">💼</span>
            <div>
              <span>Experience</span>
              <strong>
                {displayValue(getExperience())}
              </strong>
            </div>
          </div>

          <div className="job-info-card">
            <span className="job-info-icon">🔗</span>
            <div>
              <span>Job ID</span>
              <strong>
                {displayValue(getJobId())}
              </strong>
            </div>
          </div>

        </section>


        {skills.length > 0 && (

          <section className="job-details-section">

            <h2>
              Required Skills
            </h2>

            <div className="skills-list">

              {skills.map(
                (skill, index) => (
                  <span
                    className="skill-tag"
                    key={`${skill}-${index}`}
                  >
                    {skill}
                  </span>
                )
              )}

            </div>

          </section>
        )}


        {/* ====================================================
            JOB DESCRIPTION
        ==================================================== */}

        <section className="job-details-section">

          <h2>
            Job Description
          </h2>

          <div className="job-description">

            {isAvailable(job.jd)
              ? job.jd
              : "Job description is not available for this posting."}

          </div>

        </section>


        {isAvailable(
          job.jobUrl ??
          job.job_url
        ) && (

          <section className="job-source-section">

            <a
              href={
                job.jobUrl ??
                job.job_url
              }
              target="_blank"
              rel="noopener noreferrer"
            >
              View Original Job Posting ↗
            </a>

          </section>
        )}


        {applyMessage && (

          <div className="apply-error">
            ⚠️ {applyMessage}
          </div>
        )}

        {skillCheckLoading && (
          <div className="job-skill-checking">
            Checking skills required specifically for this job...
          </div>
        )}

        {skillCheckError && (
          <div className="job-skill-check-warning">
            {skillCheckError}
          </div>
        )}

        {savedJobSkills.length > 0 && !skillModalOpen && (
          <div className="job-skill-saved-summary">
            ✓ Added to your profile: {savedJobSkills.join(", ")}
          </div>
        )}

        <section className="apply-section">

          <button
            className="apply-now-button"
            onClick={handleApply}
            disabled={applying}
          >
            {applying
              ? "Submitting..."
              : "Apply Now"
            }
          </button>

          <p>
            Your resume and cold mail will be
            prepared automatically in the background.
          </p>

        </section>

      </main>
    </div>
  );
}

export default JobDetails;
