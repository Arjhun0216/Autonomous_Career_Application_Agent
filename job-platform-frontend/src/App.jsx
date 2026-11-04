import {
  BrowserRouter,
  Navigate,
  Route,
  Routes
} from "react-router-dom";

import Login from "./pages/Login";
import Register from "./pages/Register";

import PersonalDetails from "./pages/PersonalDetails";
import JobDetails from "./pages/JobDetails";
import AccountDetails from "./pages/AccountDetails";
import Education from "./pages/Education";
import Skills from "./pages/Skills";
import Experience from "./pages/Experience";
import Projects from "./pages/Projects";
import AdditionalDetails from "./pages/AdditionalDetails";
import ProfileComplete from "./pages/ProfileComplete";
import Home from "./pages/Home";
import OAuth2Success from "./pages/OAuth2Success";

import AppliedJobs from "./pages/AppliedJobs";
import PendingApprovals from "./pages/PendingApprovals";
import SkillRecommendation from "./pages/SkillRecommendation";
import CollegeMailing from "./pages/CollegeMailing";

import { ProfileProvider } from "./context/ProfileContext";

function App() {

  return (
    <BrowserRouter>

      <ProfileProvider>

        <Routes>

          {/* ==================================================
              OAUTH
          ================================================== */}

          <Route
            path="/oauth2/success"
            element={<OAuth2Success />}
          />


          {/* ==================================================
              AUTH
          ================================================== */}

          <Route
            path="/"
            element={
              <Navigate
                to="/login"
                replace
              />
            }
          />

          <Route
            path="/login"
            element={<Login />}
          />

          <Route
            path="/register"
            element={<Register />}
          />


          {/* ==================================================
              PROFILE
          ================================================== */}

          <Route
            path="/personal-details"
            element={<PersonalDetails />}
          />

          <Route
            path="/education"
            element={<Education />}
          />

          <Route
            path="/skills"
            element={<Skills />}
          />

          <Route
            path="/experience"
            element={<Experience />}
          />

          <Route
            path="/projects"
            element={<Projects />}
          />

          <Route
            path="/additional-details"
            element={<AdditionalDetails />}
          />

          <Route
            path="/profile-complete"
            element={<ProfileComplete />}
          />


          {/* ==================================================
              MAIN APPLICATION
          ================================================== */}

          <Route
            path="/home"
            element={<Home />}
          />

          <Route
            path="/review-job"
            element={<JobDetails />}
          />

          <Route
            path="/account-details"
            element={<AccountDetails />}
          />

          <Route
            path="/applied-jobs"
            element={<AppliedJobs />}
          />

            

          <Route
            path="/pending-approvals"
            element={<PendingApprovals />}
          />

          <Route
            path="/skill-recommendation"
            element={<SkillRecommendation />}
          />

          <Route
            path="/college-mailing"
            element={<CollegeMailing />}
          />


          {/* ==================================================
              FALLBACK
          ================================================== */}

          <Route
            path="*"
            element={
              <Navigate
                to="/login"
                replace
              />
            }
          />

        </Routes>

      </ProfileProvider>

    </BrowserRouter>
  );
}

export default App;
