import { API_BASE_URL } from "../apiBase.js";
import { useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import axios from "axios";

import "./PendingApprovals.css";

function PendingApprovals() {

    const navigate = useNavigate();

    const [searchParams] =
        useSearchParams();

    const [applications, setApplications] =
        useState([]);

    const [loading, setLoading] =
        useState(true);

    const [error, setError] =
        useState("");

    const [sendingId, setSendingId] =
        useState(null);

    const [gmailConnected, setGmailConnected] =
        useState(false);

    const [gmailEmail, setGmailEmail] =
        useState("");

    const [questions, setQuestions] = useState({});
    const [questionAnswers, setQuestionAnswers] = useState({});
    const [reapplyingId, setReapplyingId] = useState(null);

    const token =
        localStorage.getItem("token");


    /*
     * ============================================================
     * FETCH PENDING APPLICATIONS
     * ============================================================
     */

    const fetchPendingApplications =
        async () => {

            if (!token) {

                navigate("/login");

                return;
            }

            try {

                setLoading(true);

                setError("");

                const response =
                    await axios.get(
                        API_BASE_URL + "/api/applications",
                        {
                            headers: {
                                Authorization:
                                    `Bearer ${token}`
                            }
                        }
                    );

                const allApplications =
                    Array.isArray(response.data)
                        ? response.data
                        : [];

                const pendingApplications =
                    allApplications.filter(
                        (application) =>
                            application.status ===
                            "READY_FOR_APPROVAL" ||
                            application.status ===
                            "USER_INPUT_REQUIRED"
                    );

                setApplications(
                    pendingApplications
                );

                const questionMap = {};
                for (const application of pendingApplications) {
                    if (application.status === "USER_INPUT_REQUIRED") {
                        try {
                            const qResponse = await axios.get(
                                `${API_BASE_URL}/api/applications/${application.applicationId}/questions`,
                                { headers: { Authorization: `Bearer ${token}` } }
                            );
                            questionMap[application.applicationId] = qResponse.data || [];
                        } catch (questionError) {
                            console.error("Failed to load application questions:", questionError);
                            questionMap[application.applicationId] = [];
                        }
                    }
                }
                setQuestions(questionMap);

            } catch (err) {

                console.error(
                    "Failed to load pending applications:",
                    err
                );

                if (
                    err.response?.status === 401 ||
                    err.response?.status === 403
                ) {

                    localStorage.removeItem(
                        "token"
                    );

                    navigate("/login");

                    return;
                }

                setError(
                    "Unable to load pending applications."
                );

            } finally {

                setLoading(false);
            }
        };


    /*
     * ============================================================
     * CHECK GMAIL STATUS
     * ============================================================
     */

    const checkGmailStatus =
        async () => {

            if (!token) {
                return;
            }

            try {

                const response =
                    await axios.get(
                        API_BASE_URL + "/api/gmail/status",
                        {
                            headers: {
                                Authorization:
                                    `Bearer ${token}`
                            }
                        }
                    );

                setGmailConnected(
                    response.data?.connected === true
                );

                setGmailEmail(
                    response.data?.email || ""
                );

            } catch (err) {

                console.error(
                    "Failed to check Gmail status:",
                    err
                );

                setGmailConnected(false);

                setGmailEmail("");
            }
        };


    /*
     * ============================================================
     * INITIAL LOAD
     * ============================================================
     */

    useEffect(() => {

        fetchPendingApplications();

        checkGmailStatus();

    }, []);


    /*
     * ============================================================
     * HANDLE GMAIL OAUTH RESULT
     * ============================================================
     */

    useEffect(() => {

        const gmailStatus =
            searchParams.get("gmail");

        const email =
            searchParams.get("email");

        if (
            gmailStatus === "connected"
        ) {

            setGmailConnected(true);

            if (email) {
                setGmailEmail(email);
            }

            /*
             * Remove OAuth query parameters
             * from the browser URL.
             */
            window.history.replaceState(
                {},
                document.title,
                "/pending-approvals"
            );

            /*
             * Refresh actual Gmail status
             * from backend.
             */
            checkGmailStatus();
        }

        if (
            gmailStatus === "error"
        ) {

            setError(
                "Gmail connection failed. Please try again."
            );

            window.history.replaceState(
                {},
                document.title,
                "/pending-approvals"
            );
        }

    }, [searchParams]);


    /*
     * ============================================================
     * GET RESUME FILE NAME
     * ============================================================
     */

    const getResumeFileName =
        (path) => {

            if (!path) {

                return "Resume generated";
            }

            return path
                .split("\\")
                .pop()
                .split("/")
                .pop();
        };


    /*
     * ============================================================
     * VIEW RESUME
     * ============================================================
     */

    const handleViewResume =
        async (applicationId) => {

            try {

                const response =
                    await axios.get(
                        `${API_BASE_URL}/api/applications/${applicationId}/resume`,
                        {
                            headers: {
                                Authorization:
                                    `Bearer ${token}`
                            },
                            responseType:
                                "blob"
                        }
                    );

                const pdfBlob =
                    new Blob(
                        [response.data],
                        {
                            type:
                                "application/pdf"
                        }
                    );

                const pdfUrl =
                    URL.createObjectURL(
                        pdfBlob
                    );

                window.open(
                    pdfUrl,
                    "_blank"
                );

                /*
                 * Give the browser time to open
                 * the PDF before releasing the URL.
                 */
                setTimeout(() => {

                    URL.revokeObjectURL(
                        pdfUrl
                    );

                }, 60000);

            } catch (err) {

                console.error(
                    "Failed to open resume:",
                    err
                );

                if (
                    err.response?.status === 401 ||
                    err.response?.status === 403
                ) {

                    alert(
                        "You are not authorized to view this resume."
                    );

                    return;
                }

                alert(
                    "Unable to open the resume. Please try again."
                );
            }
        };


    /*
     * ============================================================
     * CONNECT GMAIL
     * ============================================================
     */

    const handleConnectGmail =
        async () => {

            try {

                setError("");

                const response =
                    await axios.get(
                        API_BASE_URL + "/api/gmail/connect-url",
                        {
                            headers: {
                                Authorization:
                                    `Bearer ${token}`
                            }
                        }
                    );

                const url =
                    response.data?.url;

                if (!url) {

                    throw new Error(
                        "Gmail authorization URL was not returned."
                    );
                }

                /*
                 * Redirect browser to Google's
                 * OAuth consent page.
                 */
                window.location.href =
                    url;

            } catch (err) {

                console.error(
                    "Gmail connection failed:",
                    err
                );

                setError(
                    err.response?.data ||
                    "Unable to connect Gmail."
                );
            }
        };


    /*
     * ============================================================
     * APPROVE AND SEND
     * ============================================================
     */

    const handleApproveAndSend =
        async (applicationId) => {

            if (!token) {

                navigate("/login");

                return;
            }


            /*
             * Prevent duplicate clicks.
             */
            if (
                sendingId !== null
            ) {

                return;
            }


            try {

                setSendingId(
                    applicationId
                );

                setError("");


                const response =
                    await axios.post(
                        `${API_BASE_URL}/api/applications/${applicationId}/approve-and-send`,
                        {},
                        {
                            headers: {
                                Authorization:
                                    `Bearer ${token}`
                            }
                        }
                    );


                /*
                 * ------------------------------------------------
                 * 4. Success
                 * ------------------------------------------------
                 */

                console.log(
                    "Email sent:",
                    response.data
                );


                const result = response.data || {};
                const emailMessage = result.emailSent
                    ? "Cold email sent."
                    : `Cold email not sent: ${result.emailError || "Gmail needs to be reconnected."}`;
                const applicationMessage = result.applicationMessage || result.applicationStatus || "Automation finished.";
                alert(`Application status: ${result.applicationStatus || "unknown"}. ${applicationMessage}\n${emailMessage}`);


                /*
                 * Refresh pending list.
                 */
                await fetchPendingApplications();


            } catch (err) {

                console.error(
                    "Approve & Send failed:",
                    err
                );


                let message =
                    "Failed to send application email.";


                if (
                    typeof err.response?.data ===
                    "string"
                ) {

                    message =
                        err.response.data;

                } else if (
                    err.response?.data?.message
                ) {

                    message =
                        err.response.data.message;

                } else if (
                    err.message
                ) {

                    message =
                        err.message;
                }


                setError(message);

                alert(message);

            } finally {

                setSendingId(null);
            }
        };


    const getQuestionOptions = (question) => {
        try {
            const parsed = typeof question?.availableOptions === "string"
                ? JSON.parse(question.availableOptions)
                : question?.availableOptions;
            return Array.isArray(parsed) ? parsed.filter((option) => typeof option === "string" && option.trim()) : [];
        } catch {
            return [];
        }
    };

    const getHumanQuestion = (question) => {
        const text = String(question?.question || "").trim();
        if (isUnlabeledQuestion(question)) {
            return "Unlabeled required field";
        }
        return text;
    };

    const isUnlabeledQuestion = (question) => {
        const text = String(question?.question || "").trim();
        return !text || /^(none|null|undefined|n\/?a|question[_\- ]?\d+|rec[_\-]?form[_\-]?\d+|inputid)$/i.test(text);
    };

    const handleAnswerChange = (questionId, value) => {
        setQuestionAnswers((previous) => ({ ...previous, [questionId]: value }));
    };

    const handleSaveQuestion = async (applicationId, questionId) => {
        const answer = questionAnswers[questionId];
        if (!answer?.trim()) {
            alert("Please provide an answer.");
            return;
        }
        try {
            await axios.put(
                `${API_BASE_URL}/api/applications/${applicationId}/questions/${questionId}`,
                { answer },
                { headers: { Authorization: `Bearer ${token}` } }
            );
            const qResponse = await axios.get(
                `${API_BASE_URL}/api/applications/${applicationId}/questions`,
                { headers: { Authorization: `Bearer ${token}` } }
            );
            setQuestions((previous) => ({ ...previous, [applicationId]: qResponse.data || [] }));
        } catch (err) {
            alert(err.response?.data || "Unable to save answer.");
        }
    };

    const handleReapply = async (applicationId) => {
        try {
            setReapplyingId(applicationId);
            await axios.post(
                `${API_BASE_URL}/api/applications/${applicationId}/reapply`,
                {},
                { headers: { Authorization: `Bearer ${token}` } }
            );
            alert("Application automation resumed. It will stop again if another unknown detail is required.");
            await fetchPendingApplications();
        } catch (err) {
            alert(err.response?.data || "Unable to reapply.");
        } finally {
            setReapplyingId(null);
        }
    };

    /*
     * ============================================================
     * REJECT APPLICATION
     * ============================================================
     */

    const handleReject =
        (applicationId) => {

            /*
             * Actual reject endpoint will be
             * connected separately.
             */
            alert(
                "Reject functionality will be connected next."
            );

            console.log(
                "Reject application:",
                applicationId
            );
        };


    /*
     * ============================================================
     * RENDER
     * ============================================================
     */

    return (

        <div className="pending-page">


            {/* ==================================================
                NAVBAR
            ================================================== */}

            <nav className="pending-navbar">

                <div className="pending-navbar-left">

                    <button
                        className="pending-back-button"
                        onClick={() =>
                            navigate("/home")
                        }
                    >
                        ←
                    </button>


                    <div>

                        <div className="pending-page-title">
                            Pending Approvals
                        </div>

                        <div className="pending-page-subtitle">
                            Review applications before submission
                        </div>

                    </div>

                </div>


                <button
                    className="pending-home-button"
                    onClick={() =>
                        navigate("/home")
                    }
                >
                    Home
                </button>

            </nav>


            {/* ==================================================
                MAIN
            ================================================== */}

            <main className="pending-container">


                {/* ==================================================
                    HEADER
                ================================================== */}

                <div className="pending-header">

                    <div>

                        <div className="pending-label">
                            ACTION REQUIRED
                        </div>

                        <h1>
                            Pending Applications
                        </h1>

                        <p>
                            Review the generated resume and
                            cold email before approving the
                            application.
                        </p>

                    </div>


                    <div className="pending-counter">

                        <strong>
                            {applications.length}
                        </strong>

                        <span>
                            Pending
                        </span>

                    </div>

                </div>


                {/* ==================================================
                    GMAIL CONNECTION STATUS
                ================================================== */}

                {!loading &&
                    !error && (

                        <div
                            className={
                                gmailConnected
                                    ? "gmail-status gmail-connected"
                                    : "gmail-status gmail-not-connected"
                            }
                        >

                            <div className="gmail-status-left">

                                <div className="gmail-status-icon">
                                    ✉
                                </div>


                                <div>

                                    <strong>
                                        {gmailConnected
                                            ? "Gmail Connected"
                                            : "Gmail Not Connected"}
                                    </strong>


                                    <p>

                                        {gmailConnected
                                            ? `Emails will be sent from ${gmailEmail || "your connected Gmail account"}. If sending fails, reconnect Gmail here.`
                                            : "Connect Gmail to send your approved applications."}

                                    </p>
                                    {!gmailConnected && (
                                        <p className="gmail-test-user-note">
                                            Testing with Google OAuth? Add the account under Google Cloud Console → Google Auth Platform → Audience → Test users.
                                            <a href="https://console.cloud.google.com/auth/audience" target="_blank" rel="noreferrer"> Open settings ↗</a>
                                        </p>
                                    )}

                                </div>

                            </div>


                            <button
                                className="connect-gmail-button"
                                onClick={handleConnectGmail}
                            >
                                {gmailConnected ? "Reconnect Gmail" : "Connect Gmail"}
                            </button>

                        </div>

                    )}


                {/* ==================================================
                    ERROR
                ================================================== */}

                {!loading &&
                    error && (

                        <div className="pending-error-banner">

                            <span>
                                ⚠
                            </span>

                            <div>
                                {error}
                            </div>

                        </div>

                    )}


                {/* ==================================================
                    LOADING
                ================================================== */}

                {loading && (

                    <div className="pending-state">

                        <div className="pending-spinner"></div>

                        <h3>
                            Loading pending applications...
                        </h3>

                        <p>
                            Please wait while we fetch your
                            applications.
                        </p>

                    </div>

                )}


                {/* ==================================================
                    EMPTY
                ================================================== */}

                {!loading &&
                    applications.length === 0 &&
                    !error && (

                        <div className="pending-state">

                            <div className="pending-success-icon">
                                ✓
                            </div>

                            <h3>
                                No pending approvals
                            </h3>

                            <p>
                                You don't have any applications
                                waiting for approval.
                            </p>

                            <button
                                className="pending-browse-button"
                                onClick={() =>
                                    navigate("/home")
                                }
                            >
                                Browse Recommended Jobs
                            </button>

                        </div>

                    )}


                {/* ==================================================
                    APPLICATION LIST
                ================================================== */}

                {!loading &&
                    applications.length > 0 && (

                        <div className="pending-list">

                            {applications.map(
                                (application) => (

                                    <div
                                        className="pending-card"
                                        key={
                                            application.applicationId
                                        }
                                    >


                                        {/* =========================
                                            CARD HEADER
                                        ========================= */}

                                        <div className="pending-card-header">

                                            <div>

                                                <h2>
                                                    {application.jobTitle ||
                                                        "Job Application"}
                                                </h2>

                                                <div className="pending-company">
                                                    {application.companyName ||
                                                        "Company not available"}
                                                </div>

                                            </div>


                                            <div className="pending-status">
                                                Pending Approval
                                            </div>

                                        </div>


                                        {/* =========================
                                            APPLICATION INFO
                                        ========================= */}

                                        <div className="pending-info-grid">


                                            <div className="pending-info-item">

                                                <span>
                                                    APPLICATION ID
                                                </span>

                                                <strong>
                                                    #
                                                    {
                                                        application.applicationId
                                                    }
                                                </strong>

                                            </div>


                                            <div className="pending-info-item">

                                                <span>
                                                    JOB ID
                                                </span>

                                                <strong>
                                                    {
                                                        application.jobId
                                                    }
                                                </strong>

                                            </div>


                                            <div className="pending-info-item">

                                                <span>
                                                    HR / RECRUITER
                                                </span>

                                                <strong>

                                                    {application.hrMailId ||
                                                        "Not available"}

                                                </strong>

                                            </div>


                                            <div className="pending-info-item">

                                                <span>
                                                    READY AT
                                                </span>

                                                <strong>

                                                    {application.readyAt
                                                        ? new Date(
                                                            application.readyAt
                                                        ).toLocaleString()
                                                        : "Not available"}

                                                </strong>

                                            </div>

                                        </div>


                                        {/* =========================
                                            COLD EMAIL
                                        ========================= */}

                                        <section className="email-section">

                                            <div className="section-heading">

                                                <div className="section-icon">
                                                    ✉
                                                </div>


                                                <div>

                                                    <h3>
                                                        Generated Cold Email
                                                    </h3>

                                                    <p>
                                                        Review the message
                                                        before it is sent.
                                                    </p>

                                                </div>

                                            </div>


                                            <div className="email-content">


                                                <div className="email-field">

                                                    <span>
                                                        TO
                                                    </span>

                                                    <div className="email-subject">

                                                        {application.hrMailId ||
                                                            "Recruiter email not available"}

                                                    </div>

                                                </div>


                                                <div className="email-field">

                                                    <span>
                                                        SUBJECT
                                                    </span>

                                                    <div className="email-subject">

                                                        {application.coldMailSubject ||
                                                            "No subject generated"}

                                                    </div>

                                                </div>


                                                <div className="email-field">

                                                    <span>
                                                        MESSAGE
                                                    </span>

                                                    <div className="email-body">

                                                        {application.coldMailBody ||
                                                            "No email body generated"}

                                                    </div>

                                                </div>

                                            </div>

                                        </section>


                                        {application.status === "USER_INPUT_REQUIRED" && (
                                            <section className="resume-section">
                                                <div className="section-heading">
                                                    <div className="section-icon">?</div>
                                                    <div>
                                                        <h3>Additional Details Required</h3>
                                                        <p>Answer these questions. The application will not be submitted until they are confirmed.</p>
                                                    </div>
                                                </div>

                                                {(questions[application.applicationId] || []).filter((q) => q.status === "PENDING").map((question) => (
                                                    <div className="application-question-card" key={question.questionId}>
                                                        <div className="application-question-heading">
                                                            <span className="application-question-kicker">QUESTION</span>
                                                            <span className="application-question-required">Required</span>
                                                        </div>
                                                        <div className="application-question-title">{getHumanQuestion(question)}</div>
                                                        {isUnlabeledQuestion(question) && (
                                                            <p className="application-question-help">
                                                                Zoho marked this field as required, but its question text was not captured. The saved record cannot tell us what it asks, so please check the live form before answering.
                                                                {question.fieldId && <span className="application-question-field-id">Form field: {question.fieldId}</span>}
                                                            </p>
                                                        )}
                                                        {getQuestionOptions(question).length > 0 && (
                                                            <div className="application-question-options">
                                                                <strong>Available options:</strong>
                                                                <ul>{getQuestionOptions(question).map((option, index) => <li key={`${question.questionId}-option-${index}`}>{option}</li>)}</ul>
                                                            </div>
                                                        )}
                                                        <label className="application-question-answer-label" htmlFor={`answer-${question.questionId}`}>Your answer</label>
                                                        {getQuestionOptions(question).length > 0 ? (
                                                            <select
                                                                id={`answer-${question.questionId}`}
                                                                className="application-question-input"
                                                                value={questionAnswers[question.questionId] ?? ""}
                                                                onChange={(e) => handleAnswerChange(question.questionId, e.target.value)}
                                                            >
                                                                <option value="">Choose an answer</option>
                                                                {getQuestionOptions(question).map((option, index) => (
                                                                    <option key={`${question.questionId}-answer-${index}`} value={option}>{option}</option>
                                                                ))}
                                                            </select>
                                                        ) : (
                                                            <input
                                                                id={`answer-${question.questionId}`}
                                                                type={String(question.questionType || "").toLowerCase() === "date" ? "date" : "text"}
                                                                className="application-question-input"
                                                                value={questionAnswers[question.questionId] ?? ""}
                                                                onChange={(e) => handleAnswerChange(question.questionId, e.target.value)}
                                                                placeholder="Enter your answer"
                                                            />
                                                        )}
                                                        <div className="application-question-actions">
                                                            <button className="application-question-save" onClick={() => handleSaveQuestion(application.applicationId, question.questionId)}>Save Answer</button>
                                                        </div>
                                                    </div>
                                                ))}

                                                {(questions[application.applicationId] || []).filter((q) => q.status === "PENDING").length === 0 && (
                                                    <button
                                                        className="approve-button"
                                                        onClick={() => handleReapply(application.applicationId)}
                                                        disabled={reapplyingId !== null}
                                                    >
                                                        {reapplyingId === application.applicationId ? "Reapplying..." : "↻ Reapply with Answers"}
                                                    </button>
                                                )}
                                            </section>
                                        )}

                                        {/* =========================
                                            RESUME
                                        ========================= */}

                                        <section className="resume-section">


                                            <div className="section-heading">

                                                <div className="section-icon">
                                                    📄
                                                </div>


                                                <div>

                                                    <h3>
                                                        Generated Resume
                                                    </h3>

                                                    <p>
                                                        Review the personalized
                                                        resume before submitting
                                                        the application.
                                                    </p>

                                                </div>

                                            </div>


                                            <div className="resume-file">


                                                <div className="resume-file-icon">
                                                    PDF
                                                </div>


                                                <div className="resume-file-info">

                                                    <strong>
                                                        {
                                                            getResumeFileName(
                                                                application.resumePath
                                                            )
                                                        }
                                                    </strong>

                                                    <span>
                                                        Personalized resume
                                                        generated for this job
                                                    </span>

                                                </div>


                                                <button
                                                    className="view-resume-button"
                                                    onClick={() =>
                                                        handleViewResume(
                                                            application.applicationId
                                                        )
                                                    }
                                                >
                                                    👁 View Resume
                                                </button>

                                            </div>

                                        </section>


                                        {/* =========================
                                            ACTIONS
                                        ========================= */}

                                        <div className="pending-actions">


                                            <button
                                                className="reject-button"
                                                onClick={() =>
                                                    handleReject(
                                                        application.applicationId
                                                    )
                                                }
                                                disabled={
                                                    sendingId !== null
                                                }
                                            >
                                                Reject
                                            </button>


                                            {application.status === "READY_FOR_APPROVAL" && <button
                                                className="approve-button"
                                                onClick={() =>
                                                    handleApproveAndSend(
                                                        application.applicationId
                                                    )
                                                }
                                                disabled={
                                                    sendingId !== null
                                                }
                                            >

                                                {sendingId ===
                                                application.applicationId
                                                    ? "Sending..."
                                                    : "✓ Approve & Send"}

                                            </button>}

                                        </div>

                                    </div>

                                )
                            )}

                        </div>

                    )}

            </main>

        </div>
    );
}

export default PendingApprovals;
