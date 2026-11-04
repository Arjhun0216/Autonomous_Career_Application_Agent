import { createContext, useCallback, useContext, useEffect, useState } from "react";

const ProfileContext = createContext();
const defaultProfile = {
  personal: {
    name: "",
    firstName: "",
    lastName: "",
    countryCode: "+91",
    gender: "",
    professionalTitle: "",
    location: "",
    phone: "",
    email: "",
    userExperience: 0,

    githubUrl: "",
    linkedinUrl: "",
    portfolioUrl: "",

    otherLinks: [],
  },

  education: [],

  skills: [],

  experience: [],

  projects: [],

  additional: {
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
  },
};
export function ProfileProvider({ children }) {
  const [profile, setProfile] = useState(() => {
    const saved =
    localStorage.getItem("jobPlatformProfile");

if (!saved) {
    return defaultProfile;
}

try {

    const parsed = JSON.parse(saved);

    return {
        ...defaultProfile,

        ...parsed,

        personal: {
            ...defaultProfile.personal,
            ...(parsed.personal || {}),

            userExperience:
                parsed.personal?.userExperience ??
                0,
        },

        education:
            parsed.education || [],

        skills:
            parsed.skills || [],

        experience:
            parsed.experience || [],

        projects:
            parsed.projects || [],

        additional: {
            ...defaultProfile.additional,
            ...(parsed.additional || {}),
        },
    };

} catch (error) {

    console.error(
        "Invalid saved profile:",
        error
    );

    return defaultProfile;
}
  });

  useEffect(() => {
    localStorage.setItem(
      "jobPlatformProfile",
      JSON.stringify(profile)
    );
  }, [profile]);

  const updateSection = useCallback((section, data) => {
    setProfile((previous) => ({
      ...previous,
      [section]: data,
    }));
  }, []);

  const updatePersonal = useCallback((data) => {
    setProfile((previous) => ({
      ...previous,
      personal: {
        ...previous.personal,
        ...data,
      },
    }));
  }, []);

  const clearProfile = useCallback(() => {
    localStorage.removeItem("jobPlatformProfile");
    setProfile(JSON.parse(JSON.stringify(defaultProfile)));
  }, []);

  return (
    <ProfileContext.Provider
      value={{
        profile,
        updateSection,
        updatePersonal,
        clearProfile,
      }}
    >
      {children}
    </ProfileContext.Provider>
  );
}

export function useProfile() {
  return useContext(ProfileContext);
}
