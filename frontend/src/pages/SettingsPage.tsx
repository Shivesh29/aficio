import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  api,
  type UserSettings,
  type UserQuotaResponse,
  type UserApiKeyStatusResponse,
  getCustomYouTubeApiKey,
  setCustomYouTubeApiKey,
  clearCustomYouTubeApiKey,
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
  ExternalLink,
  Sparkles,
  Radio,
  Laptop,
  Cloud,
  ChevronDown,
  ChevronUp,
} from 'lucide-react';

export const SettingsPage = () => {
  const { user, isAuthenticated, logout, refreshUser, openAuthModal } = useAuth();
  const navigate = useNavigate();

  const [activeTab, setActiveTab] = useState<'profile' | 'streaming' | 'security'>('profile');
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

  // Username form
  const [newUsername, setNewUsername] = useState('');
  const [usernameSaving, setUsernameSaving] = useState(false);
  const [usernameMsg, setUsernameMsg] = useState('');
  const [usernameError, setUsernameError] = useState('');

  // YouTube API Key (BYOK) & Storage Location
  const [personalApiKey, setPersonalApiKey] = useState(() => getCustomYouTubeApiKey() || '');
  const [showPersonalApiKey, setShowPersonalApiKey] = useState(false);
  const [storagePreference, setStoragePreference] = useState<'LOCAL' | 'CLOUD'>('LOCAL');
  const [cloudKeyStatus, setCloudKeyStatus] = useState<UserApiKeyStatusResponse | null>(null);
  const [showConsentDetails, setShowConsentDetails] = useState(false);
  const [isSavingKey, setIsSavingKey] = useState(false);
  const [apiKeySuccess, setApiKeySuccess] = useState('');
  const [apiKeyError, setApiKeyError] = useState('');

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
  const [quota, setQuota] = useState<UserQuotaResponse | null>(null);

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
      api.catalog.getUserQuota().then((q) => setQuota(q)).catch(() => {});
      api.auth.getApiKeyStatus().then((status) => {
        setCloudKeyStatus(status);
        if (status.hasCloudKey) {
          setStoragePreference('CLOUD');
        } else if (getCustomYouTubeApiKey()) {
          setStoragePreference('LOCAL');
        }
      }).catch(() => {});
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

  const handleSaveApiKey = async (e: React.FormEvent) => {
    e.preventDefault();
    setApiKeySuccess('');
    setApiKeyError('');
    setIsSavingKey(true);

    const trimmed = personalApiKey.trim();

    try {
      if (storagePreference === 'LOCAL') {
        if (!trimmed) {
          clearCustomYouTubeApiKey();
          setPersonalApiKey('');
          const updated = await api.auth.saveApiKey('', 'LOCAL');
          setCloudKeyStatus(updated);
          setApiKeySuccess('Personal API key removed. Restored system shared quota.');
          api.catalog.getUserQuota().then(setQuota).catch(() => {});
          return;
        }

        if (!trimmed.startsWith('AIzaSy')) {
          setApiKeyError('Invalid format. Official Google Cloud API keys start with "AIzaSy".');
          setIsSavingKey(false);
          return;
        }

        setCustomYouTubeApiKey(trimmed);
        const updated = await api.auth.saveApiKey('', 'LOCAL');
        setCloudKeyStatus(updated);
        setApiKeySuccess('Key saved privately in your local browser storage (Zero-Knowledge).');
      } else {
        // CLOUD STORAGE
        if (!trimmed && !cloudKeyStatus?.hasCloudKey) {
          setApiKeyError('Please enter your Google Cloud API key before saving.');
          setIsSavingKey(false);
          return;
        }

        if (!trimmed && cloudKeyStatus?.hasCloudKey) {
          setApiKeySuccess('Active in Encrypted Cloud Vault.');
          setIsSavingKey(false);
          return;
        }

        if (!trimmed.startsWith('AIzaSy')) {
          setApiKeyError('Invalid format. Official Google Cloud API keys start with "AIzaSy".');
          setIsSavingKey(false);
          return;
        }

        const updated = await api.auth.saveApiKey(trimmed, 'CLOUD');
        setCloudKeyStatus(updated);
        clearCustomYouTubeApiKey();
        setPersonalApiKey('');
        setApiKeySuccess('Key encrypted with AES-256-GCM and saved to your Aficio account. Synced across all your devices!');
      }

      api.catalog.getUserQuota().then(setQuota).catch(() => {});
    } catch (err) {
      setApiKeyError(err instanceof Error ? err.message : 'Failed to save API key');
    } finally {
      setIsSavingKey(false);
    }
  };

  const handleClearApiKey = async () => {
    setIsSavingKey(true);
    setApiKeySuccess('');
    setApiKeyError('');
    try {
      clearCustomYouTubeApiKey();
      setPersonalApiKey('');
      if (cloudKeyStatus?.hasCloudKey) {
        await api.auth.deleteApiKey();
        setCloudKeyStatus(null);
      }
      setApiKeySuccess('API key removed from storage. Restored system shared quota.');
      api.catalog.getUserQuota().then(setQuota).catch(() => {});
    } catch (err) {
      setApiKeyError(err instanceof Error ? err.message : 'Failed to remove API key');
    } finally {
      setIsSavingKey(false);
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

  const isDevTier = Boolean(
    settings?.email?.toLowerCase().includes('sharmashivesh29') ||
    settings?.username?.toLowerCase() === 'shivesh' ||
    settings?.username?.toLowerCase() === 'sharmashivesh29' ||
    user?.email?.toLowerCase().includes('sharmashivesh29') ||
    user?.username?.toLowerCase() === 'shivesh' ||
    user?.username?.toLowerCase() === 'sharmashivesh29'
  );

  const hasPersonalKey = Boolean(
    isDevTier ||
    personalApiKey.trim() ||
    cloudKeyStatus?.hasCloudKey ||
    quota?.hasCustomKey ||
    (storagePreference === 'CLOUD' && cloudKeyStatus?.hasCloudKey)
  );

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
              className={`settings-nav-item ${activeTab === 'streaming' ? 'active' : ''}`}
              onClick={() => setActiveTab('streaming')}
            >
              <Key size={18} className="nav-item-icon" />
              <div className="nav-item-text">
                <span className="nav-item-title">Streaming & API Keys</span>
                <span className="nav-item-sub">YouTube quota & BYOK</span>
              </div>
              {hasPersonalKey && <span className="nav-item-status-dot active" title="Personal key active" />}
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

          {/* Account Playback Tier Card */}
          <div className="settings-sidebar-tier-card">
            <div className="tier-header-row">
              <span className="tier-label">PLAYBACK TIER</span>
              {isDevTier ? (
                <span className="tier-tag dev">DEVELOPER</span>
              ) : hasPersonalKey ? (
                <span className="tier-tag custom">BYOK ACTIVE</span>
              ) : (
                <span className="tier-tag standard">STANDARD</span>
              )}
            </div>
            <p className="tier-desc">
              {isDevTier
                ? 'Unlimited playback & queries (sharmashivesh29)'
                : hasPersonalKey
                ? 'Private Google Cloud quota (10k units/day ~99 albums)'
                : '5 complete album stream discoveries'}
            </p>
          </div>
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
                        placeholder="e.g. shivesh"
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
                        <p>Only followers you explicitly approve can view your activity and lists.</p>
                      </div>
                    </div>
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

          {/* ─── TAB 2: Streaming & API Keys ─── */}
          {activeTab === 'streaming' && (
            <div className="settings-pane-card">
              <div className="pane-header">
                <div>
                  <h2>Playback Quota & API Key</h2>
                  <p>Configure YouTube Data API access to enable seamless album streaming and discoveries.</p>
                </div>
              </div>

              <div className="settings-form">
                {/* Quota Summary Card */}
                <div className="quota-summary-card">
                  <div className="quota-summary-badge">
                    {isDevTier ? (
                      <Sparkles size={20} className="tier-icon-dev" />
                    ) : hasPersonalKey ? (
                      <Key size={20} className="tier-icon-custom" />
                    ) : (
                      <Radio size={20} className="tier-icon-standard" />
                    )}
                  </div>
                  <div className="quota-summary-content">
                    <div className="quota-title-row">
                      <h3>
                        {isDevTier
                          ? 'Developer Tier • Unlimited Discovery'
                          : hasPersonalKey
                          ? 'Personal Google Cloud Key Active'
                          : 'Standard Tier • 5 Shared Albums'}
                      </h3>
                      <span className="quota-status-pill active">Active</span>
                    </div>
                    <p>
                      {isDevTier
                        ? 'Your account (sharmashivesh29) has developer bypass privileges with unlimited search queries.'
                        : hasPersonalKey
                        ? 'Streaming is powered by your private 10,000 requests/day quota (~99 daily album discoveries).'
                        : 'Standard accounts share the public server quota for up to 4 new album discoveries per day. Connect your free personal API key below for 99 daily discoveries.'}
                    </p>

                    {quota && !quota.unlimited && (
                      <div className="settings-quota-meter">
                        <div className="meter-labels">
                          <span>Today's New Album Discoveries</span>
                          <strong>{quota.usedToday} / {quota.dailyLimit} Used</strong>
                        </div>
                        <div className="settings-quota-track">
                          <div
                            className={`settings-quota-fill ${quota.isExhausted ? 'exhausted' : quota.usedToday >= (quota.dailyLimit * 0.8) ? 'warning' : ''}`}
                            style={{ width: `${Math.min(100, (quota.usedToday / quota.dailyLimit) * 100)}%` }}
                          />
                        </div>
                        <div className="meter-subnote">
                          {quota.isExhausted ? (
                            <span className="meter-exhausted-text">
                              {hasPersonalKey
                                ? 'Daily personal API key quota reached (99/99 albums). Resets at midnight Pacific Time. All catalogued and cached albums remain 100% free to replay anytime.'
                                : `Daily discovery limit reached (${quota.dailyLimit}/${quota.dailyLimit} albums). Resets at midnight UTC. All catalogued and cached albums remain 100% free to replay anytime.`}
                            </span>
                          ) : (
                            <span>
                              {quota.remaining} new album {quota.remaining === 1 ? 'discovery' : 'discoveries'} remaining today ({hasPersonalKey ? '10k Google Cloud quota' : 'shared server quota'}). Replaying already-catalogued albums is always free and unlimited.
                            </span>
                          )}
                        </div>
                      </div>
                    )}
                  </div>
                </div>

                {/* Storage Choice Selector */}
                <div className="storage-choice-section">
                  <div className="storage-choice-header">
                    <h4>Choose Key Storage Location</h4>
                    <p>Select where your personal Google Cloud API key will be kept based on your privacy and convenience preferences.</p>
                  </div>

                  <div className="storage-options-grid">
                    {/* Option 1: Local Browser */}
                    <div
                      className={`storage-option-card ${storagePreference === 'LOCAL' ? 'active' : ''}`}
                      onClick={() => setStoragePreference('LOCAL')}
                    >
                      <div className="storage-option-radio">
                        <div className={`radio-dot ${storagePreference === 'LOCAL' ? 'selected' : ''}`} />
                      </div>
                      <div className="storage-option-content">
                        <div className="storage-option-title-row">
                          <Laptop size={18} className="text-emerald" />
                          <h5>Local Browser Only</h5>
                          <span className="storage-badge privacy">Highest Privacy</span>
                        </div>
                        <p className="storage-option-desc">
                          Kept exclusively inside your browser's local sandbox (<code>localStorage</code>). Never stored on our backend databases at rest.
                        </p>
                        <ul className="storage-option-bullets">
                          <li>✓ Zero database footprint & zero server-side leak risk</li>
                          <li>✗ Device-specific (will not sync across other computers or phones)</li>
                        </ul>
                      </div>
                    </div>

                    {/* Option 2: Encrypted Cloud Vault */}
                    <div
                      className={`storage-option-card ${storagePreference === 'CLOUD' ? 'active' : ''}`}
                      onClick={() => setStoragePreference('CLOUD')}
                    >
                      <div className="storage-option-radio">
                        <div className={`radio-dot ${storagePreference === 'CLOUD' ? 'selected' : ''}`} />
                      </div>
                      <div className="storage-option-content">
                        <div className="storage-option-title-row">
                          <Cloud size={18} className="text-cyan" />
                          <h5>Encrypted Cloud Vault</h5>
                          <span className="storage-badge encrypted">AES-256-GCM</span>
                        </div>
                        <p className="storage-option-desc">
                          Stored securely on your Aficio account, encrypted at rest using authenticated AES-256-GCM.
                        </p>
                        <ul className="storage-option-bullets">
                          <li>✓ Seamlessly syncs across all your devices (desktop, phone, tablet)</li>
                          <li>✓ Hardware-standard AEAD encryption with per-record Initialization Vectors</li>
                        </ul>
                      </div>
                    </div>
                  </div>

                  {/* Informed Consent Disclosure Accordion */}
                  <div className="informed-consent-box">
                    <button
                      type="button"
                      className="informed-consent-toggle"
                      onClick={() => setShowConsentDetails(!showConsentDetails)}
                    >
                      <div className="consent-toggle-left">
                        <ShieldCheck size={16} className="text-cyan" />
                        <span>Informed Consent & Cryptographic Security Guarantee</span>
                      </div>
                      {showConsentDetails ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
                    </button>

                    {showConsentDetails && (
                      <div className="informed-consent-body">
                        <p>
                          We believe in total transparency. Before providing your personal Google Cloud API key, here is exactly how your data is handled:
                        </p>
                        <div className="consent-points-list">
                          <div className="consent-point">
                            <strong>1. Authenticated Encryption at Rest (AES-256-GCM)</strong>
                            <span>If Cloud Vault is selected, your key is encrypted in PostgreSQL using Galois/Counter Mode (AES-256-GCM) with 128-bit authentication tags and unique 12-byte cryptographically secure random IVs. It is never stored in plaintext in the database.</span>
                          </div>
                          <div className="consent-point">
                            <strong>2. Single-Purpose Scope of Use</strong>
                            <span>Your key is utilized exclusively by the backend playback resolver to query YouTube's official Data API v3 (<code>search.list</code>) to discover official music video identifiers for full albums.</span>
                          </div>
                          <div className="consent-point">
                            <strong>3. Zero Third-Party Sharing or Monetization</strong>
                            <span>Your key is never shared, sold, logged in plain text, or exposed to other users or external third parties.</span>
                          </div>
                          <div className="consent-point">
                            <strong>4. Instant Revocation & Deletion</strong>
                            <span>You can delete your key or switch back to Local Browser Storage at any moment. Doing so immediately purges the ciphertext from PostgreSQL and flushes all Redis in-memory caches.</span>
                          </div>
                        </div>
                      </div>
                    )}
                  </div>
                </div>

                {/* API Key Form */}
                <form onSubmit={handleSaveApiKey} className="api-key-form-section">
                  <div className="settings-field">
                    <label htmlFor="settings-personal-api-key">
                      {storagePreference === 'CLOUD'
                        ? 'Google Cloud YouTube Data API v3 Key (Cloud Encrypted)'
                        : 'Google Cloud YouTube Data API v3 Key (Local Browser)'}
                    </label>
                    <div className="input-with-action">
                      <input
                        id="settings-personal-api-key"
                        type={showPersonalApiKey ? 'text' : 'password'}
                        value={personalApiKey}
                        onChange={(e) => setPersonalApiKey(e.target.value)}
                        placeholder={
                          storagePreference === 'CLOUD' && cloudKeyStatus?.hasCloudKey
                            ? cloudKeyStatus.maskedKey || '••••••••••••••••••••••••••••••••'
                            : 'AIzaSy...'
                        }
                        className="settings-input font-mono"
                        autoComplete="off"
                      />
                      <button
                        type="button"
                        className="input-action-btn"
                        onClick={() => setShowPersonalApiKey(!showPersonalApiKey)}
                        title={showPersonalApiKey ? 'Hide key' : 'Show key'}
                      >
                        {showPersonalApiKey ? <EyeOff size={16} /> : <Eye size={16} />}
                      </button>
                    </div>

                    <div className="settings-hint">
                      {storagePreference === 'CLOUD' && cloudKeyStatus?.hasCloudKey ? (
                        <span className="text-cyan">
                          ✓ Active Cloud Key: <code>{cloudKeyStatus.maskedKey}</code> (AES-256-GCM encrypted in account). Enter a new key above only if you wish to replace it.
                        </span>
                      ) : storagePreference === 'LOCAL' && getCustomYouTubeApiKey() ? (
                        <span className="text-emerald">
                          ✓ Active Local Key: Saved in this browser's local sandbox.
                        </span>
                      ) : (
                        <span>
                          {storagePreference === 'CLOUD'
                            ? 'Your key will be encrypted with AES-256-GCM before saving to your Aficio account.'
                            : 'Your key remains local in your browser and will never touch our database.'}
                        </span>
                      )}
                    </div>
                  </div>

                  {apiKeySuccess && (
                    <div className="settings-alert-banner success">
                      <Check size={16} />
                      <span>{apiKeySuccess}</span>
                    </div>
                  )}
                  {apiKeyError && (
                    <div className="settings-alert-banner error">
                      <AlertTriangle size={16} />
                      <span>{apiKeyError}</span>
                    </div>
                  )}

                  <div className="settings-footer-actions">
                    <button type="submit" className="btn-primary" disabled={isSavingKey}>
                      {isSavingKey ? <Loader2 size={16} className="animate-spin" /> : <Save size={16} />}
                      <span>Save & Apply Preference</span>
                    </button>
                    {(getCustomYouTubeApiKey() || cloudKeyStatus?.hasCloudKey) && (
                      <button
                        type="button"
                        onClick={handleClearApiKey}
                        className="btn-secondary"
                        disabled={isSavingKey}
                      >
                        <Trash2 size={16} />
                        <span>Remove Key</span>
                      </button>
                    )}
                  </div>
                </form>

                <div className="settings-divider" />

                {/* Free Setup Guide Card */}
                <div className="setup-guide-card">
                  <div className="setup-guide-header">
                    <h4>How to get your free Google Cloud API key in 1 minute</h4>
                    <p>Google offers every Google account 10,000 free requests per day (~100 complete album queries) with zero cost or billing required.</p>
                  </div>
                  <ol className="setup-steps-list">
                    <li>
                      <span>Open the 1-click Google Cloud shortcut:</span>
                      <a
                        href="https://console.cloud.google.com/flows/enableapi?apiid=youtube.googleapis.com"
                        target="_blank"
                        rel="noopener noreferrer"
                        className="setup-shortcut-link"
                      >
                        <span>Open Google Cloud Console</span>
                        <ExternalLink size={13} />
                      </a>
                    </li>
                    <li>
                      <span>Click <strong>&quot;Enable&quot;</strong>, then navigate to <strong>Credentials &rarr; Create Credentials &rarr; API Key</strong>.</span>
                    </li>
                    <li>
                      <span>Copy your generated key (starts with <code>AIzaSy</code>) and paste it into the field above.</span>
                    </li>
                  </ol>
                </div>
              </div>
            </div>
          )}

          {/* ─── TAB 3: Security & Login ─── */}
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
