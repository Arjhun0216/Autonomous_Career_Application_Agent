import { API_BASE_URL } from "../apiBase.js";
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import axios from "axios";

export default function AccountDetails() {

    const navigate = useNavigate();

    const [profile, setProfile] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");

    const token = localStorage.getItem("token");


    useEffect(() => {

        if (!token) {
            navigate("/login");
            return;
        }

        const loadProfile = async () => {

            try {

                const response = await axios.get(
                    API_BASE_URL + "/api/profile/me",
                    {
                        headers: {
                            Authorization:
                                `Bearer ${token}`
                        }
                    }
                );

                setProfile(response.data);

            } catch (error) {

                console.error(
                    "ACCOUNT PROFILE ERROR:",
                    error
                );

                setError(
                    "Unable to load your account details."
                );

            } finally {

                setLoading(false);

            }
        };

        loadProfile();

    }, [token, navigate]);


    if (loading) {

        return (
            <div className="account-page">

                <div className="account-card">

                    <div className="account-loading">
                        Loading your profile...
                    </div>

                </div>

            </div>
        );
    }


    if (error) {

        return (
            <div className="account-page">

                <div className="account-card">

                    <div className="account-error">

                        <h2>
                            Unable to load profile
                        </h2>

                        <p>
                            {error}
                        </p>

                        <button
                            onClick={() =>
                                navigate("/home")
                            }
                        >
                            Back to Home
                        </button>

                    </div>

                </div>

            </div>
        );
    }


    let skills = [];

    try {

        if (typeof profile.skills === "string") {

            skills =
                profile.skills
                    ? JSON.parse(profile.skills)
                    : [];

        } else {

            skills =
                Array.isArray(profile.skills)
                    ? profile.skills
                    : [];

        }

    } catch {

        skills = [];

    }


    return (

        <div className="account-page">

            <div className="account-card">


                {/* =================================================
                    HEADER
                ================================================= */}

                <div className="account-header">

                    <button
                        className="account-back-button"
                        onClick={() =>
                            navigate("/home")
                        }
                    >
                        ←
                    </button>

                    <div className="account-title">

                        <h1>
                            My Account
                        </h1>

                        <p>
                            View and manage your professional profile
                        </p>

                    </div>

                    <button
                        className="account-edit-button"
                        onClick={() => {
                            localStorage.setItem("profileEditMode", "true");
                            navigate("/personal-details");
                        }}
                    >
                        Edit Profile
                    </button>

                </div>


                {/* =================================================
                    PERSONAL DETAILS
                ================================================= */}

                <section className="account-section">

                    <h2>
                        Personal Details
                    </h2>

                    <div className="account-grid">

                        <div className="account-field">

                            <span>
                                Name
                            </span>

                            <strong>
                                {profile.name ||
                                    "Not provided"}
                            </strong>

                        </div>


                        <div className="account-field">

                            <span>
                                Email
                            </span>

                            <strong>
                                {profile.email ||
                                    "Not provided"}
                            </strong>

                        </div>

                        <div className="account-field">
                            <span>Gender</span>
                            <strong>{profile.gender || "Not provided"}</strong>
                        </div>


                        <div className="account-field">

                            <span>
                                Phone
                            </span>

                            <strong>
                                {profile.phone ||
                                    "Not provided"}
                            </strong>

                        </div>


                        <div className="account-field">

                            <span>
                                Professional Title
                            </span>

                            <strong>
                                {profile.professionalTitle ||
                                    "Not provided"}
                            </strong>

                        </div>


                        <div className="account-field">

                            <span>
                                Location
                            </span>

                            <strong>
                                {profile.location ||
                                    "Not provided"}
                            </strong>

                        </div>


                        <div className="account-field">

                            <span>
                                Experience
                            </span>

                            <strong>
                                {profile.userExperience ?? 0} years
                            </strong>

                        </div>

                    </div>

                </section>


                {/* =================================================
                    PROFESSIONAL LINKS
                ================================================= */}

                <section className="account-section">

                    <h2>
                        Professional Links
                    </h2>

                    <div className="account-grid">


                        <div className="account-field">

                            <span>
                                GitHub
                            </span>

                            {profile.githubUrl ? (

                                <a
                                    href={profile.githubUrl}
                                    target="_blank"
                                    rel="noopener noreferrer"
                                >
                                    {profile.githubUrl}
                                </a>

                            ) : (

                                <strong>
                                    Not provided
                                </strong>

                            )}

                        </div>


                        <div className="account-field">

                            <span>
                                LinkedIn
                            </span>

                            {profile.linkedinUrl ? (

                                <a
                                    href={profile.linkedinUrl}
                                    target="_blank"
                                    rel="noopener noreferrer"
                                >
                                    {profile.linkedinUrl}
                                </a>

                            ) : (

                                <strong>
                                    Not provided
                                </strong>

                            )}

                        </div>


                        <div className="account-field">

                            <span>
                                Portfolio
                            </span>

                            {profile.portfolioUrl ? (

                                <a
                                    href={profile.portfolioUrl}
                                    target="_blank"
                                    rel="noopener noreferrer"
                                >
                                    {profile.portfolioUrl}
                                </a>

                            ) : (

                                <strong>
                                    Not provided
                                </strong>

                            )}

                        </div>

                    </div>

                </section>


                {/* =================================================
                    SKILLS
                ================================================= */}

                <section className="account-section">

                    <h2>
                        Skills
                    </h2>

                    {skills.length > 0 ? (

                        <div className="account-skills">

                            {skills.map(
                                (skill, index) => (

                                    <span
                                        className="account-skill"
                                        key={`${skill}-${index}`}
                                    >
                                        {skill}
                                    </span>

                                )
                            )}

                        </div>

                    ) : (

                        <p className="account-empty">
                            No skills added.
                        </p>

                    )}

                </section>


                {/* =================================================
                    BOTTOM ACTION
                ================================================= */}

                <div className="account-footer">

                    <button
                        className="account-back-home"
                        onClick={() =>
                            navigate("/home")
                        }
                    >
                        Back to Jobs
                    </button>

                    <button
                        className="account-edit-button"
                        onClick={() => {
                            localStorage.setItem("profileEditMode", "true");
                            navigate("/personal-details");
                        }}
                    >
                        Edit Profile
                    </button>

                </div>

            </div>

        </div>
    );
}
