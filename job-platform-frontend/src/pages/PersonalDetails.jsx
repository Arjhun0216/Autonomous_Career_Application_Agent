import { API_BASE_URL } from "../apiBase.js";
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import axios from "axios";

import ProfileLayout from "../components/ProfileLayout";
import { useProfile } from "../context/ProfileContext";
import { uniqueCountryCallingCodes } from "../data/countryCallingCodes";


function PersonalDetails() {

  const navigate = useNavigate();

  const {
    profile,
    updatePersonal,
    updateSection,
  } = useProfile();

  const editMode =
    localStorage.getItem("profileEditMode") === "true";

  const [editProfileLoading, setEditProfileLoading] =
    useState(editMode);

  useEffect(() => {
    if (!editMode) return;

    const token = localStorage.getItem("token");
    if (!token) {
      navigate("/login", { replace: true });
      return;
    }

    const loadSavedProfile = async () => {
      try {
        const response = await axios.get(
          API_BASE_URL + "/api/profile/me",
          { headers: { Authorization: `Bearer ${token}` } }
        );
        const saved = response.data || {};
        const parseJson = (value, fallback) => {
          if (Array.isArray(value)) return value;
          if (typeof value !== "string" || !value.trim()) return fallback;
          try { return JSON.parse(value) ?? fallback; } catch { return fallback; }
        };
        const otherDetails = parseJson(saved.otherDetails, {});
        const nameParts = (saved.name || "").trim().split(/\s+/).filter(Boolean);
        const personal = {
          name: saved.name || "",
          firstName: saved.firstName || (nameParts.length > 1 ? nameParts.slice(0, -1).join(" ") : nameParts[0] || ""),
          lastName: saved.lastName || (nameParts.length > 1 ? nameParts[nameParts.length - 1] : ""),
          countryCode: saved.countryCode || "+91",
          gender: saved.gender || "",
          location: saved.location || "",
          phone: saved.phone || "",
          email: saved.email || "",
          userExperience: saved.userExperience ?? 0,
          githubUrl: saved.githubUrl || "",
          linkedinUrl: saved.linkedinUrl || "",
          portfolioUrl: saved.portfolioUrl || "",
          otherLinks: parseJson(saved.otherLinks, []),
        };

        setForm(personal);
        updatePersonal(personal);
        updateSection("education", parseJson(saved.education, []));
        updateSection("skills", parseJson(saved.skills, []));
        updateSection("experience", parseJson(saved.experience, []));
        updateSection("projects", parseJson(saved.projects, []));
        updateSection("additional", {
          certifications: parseJson(saved.certifications, []),
          achievements: parseJson(saved.achievements, []),
          leadership: otherDetails?.leadership || [],
          publications: otherDetails?.publications || [],
          volunteering: otherDetails?.volunteering || [],
          languages: otherDetails?.languages || [],
          other: otherDetails?.other || "",
          workAuthorization: saved.workAuthorization || "",
          workAuthorizationCountry: saved.workAuthorizationCountry || "",
          visaSponsorship: saved.visaSponsorship || "",
          militaryStatus: saved.militaryStatus || "",
          governmentProjectExperience: saved.governmentProjectExperience || "",
          governmentProjectDetails: saved.governmentProjectDetails || "",
        });
      } catch (error) {
        console.error("Failed to load saved profile for edit:", error);
        setError("Unable to load your saved profile. Please try again.");
      } finally {
        setEditProfileLoading(false);
      }
    };
    loadSavedProfile();
  }, [editMode, navigate, updatePersonal, updateSection]);


  const [form, setForm] = useState({
    name: "",
    firstName: "",
    lastName: "",
    countryCode: "+91",
    gender: "",
    location: "",
    phone: "",
    email: "",
    userExperience: 0,

    githubUrl: "",
    linkedinUrl: "",
    portfolioUrl: "",

    otherLinks: [],

    ...profile.personal,
  });


  const [error, setError] =
    useState("");

  const [successMessage, setSuccessMessage] =
    useState("");

  const [resumeFile, setResumeFile] =
    useState(null);

  const [extractingResume, setExtractingResume] =
    useState(false);


  // ==================================================
  // INPUT CHANGE
  // ==================================================

  const handleChange = (e) => {

    const {
      name,
      value,
    } = e.target;

    setForm((previous) => ({
      ...previous,
      [name]: value,
    }));

    setError("");
    setSuccessMessage("");
  };


  // ==================================================
  // EXPERIENCE CHANGE
  // ==================================================

  const handleExperienceChange = (e) => {

    const value =
      e.target.value;


    if (value === "") {

      setForm((previous) => ({
        ...previous,
        userExperience: "",
      }));

      return;
    }


    if (!/^\d+$/.test(value)) {
      return;
    }


    setForm((previous) => ({
      ...previous,
      userExperience:
        Number(value),
    }));

    setError("");
  };


  // ==================================================
  // RESUME FILE
  // ==================================================

  const handleResumeChange = (e) => {

    const file =
      e.target.files?.[0] ||
      null;

    setResumeFile(file);

    setError("");
    setSuccessMessage("");
  };


  // ==================================================
  // EXTRACT RESUME
  // ==================================================

  const handleResumeExtraction =
    async () => {

      if (!resumeFile) {

        setError(
          "Please select your resume first."
        );

        return;
      }


      if (
        resumeFile.type !==
          "application/pdf" &&
        !resumeFile.name
          .toLowerCase()
          .endsWith(".pdf")
      ) {

        setError(
          "Please upload a PDF resume."
        );

        return;
      }


      const token =
        localStorage.getItem(
          "token"
        );


      if (!token) {

        setError(
          "Your login session has expired. Please login again."
        );

        return;
      }


      setExtractingResume(true);

      setError("");
      setSuccessMessage("");


      try {

        const data =
          new FormData();

        data.append(
          "resume",
          resumeFile
        );


        const response =
          await axios.post(
            API_BASE_URL + "/api/profile/extract-resume",
            data,
            {
              headers: {
                Authorization:
                  `Bearer ${token}`,
              },
            }
          );


        const extracted =
          response.data;


        console.log(
          "RESUME EXTRACTION RESULT:",
          extracted
        );


        // ==================================================
        // PERSONAL
        // ==================================================

        const personal =
          extracted.personal ||
          {};


        const extractedName = (personal.name || "").trim().split(/\s+/).filter(Boolean);
        const extractedPhone = personal.phone || "";
        const embeddedCountryCode = extractedPhone.match(/^\s*(\+\d{1,4})/);
        const extractedPersonal = {
          name: personal.name || "",
          firstName: personal.firstName || (extractedName.length > 1 ? extractedName.slice(0, -1).join(" ") : extractedName[0] || ""),
          lastName: personal.lastName || (extractedName.length > 1 ? extractedName[extractedName.length - 1] : ""),
          countryCode: personal.countryCode || (embeddedCountryCode && embeddedCountryCode[1]) || form.countryCode || "+91",

          // Gender is explicit profile data; never infer it from the name.
          gender:
            form.gender ||
            "",

          location:
            personal.location ||
            "",

          phone: extractedPhone.replace(/^\s*\+\d{1,4}\s*/, "").replace(/\D/g, ""),

          email:
            personal.email ||
            "",

          userExperience:
            personal.userExperience ??
            0,

          githubUrl:
            personal.githubUrl ||
            "",

          linkedinUrl:
            personal.linkedinUrl ||
            "",

          portfolioUrl:
            personal.portfolioUrl ||
            "",

          otherLinks:
            personal.otherLinks ||
            [],
        };


        // ==================================================
        // UPDATE LOCAL FORM
        // ==================================================

        setForm(
          extractedPersonal
        );


        // ==================================================
        // UPDATE GLOBAL PERSONAL CONTEXT
        // ==================================================

        updatePersonal(
          extractedPersonal
        );


        // ==================================================
        // EDUCATION
        // ==================================================

        if (
          Array.isArray(
            extracted.education
          )
        ) {

          updateSection(
            "education",
            extracted.education
          );
        }


        // ==================================================
        // SKILLS
        // ==================================================

        if (
          Array.isArray(
            extracted.skills
          )
        ) {

          updateSection(
            "skills",
            extracted.skills
          );
        }


        // ==================================================
        // EXPERIENCE
        // ==================================================

        if (
          Array.isArray(
            extracted.experience
          )
        ) {

          updateSection(
            "experience",
            extracted.experience
          );
        }


        // ==================================================
        // PROJECTS
        // ==================================================

        if (
          Array.isArray(
            extracted.projects
          )
        ) {

          updateSection(
            "projects",
            extracted.projects
          );
        }


        // ==================================================
        // ADDITIONAL DETAILS
        // ==================================================

        if (
          extracted.additional
        ) {

          updateSection(
            "additional",
            {
              certifications:
                extracted.additional
                  .certifications ||
                [],

              achievements:
                extracted.additional
                  .achievements ||
                [],

              leadership:
                extracted.additional
                  .leadership ||
                [],

              publications:
                extracted.additional
                  .publications ||
                [],

              volunteering:
                extracted.additional
                  .volunteering ||
                [],

              languages:
                extracted.additional
                  .languages ||
                [],

              other:
                extracted.additional
                  .other ||
                "",
            }
          );
        }


        setSuccessMessage(
          "Resume information extracted successfully. Please review the details."
        );


      } catch (error) {

        console.error(
          "Resume extraction error:",
          error
        );


        let message =
          "Failed to extract information from resume.";


        if (
          error.response
        ) {

          const data =
            error.response.data;


          if (
            typeof data ===
            "string"
          ) {

            message =
              data;

          } else if (
            data?.detail
          ) {

            message =
              data.detail;

          } else if (
            data?.message
          ) {

            message =
              data.message;
          }
        }


        setError(
          message
        );

      } finally {

        setExtractingResume(
          false
        );
      }
    };


  // ==================================================
  // NEXT
  // ==================================================

  const handleNext = () => {

    if (!form.firstName.trim() || !form.lastName.trim()) {
      setError("First name and last name are required.");

      return;
    }

    if (!form.gender) {
      setError("Please select your gender.");
      return;
    }



    if (!form.location.trim()) {

      setError(
        "Location is required."
      );

      return;
    }


    if (!form.email.trim()) {

      setError(
        "Email is required."
      );

      return;
    }


    if (
      form.userExperience === "" ||
      form.userExperience === null ||
      form.userExperience === undefined
    ) {

      setError(
        "Experience in years is required."
      );

      return;
    }


    if (
      !Number.isInteger(
        Number(
          form.userExperience
        )
      ) ||
      Number(
        form.userExperience
      ) < 0
    ) {

      setError(
        "Experience must be a valid number of years."
      );

      return;
    }


    updatePersonal({
      ...form,
      name: `${form.firstName.trim()} ${form.lastName.trim()}`.trim(),

      userExperience:
        Number(
          form.userExperience
        ),
    });


    navigate(
      "/education"
    );
  };


  // ==================================================
  // RENDER
  // ==================================================

  if (editMode && editProfileLoading) {
    return (
      <div className="profile-page">
        <div className="profile-container">
          <div className="profile-card">
            <div className="profile-heading">
              <h2>Loading saved profile...</h2>
              <p>Reading your current profile from the database.</p>
            </div>
          </div>
        </div>
      </div>
    );
  }

  return (

    <ProfileLayout
      step={2}
      title="Personal & Professional Details"
      subtitle={editMode ? "Edit your saved professional profile." : "Tell us about yourself and your professional identity."}
      onNext={handleNext}
      onBack={() =>
        navigate(editMode ? "/account-details" : "/register")
      }
    >


      {/* ==================================================
          RESUME EXTRACTION
          ================================================== */}

      <div className="section-block resume-import-card">

        <h3>
          Extract Profile From Resume
        </h3>

        <p>Drop in a PDF and we’ll extract your profile details for you to review.</p>


        <div className="form-group">

          <label htmlFor="resume">
            Resume PDF
          </label>

          <input
            id="resume"
            className="resume-file-input"
            type="file"
            accept=".pdf,application/pdf"
            onChange={
              handleResumeChange
            }
          />


          {resumeFile && (

            <p>
              Selected:{" "}
              <strong>
                {resumeFile.name}
              </strong>
            </p>

          )}


          <button
            type="button"
            className="secondary-button"
            onClick={
              handleResumeExtraction
            }
            disabled={
              !resumeFile ||
              extractingResume
            }
          >

            {extractingResume
              ? "Extracting..."
              : "Extract using Resume"}

          </button>

          <p className="resume-import-hint">PDF format · Your details stay editable before you save</p>

        </div>

        {successMessage && (

          <div className="success-message">
            {successMessage}
          </div>

        )}

      </div>


      {/* ==================================================
          PERSONAL DETAILS
          ================================================== */}

      <div className="form-grid">


        {/* FIRST NAME */}

        <div className="form-group">

          <label>
            First Name *
          </label>

          <input
            name="firstName"
            value={form.firstName}
            onChange={
              handleChange
            }
            placeholder="First name"
          />

        </div>

        <div className="form-group">
          <label>Last Name *</label>
          <input name="lastName" value={form.lastName} onChange={handleChange} placeholder="Last name" />
        </div>

        <div className="form-group">
          <label>Gender *</label>
          <select name="gender" value={form.gender} onChange={handleChange}>
            <option value="">Select gender</option>
            <option value="Male">Male</option>
            <option value="Female">Female</option>
          </select>
        </div>


        {/* LOCATION */}

        <div className="form-group">

          <label>
            Location *
          </label>

          <input
            name="location"
            value={
              form.location
            }
            onChange={
              handleChange
            }
            placeholder="City, State"
          />

        </div>


        {/* EMAIL */}

        <div className="form-group">

          <label>
            Email *
          </label>

          <input
            name="email"
            type="email"
            value={
              form.email
            }
            onChange={
              handleChange
            }
            placeholder="your@email.com"
          />

        </div>


        {/* PHONE */}

        <div className="form-group">

          <label>
            Country Code *
          </label>

          <select name="countryCode" value={form.countryCode} onChange={handleChange}>
            {uniqueCountryCallingCodes.map(([callingCode, region]) => (
              <option value={callingCode} key={callingCode}>{region} ({callingCode})</option>
            ))}
          </select>
        </div>

        <div className="form-group">
          <label>Phone</label>

          <input
            name="phone"
            value={
              form.phone
            }
            onChange={
              handleChange
            }
            placeholder="Phone number"
          />

        </div>


        {/* EXPERIENCE */}

        <div className="form-group">

          <label>
            Experience (Years) *
          </label>

          <input
            name="userExperience"
            type="number"
            min="0"
            step="1"
            value={
              form.userExperience
            }
            onChange={
              handleExperienceChange
            }
            placeholder="e.g. 2"
          />

        </div>


        {/* GITHUB */}

        <div className="form-group">

          <label>
            GitHub
          </label>

          <input
            name="githubUrl"
            value={
              form.githubUrl
            }
            onChange={
              handleChange
            }
            placeholder="https://github.com/username"
          />

        </div>


        {/* LINKEDIN */}

        <div className="form-group">

          <label>
            LinkedIn
          </label>

          <input
            name="linkedinUrl"
            value={
              form.linkedinUrl
            }
            onChange={
              handleChange
            }
            placeholder="https://linkedin.com/in/username"
          />

        </div>


        {/* PORTFOLIO */}

        <div className="form-group">

          <label>
            Portfolio
          </label>

          <input
            name="portfolioUrl"
            value={
              form.portfolioUrl
            }
            onChange={
              handleChange
            }
            placeholder="https://yourportfolio.com"
          />

        </div>

      </div>


      {/* ==================================================
          OTHER LINKS
          ================================================== */}

      <div className="form-group full-width">

        <div className="other-links-header">

          <label>
            Other Links
          </label>

          <button
            type="button"
            className="add-link-button"
            onClick={() =>
              setForm((previous) => ({
                ...previous,

                otherLinks: [
                  ...(previous.otherLinks || []),

                  {
                    name: "",
                    url: "",
                  },
                ],
              }))
            }
          >
            + Add Link
          </button>

        </div>


        {(form.otherLinks || [])
          .map(
            (link, index) => (

              <div
                className="other-link-row"
                key={index}
              >

                <input
                  type="text"
                  placeholder="Link name (e.g. LeetCode)"
                  value={
                    link.name
                  }
                  onChange={(e) => {

                    const updated =
                      [
                        ...form.otherLinks,
                      ];

                    updated[index] = {

                      ...updated[index],

                      name:
                        e.target.value,
                    };

                    setForm({
                      ...form,

                      otherLinks:
                        updated,
                    });

                  }}
                />


                <input
                  type="url"
                  placeholder="https://..."
                  value={
                    link.url
                  }
                  onChange={(e) => {

                    const updated =
                      [
                        ...form.otherLinks,
                      ];

                    updated[index] = {

                      ...updated[index],

                      url:
                        e.target.value,
                    };

                    setForm({
                      ...form,

                      otherLinks:
                        updated,
                    });

                  }}
                />


                <button
                  type="button"
                  className="remove-link-button"
                  onClick={() => {

                    const updated =
                      form.otherLinks.filter(
                        (_, i) =>
                          i !== index
                      );

                    setForm({
                      ...form,

                      otherLinks:
                        updated,
                    });

                  }}
                >
                  ×
                </button>

              </div>

            )
          )}

      </div>


      {error && (

        <p className="error-text">
          {error}
        </p>

      )}

    </ProfileLayout>
  );
}

export default PersonalDetails;
