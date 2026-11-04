import { API_BASE_URL } from "../apiBase.js";
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import axios from "axios";
import "./AppliedJobs.css";

function AppliedJobs() {

  const navigate = useNavigate();

  const [applications, setApplications] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [retryingId, setRetryingId] = useState(null);

  const token = localStorage.getItem("token");


  // ============================================================
  // STATUS
  // ============================================================

  const getStatusLabel = (status) => {

    switch (status) {

      case "CREATED":
        return "Created";

      case "PROCESSING":
        return "Processing";

      case "GENERATING":
        return "Generating";

      case "READY_FOR_APPROVAL":
        return "Pending Approval";

      case "APPROVED":
        return "Approved";

      case "APPLYING":
        return "Applying";

      case "APPLIED":
        return "Applied";

      case "APPLICATION_PAGE_READY":
        return "Zoho Form Opened";

      case "FAILED":
        return "Unable to Apply";

      case "MANUAL_ACTION_REQUIRED":
        return "Manual Action Needed";

      case "USER_INPUT_REQUIRED":
        return "Waiting for Your Answers";

      case "EMAIL_FAILED":
        return "Email Failed — Application Not Started";

      case "CANCELLED":
        return "Cancelled";

      default:
        return status || "Unknown";
    }
  };


  const getStatusClass = (status) => {

    switch (status) {

      case "READY_FOR_APPROVAL":
      case "USER_INPUT_REQUIRED":
        return "status-pending";

      case "APPROVED":
        return "status-approved";

      case "APPLIED":
        return "status-applied";

      case "FAILED":
      case "EMAIL_FAILED":
      case "MANUAL_ACTION_REQUIRED":
        return "status-failed";

      case "CANCELLED":
        return "status-cancelled";

      case "PROCESSING":
      case "GENERATING":
      case "APPLYING":
      case "APPLICATION_PAGE_READY":
        return "status-processing";

      default:
        return "status-created";
    }
  };


  // ============================================================
  // FETCH APPLICATIONS
  // ============================================================

  const fetchApplications = async () => {

    if (!token) {

      navigate("/login");

      return;
    }

    try {

      setLoading(true);
      setError("");

      const response = await axios.get(
        API_BASE_URL + "/api/applications",
        {
          headers: {
            Authorization: `Bearer ${token}`
          }
        }
      );

      const data = Array.isArray(response.data)
        ? response.data
        : [];

      setApplications(data);

    } catch (err) {

      console.error(
        "Failed to load applications:",
        err
      );

      if (err.response?.status === 401) {

        localStorage.removeItem("token");

        navigate("/login");

        return;
      }

      setError(
        "Unable to load your applications."
      );

    } finally {

      setLoading(false);

    }
  };


  const viewResume = async (applicationId) => {

    try {

      const response = await axios.get(
        `${API_BASE_URL}/api/applications/${applicationId}/resume`,
        {
          headers: {
            Authorization: `Bearer ${token}`
          },
          responseType: "blob"
        }
      );

      const url = window.URL.createObjectURL(
        new Blob([response.data], { type: "application/pdf" })
      );

      window.open(
        url,
        "_blank",
        "noopener,noreferrer"
      );

      setTimeout(() => {
        window.URL.revokeObjectURL(url);
      }, 60000);

    } catch (err) {

      console.error("Failed to open resume:", err);

      if (err.response?.status === 401) {
        localStorage.removeItem("token");
        navigate("/login");
        return;
      }

      alert("Unable to open the generated resume.");
    }
  };

  const retryApplication = async (applicationId) => {
    try {
      setRetryingId(applicationId);
      const response = await axios.post(
        `${API_BASE_URL}/api/applications/${applicationId}/reapply`,
        {},
        { headers: { Authorization: `Bearer ${token}` } }
      );
      alert(response.data?.message || `Application status: ${response.data?.status || "unknown"}`);
      await fetchApplications();
    } catch (err) {
      alert(err.response?.data || "Unable to retry the job application.");
    } finally {
      setRetryingId(null);
    }
  };


  useEffect(() => {

    fetchApplications();

  }, []);


  // ============================================================
  // RENDER
  // ============================================================

  return (

    <div className="applied-jobs-page">

      {/* ======================================================
          NAVBAR
      ====================================================== */}

      <nav className="applications-navbar">

        <div className="applications-brand">

          <button
            className="back-button"
            onClick={() => navigate("/home")}
          >
            ←
          </button>

          <div>

            <div className="applications-title">
              Applied Jobs
            </div>

            <div className="applications-subtitle">
              Track your job applications
            </div>

          </div>

        </div>


        <button
          className="home-button"
          onClick={() => navigate("/home")}
        >
          Home
        </button>

      </nav>


      {/* ======================================================
          CONTENT
      ====================================================== */}

      <main className="applications-content">

        <div className="applications-header">

          <div>

            <h1>
              Your Applications
            </h1>

            <p>
              Track the progress of jobs you have applied for.
            </p>

          </div>


          <div className="application-count">

            {applications.length}

            <span>
              {applications.length === 1
                ? " Application"
                : " Applications"}
            </span>

          </div>

        </div>


        {/* ==================================================
            LOADING
        ================================================== */}

        {loading && (

          <div className="application-state">

            <div className="application-loader"></div>

            <h3>
              Loading applications...
            </h3>

            <p>
              Fetching your application history.
            </p>

          </div>

        )}


        {/* ==================================================
            ERROR
        ================================================== */}

        {!loading && error && (

          <div className="application-state error">

            <div className="state-icon">
              ⚠️
            </div>

            <h3>
              Something went wrong
            </h3>

            <p>
              {error}
            </p>

            <button
              className="retry-button"
              onClick={fetchApplications}
            >
              Try Again
            </button>

          </div>

        )}


        {/* ==================================================
            EMPTY
        ================================================== */}

        {!loading &&
          !error &&
          applications.length === 0 && (

            <div className="application-state">

              <div className="empty-icon">
                📭
              </div>

              <h3>
                No applications yet
              </h3>

              <p>
                When you apply for a job, it will appear here.
              </p>

              <button
                className="browse-button"
                onClick={() => navigate("/home")}
              >
                Browse Jobs
              </button>

            </div>

          )}


        {/* ==================================================
            APPLICATION LIST
        ================================================== */}

        {!loading &&
          !error &&
          applications.length > 0 && (

            <div className="applications-list">

              {applications.map((application) => (

                <div
                  className="application-card"
                  key={application.applicationId}
                >

                  {/* TOP */}

                  <div className="application-card-top">

                    <div>

                      <h2>
                        {application.jobTitle ||
                          "Job Application"}
                      </h2>

                      <p className="company-name">
                        {application.companyName ||
                          "Company not available"}
                      </p>

                    </div>


                    <div
                      className={`application-status ${getStatusClass(
                        application.status
                      )}`}
                    >
                      {getStatusLabel(
                        application.status
                      )}
                    </div>

                  </div>

                  {application.status === "APPLICATION_PAGE_READY" && (
                    <p className="application-failure-message">
                      The application form is open. Check the current application status for the latest automation result.
                    </p>
                  )}


                  {/* DETAILS */}

                  <div className="application-details">

                    <div className="application-detail">

                      <span className="detail-label">
                        Application ID
                      </span>

                      <span className="detail-value">
                        #{application.applicationId}
                      </span>

                    </div>


                    <div className="application-detail">

                      <span className="detail-label">
                        Job ID
                      </span>

                      <span className="detail-value">
                        {application.jobId}
                      </span>

                    </div>


                    {application.hrMailId && (

                      <div className="application-detail">

                        <span className="detail-label">
                          Recruiter
                        </span>

                        <span className="detail-value">
                          {application.hrMailId}
                        </span>

                      </div>

                    )}


                    {application.createdAt && (

                      <div className="application-detail">

                        <span className="detail-label">
                          Created
                        </span>

                        <span className="detail-value">
                          {new Date(
                            application.createdAt
                          ).toLocaleDateString()}
                        </span>

                      </div>

                    )}

                  </div>


                  {/* ACTIONS */}

                  <div className="application-actions">

                    {application.status ===
                      "READY_FOR_APPROVAL" && (

                        <button
                          className="review-button"
                          onClick={() =>
                            navigate(
                              "/pending-approvals"
                            )
                          }
                        >
                          Review & Approve
                        </button>

                    )}

                    {application.status === "USER_INPUT_REQUIRED" && (
                      <button
                        className="review-button"
                        onClick={() => navigate("/pending-approvals")}
                      >
                        Answer Pending Questions
                      </button>
                    )}


                    {["FAILED", "EMAIL_FAILED", "MANUAL_ACTION_REQUIRED", "USER_INPUT_REQUIRED"].includes(application.status) && (

                      <div className="application-failure-message">
                        <strong>{application.status === "USER_INPUT_REQUIRED" ? "Your answer is needed" : "Automation could not finish"}</strong>
                        <p>
                          {application.errorMessage ||
                            "We could not complete this application automatically. Please review it or apply using the job link."}
                        </p>
                      </div>

                    )}

                    {application.status === "EMAIL_FAILED" && (
                      <button
                        className="review-button"
                        disabled={retryingId !== null}
                        onClick={() => retryApplication(application.applicationId)}
                      >
                        {retryingId === application.applicationId ? "Starting..." : "Retry Zoho Application"}
                      </button>
                    )}

                    {application.resumePath && (

                        <button
                          className="review-button"
                          onClick={() =>
                            viewResume(
                              application.applicationId
                            )
                          }
                        >
                          View Resume
                        </button>

                    )}

                    {application.jobUrl && (

                      <button
                        className="job-link-button"
                        onClick={() =>
                          window.open(
                            application.jobUrl,
                            "_blank",
                            "noopener,noreferrer"
                          )
                        }
                      >
                        Apply Manually ↗
                      </button>

                    )}

                  </div>

                </div>

              ))}

            </div>

          )}

      </main>

    </div>
  );
}

export default AppliedJobs;
