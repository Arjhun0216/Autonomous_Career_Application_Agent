import { API_BASE_URL } from "../apiBase.js";
import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import axios from "axios";
import "./CollegeMailing.css";

const API = API_BASE_URL + "/api";

export default function CollegeMailing() {
  const navigate = useNavigate();
  const token = localStorage.getItem("token");
  const [draft, setDraft] = useState(null);
  const [contacts, setContacts] = useState([]);
  const [file, setFile] = useState(null);
  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState(false);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [sendResult, setSendResult] = useState(null);

  const headers = { Authorization: `Bearer ${token}` };

  const loadPage = useCallback(async () => {
    if (!token) {
      navigate("/login", { replace: true });
      return;
    }
    setLoading(true);
    setError("");
    try {
      const [draftResponse, contactsResponse] = await Promise.all([
        axios.get(`${API}/college-mail/draft`, { headers }),
        axios.get(`${API}/hr`, { headers }),
      ]);
      setDraft(draftResponse.data);
      setContacts(Array.isArray(contactsResponse.data) ? contactsResponse.data : []);
    } catch (requestError) {
      if (requestError.response?.status === 401) {
        localStorage.removeItem("token");
        navigate("/login", { replace: true });
        return;
      }
      setError(requestError.response?.data?.message || "Could not load College Mailing.");
    } finally {
      setLoading(false);
    }
  }, [navigate, token]);

  useEffect(() => {
    loadPage();
  }, [loadPage]);

  const uploadBrochure = async (event) => {
    event.preventDefault();
    if (!file) {
      setError("Choose the college brochure PDF first.");
      return;
    }
    setUploading(true);
    setError("");
    setNotice("");
    try {
      const formData = new FormData();
      formData.append("file", file);
      await axios.post(`${API}/college-mail/brochure`, formData, {
        headers: { ...headers, "Content-Type": "multipart/form-data" },
      });
      setFile(null);
      event.target.reset();
      setNotice("College brochure uploaded and ready to attach.");
      await loadPage();
    } catch (uploadError) {
      setError(uploadError.response?.data?.message || "Brochure upload failed.");
    } finally {
      setUploading(false);
    }
  };

  const sendMail = async () => {
    setSending(true);
    setError("");
    setNotice("");
    setSendResult(null);
    try {
      const response = await axios.post(`${API}/college-mail/send`, null, { headers });
      setSendResult(response.data);
      if (response.data.failedCount === 0) {
        setNotice(`Email sent to all ${response.data.sentCount} HR contacts.`);
      } else {
        setNotice(`Sent ${response.data.sentCount} of ${response.data.recipientCount} emails.`);
      }
    } catch (sendError) {
      setError(sendError.response?.data?.message || "The email batch could not be sent.");
    } finally {
      setSending(false);
    }
  };

  const uniqueContactCount = new Set(
    contacts.map((contact) => contact.mailId?.trim().toLowerCase()).filter(Boolean)
  ).size;

  if (loading) {
    return <main className="college-mail-page"><p className="college-mail-loading">Loading College Mailing…</p></main>;
  }

  return (
    <main className="college-mail-page">
      <header className="college-mail-navbar">
        <button className="college-mail-back" onClick={() => navigate("/home")}>← Home</button>
        <div className="college-mail-brand"><span className="college-mail-brand-icon">CM</span> College Mailing</div>
        <button className="college-mail-home" onClick={() => navigate("/home")}>Home</button>
      </header>

      <section className="college-mail-content">
        <div className="college-mail-heading">
          <div>
            <span className="college-mail-eyebrow">CAREER DEVELOPMENT CELL</span>
            <h1>College Mailing</h1>
            <p>Review the fixed college introduction, upload the official brochure, and send it to the HR contacts.</p>
          </div>
          <div className="college-mail-count">
            <strong>{contacts.length}</strong>
            <span>HR records · {uniqueContactCount} unique email recipients</span>
          </div>
        </div>

        {error && <div className="college-mail-alert college-mail-alert-error" role="alert">{error}</div>}
        {notice && <div className="college-mail-alert college-mail-alert-success" role="status">{notice}</div>}

        <section className="college-mail-card">
          <div className="college-mail-card-heading">
            <div><span className="college-mail-step">01</span><h2>College brochure</h2></div>
            <span className={`college-mail-status ${draft?.brochureUploaded ? "is-ready" : "is-missing"}`}>
              {draft?.brochureUploaded ? "Ready" : "Upload required"}
            </span>
          </div>
          <p className="college-mail-card-copy">
            Upload the approved college brochure PDF. The project stores it as <code>{draft?.brochureName || "college-brochure.pdf"}</code> and attaches it to each message.
          </p>
          <form className="college-mail-upload" onSubmit={uploadBrochure}>
            <label className="college-mail-file-label">
              <span>{file?.name || "Choose a PDF brochure"}</span>
              <input
                type="file"
                accept="application/pdf,.pdf"
                onChange={(event) => setFile(event.target.files?.[0] || null)}
              />
            </label>
            <button className="college-mail-secondary-button" disabled={!file || uploading}>
              {uploading ? "Uploading…" : draft?.brochureUploaded ? "Replace brochure" : "Upload brochure"}
            </button>
          </form>
          <small>PDF only, up to 15 MB. This replaces the project brochure used for future messages.</small>
        </section>

        <section className="college-mail-card">
          <div className="college-mail-card-heading">
            <div><span className="college-mail-step">02</span><h2>Fixed email draft</h2></div>
            <span className="college-mail-status is-fixed">Stored in code</span>
          </div>
          <label className="college-mail-field-label" htmlFor="college-mail-subject">Subject</label>
          <div id="college-mail-subject" className="college-mail-subject">{draft?.subject}</div>
          <label className="college-mail-field-label" htmlFor="college-mail-body">Message</label>
          <pre id="college-mail-body" className="college-mail-body">{draft?.body}</pre>
        </section>

        <section className="college-mail-card">
          <div className="college-mail-card-heading">
            <div><span className="college-mail-step">03</span><h2>Recipients and sending account</h2></div>
            <span className={`college-mail-status ${draft?.gmailConnected ? "is-ready" : "is-missing"}`}>
              {draft?.gmailConnected ? "Gmail connected" : "Gmail connection required"}
            </span>
          </div>
          {draft?.gmailConnected ? (
            <p className="college-mail-card-copy">Sending from <strong>{draft.gmailEmail}</strong>. Each HR contact receives an individual email.</p>
          ) : (
            <div className="college-mail-connect">
              <p>Connect the Gmail account that should send these messages.</p>
              <button className="college-mail-secondary-button" onClick={() => navigate("/pending-approvals")}>
                Open Gmail connection
              </button>
            </div>
          )}
          {contacts.length ? (
            <div className="college-mail-contact-list">
              {contacts.map((contact) => (
                <div className="college-mail-contact" key={contact.hrId || `${contact.mailId}-${contact.companyName}`}>
                  <span>{contact.mailId}</span><small>{contact.companyName}</small>
                </div>
              ))}
            </div>
          ) : (
            <p className="college-mail-empty">No HR contacts are saved yet. Add contacts to the HR contacts table before sending.</p>
          )}
          <button
            className="college-mail-send-button"
            onClick={sendMail}
            disabled={sending || !draft?.brochureUploaded || !draft?.gmailConnected || uniqueContactCount === 0}
          >
            {sending ? "Sending emails…" : `Send to all ${uniqueContactCount} unique email addresses`}
          </button>
          <small className="college-mail-send-note">One click sends one separate message per unique email address, with the brochure attached. Failed addresses are reported below.</small>
        </section>

        {sendResult?.failures?.length > 0 && (
          <section className="college-mail-card college-mail-failures">
            <h2>Messages that could not be sent</h2>
            {sendResult.failures.map((failure) => (
              <p key={failure.email}><strong>{failure.email}</strong><span>{failure.reason}</span></p>
            ))}
          </section>
        )}
      </section>
    </main>
  );
}
