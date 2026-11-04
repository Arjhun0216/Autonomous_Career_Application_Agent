import { useEffect, useState } from "react";
import {
  useNavigate,
  useSearchParams,
} from "react-router-dom";

import { useProfile } from "../context/ProfileContext";


function OAuth2Success() {

  const navigate = useNavigate();

  const [searchParams] =
    useSearchParams();

  const { clearProfile } =
    useProfile();

  const [error, setError] =
    useState("");


  useEffect(() => {

    const token =
      searchParams.get("token");

    const profileCompleted =
      searchParams.get(
        "profileCompleted"
      );


    if (!token) {

      setError(
        "Google authentication failed. Authentication token was not received."
      );

      return;
    }


    localStorage.setItem(
      "token",
      token
    );

    localStorage.setItem(
      "authProvider",
      "GOOGLE"
    );


    const completed =
      profileCompleted === "true";


    localStorage.setItem(
      "profileCompleted",
      String(completed)
    );


    /*
     * BRAND-NEW GOOGLE USER
     *
     * Start the profile with empty
     * frontend context.
     */

    if (!completed) {

      clearProfile();

      navigate(
        "/personal-details",
        {
          replace: true,
        }
      );

      return;
    }


    /*
     * EXISTING COMPLETED USER
     */

    navigate(
      "/home",
      {
        replace: true,
      }
    );

  }, [
    navigate,
    searchParams,
    clearProfile,
  ]);


  if (error) {

    return (

      <div className="auth-page">

        <div className="auth-container">

          <div className="auth-card">

            <div className="auth-heading">

              <h2>
                Google Login Failed
              </h2>

              <p>
                {error}
              </p>

            </div>


            <button
              type="button"
              className="primary-button"
              onClick={() =>
                navigate("/login")
              }
            >
              Back to Login
            </button>

          </div>

        </div>

      </div>
    );
  }


  return (

    <div className="auth-page">

      <div className="auth-container">

        <div className="auth-card">

          <div className="auth-heading">

            <h2>
              Signing you in...
            </h2>

            <p>
              Please wait while we
              prepare your profile.
            </p>

          </div>

        </div>

      </div>

    </div>
  );
}

export default OAuth2Success;