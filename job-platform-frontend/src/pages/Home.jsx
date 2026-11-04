import { API_BASE_URL } from "../apiBase.js";
import { useEffect, useMemo, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import axios from "axios";
import "./Home.css";
import { useProfile } from "../context/ProfileContext";

function Home() {

  const navigate = useNavigate();
  const location = useLocation();
  const { clearProfile } = useProfile();

  const [jobs, setJobs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [menuOpen, setMenuOpen] = useState(false);
  const [appliedJobIds, setAppliedJobIds] = useState([]);

  const [locationFilter, setLocationFilter] = useState("ALL");
  const [positionFilter, setPositionFilter] = useState("ALL");

  const token = localStorage.getItem("token");


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


  const notAvailable = (value) => {

    return isAvailable(value)
      ? value
      : "Not Available";
  };


  // ============================================================
  // SCORE
  // ============================================================

  const getScore = (job) => {

    const score =
      job.matchScore ??
      job.finalScore ??
      job.score;

    if (!isAvailable(score)) {
      return 0;
    }

    let numericScore = Number(score);

    if (Number.isNaN(numericScore)) {
      return 0;
    }

    /*
     * If backend sends:
     *
     * 0.92
     *
     * convert it to:
     *
     * 92
     */

    if (
      numericScore > 0 &&
      numericScore <= 1
    ) {
      numericScore *= 100;
    }

    return numericScore;
  };


  // ============================================================
  // FETCH APPLIED JOBS
  // ============================================================

  const fetchAppliedJobs = async () => {

    if (!token) {
      return;
    }

    try {

      const response = await axios.get(
        API_BASE_URL + "/api/applications",
        {
          headers: {
            Authorization: `Bearer ${token}`
          }
        }
      );

      const applications = Array.isArray(response.data)
        ? response.data
        : [];

      const ids = applications
        .map((application) => application.jobId)
        .filter(
          (jobId) =>
            jobId !== null &&
            jobId !== undefined
        )
        .map((jobId) => String(jobId));

      setAppliedJobIds(ids);

    } catch (error) {

      console.error(
        "Failed to load applied jobs:",
        error
      );

    }
  };


  // ============================================================
  // FETCH RECOMMENDATIONS
  // ============================================================

  const fetchRecommendations = async () => {

    if (!token) {

      navigate("/login");

      return;
    }


    try {

      setLoading(true);
      setError("");

      const response = await axios.get(
        API_BASE_URL + "/api/recommendations",
        {
          headers: {
            Authorization: `Bearer ${token}`,
          },
        }
      );


      const data = response.data;

      let recommendationList = [];


      if (Array.isArray(data)) {

        recommendationList = data;

      } else if (
        data &&
        Array.isArray(data.recommendations)
      ) {

        recommendationList =
          data.recommendations;

      }


      setJobs(recommendationList);

    } catch (err) {

      console.error(
        "Failed to load recommendations:",
        err
      );


      if (
        err.response?.status === 401
      ) {

        localStorage.removeItem("token");

        navigate("/login");

        return;
      }


      setError(
        "Unable to load recommended jobs. Please try again."
      );

    } finally {

      setLoading(false);

    }
  };


  // ============================================================
  // LOAD RECOMMENDATIONS
  // ============================================================

  useEffect(() => {

    fetchRecommendations();
    fetchAppliedJobs();

  }, []);


  // ============================================================
  // GENERIC LOCATION / POSITION FILTERS
  // ============================================================

  // Keep filters generic instead of exposing every raw database value.
  const locations = [
    { label: "Chennai", terms: ["chennai"] },
    { label: "Coimbatore", terms: ["coimbatore"] },
    { label: "Bangalore", terms: ["bangalore", "bengaluru"] },
    { label: "Hyderabad", terms: ["hyderabad"] },
    { label: "Mumbai", terms: ["mumbai"] },
    { label: "Delhi / NCR", terms: ["delhi", "noida", "gurugram", "gurgaon"] },
    { label: "India", terms: ["india"] },
    { label: "United States", terms: ["united states", "usa", " us "] },
    { label: "Texas", terms: ["texas"] },
    { label: "California", terms: ["california"] },
    { label: "New York", terms: ["new york"] },
    { label: "United Kingdom", terms: ["united kingdom", "uk"] },
    { label: "Singapore", terms: ["singapore"] },
    { label: "Remote", terms: ["remote", "work from home", "anywhere"] },
  ];

  const positions = [
    { label: "Developer", terms: ["developer", "development"] },
    { label: "Software Engineer", terms: ["software engineer", "software engineering"] },
    { label: "Frontend Developer", terms: ["frontend", "front-end"] },
    { label: "Backend Developer", terms: ["backend", "back-end"] },
    { label: "Full Stack Developer", terms: ["full stack", "full-stack"] },
    { label: "Java Developer", terms: ["java developer", "java engineer"] },
    { label: "React Developer", terms: ["react developer", "react engineer"] },
    { label: "ML Intern", terms: ["ml intern", "machine learning intern", "machine learning internship"] },
    { label: "Machine Learning Engineer", terms: ["machine learning engineer", "ml engineer"] },
    { label: "AI Engineer", terms: ["ai engineer", "artificial intelligence engineer"] },
    { label: "Data Scientist", terms: ["data scientist", "data science"] },
    { label: "Data Engineer", terms: ["data engineer", "data engineering"] },
    { label: "DevOps Engineer", terms: ["devops", "devops engineer"] },
    { label: "Intern", terms: ["intern", "internship", "trainee"] },
  ];

  // ============================================================
  // FILTER RECOMMENDATIONS
  // ============================================================

  const displayedJobs = useMemo(() => {

    // Show only strong matches and hide jobs the user already applied to.

    let filteredJobs = jobs.filter(
      (job) => {

        const jobId =
          job.jobId ??
          job.job_id;

        if (!jobId) {
          return false;
        }

        const alreadyApplied =
          appliedJobIds.includes(
            String(jobId)
          );

        return !alreadyApplied && getScore(job) > 50;
      }
    );


    // ----------------------------------------------------------
    // LOCATION FILTER
    // ----------------------------------------------------------

    if (
      locationFilter !== "ALL"
    ) {

      filteredJobs =
        filteredJobs.filter(
          (job) => {

            if (
              !isAvailable(
                job.location
              )
            ) {
              return false;
            }


            const selected = locations.find(
              (item) => item.label === locationFilter
            );

            if (!selected) return true;

            const location = String(job.location).toLowerCase();

            return selected.terms.some((term) =>
              location.includes(term)
            );

          }
        );
    }


    // ----------------------------------------------------------
    // POSITION FILTER
    // ----------------------------------------------------------

    if (
      positionFilter !== "ALL"
    ) {

      filteredJobs =
        filteredJobs.filter(
          (job) => {

            const title =
              job.title ??
              job.jobTitle;


            if (
              !isAvailable(title)
            ) {
              return false;
            }


            const selected = positions.find(
              (item) => item.label === positionFilter
            );

            if (!selected) return true;

            const normalizedTitle = String(title).toLowerCase();

            return selected.terms.some((term) =>
              normalizedTitle.includes(term)
            );

          }
        );
    }


    return filteredJobs;

  }, [
    jobs,
    getScore,
    appliedJobIds,
    locationFilter,
    positionFilter,
  ]);


  // ============================================================
  // REVIEW & APPLY
  // ============================================================

  const handleApply = (job) => {

    /*
     * Store the selected job so the
     * Review & Apply page can access it.
     */

    localStorage.setItem(
      "selectedJob",
      JSON.stringify(job)
    );


    navigate("/review-job");
  };

  const handleLogout = () => {
    ["token", "user", "userId", "selectedJob"].forEach((key) =>
      localStorage.removeItem(key)
    );
    clearProfile();
    navigate("/login", { replace: true });
  };


  // ============================================================
  // RENDER
  // ============================================================

  return (

    <div className="home-page">


      {/* ======================================================
          NAVBAR
      ====================================================== */}

      <nav className="home-navbar">

        <div className="home-brand">

          <span className="brand-icon">
            AI
          </span>

          <span>
            AI Job Navigator
          </span>

        </div>


        <div className="navbar-actions">


          {/* ACCOUNT */}

          <button
            className="account-button"
            onClick={() =>
              navigate(
                "/account-details"
              )
            }
            title="Account"
          >
            👤
          </button>


          {/* MENU */}

          <button
            className="menu-button"
            onClick={() =>
              setMenuOpen(
                (previous) =>
                  !previous
              )
            }
            title="Menu"
          >
            ☰
          </button>

        </div>

      </nav>


      {/* ======================================================
          SIDE MENU
      ====================================================== */}

      {menuOpen && (

        <div className="side-menu">


          <button
            onClick={() =>
              navigate(
                "/applied-jobs"
              )
            }
          >
            Applied Jobs
          </button>


          <button
            onClick={() =>
              navigate(
                "/skill-recommendation"
              )
            }
          >
            Skill Recommendation
          </button>


          <button
            onClick={() =>
              navigate(
                "/pending-approvals"
              )
            }
          >
            Pending Approvals
          </button>

          <button
            onClick={() => {
              setMenuOpen(false);
              navigate("/college-mailing");
            }}
          >
            College Mailing
          </button>

          <button className="logout-menu-button" onClick={handleLogout}>
            Log out
          </button>


        </div>

      )}


      {/* ======================================================
          MAIN CONTENT
      ====================================================== */}

      <main className="home-content">

        {location.state?.appliedNotice && (
          <div className="application-notice" role="status">
            <span className="application-notice-icon">✓</span>
            <span>{location.state.appliedNotice}</span>
          </div>
        )}


        {/* ====================================================
            HERO
        ==================================================== */}

        <section className="hero-section">

          <div className="hero-badge">
            YOUR NEXT MOVE STARTS HERE
          </div>


          <h1>
            Find work that moves you forward.
          </h1>


          <p>
            Explore roles matched to your skills and experience, then take your next step with a profile and application tools built around you.
          </p>

        </section>


        {/* ====================================================
            LOADING
        ==================================================== */}

        {loading && (

          <div className="state-card">

            <div className="loader"></div>


            <h3>
              Finding your best opportunities...
            </h3>


            <p>
              Matching your profile with
              available jobs.
            </p>

          </div>

        )}


        {/* ====================================================
            ERROR
        ==================================================== */}

        {!loading && error && (

          <div className="state-card error-state">


            <div className="state-icon">
              ⚠️
            </div>


            <h3>
              Unable to load recommendations
            </h3>


            <p>
              {error}
            </p>


            <button
              className="retry-button"
              onClick={() => {
                fetchRecommendations();
                fetchAppliedJobs();
              }}
            >
              Retry
            </button>


          </div>

        )}


        {/* ====================================================
            CONTENT
        ==================================================== */}

        {!loading &&
          !error && (

            <>

              {/* =================================================
                  FILTER PANEL
              ================================================= */}

              <section className="filter-panel">


                <div className="filter-heading">


                  <div>

                    <h2>
                      Recommended Jobs
                    </h2>


                    <p>Ranked by how well each job matches your profile.</p>

                  </div>


                  <div className="result-count">

                    {displayedJobs.length}

                    {" "}

                    {displayedJobs.length === 1
                      ? "Job"
                      : "Jobs"}

                  </div>


                </div>


                {/* FILTERS */}

                <div className="filters">


                  {/* LOCATION */}

                  <div className="filter-group">

                    <label>
                      Location
                    </label>


                    <select
                      value={
                        locationFilter
                      }
                      onChange={(event) =>
                        setLocationFilter(
                          event.target.value
                        )
                      }
                    >

                      <option value="ALL">
                        All Locations
                      </option>


                      {locations.map((location) => (
                        <option
                          key={location.label}
                          value={location.label}
                        >
                          {location.label}
                        </option>
                      ))}

                    </select>

                  </div>


                  {/* POSITION */}

                  <div className="filter-group">

                    <label>
                      Desired Position
                    </label>


                    <select
                      value={
                        positionFilter
                      }
                      onChange={(event) =>
                        setPositionFilter(
                          event.target.value
                        )
                      }
                    >

                      <option value="ALL">
                        All Positions
                      </option>


                      {positions.map((position) => (
                        <option
                          key={position.label}
                          value={position.label}
                        >
                          {position.label}
                        </option>
                      ))}

                    </select>

                  </div>


                </div>

              </section>


              {/* =================================================
                  NO MATCHING JOBS
              ================================================= */}

              {displayedJobs.length === 0 && (

                <div className="state-card">


                  <div className="state-icon">
                    🔎
                  </div>


                  <h3>
                    {jobs.length === 0 ? "No jobs available right now" : "No jobs above 50% match"}
                  </h3>


                  <p>
                    {jobs.length === 0
                      ? "There are no valid job listings in the database yet. Please check again after new jobs are added."
                      : "Try changing your location or position filters. Only recommendations above 50% are shown."}
                  </p>


                  {jobs.length > 0 && (locationFilter !== "ALL" || positionFilter !== "ALL") && (
                    <button
                      className="clear-filter-button"
                      onClick={() => {
                        setLocationFilter("ALL");
                        setPositionFilter("ALL");
                      }}
                    >
                      Clear Filters
                    </button>
                  )}


                </div>

              )}


              {/* =================================================
                  JOB GRID
              ================================================= */}

              {displayedJobs.length > 0 && (

                <section className="jobs-grid">


                  {displayedJobs.map(
                    (job, index) => {


                      const jobId =
                        job.jobId ??
                        job.job_id ??
                        `job-${index}`;


                      const title =
                        job.title ??
                        job.jobTitle;


                      const company =
                        job.company ??
                        job.companyName;


                      const location =
                        job.location;


                      const salary =
                        job.salary;


                      return (

                        <article
                          className="job-card"
                          key={jobId}
                        >


                          {/* =========================
                              TITLE
                          ========================= */}

                          <h3 className="job-title">

                            {notAvailable(
                              title
                            )}

                          </h3>


                          {/* =========================
                              COMPANY
                          ========================= */}

                          <p className="job-company">

                            {notAvailable(
                              company
                            )}

                          </p>


                          {/* =========================
                              DETAILS
                          ========================= */}

                          <div className="job-details">


                            {/* LOCATION */}

                            <div className="job-detail">

                              <span className="detail-icon">
                                📍
                              </span>


                              <div>

                                <span className="detail-label">
                                  Location
                                </span>


                                <span>
                                  {notAvailable(
                                    location
                                  )}
                                </span>

                              </div>

                            </div>


                            {/* SALARY */}

                            <div className="job-detail">

                              <span className="detail-icon">
                                💰
                              </span>


                              <div>

                                <span className="detail-label">
                                  Salary
                                </span>


                                <span>
                                  {notAvailable(
                                    salary
                                  )}
                                </span>

                              </div>

                            </div>


                          </div>


                          {/* =================================================
                              SINGLE ACTION
                          ================================================= */}

                          <div className="job-actions">

                            <button
                              className="review-apply-button"
                              onClick={() =>
                                handleApply(
                                  job
                                )
                              }
                            >
                              Review & Apply
                            </button>

                          </div>


                        </article>

                      );

                    }
                  )}


                </section>

              )}

            </>

          )}


      </main>


      {/* ======================================================
          FOOTER
      ====================================================== */}

      <footer className="home-footer">

        © 2026 AI Job Navigator.
        All rights reserved.

      </footer>


    </div>
  );
}


export default Home;
