function GoogleButton({ onClick, disabled = false }) {
  return (
    <button
      type="button"
      className="google-button"
      onClick={onClick}
      disabled={disabled}
    >
      <span className="google-icon">G</span>
      <span>Continue with Google</span>
    </button>
  );
}

export default GoogleButton;