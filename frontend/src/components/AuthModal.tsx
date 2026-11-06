import { useState, useRef, useEffect, type FormEvent } from 'react';
import { useAuth } from '../context/useAuth';
import { X, Disc3, LogIn, UserPlus, Eye, EyeOff, MailCheck, Copy, Check } from 'lucide-react';

declare global {
  interface Window {
    google?: any;
  }
}

const GoogleIcon = () => (
  <svg className="google-icon-svg" viewBox="0 0 24 24">
    <path
      fill="#4285F4"
      d="M23.745 12.27c0-.7-.06-1.4-.19-2.07H12v4.51h6.6c-.29 1.52-1.14 2.82-2.4 3.68v3.05h3.88c2.27-2.09 3.665-5.17 3.665-9.17Z"
    />
    <path
      fill="#34A853"
      d="M12 24c3.24 0 5.95-1.08 7.93-2.91l-3.88-3.05c-1.08.72-2.45 1.16-4.05 1.16-3.12 0-5.77-2.1-6.72-4.93H1.25v3.15C3.26 21.36 7.33 24 12 24Z"
    />
    <path
      fill="#FBBC05"
      d="M5.28 14.27c-.25-.72-.38-1.49-.38-2.27s.13-1.55.38-2.27V6.58H1.25C.45 8.18 0 10.04 0 12s.45 3.82 1.25 5.42l4.03-3.15Z"
    />
    <path
      fill="#EA4335"
      d="M12 4.75c1.77 0 3.35.61 4.6 1.8l3.42-3.42C17.95 1.19 15.24 0 12 0 7.33 0 3.26 2.64 1.25 6.58l4.03 3.15c.95-2.83 3.6-4.98 6.72-4.98Z"
    />
  </svg>
);

