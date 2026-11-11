import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  api,
  type UserSettings,
} from '../api/client';
import { useAuth } from '../context/useAuth';
import {
  User,
  Lock,
  Globe,
  Shield,
  ShieldCheck,
  Trash2,
  Loader2,
  Save,
  Eye,
  EyeOff,
  AlertTriangle,
  Check,
  X,
  Key,
  Copy,
  Share2,
} from 'lucide-react';

export const SettingsPage = () => {
  const { user, isAuthenticated, logout, refreshUser, openAuthModal } = useAuth();
  const navigate = useNavigate();

  const [activeTab, setActiveTab] = useState<'profile' | 'security'>('profile');
  const [settings, setSettings] = useState<UserSettings | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [successMsg, setSuccessMsg] = useState('');
  const [errorMsg, setErrorMsg] = useState('');

  // Profile form
  const [displayName, setDisplayName] = useState('');
  const [bio, setBio] = useState('');
  const [avatarUrl, setAvatarUrl] = useState('');
  const [isPrivate, setIsPrivate] = useState(false);
  const [copiedListenerCode, setCopiedListenerCode] = useState(false);
  const [copiedProfileUrl, setCopiedProfileUrl] = useState(false);

  const activeListenerCode = settings?.listenerCode || user?.listenerCode;

  const handleCopyListenerCode = () => {
    if (!activeListenerCode) return;
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(activeListenerCode).then(() => {
        setCopiedListenerCode(true);
        setTimeout(() => setCopiedListenerCode(false), 2500);
      }).catch(() => {
        setCopiedListenerCode(true);
        setTimeout(() => setCopiedListenerCode(false), 2500);
      });
    } else {
      const input = document.createElement('input');
      input.value = activeListenerCode;
      document.body.appendChild(input);
      input.select();
      document.execCommand('copy');
      document.body.removeChild(input);
      setCopiedListenerCode(true);
      setTimeout(() => setCopiedListenerCode(false), 2500);
    }
  };

  const handleCopyProfileUrl = () => {
    const code = settings?.listenerCode || user?.listenerCode;
    const shareKey = isPrivate && code ? code : (settings?.username || user?.username);
    if (!shareKey) return;
    const url = `${window.location.origin}/u/${shareKey}`;
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(url).then(() => {
        setCopiedProfileUrl(true);
        setTimeout(() => setCopiedProfileUrl(false), 2500);
      }).catch(() => {
        setCopiedProfileUrl(true);
        setTimeout(() => setCopiedProfileUrl(false), 2500);
      });
    } else {
      const input = document.createElement('input');
      input.value = url;
      document.body.appendChild(input);
      input.select();
      document.execCommand('copy');
      document.body.removeChild(input);
      setCopiedProfileUrl(true);
      setTimeout(() => setCopiedProfileUrl(false), 2500);
    }
  };

  // Username form
  const [newUsername, setNewUsername] = useState('');
  const [usernameSaving, setUsernameSaving] = useState(false);
  const [usernameMsg, setUsernameMsg] = useState('');
  const [usernameError, setUsernameError] = useState('');


  // Password form
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showCurrentPassword, setShowCurrentPassword] = useState(false);
  const [showNewPassword, setShowNewPassword] = useState(false);
  const [passwordSaving, setPasswordSaving] = useState(false);
  const [passwordMsg, setPasswordMsg] = useState('');
  const [passwordError, setPasswordError] = useState('');

  // 2FA state
  const [twoFactorSetupData, setTwoFactorSetupData] = useState<{ secret: string; qrCodeUri: string } | null>(null);
  const [twoFactorCode, setTwoFactorCode] = useState('');
  const [is2faSubmitting, setIs2faSubmitting] = useState(false);
  const [twoFactorError, setTwoFactorError] = useState('');
  const [twoFactorSuccess, setTwoFactorSuccess] = useState('');

  // Delete account
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [deletePassword, setDeletePassword] = useState('');
  const [deleteLoading, setDeleteLoading] = useState(false);
  const [deleteError, setDeleteError] = useState('');

  useEffect(() => {
    if (!isAuthenticated) {
      openAuthModal('login');
      return;
    }
    loadSettings();
  }, [isAuthenticated]);

  const loadSettings = async () => {
    try {
      setLoading(true);
      const data = await api.auth.getSettings();
      setSettings(data);
      setDisplayName(data.displayName || '');
      setNewUsername(data.username || '');
      setBio(data.bio || '');
      setAvatarUrl(data.avatarUrl || '');
      setIsPrivate(data.isPrivate);
    } catch (err) {
      setErrorMsg(err instanceof Error ? err.message : 'Failed to load settings');
    } finally {
      setLoading(false);
    }
  };

  const handleUpdateUsername = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    const trimmed = newUsername.trim();
    if (!trimmed) {
      setUsernameError('Username cannot be empty.');
      return;
    }
    if (trimmed === settings?.username) {
      setUsernameMsg('Username is already set to @' + trimmed);
      return;
    }
    if (!/^[a-zA-Z0-9_-]{3,30}$/.test(trimmed)) {
      setUsernameError('Username must be 3-30 characters (letters, numbers, underscores, hyphens).');
      return;
    }

    setUsernameSaving(true);
    setUsernameError('');
    setUsernameMsg('');

    try {
      const res = await api.auth.updateUsername(trimmed);
      setSettings((prev) => (prev ? { ...prev, username: res.username } : null));
      setNewUsername(res.username);
      setUsernameMsg(`Username successfully updated to @${res.username}`);
      await refreshUser(res.token);
    } catch (err) {
      setUsernameError(err instanceof Error ? err.message : 'Failed to update username.');
    } finally {
      setUsernameSaving(false);
    }
  };

  const handleSaveProfile = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    setSuccessMsg('');
    setErrorMsg('');
    try {
      const updated = await api.auth.updateSettings({
        displayName: displayName || '',
        bio: bio || '',
        avatarUrl: avatarUrl || '',
        isPrivate,
      });
      setSettings(updated);
      setSuccessMsg('Profile settings saved successfully.');
      await refreshUser();
    } catch (err) {
      setErrorMsg(err instanceof Error ? err.message : 'Failed to save settings');
    } finally {
      setSaving(false);
    }
  };


  const handleChangePassword = async (e: React.FormEvent) => {
    e.preventDefault();
    if (newPassword !== confirmPassword) {
      setPasswordError('New passwords do not match.');
      return;
    }
    setPasswordSaving(true);
    setPasswordMsg('');
    setPasswordError('');
    try {
      const res = await api.auth.changePassword(currentPassword, newPassword);
      setPasswordMsg(res.message || 'Password updated successfully.');
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
    } catch (err) {
      setPasswordError(err instanceof Error ? err.message : 'Failed to change password');
    } finally {
      setPasswordSaving(false);
    }
  };

  const handleStart2faSetup = async () => {
    setIs2faSubmitting(true);
    setTwoFactorError('');
    setTwoFactorSuccess('');
    try {
      const res = await api.auth.setup2fa();
      setTwoFactorSetupData(res);
    } catch {
      setTwoFactorError('Failed to start 2FA setup');
    } finally {
      setIs2faSubmitting(false);
    }
  };

  const handleEnable2fa = async () => {
    setIs2faSubmitting(true);
    setTwoFactorError('');
    try {
      await api.auth.enable2fa(twoFactorCode);
      setTwoFactorSetupData(null);
      setTwoFactorCode('');
      setTwoFactorSuccess('Two-factor authentication enabled successfully!');
      await refreshUser();
      await loadSettings();
    } catch (err) {
      setTwoFactorError(err instanceof Error ? err.message : 'Invalid code');
    } finally {
      setIs2faSubmitting(false);
    }
  };

  const handleDisable2fa = async () => {
    setIs2faSubmitting(true);
    setTwoFactorError('');
    try {
      await api.auth.disable2fa(twoFactorCode);
      setTwoFactorCode('');
      setTwoFactorSuccess('Two-factor authentication disabled.');
      await refreshUser();
      await loadSettings();
    } catch (err) {
      setTwoFactorError(err instanceof Error ? err.message : 'Invalid code');
    } finally {
      setIs2faSubmitting(false);
    }
  };

  const handleDeleteAccount = async () => {
    setDeleteLoading(true);
    setDeleteError('');
    try {
      await api.auth.deleteAccount(deletePassword);
      logout();
      navigate('/');
    } catch (err) {
      setDeleteError(err instanceof Error ? err.message : 'Failed to delete account');
    } finally {
      setDeleteLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="dossier-loading">
        <Loader2 size={36} className="spin-loader" />
        <p>Loading settings...</p>
      </div>
    );
  }

  if (!settings) {
    return (
      <div className="dossier-error">
        <h3>Could not load settings</h3>
        <p>{errorMsg}</p>
      </div>
    );
  }

  return (
    <div className="settings-container">
      {/* ─── Page Header ─── */}
      <header className="settings-page-header">
        <div className="settings-header-titles">
          <h1>Account Settings</h1>
          <p>Manage your profile, playback preferences, and security credentials.</p>
        </div>

        {/* User Identity Chip */}
        <div className="settings-header-user-badge">
          <div className="settings-badge-avatar">
            {avatarUrl || settings.avatarUrl ? (
              <img src={avatarUrl || settings.avatarUrl} alt={settings.username} />
            ) : (
              <span>{(displayName || settings.displayName || settings.username).slice(0, 2).toUpperCase()}</span>
            )}
          </div>
          <div className="settings-badge-info">
            <span className="settings-badge-name">
              {displayName || settings.displayName || settings.username}
            </span>
            <span className="settings-badge-handle">@{settings.username}</span>
          </div>
        </div>
      </header>

      {/* ─── Main 2-Column Grid Layout ─── */}
      <div className="settings-layout-grid">
        {/* Left Sidebar Navigation */}
        <aside className="settings-sidebar">
          <nav className="settings-nav-menu" aria-label="Settings Sections">
            <button
              type="button"
              className={`settings-nav-item ${activeTab === 'profile' ? 'active' : ''}`}
              onClick={() => setActiveTab('profile')}
            >
              <User size={18} className="nav-item-icon" />
              <div className="nav-item-text">
                <span className="nav-item-title">Profile & Identity</span>
                <span className="nav-item-sub">Avatar, bio, and privacy</span>
              </div>
            </button>

            <button
              type="button"
              className={`settings-nav-item ${activeTab === 'security' ? 'active' : ''}`}
              onClick={() => setActiveTab('security')}
            >
              <Lock size={18} className="nav-item-icon" />
              <div className="nav-item-text">
                <span className="nav-item-title">Security & Login</span>
                <span className="nav-item-sub">Password, 2FA, danger zone</span>
              </div>
              {settings.twoFactorEnabled && <span className="nav-item-status-dot active" title="2FA enabled" />}
            </button>
          </nav>
        </aside>

        {/* Right Content Pane */}
        <main className="settings-content-pane">
          {/* ─── TAB 1: Profile & Identity ─── */}
          {activeTab === 'profile' && (
            <div className="settings-pane-card">
              <div className="pane-header">
                <div>
                  <h2>Profile & Identity</h2>
                  <p>Configure how you appear across the Aficio community and catalog.</p>
                </div>
              </div>

              <form onSubmit={handleSaveProfile} className="settings-form">
                {/* Avatar Row */}
                <div className="settings-avatar-row">
                  <div className="avatar-preview-box">
                    {avatarUrl ? (
                      <img
                        src={avatarUrl}
                        alt="Avatar preview"
                        onError={(e) => ((e.target as HTMLElement).style.display = 'none')}
                      />
                    ) : (
                      <div className="avatar-fallback">
                        {(displayName || settings.username).slice(0, 2).toUpperCase()}
                      </div>
                    )}
                  </div>
                  <div className="avatar-inputs">
                    <label htmlFor="settings-avatar">Avatar Image URL</label>
                    <div className="input-with-action">
                      <input
                        id="settings-avatar"
                        type="url"
                        value={avatarUrl}
                        onChange={(e) => setAvatarUrl(e.target.value)}
                        placeholder="https://example.com/avatar.jpg"
                        maxLength={512}
                        className="settings-input"
                      />
                      {avatarUrl && (
                        <button
                          type="button"
                          className="input-clear-btn"
                          onClick={() => setAvatarUrl('')}
                          title="Clear avatar URL"
                        >
                          <X size={14} />
                        </button>
                      )}
                    </div>
                    <span className="settings-hint">Paste a direct link to an image (JPG, PNG, or WebP).</span>
                  </div>
                </div>

                <div className="settings-divider" />

                {/* Identity Fields Grid */}
                <div className="settings-field-grid">
                  <div className="settings-field">
                    <label htmlFor="settings-displayname">Display Name</label>
                    <input
                      id="settings-displayname"
                      type="text"
                      value={displayName}
                      onChange={(e) => setDisplayName(e.target.value)}
                      placeholder="e.g. Joy Division Archivist"
                      maxLength={100}
                      className="settings-input"
                    />
                    <span className="settings-hint">Public name displayed on reviews and collections.</span>
                  </div>

                  <div className="settings-field">
                    <div className="label-with-badge">
                      <label htmlFor="settings-username">Username (Handle)</label>
                      {newUsername.trim() !== settings.username && (
                        <span className="unsaved-pill">Unsaved</span>
                      )}
                    </div>
                    <div className="input-with-action">
                      <input
                        id="settings-username"
                        type="text"
                        value={newUsername}
                        onChange={(e) => {
                          setNewUsername(e.target.value);
                          setUsernameError('');
                          setUsernameMsg('');
                        }}
                        placeholder="e.g. musiclover"
                        maxLength={30}
                        className="settings-input"
                      />
                      {newUsername.trim() !== settings.username && (
                        <button
                          type="button"
                          className="btn-accent-sm"
                          disabled={usernameSaving || !newUsername.trim()}
                          onClick={() => handleUpdateUsername()}
                        >
                          {usernameSaving ? <Loader2 size={13} className="spin-loader" /> : 'Update Handle'}
                        </button>
                      )}
                    </div>
                    {usernameError && <span className="settings-error-text">{usernameError}</span>}
                    {usernameMsg && <span className="settings-success-text">{usernameMsg}</span>}
                    <span className="settings-hint">Unique handle for your profile (/u/{newUsername.trim() || settings.username}) and direct messages.</span>
                  </div>
                </div>

                <div className="settings-field">
                  <div className="label-with-badge">
                    <label htmlFor="settings-email">Registered Email Address</label>
                    <span className="verified-pill">
                      <Check size={11} /> Verified
                    </span>
                  </div>
                  <input
                    id="settings-email"
                    type="email"
                    value={settings.email || user?.email || ''}
                    disabled
                    className="settings-input is-disabled"
                  />
                  <span className="settings-hint">Used for sign-in, account notifications, and recovery.</span>
                </div>

                <div className="settings-field">
                  <div className="label-with-count">
                    <label htmlFor="settings-bio">Biography</label>
                    <span className="char-counter">{bio.length}/500</span>
                  </div>
                  <textarea
                    id="settings-bio"
                    value={bio}
                    onChange={(e) => setBio(e.target.value)}
                    placeholder="Tell the community about your musical taste, favorite records, or audio setup..."
                    maxLength={500}
                    rows={4}
                    className="settings-textarea"
                  />
                </div>

                <div className="settings-divider" />

                {/* Privacy Selection */}
                <div className="settings-field">
                  <label>Library & Activity Privacy</label>
                  <div className="privacy-card-grid">
                    <div
                      className={`privacy-selector-card ${!isPrivate ? 'is-selected' : ''}`}
                      onClick={() => setIsPrivate(false)}
                      role="button"
                      tabIndex={0}
                    >
                      <div className="selector-radio">
                        {!isPrivate && <div className="selector-radio-dot" />}
                      </div>
                      <div className="selector-body">
                        <div className="selector-title-row">
                          <Globe size={16} />
                          <strong>Public Profile</strong>
                        </div>
                        <p>Anyone can view your ratings, reviews, and curated custom lists.</p>
                      </div>
                    </div>

                    <div
                      className={`privacy-selector-card ${isPrivate ? 'is-selected' : ''}`}
                      onClick={() => setIsPrivate(true)}
                      role="button"
                      tabIndex={0}
                    >
                      <div className="selector-radio">
                        {isPrivate && <div className="selector-radio-dot" />}
                      </div>
                      <div className="selector-body">
                        <div className="selector-title-row">
                          <Lock size={16} />
                          <strong>Private Profile</strong>
                        </div>
                        <p>Only followers you explicitly approve can view your activity, network, and lists. Private accounts are hidden from discovery and community search.</p>
                      </div>
                    </div>
                  </div>
                </div>

                {/* Unique Listener Code & Direct Sharing */}
                <div className="settings-field">
                  <label>Your Listener Code & Direct Sharing</label>
                  <div className="listener-code-settings-card">
                    <div className="listener-code-settings-top">
                      <div className="listener-code-digits-wrap">
                        <Key size={16} style={{ color: 'var(--accent-gold)' }} />
                        <span className="listener-code-number">{activeListenerCode || 'AF-XXXX-XXXX'}</span>
                      </div>
                      <div className="listener-code-settings-actions">
                        <button
                          type="button"
                          className="btn-secondary-sm"
                          onClick={handleCopyListenerCode}
                          title="Copy your random Listener Code"
                        >
                          {copiedListenerCode ? <Check size={13} style={{ color: '#10b981' }} /> : <Copy size={13} />}
                          <span>{copiedListenerCode ? 'Copied' : 'Copy Code'}</span>
                        </button>
                        <button
                          type="button"
                          className="btn-secondary-sm"
                          onClick={handleCopyProfileUrl}
                          title="Copy direct profile link"
                        >
                          {copiedProfileUrl ? <Check size={13} style={{ color: '#10b981' }} /> : <Share2 size={13} />}
                          <span>{copiedProfileUrl ? 'Link Copied' : 'Copy Link'}</span>
                        </button>
                      </div>
                    </div>
                    <p className="settings-field-hint" style={{ marginTop: '0.65rem' }}>
                      <strong>How sharing works:</strong> When your account is private, you are completely hidden from public username and display name search results. Friends can find and follow you by pasting your random Listener Code into Search or opening your direct profile link.
                    </p>
                  </div>
                </div>

                {successMsg && (
                  <div className="settings-alert-banner success">
                    <Check size={16} />
                    <span>{successMsg}</span>
                  </div>
                )}
                {errorMsg && (
                  <div className="settings-alert-banner error">
                    <AlertTriangle size={16} />
                    <span>{errorMsg}</span>
                  </div>
                )}

                <div className="settings-footer-actions">
                  <button type="submit" className="btn-primary" disabled={saving}>
                    {saving ? <Loader2 size={16} className="spin-loader" /> : <Save size={16} />}
                    <span>{saving ? 'Saving...' : 'Save Profile Changes'}</span>
                  </button>
                </div>
              </form>
            </div>
          )}

          {/* ─── TAB 2: Security & Login ─── */}
          {activeTab === 'security' && (
            <div className="settings-stack-panes">
              {/* Change Password Card */}
              <div className="settings-pane-card">
                <div className="pane-header">
                  <div>
                    <h2>Change Password</h2>
                    <p>Update the password used to sign in to your Aficio account.</p>
                  </div>
                </div>

                <form onSubmit={handleChangePassword} className="settings-form">
                  <div className="settings-field">
                    <label htmlFor="current-password">Current Password</label>
                    <div className="input-with-action">
                      <input
                        id="current-password"
                        type={showCurrentPassword ? 'text' : 'password'}
                        value={currentPassword}
                        onChange={(e) => setCurrentPassword(e.target.value)}
                        required
                        className="settings-input"
                      />
                      <button
                        type="button"
                        className="input-action-btn"
                        onClick={() => setShowCurrentPassword(!showCurrentPassword)}
                        tabIndex={-1}
                      >
                        {showCurrentPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                      </button>
                    </div>
                  </div>

                  <div className="settings-field-grid">
                    <div className="settings-field">
                      <label htmlFor="new-password">New Password</label>
                      <div className="input-with-action">
                        <input
                          id="new-password"
                          type={showNewPassword ? 'text' : 'password'}
                          value={newPassword}
                          onChange={(e) => setNewPassword(e.target.value)}
                          required
                          minLength={6}
                          className="settings-input"
                        />
                        <button
                          type="button"
                          className="input-action-btn"
                          onClick={() => setShowNewPassword(!showNewPassword)}
                          tabIndex={-1}
                        >
                          {showNewPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                        </button>
                      </div>
                    </div>

                    <div className="settings-field">
                      <label htmlFor="confirm-password">Confirm New Password</label>
                      <input
                        id="confirm-password"
                        type="password"
                        value={confirmPassword}
                        onChange={(e) => setConfirmPassword(e.target.value)}
                        required
                        minLength={6}
                        className="settings-input"
                      />
                    </div>
                  </div>

                  {passwordMsg && (
                    <div className="settings-alert-banner success">
                      <Check size={16} /> <span>{passwordMsg}</span>
                    </div>
                  )}
                  {passwordError && (
                    <div className="settings-alert-banner error">
                      <AlertTriangle size={16} /> <span>{passwordError}</span>
                    </div>
                  )}

                  <div className="settings-footer-actions">
                    <button type="submit" className="btn-secondary" disabled={passwordSaving}>
                      {passwordSaving ? <Loader2 size={16} className="spin-loader" /> : <Lock size={16} />}
                      <span>{passwordSaving ? 'Updating...' : 'Update Password'}</span>
                    </button>
                  </div>
                </form>
              </div>

              {/* Two-Factor Authentication Card */}
              <div className="settings-pane-card">
                <div className="pane-header">
                  <div>
                    <h2>Two-Factor Authentication (2FA)</h2>
                    <p>Add an extra layer of protection using a TOTP authenticator app.</p>
                  </div>
                </div>

                <div className="settings-form">
                  <div className="twofa-status-banner">
                    <ShieldCheck size={22} className={settings.twoFactorEnabled ? 'text-emerald' : 'text-muted'} />
                    <div className="twofa-status-text">
                      <strong>Status: {settings.twoFactorEnabled ? 'Enabled' : 'Disabled'}</strong>
                      <p>
                        {settings.twoFactorEnabled
                          ? 'Your account is secured with two-factor authentication.'
                          : 'Two-factor authentication is not active on this account.'}
                      </p>
                    </div>
                  </div>

                  {twoFactorSuccess && (
                    <div className="settings-alert-banner success">
                      <Check size={16} /> <span>{twoFactorSuccess}</span>
                    </div>
                  )}
                  {twoFactorError && (
                    <div className="settings-alert-banner error">
                      <AlertTriangle size={16} /> <span>{twoFactorError}</span>
                    </div>
                  )}

                  {twoFactorSetupData ? (
                    <div className="twofa-setup-box">
                      <p className="setup-instruction">Scan this QR code with your authenticator app (Google Authenticator, 1Password, Authy):</p>
                      <div className="twofa-qr-frame">
                        <img src={twoFactorSetupData.qrCodeUri} alt="2FA QR Code" />
                      </div>
                      <p className="setup-secret">
                        Or enter setup secret manually: <code>{twoFactorSetupData.secret}</code>
                      </p>
                      <div className="twofa-verify-form">
                        <input
                          type="text"
                          placeholder="000000"
                          value={twoFactorCode}
                          onChange={(e) => setTwoFactorCode(e.target.value)}
                          maxLength={6}
                          className="settings-input twofa-input font-mono"
                        />
                        <button
                          type="button"
                          className="btn-primary"
                          onClick={handleEnable2fa}
                          disabled={is2faSubmitting || twoFactorCode.length < 6}
                        >
                          {is2faSubmitting ? 'Verifying...' : 'Verify & Enable'}
                        </button>
                      </div>
                    </div>
                  ) : (
                    <div className="settings-footer-actions">
                      {!settings.twoFactorEnabled ? (
                        <button
                          type="button"
                          className="btn-secondary"
                          onClick={handleStart2faSetup}
                          disabled={is2faSubmitting}
                        >
                          <Shield size={16} />
                          <span>{is2faSubmitting ? 'Starting...' : 'Set Up Two-Factor Authentication'}</span>
                        </button>
                      ) : (
                        <div className="twofa-disable-form">
                          <input
                            type="text"
                            placeholder="Current 2FA code"
                            value={twoFactorCode}
                            onChange={(e) => setTwoFactorCode(e.target.value)}
                            maxLength={6}
                            className="settings-input twofa-input font-mono"
                          />
                          <button
                            type="button"
                            className="btn-secondary"
                            onClick={handleDisable2fa}
                            disabled={is2faSubmitting || !twoFactorCode}
                          >
                            Disable 2FA
                          </button>
                        </div>
                      )}
                    </div>
                  )}
                </div>
              </div>

              {/* Danger Zone */}
              <div className="settings-pane-card danger-zone">
                <div className="pane-header">
                  <div>
                    <h2 className="text-danger">Danger Zone</h2>
                    <p>Irreversible actions regarding your Aficio account.</p>
                  </div>
                </div>

                <div className="settings-form">
                  <p className="danger-description">
                    Permanently delete your Aficio account and all associated listening history, ratings, reviews, and custom lists. This action is immediate and non-reversible.
                  </p>

                  {!showDeleteConfirm ? (
                    <div className="settings-footer-actions">
                      <button
                        type="button"
                        className="btn-danger"
                        onClick={() => setShowDeleteConfirm(true)}
                      >
                        <Trash2 size={16} />
                        <span>Delete My Account</span>
                      </button>
                    </div>
                  ) : (
                    <div className="danger-confirm-box">
                      <div className="danger-confirm-header">
                        <AlertTriangle size={18} />
                        <span>Are you sure? Enter your current password to permanently delete your account:</span>
                      </div>
                      <input
                        type="password"
                        value={deletePassword}
                        onChange={(e) => setDeletePassword(e.target.value)}
                        placeholder="Enter your current password"
                        className="settings-input"
                      />
                      {deleteError && (
                        <div className="settings-alert-banner error">
                          <AlertTriangle size={16} /> <span>{deleteError}</span>
                        </div>
                      )}
                      <div className="danger-actions">
                        <button
                          type="button"
                          className="btn-danger"
                          onClick={handleDeleteAccount}
                          disabled={deleteLoading || !deletePassword}
                        >
                          {deleteLoading ? 'Deleting...' : 'Confirm Account Deletion'}
                        </button>
                        <button
                          type="button"
                          className="btn-secondary"
                          onClick={() => {
                            setShowDeleteConfirm(false);
                            setDeletePassword('');
                            setDeleteError('');
                          }}
                        >
                          <X size={16} />
                          <span>Cancel</span>
                        </button>
                      </div>
                    </div>
                  )}
                </div>
              </div>
            </div>
          )}
        </main>
      </div>
    </div>
  );
};


