import { useEffect, useState } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import { useAuth } from '../context/AuthContext';
import { Mail } from 'lucide-react';
import './VerifyEmailPage.css';

export function VerifyEmailPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');
  const navigate = useNavigate();
  const { openAuthModal } = useAuth();

  const [status, setStatus] = useState<'loading' | 'success' | 'error'>('loading');
  const [message, setMessage] = useState('');
  const [resendEmail, setResendEmail] = useState('');
  const [resending, setResending] = useState(false);
  const [resendMessage, setResendMessage] = useState('');

  useEffect(() => {
    if (!token) {
      setStatus('error');
      setMessage('No verification token provided.');
      return;
    }

    api.auth.verifyEmail(token)
      .then((res) => {
        setStatus('success');
        setMessage(res.message || 'Your email has been successfully verified.');
      })
      .catch((err) => {
        setStatus('error');
        setMessage(err.message || 'Failed to verify email. The token might be expired or invalid.');
      });
  }, [token]);

  const handleResend = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!resendEmail.trim()) return;
    setResending(true);
    setResendMessage('');
    try {
      const res = await api.auth.resendVerification(resendEmail.trim());
      setResendMessage(res.message || 'If an unverified account exists, a new verification email has been sent.');
    } catch (err) {
      setResendMessage(err instanceof Error ? err.message : 'Failed to resend verification email.');
    } finally {
      setResending(false);
    }
  };

  const handleLoginClick = () => {
    navigate('/');
    openAuthModal();
  };

  return (
    <div className="verify-email-page">
      <div className="verify-email-container">
        <h2>Email Verification</h2>

        {status === 'loading' && (
          <div className="verify-loading">
            <p>Verifying your email...</p>
          </div>
        )}

        {status === 'success' && (
          <div className="verify-success">
            <p className="success-message">{message}</p>
            <button className="aficio-btn-primary" onClick={handleLoginClick}>
              Go to Login
            </button>
          </div>
        )}

        {status === 'error' && (
          <div className="verify-error">
            <p className="error-message">{message}</p>
            <button className="aficio-btn-secondary" onClick={() => navigate('/')} style={{ marginRight: '8px' }}>
              Return to Home
            </button>
            <button className="aficio-btn-secondary" onClick={() => setResendEmail('')}>
              Resend Verification Email
            </button>
          </div>
        )}

        {(status === 'error' || status === 'loading') && (
          <form onSubmit={handleResend} className="resend-verification-form" style={{ marginTop: '1rem' }}>
            <p style={{ marginBottom: '0.5rem', fontSize: '0.9rem', color: 'var(--text-muted)' }}>
              <Mail size={14} style={{ marginRight: '6px', verticalAlign: 'middle' }} />
              Enter your email to receive a new verification link.
            </p>
            <input
              type="email"
              placeholder="you@example.com"
              value={resendEmail}
              onChange={(e) => setResendEmail(e.target.value)}
              required
            />
            <button type="submit" className="aficio-btn-primary" disabled={resending}>
              {resending ? 'Sending...' : 'Resend Verification Email'}
            </button>
            {resendMessage && <p className="resend-message">{resendMessage}</p>}
          </form>
        )}
      </div>
    </div>
  );
}