export const AuthModal = () => {
  const {
    isAuthModalOpen,
    authModalTab,
    closeAuthModal,
    openAuthModal,
    login,
    register,
    loginWithGoogle,
    finalizeLogin,
  } = useAuth();

  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  // Registration verification state
  const [registrationSuccess, setRegistrationSuccess] = useState(false);
  const [registeredEmail, setRegisteredEmail] = useState('');
  const [verificationUrl, setVerificationUrl] = useState('');
  const [resending, setResending] = useState(false);
  const [resendSuccessMsg, setResendSuccessMsg] = useState('');
  const [copiedLink, setCopiedLink] = useState(false);

  // Prevent accidental dismissal when user drags cursor while selecting/copying text
  const backdropMouseDownRef = useRef(false);

  const handleBackdropMouseDown = (e: React.MouseEvent<HTMLDivElement>) => {
    backdropMouseDownRef.current = e.target === e.currentTarget;
  };

  const handleBackdropMouseUp = (e: React.MouseEvent<HTMLDivElement>) => {
    if (backdropMouseDownRef.current && e.target === e.currentTarget) {
      closeAuthModal();
    }
    backdropMouseDownRef.current = false;
  };

  const handleCopyVerificationLink = () => {
    if (!verificationUrl) return;
    navigator.clipboard.writeText(verificationUrl);
    setCopiedLink(true);
    setTimeout(() => setCopiedLink(false), 2000);
  };

  // 2FA state
  const [is2faStep, setIs2faStep] = useState(false);
  const [totpCode, setTotpCode] = useState('');
  const [tempToken, setTempToken] = useState('');

  // Google OAuth state
  const [googleSubmitting, setGoogleSubmitting] = useState(false);
  const [showGoogleSetupNotice, setShowGoogleSetupNotice] = useState(false);
  const googleBtnContainerRef = useRef<HTMLDivElement>(null);
  const googleClientId = import.meta.env.VITE_GOOGLE_CLIENT_ID;

  useEffect(() => {
    if (!isAuthModalOpen || is2faStep || registrationSuccess || !googleClientId) return;

    let isMounted = true;

    const setupGoogle = () => {
      if (typeof window === 'undefined' || !window.google?.accounts?.id || !isMounted) return;

      try {
        window.google.accounts.id.initialize({
          client_id: googleClientId,
          callback: async (response: { credential?: string }) => {
            if (!response?.credential) return;
            setGoogleSubmitting(true);
            setError('');
            try {
              const res = await loginWithGoogle(response.credential);
              if (res?.requires2fa) {
                setIs2faStep(true);
                setTempToken(res.tempToken!);
              } else {
                closeAuthModal();
              }
            } catch (err) {
              setError(err instanceof Error ? err.message : 'Google authentication failed');
            } finally {
              setGoogleSubmitting(false);
            }
          },
          auto_select: false,
          cancel_on_tap_outside: true,
        });

        if (googleBtnContainerRef.current) {
          googleBtnContainerRef.current.innerHTML = '';
          window.google.accounts.id.renderButton(googleBtnContainerRef.current, {
            type: 'standard',
            theme: 'outline',
            size: 'large',
            text: authModalTab === 'login' ? 'signin_with' : 'signup_with',
            shape: 'rectangular',
            width: Math.min(googleBtnContainerRef.current.offsetWidth || 380, 400),
            logo_alignment: 'left',
            locale: 'en',
          });
        }
      } catch (err) {
        console.error('Google Sign-In initialization error:', err);
      }
    };

    if (window.google?.accounts?.id) {
      setupGoogle();
    } else {
      const timer = setInterval(() => {
        if (window.google?.accounts?.id) {
          clearInterval(timer);
          setupGoogle();
        }
      }, 200);
      return () => {
        isMounted = false;
        clearInterval(timer);
      };
    }

    return () => {
      isMounted = false;
    };
  }, [isAuthModalOpen, authModalTab, is2faStep, registrationSuccess, googleClientId, loginWithGoogle, closeAuthModal]);

  if (!isAuthModalOpen) return null;

  const resetFields = () => {
    setError('');
    setRegistrationSuccess(false);
    setIs2faStep(false);
    setPassword('');
    setConfirmPassword('');
    setShowPassword(false);
    setShowConfirmPassword(false);
    setResendSuccessMsg('');
    setShowGoogleSetupNotice(false);
  };

  const handleTabSwitch = (tab: 'login' | 'register') => {
    resetFields();
    openAuthModal(tab);
  };

  const handleResendVerification = async () => {
    if (!registeredEmail) return;
    setResending(true);
    setError('');
    try {
      const { api } = await import('../api/client');
      await api.auth.resendVerification(registeredEmail);
      setResendSuccessMsg('New activation email sent! Check your inbox.');
      setTimeout(() => setResendSuccessMsg(''), 5000);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not resend verification email');
    } finally {
      setResending(false);
    }
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    setSubmitting(true);

    try {
      if (is2faStep) {
        // Verify 2FA code
        const { api } = await import('../api/client');
        const res = await api.auth.verify2fa(totpCode, tempToken);
        finalizeLogin(username, res.token);
      } else if (authModalTab === 'login') {
        const res = await login(username, password);
        if (res?.requires2fa) {
          setIs2faStep(true);
          setTempToken(res.tempToken!);
          setSubmitting(false);
          return;
        }
      } else {
        // Register validation
        if (!username.trim()) throw new Error('Username is required');
        if (!email.trim()) throw new Error('Email address is required');
        if (!password) throw new Error('Password is required');
        if (password.length < 6) {
          throw new Error('Password must be at least 6 characters long');
        }
        if (password !== confirmPassword) {
          throw new Error('Passwords do not match. Please verify your password entry.');
        }

        const res = await register(username.trim(), email.trim(), password);
        setRegisteredEmail(email.trim());

        let directLink = res?.verificationUrl || '';
        if (directLink && typeof window !== 'undefined') {
          try {
            const urlObj = new URL(directLink);
            directLink = `${window.location.origin}${urlObj.pathname}${urlObj.search}`;
          } catch {
            // fallback
          }
        }
        setVerificationUrl(directLink);
        setRegistrationSuccess(true);
        return;
      }

      // Reset form on success if not 2FA
      if (!is2faStep) {
        setUsername('');
        setEmail('');
        setPassword('');
        setConfirmPassword('');
      }
      setIs2faStep(false);
      setTotpCode('');
      setTempToken('');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Authentication failed');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      className="modal-backdrop"
      onMouseDown={handleBackdropMouseDown}
      onMouseUp={handleBackdropMouseUp}
    >
      <div className="modal-card auth-modal-card" onClick={(e) => e.stopPropagation()}>
        <button className="modal-close-btn" onClick={closeAuthModal} aria-label="Close modal">
          <X size={18} />
        </button>

        <div className="auth-header">
          <div className="auth-icon-badge">
            <Disc3 size={20} style={{ color: 'var(--accent-gold)' }} />
          </div>
          <h2>{authModalTab === 'login' ? 'Welcome back' : 'Join Aficio'}</h2>
          <p>
            {authModalTab === 'login'
              ? 'Sign in to review albums, curate lists, and talk music.'
              : 'Create an account to review albums, build lists, and share your music taste.'}
          </p>
        </div>

        <div className="auth-tabs" style={{ display: is2faStep || registrationSuccess ? 'none' : 'flex' }}>
          <button
            className={`auth-tab ${authModalTab === 'login' ? 'active' : ''}`}
            onClick={() => handleTabSwitch('login')}
            type="button"
          >
            <LogIn size={15} /> Sign In
          </button>
          <button
            className={`auth-tab ${authModalTab === 'register' ? 'active' : ''}`}
            onClick={() => handleTabSwitch('register')}
            type="button"
          >
            <UserPlus size={15} /> Create Account
          </button>
        </div>

        {error && (
          <div className="auth-error-banner" style={{ display: 'flex', flexDirection: 'column', gap: '0.4rem' }}>
            <span>{error}</span>
            {error.toLowerCase().includes('verify') && (
              <a
                href="/verify-email"
                style={{ color: 'var(--accent-gold)', textDecoration: 'underline', fontSize: '0.8rem', fontWeight: 600 }}
              >
                Have an activation code or need to resend? Click here &rarr;
              </a>
            )}
          </div>
        )}

        {registrationSuccess && authModalTab === 'register' ? (
          <div className="auth-verification-box" style={{ textAlign: 'center', padding: '0.5rem 0' }}>
            <div
              style={{
                width: '54px',
                height: '54px',
                borderRadius: '50%',
                background: 'rgba(230, 161, 71, 0.15)',
                color: 'var(--accent-gold)',
                display: 'grid',
                placeItems: 'center',
                margin: '0 auto 1.25rem',
              }}
            >
              <MailCheck size={28} />
            </div>
            <h3 style={{ fontSize: '1.3rem', fontWeight: 700, marginBottom: '0.5rem' }}>Verify Your Email</h3>
            <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem', marginBottom: '1.25rem', lineHeight: '1.5' }}>
              We've sent an activation link to <strong style={{ color: 'var(--text-main)' }}>{registeredEmail}</strong>.<br />
              Please check your inbox (or spam folder) and click the link to activate your account.
            </p>

            {resendSuccessMsg && (
              <div
                style={{
                  padding: '0.65rem 1rem',
                  backgroundColor: 'rgba(16, 185, 129, 0.15)',
                  color: 'var(--accent-emerald)',
                  borderRadius: 'var(--radius-sm)',
                  fontSize: '0.85rem',
                  marginBottom: '1rem',
                  fontWeight: 600,
                }}
              >
                {resendSuccessMsg}
              </div>
            )}

            {verificationUrl && (
              <div
                style={{
                  marginBottom: '1.5rem',
                  padding: '0.85rem',
                  background: 'rgba(230, 161, 71, 0.08)',
                  border: '1px solid rgba(230, 161, 71, 0.25)',
                  borderRadius: 'var(--radius-sm)',
                  textAlign: 'left',
                }}
              >
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    marginBottom: '0.35rem',
                  }}
                >
                  <span
                    style={{
                      fontSize: '0.75rem',
                      fontWeight: 600,
                      color: 'var(--accent-gold)',
                      textTransform: 'uppercase',
                      letterSpacing: '0.04em',
                    }}
                  >
                    Direct Activation Link
                  </span>
                  <button
                    type="button"
                    onClick={handleCopyVerificationLink}
                    style={{
                      background: 'none',
                      border: 'none',
                      color: copiedLink ? '#34d399' : 'var(--text-muted)',
                      cursor: 'pointer',
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: '0.25rem',
                      fontSize: '0.75rem',
                      fontWeight: 600,
                      padding: '0.15rem 0.35rem',
                    }}
                    title="Copy verification link"
                  >
                    {copiedLink ? <Check size={13} /> : <Copy size={13} />}
                    <span>{copiedLink ? 'Copied!' : 'Copy Link'}</span>
                  </button>
                </div>
                <a
                  href={verificationUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  style={{
                    color: 'var(--text-main)',
                    fontSize: '0.825rem',
                    textDecoration: 'underline',
                    wordBreak: 'break-all',
                    display: 'block',
                  }}
                >
                  Click here to verify {registeredEmail} &rarr;
                </a>
              </div>
            )}

            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.65rem' }}>
              <button
                type="button"
                className="auth-submit-btn"
                onClick={() => {
                  setRegistrationSuccess(false);
                  setPassword('');
                  setConfirmPassword('');
                  openAuthModal('login');
                }}
              >
                Proceed to Sign In
              </button>
              <button
                type="button"
                onClick={handleResendVerification}
                disabled={resending}
                style={{
                  color: 'var(--text-muted)',
                  fontSize: '0.85rem',
                  textDecoration: 'underline',
                  padding: '0.4rem',
                  cursor: 'pointer',
                  background: 'none',
                  border: 'none',
                }}
              >
                {resending ? 'Sending...' : "Didn't receive the email? Resend verification"}
              </button>
            </div>
          </div>
        ) : is2faStep ? (
          <form onSubmit={handleSubmit} className="auth-form">
            <div className="form-field">
              <label htmlFor="auth-totp">Enter the 6-digit code from your authenticator app.</label>
              <input
                id="auth-totp"
                type="text"
                placeholder="123456"
                value={totpCode}
                onChange={(e) => setTotpCode(e.target.value)}
                maxLength={6}
                required
                autoFocus
              />
            </div>

            <div style={{ display: 'flex', gap: '1rem' }}>
              <button
                type="button"
                className="auth-submit-btn"
                style={{ backgroundColor: 'var(--bg-surface-hover)', color: 'var(--text-main)' }}
                onClick={() => {
                  setIs2faStep(false);
                  setTotpCode('');
                }}
              >
                Back
              </button>
              <button type="submit" className="auth-submit-btn" disabled={submitting} style={{ flex: 1 }}>
                {submitting ? 'Verifying...' : 'Verify Code'}
              </button>
            </div>
          </form>
        ) : (
          <>
            {/* Google OAuth Section */}
            {googleClientId ? (
              <div
                ref={googleBtnContainerRef}
                style={{
                  width: '100%',
                  display: 'flex',
                  justifyContent: 'center',
                  minHeight: '44px',
                  marginTop: '0.25rem',
                  marginBottom: '0.25rem',
                }}
              />
            ) : (
              <div style={{ marginTop: '0.25rem' }}>
                <button
                  type="button"
                  className="google-oauth-btn"
                  onClick={() => setShowGoogleSetupNotice((prev) => !prev)}
                  disabled={googleSubmitting}
                >
                  <GoogleIcon />
                  <span>{googleSubmitting ? 'Authenticating with Google...' : 'Continue with Google'}</span>
                </button>
                {showGoogleSetupNotice && (
                  <div className="oauth-info-box" style={{ marginTop: '0.65rem' }}>
                    <strong>Google Sign-In Ready:</strong>
                    <p style={{ margin: '0.25rem 0 0.4rem', fontSize: '0.78rem' }}>
                      To enable 1-click Google OAuth, add your Google OAuth Web Client ID as <code>GOOGLE_CLIENT_ID</code> in <code>.env</code>.
                    </p>
                    <p style={{ margin: 0, fontSize: '0.78rem' }}>
                      Standard Aficio email &amp; password authentication is live and operational below!
                    </p>
                  </div>
                )}
              </div>
            )}

            <div className="auth-divider">
              <div className="auth-divider-line" />
              <span className="auth-divider-text">or continue with email</span>
              <div className="auth-divider-line" />
            </div>

            <form onSubmit={handleSubmit} className="auth-form">
              <div className="form-field">
                <label htmlFor="auth-username">
                  {authModalTab === 'login' ? 'Username or Email' : 'Username'}
                </label>
                <input
                  id="auth-username"
                  type="text"
                  placeholder={authModalTab === 'login' ? 'e.g. vinylfanatic or you@example.com' : 'e.g. vinylfanatic'}
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  required
                  autoFocus
                />
              </div>

              {authModalTab === 'register' && (
                <div className="form-field">
                  <label htmlFor="auth-email">Email Address</label>
                  <input
                    id="auth-email"
                    type="email"
                    placeholder="you@example.com"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    required
                  />
                </div>
              )}

              <div className="form-field">
                <label htmlFor="auth-password">Password</label>
                <div className="password-input-wrapper">
                  <input
                    id="auth-password"
                    type={showPassword ? 'text' : 'password'}
                    placeholder={authModalTab === 'register' ? 'At least 6 characters' : '••••••••'}
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    required
                    minLength={authModalTab === 'register' ? 6 : undefined}
                  />
                  <button
                    type="button"
                    className="password-toggle-btn"
                    onClick={() => setShowPassword((prev) => !prev)}
                    aria-label={showPassword ? 'Hide password' : 'Show password'}
                    tabIndex={-1}
                  >
                    {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                  </button>
                </div>
              </div>

              {authModalTab === 'register' && (
                <div className="form-field">
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <label htmlFor="auth-confirm-password">Confirm Password</label>
                    {confirmPassword && (
                      <span
                        className={`password-match-indicator ${
                          password === confirmPassword ? 'matched' : 'mismatched'
                        }`}
                      >
                        {password === confirmPassword ? '✓ Passwords match' : '✕ Passwords do not match'}
                      </span>
                    )}
                  </div>
                  <div className="password-input-wrapper">
                    <input
                      id="auth-confirm-password"
                      type={showConfirmPassword ? 'text' : 'password'}
                      placeholder="Re-enter your password"
                      value={confirmPassword}
                      onChange={(e) => setConfirmPassword(e.target.value)}
                      required
                    />
                    <button
                      type="button"
                      className="password-toggle-btn"
                      onClick={() => setShowConfirmPassword((prev) => !prev)}
                      aria-label={showConfirmPassword ? 'Hide password' : 'Show password'}
                      tabIndex={-1}
                    >
                      {showConfirmPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                    </button>
                  </div>
                </div>
              )}

              <button type="submit" className="auth-submit-btn" disabled={submitting}>
                {submitting
                  ? 'Please wait...'
                  : authModalTab === 'login'
                  ? 'Sign In to Aficio'
                  : 'Create Account & Send Verification'}
              </button>
            </form>
          </>
        )}

        <div className="auth-footer-hint" style={{ display: is2faStep || registrationSuccess ? 'none' : 'block' }}>
          {authModalTab === 'login' ? (
            <span>
              Don't have an account yet?{' '}
              <button
                type="button"
                onClick={() => handleTabSwitch('register')}
              >
                Create one now
              </button>
            </span>
          ) : (
            <span>
              Already a member?{' '}
              <button
                type="button"
                onClick={() => handleTabSwitch('login')}
              >
                Sign in
              </button>
            </span>
          )}
        </div>
      </div>
    </div>
  );
};
