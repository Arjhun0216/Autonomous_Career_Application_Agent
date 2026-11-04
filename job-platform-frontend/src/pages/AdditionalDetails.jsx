import { API_BASE_URL } from "../apiBase.js";
import { useState } from "react";
import { useNavigate } from "react-router-dom";
import axios from "axios";
import ProfileLayout from "../components/ProfileLayout";
import { useProfile } from "../context/ProfileContext";

function AdditionalDetails() {
  const navigate = useNavigate();

  const { profile, updateSection } = useProfile();

  const [data, setData] = useState(
    profile.additional || {
      certifications: [],
      achievements: [],
      leadership: [],
      publications: [],
      volunteering: [],
      languages: [],
      other: "",
      workAuthorization: "",
      workAuthorizationCountry: "",
      visaSponsorship: "",
      militaryStatus: "",
      governmentProjectExperience: "",
      governmentProjectDetails: "",
    }
  );

  const [certification, setCertification] = useState({
    name: "",
    issuer: "",
    year: "",
    credentialUrl: "",
  });

  const [achievement, setAchievement] = useState({
    title: "",
    organization: "",
    year: "",
    description: "",
  });

  const [saving, setSaving] = useState(false);
  const [languageInput, setLanguageInput] = useState("");
  const [formError, setFormError] = useState("");

  const addLanguages = (rawValue) => {
    const additions = rawValue.split(/[\n,;]+/).map((language) => language.trim()).filter(Boolean);
    if (!additions.length) return;
    setData((previous) => {
      const existing = previous.languages || [];
      const seen = new Set(existing.map((language) => language.toLocaleLowerCase()));
      return { ...previous, languages: [...existing, ...additions.filter((language) => {
        const key = language.toLocaleLowerCase();
        if (seen.has(key)) return false;
        seen.add(key);
        return true;
      })] };
    });
    setLanguageInput("");
  };

  // --------------------------------------------------
  // ADD CERTIFICATION
  // --------------------------------------------------

  const addCertification = () => {
    if (!certification.name.trim()) return;

    setData((previous) => ({
      ...previous,
      certifications: [
        ...(previous.certifications || []),
        certification,
      ],
    }));

    setCertification({
      name: "",
      issuer: "",
      year: "",
      credentialUrl: "",
    });
  };

  // --------------------------------------------------
  // ADD ACHIEVEMENT
  // --------------------------------------------------

  const addAchievement = () => {
    if (!achievement.title.trim()) return;

    setData((previous) => ({
      ...previous,
      achievements: [
        ...(previous.achievements || []),
        achievement,
      ],
    }));

    setAchievement({
      title: "",
      organization: "",
      year: "",
      description: "",
    });
  };

  // --------------------------------------------------
  // SAVE COMPLETE PROFILE
  // --------------------------------------------------

  const handleNext = async () => {
    if (saving) return;
    if (!data.workAuthorization) {
      setFormError("Please select your work authorization status to continue.");
      return;
    }
    setFormError("");

    try {
      setSaving(true);

      // ----------------------------------------------
      // 1. Save latest additional details to context
      // ----------------------------------------------

      updateSection("additional", data);

      // ----------------------------------------------
      // 2. Get JWT token
      // ----------------------------------------------

      const token = localStorage.getItem("token");

      if (!token) {
        alert("Session expired. Please login again.");
        navigate("/login");
        return;
      }

      // ----------------------------------------------
      // 3. Build complete profile payload
      // ----------------------------------------------

      const payload = {
        // Personal details
        name: profile.personal?.name || "",
        firstName: profile.personal?.firstName || "",
        lastName: profile.personal?.lastName || "",
        countryCode: profile.personal?.countryCode || "+91",
        gender: profile.personal?.gender || "",
        location: profile.personal?.location || "",
        phone: profile.personal?.phone || "",
        email: profile.personal?.email || "",

        githubUrl:
          profile.personal?.githubUrl || "",

        linkedinUrl:
          profile.personal?.linkedinUrl || "",

        portfolioUrl:
          profile.personal?.portfolioUrl || "",

        otherLinks:
          profile.personal?.otherLinks || [],

        // Profile sections
        education: profile.education || [],

        skills: profile.skills || [],

        experience:
          profile.experience || [],

        projects:
          profile.projects || [],

        // Additional details
        certifications:
          data.certifications || [],

        achievements:
          data.achievements || [],

        workAuthorization: data.workAuthorization || "",
        workAuthorizationCountry: data.workAuthorizationCountry || "",
        visaSponsorship: data.visaSponsorship || "",
        militaryStatus: data.militaryStatus || "",
        governmentProjectExperience: data.governmentProjectExperience || "",
        governmentProjectDetails: data.governmentProjectDetails || "",

        otherDetails: {
          leadership:
            data.leadership || [],

          publications:
            data.publications || [],

          volunteering:
            data.volunteering || [],

          languages:
            data.languages || [],

          other:
            data.other || "",
        },
      };

      // ----------------------------------------------
      // 4. Debug information
      // ----------------------------------------------

      console.log(
        "========== PROFILE SUBMISSION =========="
      );

      console.log("Payload:", payload);

      console.log(
        "Token exists:",
        !!token
      );

      // ----------------------------------------------
      // 5. Save to Spring Boot
      // ----------------------------------------------

      const response = await axios.put(
        API_BASE_URL + "/api/profile/me",
        payload,
        {
          headers: {
            Authorization: `Bearer ${token}`,
            "Content-Type": "application/json",
          },
        }
      );

      console.log(
        "PROFILE SAVED SUCCESSFULLY:",
        response.data
      );

      // ----------------------------------------------
      // 6. Mark profile as completed
      // ----------------------------------------------

      localStorage.setItem(
        "profileCompleted",
        "true"
      );

      localStorage.removeItem("profileEditMode");

      // ----------------------------------------------
      // 7. Go to Home page
      // ----------------------------------------------

      navigate("/home");

    } catch (error) {
      console.error(
        "PROFILE SAVE ERROR:",
        error
      );

      console.error(
        "BACKEND RESPONSE:",
        error.response?.data
      );

      console.error(
        "STATUS:",
        error.response?.status
      );

      alert(
        error.response?.data ||
        "Failed to save profile. Please try again."
      );

    } finally {
      setSaving(false);
    }
  };

  // --------------------------------------------------
  // RENDER
  // --------------------------------------------------

  return (
    <ProfileLayout
      step={7}
      title="Certifications & Achievements"
      subtitle="Add additional information that strengthens your profile."
      onNext={handleNext}
      onBack={() => navigate("/projects")}
      nextText={
        saving
          ? "Saving Profile..."
          : "Complete Profile"
      }
    >

      {/* ==================================================
          CERTIFICATIONS
          ================================================== */}

      <div className="section-block">
        <h3>Certifications</h3>

        <div className="form-grid">

          <div className="form-group">
            <label>Certification Name</label>

            <input
              value={certification.name}
              onChange={(e) =>
                setCertification({
                  ...certification,
                  name: e.target.value,
                })
              }
              placeholder="AWS Cloud Practitioner"
              disabled={saving}
            />
          </div>

          <div className="form-group">
            <label>Issuing Organization</label>

            <input
              value={certification.issuer}
              onChange={(e) =>
                setCertification({
                  ...certification,
                  issuer: e.target.value,
                })
              }
              placeholder="Amazon Web Services"
              disabled={saving}
            />
          </div>

          <div className="form-group">
            <label>Year</label>

            <input
              value={certification.year}
              onChange={(e) =>
                setCertification({
                  ...certification,
                  year: e.target.value,
                })
              }
              placeholder="2026"
              disabled={saving}
            />
          </div>

          <div className="form-group">
            <label>Credential URL</label>

            <input
              value={certification.credentialUrl}
              onChange={(e) =>
                setCertification({
                  ...certification,
                  credentialUrl: e.target.value,
                })
              }
              placeholder="https://..."
              disabled={saving}
            />
          </div>

        </div>

        <button
          type="button"
          className="add-button"
          onClick={addCertification}
          disabled={saving}
        >
          + Add Certification
        </button>

        {(data.certifications || []).map(
          (item, index) => (
            <div
              className="saved-item"
              key={index}
            >
              <strong>
                {item.name}
              </strong>

              <span>
                {item.issuer}

                {item.year &&
                  ` • ${item.year}`}
              </span>
            </div>
          )
        )}
      </div>

      {/* ==================================================
          ACHIEVEMENTS
          ================================================== */}

      <div className="section-block">
        <h3>Achievements</h3>

        <div className="form-grid">

          <div className="form-group">
            <label>Achievement</label>

            <input
              value={achievement.title}
              onChange={(e) =>
                setAchievement({
                  ...achievement,
                  title: e.target.value,
                })
              }
              placeholder="Hackathon Winner"
              disabled={saving}
            />
          </div>

          <div className="form-group">
            <label>
              Organization / Event
            </label>

            <input
              value={achievement.organization}
              onChange={(e) =>
                setAchievement({
                  ...achievement,
                  organization: e.target.value,
                })
              }
              placeholder="Hackathon / Organization"
              disabled={saving}
            />
          </div>

          <div className="form-group">
            <label>Year</label>

            <input
              value={achievement.year}
              onChange={(e) =>
                setAchievement({
                  ...achievement,
                  year: e.target.value,
                })
              }
              placeholder="2026"
              disabled={saving}
            />
          </div>

        </div>

        <div className="form-group">
          <label>Description</label>

          <textarea
            rows="3"
            value={achievement.description}
            onChange={(e) =>
              setAchievement({
                ...achievement,
                description: e.target.value,
              })
            }
            placeholder="Describe your achievement..."
            disabled={saving}
          />
        </div>

        <button
          type="button"
          className="add-button"
          onClick={addAchievement}
          disabled={saving}
        >
          + Add Achievement
        </button>

        {(data.achievements || []).map(
          (item, index) => (
            <div
              className="saved-item"
              key={index}
            >
              <strong>
                {item.title}
              </strong>

              <span>
                {item.organization}

                {item.year &&
                  ` • ${item.year}`}
              </span>
            </div>
          )
        )}
      </div>

      {/* ==================================================
          OTHER INFORMATION
          ================================================== */}

      <div className="section-block">
        <h3>Application & Compliance Information</h3>

        <div className="form-grid">
          <div className="form-group">
            <label htmlFor="work-authorization">Work Authorization *</label>
            <select id="work-authorization" required value={data.workAuthorization || ""} onChange={(e) => { setData((p) => ({ ...p, workAuthorization: e.target.value })); setFormError(""); }} disabled={saving}>
              <option value="">Select</option>
              <option value="AUTHORIZED">Authorized to work</option>
              <option value="NOT_AUTHORIZED">Not currently authorized</option>
            </select>
          </div>

          <div className="form-group">
            <label>Work Authorization Country</label>
            <input value={data.workAuthorizationCountry || ""} onChange={(e) => setData((p) => ({ ...p, workAuthorizationCountry: e.target.value }))} placeholder="India" disabled={saving} />
          </div>

          <div className="form-group">
            <label>Visa Sponsorship</label>
            <select value={data.visaSponsorship || ""} onChange={(e) => setData((p) => ({ ...p, visaSponsorship: e.target.value }))} disabled={saving}>
              <option value="">Select</option>
              <option value="NO">No sponsorship required</option>
              <option value="YES">Sponsorship required</option>
              <option value="UNKNOWN">Not sure / needs confirmation</option>
            </select>
          </div>

          <div className="form-group">
            <label>Military Status</label>
            <select value={data.militaryStatus || ""} onChange={(e) => setData((p) => ({ ...p, militaryStatus: e.target.value }))} disabled={saving}>
              <option value="">Select</option>
              <option value="NONE">No military service</option>
              <option value="VETERAN">Veteran</option>
              <option value="ACTIVE_DUTY">Active duty</option>
              <option value="OTHER">Other</option>
              <option value="PREFER_NOT_TO_SAY">Prefer not to say</option>
            </select>
          </div>

          <div className="form-group">
            <label>Government / Public-Sector Project Experience</label>
            <select value={data.governmentProjectExperience || ""} onChange={(e) => setData((p) => ({ ...p, governmentProjectExperience: e.target.value }))} disabled={saving}>
              <option value="">Select</option>
              <option value="YES">Yes</option>
              <option value="NO">No</option>
            </select>
          </div>
        </div>

        <div className="form-group">
          <label>Government Project Details</label>
          <textarea rows="4" value={data.governmentProjectDetails || ""} onChange={(e) => setData((p) => ({ ...p, governmentProjectDetails: e.target.value }))} placeholder="Government body, project, role, technologies, duration..." disabled={saving} />
        </div>
      </div>

      <div className="section-block">
        <h3>Other Information</h3>

        <div className="form-group">
          <label htmlFor="language-input">Languages</label>
          <div className="language-entry">
            {(data.languages || []).map((language) => (
              <span className="language-chip" key={language}>
                {language}
                <button type="button" aria-label={`Remove ${language}`} onClick={() => setData((p) => ({ ...p, languages: p.languages.filter((item) => item !== language) }))} disabled={saving}>×</button>
              </span>
            ))}
            <input
              id="language-input"
              value={languageInput}
              onChange={(e) => {
                const value = e.target.value;
                if (/[\n,;]/.test(value)) addLanguages(value);
                else setLanguageInput(value);
              }}
              onKeyDown={(e) => {
                if (e.key === "Enter" || e.key === ",") { e.preventDefault(); addLanguages(languageInput); }
                if (e.key === "Backspace" && !languageInput && data.languages?.length) setData((p) => ({ ...p, languages: p.languages.slice(0, -1) }));
              }}
              onBlur={() => addLanguages(languageInput)}
              placeholder={data.languages?.length ? "Add another language" : "Type a language and press Enter"}
              disabled={saving}
            />
          </div>
          <small className="field-hint">Add multiple languages one at a time. Press Enter or separate entries with commas.</small>
        </div>

        <div className="form-group">
          <label>
            Leadership / Volunteering / Other
          </label>

          <textarea
            rows="4"
            value={data.other || ""}
            onChange={(e) =>
              setData((previous) => ({
                ...previous,
                other: e.target.value,
              }))
            }
            placeholder="Any other information relevant to your professional profile..."
            disabled={saving}
          />
        </div>
      </div>

      {formError && <p className="error-text" role="alert">{formError}</p>}

    </ProfileLayout>
  );
}

export default AdditionalDetails;
