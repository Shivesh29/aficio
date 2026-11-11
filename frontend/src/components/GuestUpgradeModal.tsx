import React, { useEffect, useRef } from 'react';
import { usePlayer } from '../context/PlayerContext';
import { useAuth } from '../context/useAuth';
import {
  X,
  Sparkles,
  Star,
  ListMusic,
  MessageSquare,
  Radio,
  Users,
  ArrowRight,
} from 'lucide-react';

export const GuestUpgradeModal: React.FC = () => {
  const { isGuestPromptOpen, closeGuestPrompt } = usePlayer();
  const { openAuthModal } = useAuth();
  const backdropMouseDownRef = useRef(false);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && isGuestPromptOpen) {
        closeGuestPrompt();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isGuestPromptOpen, closeGuestPrompt]);

  if (!isGuestPromptOpen) return null;

  const handleRegister = () => {
    closeGuestPrompt();
    openAuthModal('register');
  };

  const handleLogin = () => {
    closeGuestPrompt();
    openAuthModal('login');
  };

  return (
    <div
      className="modal-backdrop guest-upgrade-backdrop"
      onMouseDown={(e) => {
        backdropMouseDownRef.current = e.target === e.currentTarget;
      }}
      onMouseUp={(e) => {
        if (backdropMouseDownRef.current && e.target === e.currentTarget) {
          closeGuestPrompt();
        }
        backdropMouseDownRef.current = false;
      }}
    >
      <div
        className="modal-card guest-upgrade-modal-card"
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-labelledby="guest-upgrade-title"
      >
        <button
          type="button"
          className="modal-close-btn"
          onClick={closeGuestPrompt}
          aria-label="Dismiss and continue listening"
        >
          <X size={18} />
        </button>

        <div className="guest-upgrade-header">
          <div className="guest-upgrade-badge">
            <Sparkles size={22} />
          </div>
          <span className="guest-upgrade-kicker">GUEST LISTENER</span>
          <h2 id="guest-upgrade-title" className="guest-upgrade-title">
            Enjoying the Music on Aficio?
          </h2>
          <p className="guest-upgrade-desc">
            You're free to keep playing all catalog albums without limits! Create a free profile anytime to unlock the full community experience:
          </p>
        </div>

        <div className="guest-upgrade-features">
          <div className="guest-feature-item">
            <div className="guest-feature-icon">
              <Star size={18} />
            </div>
            <div className="guest-feature-text">
              <strong>Ratings & Reviews</strong>
              <span>Score albums 1–5 stars and publish detailed reviews with markdown notes.</span>
            </div>
          </div>

          <div className="guest-feature-item">
            <div className="guest-feature-icon">
              <ListMusic size={18} />
            </div>
            <div className="guest-feature-text">
              <strong>Aesthetic Collections</strong>
              <span>Curate custom track & album lists with auto-generated cover art collages.</span>
            </div>
          </div>

          <div className="guest-feature-item">
            <div className="guest-feature-icon">
              <MessageSquare size={18} />
            </div>
            <div className="guest-feature-text">
              <strong>Direct Messaging & Chat</strong>
              <span>Chat 1-on-1 in real-time with fellow music enthusiasts and trade recommendations.</span>
            </div>
          </div>

          <div className="guest-feature-item">
            <div className="guest-feature-icon">
              <Radio size={18} />
            </div>
            <div className="guest-feature-text">
              <strong>Release Radar</strong>
              <span>Personalized recommendations tailored to what you listen to over time.</span>
            </div>
          </div>

          <div className="guest-feature-item span-full">
            <div className="guest-feature-icon">
              <Users size={18} />
            </div>
            <div className="guest-feature-text">
              <strong>Social Activity Feed</strong>
              <span>Follow reviewers with similar taste and track their recent spins in real time.</span>
            </div>
          </div>
        </div>

        <div className="guest-upgrade-actions">
          <button
            type="button"
            className="btn-primary guest-cta-btn"
            onClick={handleRegister}
          >
            <span>Create Free Account</span>
            <ArrowRight size={16} />
          </button>
          <button
            type="button"
            className="btn-secondary guest-dismiss-btn"
            onClick={closeGuestPrompt}
          >
            Continue as Guest
          </button>
        </div>

        <div className="guest-upgrade-footer">
          <span>Already have an account? </span>
          <button
            type="button"
            className="guest-login-link"
            onClick={handleLogin}
          >
            Sign in
          </button>
        </div>
      </div>
    </div>
  );
};

