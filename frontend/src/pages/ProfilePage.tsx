import { useEffect, useState, useRef } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { api, isListPublic, type UserProfile, type Review, type MusicList, type MusicListItem } from '../api/client';
import { useAuth } from '../context/useAuth';
import { parseUtcDate } from '../utils/date';
import {
  Star,
  UserPlus,
  UserCheck,
  Users,
  Search,
  ArrowRight,
  Send,
  Loader2,
  Disc3,
  Bookmark,
  Globe,
  Lock,
  X,
  Shield,
  ShieldCheck,
  Clock,
  Check,
  Settings,
  Share2,
  Key,
  Copy,
} from 'lucide-react';

const getAvatarGradient = (name: string) => {
  const gradients = [
    'linear-gradient(135deg, #f59e0b 0%, #b45309 100%)',
    'linear-gradient(135deg, #ec4899 0%, #be185d 100%)',
    'linear-gradient(135deg, #8b5cf6 0%, #6d28d9 100%)',
    'linear-gradient(135deg, #3b82f6 0%, #1d4ed8 100%)',
    'linear-gradient(135deg, #10b981 0%, #047857 100%)',
    'linear-gradient(135deg, #f97316 0%, #c2410c 100%)',
    'linear-gradient(135deg, #06b6d4 0%, #0e7490 100%)',
  ];
  let hash = 0;
  for (let i = 0; i < (name || '').length; i++) {
    hash = name.charCodeAt(i) + ((hash << 5) - hash);
  }
  return gradients[Math.abs(hash) % gradients.length];
};

export const ProfilePage = () => {
  const { username } = useParams<{ username: string }>();
  const { user: currentUser, isAuthenticated, openAuthModal } = useAuth();
  const navigate = useNavigate();

  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [reviews, setReviews] = useState<Review[]>([]);
  const [collections, setCollections] = useState<MusicList[]>([]);
  const [listPreviewMap, setListPreviewMap] = useState<Record<number, string[]>>({});
  const [followStatus, setFollowStatus] = useState<'NONE' | 'PENDING' | 'FOLLOWING'>('NONE');
  const [pendingRequests, setPendingRequests] = useState<any[]>([]);
  const [activeTab, setActiveTab] = useState<'reviews' | 'collections'>('reviews');
  const [loading, setLoading] = useState(true);
  const [followLoading, setFollowLoading] = useState(false);
  const [error, setError] = useState('');

  // Follow list modal state
  const [socialModalType, setSocialModalType] = useState<'followers' | 'following' | null>(null);
  const [socialUsers, setSocialUsers] = useState<any[]>([]);
  const [loadingSocialUsers, setLoadingSocialUsers] = useState(false);
  const [socialModalError, setSocialModalError] = useState('');
  const [socialSearchQuery, setSocialSearchQuery] = useState('');
  const [unfollowingUsername, setUnfollowingUsername] = useState<string | null>(null);
  const socialBackdropMouseDownRef = useRef(false);

  // 2FA state
  const [is2faModalOpen, setIs2faModalOpen] = useState(false);
  const [twoFactorSetupData, setTwoFactorSetupData] = useState<{ secret: string; qrCodeUri: string } | null>(null);
  const [twoFactorCode, setTwoFactorCode] = useState('');
  const [is2faSubmitting, setIs2faSubmitting] = useState(false);
  const [twoFactorError, setTwoFactorError] = useState('');
  const [copiedLink, setCopiedLink] = useState(false);
  const [copiedCode, setCopiedCode] = useState(false);

  const handleShareProfile = () => {
    const isTargetPrivate = profile ? profile.isPrivate : Boolean(currentUser?.isPrivate);
    const code = profile?.listenerCode || currentUser?.listenerCode;
    const shareKey = isTargetPrivate && code ? code : (profile?.username || currentUser?.username);
    if (!shareKey) return;
    const url = `${window.location.origin}/u/${shareKey}`;
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(url).then(() => {
        setCopiedLink(true);
        setTimeout(() => setCopiedLink(false), 2500);
      }).catch(() => {
        setCopiedLink(true);
        setTimeout(() => setCopiedLink(false), 2500);
      });
    } else {
      const input = document.createElement('input');
      input.value = url;
      document.body.appendChild(input);
      input.select();
      document.execCommand('copy');
      document.body.removeChild(input);
      setCopiedLink(true);
      setTimeout(() => setCopiedLink(false), 2500);
    }
  };

  const handleCopyListenerCode = (code: string) => {
    if (!code) return;
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(code).then(() => {
        setCopiedCode(true);
        setTimeout(() => setCopiedCode(false), 2500);
      }).catch(() => {
        setCopiedCode(true);
        setTimeout(() => setCopiedCode(false), 2500);
      });
    } else {
      const input = document.createElement('input');
      input.value = code;
      document.body.appendChild(input);
      input.select();
      document.execCommand('copy');
      document.body.removeChild(input);
      setCopiedCode(true);
      setTimeout(() => setCopiedCode(false), 2500);
    }
  };

  const isSelf = Boolean(
    currentUser && (
      (profile && currentUser.username.toLowerCase() === profile.username.toLowerCase()) ||
      (username && currentUser.username.toLowerCase() === username.toLowerCase()) ||
      (username && currentUser.listenerCode && currentUser.listenerCode.toLowerCase() === username.toLowerCase())
    )
  );

  useEffect(() => {
    async function loadUserProfile() {
      if (!username) return;
      try {
        setLoading(true);
        setError('');
        const prof = await api.social.getUserProfile(username);

        // Fetch user reviews, collections, followers & following concurrently using the resolved username
        const [userRevs, userLists, followers, following] = await Promise.all([
          api.reviews.getUserReviews(prof.id).catch(() => []),
          api.lists.getUserLists(prof.username).catch(() => []),
          api.social.getFollowers(prof.username).catch(() => []),
          api.social.getFollowing(prof.username).catch(() => []),
        ]);
        setProfile({
          ...prof,
          followersCount: typeof prof.followersCount === 'number' ? prof.followersCount : followers.length,
          followingCount: typeof prof.followingCount === 'number' ? prof.followingCount : following.length,
          reviewsCount: typeof prof.reviewsCount === 'number' ? prof.reviewsCount : userRevs.length,
          listsCount: typeof prof.listsCount === 'number' ? prof.listsCount : userLists.length,
        });
        setReviews(userRevs);
        setCollections(userLists);

        // Fetch preview albums for each list (first 4 items for collage)
        const previews: Record<number, string[]> = {};
        await Promise.all(
          userLists.map(async (l) => {
            try {
              const items: MusicListItem[] = await api.lists.getListAlbums(l.id);
              previews[l.id] = items
                .map((item) => item.album?.coverArtUrl)
                .filter(Boolean) as string[];
            } catch {
              previews[l.id] = [];
            }
          })
        );
        setListPreviewMap(previews);

        // Check if currently following and follow status
        const isProfileSelf = currentUser && currentUser.username.toLowerCase() === prof.username.toLowerCase();
        if (isAuthenticated && !isProfileSelf) {
          const statusRes = await api.social.getFollowStatus(prof.username).catch(() => ({
            status: 'NONE' as const,
            following: false,
            pending: false,
          }));
          setFollowStatus(statusRes.status);
        }

        // If viewing self, load any pending follow requests
        if (isAuthenticated && isProfileSelf) {
          const requests = await api.social.getPendingFollowRequests().catch(() => []);
          setPendingRequests(requests);
        }
      } catch (err) {
        setError(err instanceof Error ? err.message : 'User not found');
      } finally {
        setLoading(false);
      }
    }
    void loadUserProfile();
  }, [username, currentUser, isAuthenticated]);

  const handleToggleFollow = async () => {
    if (!isAuthenticated) {
      openAuthModal('login');
      return;
    }
    const targetUsername = profile?.username || username;
    if (!targetUsername) return;

    setFollowLoading(true);
    try {
      if (followStatus === 'FOLLOWING' || followStatus === 'PENDING') {
        await api.social.unfollow(targetUsername);
        if (followStatus === 'FOLLOWING') {
          setProfile((prev) => (prev ? { ...prev, followersCount: Math.max(0, prev.followersCount - 1) } : null));
        }
        setFollowStatus('NONE');
      } else {
        const res = await api.social.follow(targetUsername);
        if (res.status === 'PENDING' || profile?.isPrivate) {
          setFollowStatus('PENDING');
        } else {
          setFollowStatus('FOLLOWING');
          setProfile((prev) => (prev ? { ...prev, followersCount: prev.followersCount + 1 } : null));
        }
      }
    } catch {
      alert('Could not update follow status');
    } finally {
      setFollowLoading(false);
    }
  };

  const handleAcceptRequest = async (requestId: number) => {
    try {
      await api.social.acceptFollowRequest(requestId);
      setPendingRequests((prev) => prev.filter((r) => r.id !== requestId));
      setProfile((prev) => (prev ? { ...prev, followersCount: prev.followersCount + 1 } : null));
    } catch {
      alert('Could not accept follow request');
    }
  };

  const handleDeclineRequest = async (requestId: number) => {
    try {
      await api.social.declineFollowRequest(requestId);
      setPendingRequests((prev) => prev.filter((r) => r.id !== requestId));
    } catch {
      alert('Could not decline follow request');
    }
  };

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && socialModalType) {
        setSocialModalType(null);
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [socialModalType]);

  const handleOpenSocialModal = async (type: 'followers' | 'following') => {
    const targetUsername = profile?.username || username;
    if (!targetUsername) return;
    setSocialModalType(type);
    setSocialSearchQuery('');
    setLoadingSocialUsers(true);
    setSocialModalError('');
    try {
      if (type === 'followers') {
        const list = await api.social.getFollowers(targetUsername);
        setSocialUsers(list.map((f) => f.follower).filter(Boolean));
      } else {
        const list = await api.social.getFollowing(targetUsername);
        setSocialUsers(list.map((f) => f.following).filter(Boolean));
      }
    } catch (err) {
      setSocialModalError(err instanceof Error ? err.message : 'Unable to view list for private account.');
      setSocialUsers([]);
    } finally {
      setLoadingSocialUsers(false);
    }
  };

  const handleUnfollowFromList = async (targetUsername: string, e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    if (!targetUsername || unfollowingUsername) return;

    setUnfollowingUsername(targetUsername);
    try {
      await api.social.unfollow(targetUsername);
      setSocialUsers((prev) => prev.filter((u) => u.username !== targetUsername));
      setProfile((prev) => (prev ? { ...prev, followingCount: Math.max(0, prev.followingCount - 1) } : null));
    } catch {
      alert(`Could not unfollow @${targetUsername}`);
    } finally {
      setUnfollowingUsername(null);
    }
  };

  const handleStart2faSetup = async () => {
    setIs2faSubmitting(true);
    setTwoFactorError('');
    try {
      const res = await api.auth.setup2fa();
      setTwoFactorSetupData(res);
      setIs2faModalOpen(true);
    } catch (err) {
      alert('Failed to start 2FA setup');
    } finally {
      setIs2faSubmitting(false);
    }
  };

  const handleEnable2fa = async () => {
    setIs2faSubmitting(true);
    setTwoFactorError('');
    try {
      await api.auth.enable2fa(twoFactorCode);
      setIs2faModalOpen(false);
      setTwoFactorSetupData(null);
      setTwoFactorCode('');
      alert('2FA successfully enabled!');
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
      setIs2faModalOpen(false);
      setTwoFactorCode('');
      alert('2FA successfully disabled.');
    } catch (err) {
      setTwoFactorError(err instanceof Error ? err.message : 'Invalid code');
    } finally {
      setIs2faSubmitting(false);
    }
  };

  if (loading) {
    return (
      <div className="dossier-loading">
        <Loader2 size={36} className="spin-loader" />
        <p>Loading profile...</p>
      </div>
    );
  }

  if (error || !profile) {
    return (
      <div className="dossier-error">
        <h3>User not found</h3>
        <p>{error || 'This user profile does not exist.'}</p>
        <Link to="/" className="btn-secondary">
          Back to Catalog
        </Link>
      </div>
    );
  }

  const isPrivateLocked = Boolean(profile.isPrivate) && !isSelf && followStatus !== 'FOLLOWING';

  const filteredSocialUsers = socialUsers.filter((u) =>
    (u.username || '').toLowerCase().includes(socialSearchQuery.toLowerCase().trim())
  );

  return (
    <div className="profile-page">
      {/* Profile Header */}
      <header className="profile-hero-card">
        <div className="profile-avatar-large">
          <span>{(profile.displayName || profile.username).charAt(0).toUpperCase()}</span>
        </div>

        <div className="profile-hero-info">
          <div className="profile-title-row">
            <h1>{profile.displayName || profile.username}</h1>
            {profile.displayName && (
              <span className="profile-handle-subtle" style={{ color: 'var(--text-muted)', fontSize: '0.95rem' }}>
                @{profile.username}
              </span>
            )}
            {profile.isPrivate && (
              <span className="profile-role-badge" style={{ display: 'inline-flex', alignItems: 'center', gap: '0.35rem', background: 'var(--bg-surface-elevated)', color: 'var(--accent-gold)', border: '1px solid var(--border-subtle)' }}>
                <Lock size={12} />
                <span>Private</span>
              </span>
            )}
            {isSelf && (currentUser?.listenerCode || profile.listenerCode) && (
              <button
                type="button"
                className="profile-listener-quick-pill"
                onClick={handleShareProfile}
                title="Click to copy your unique private profile link"
              >
                <Key size={12} style={{ color: 'var(--accent-gold)' }} />
                <span>{profile.listenerCode || currentUser?.listenerCode}</span>
                {copiedLink ? (
                  <span style={{ color: '#10b981', fontWeight: 600 }}>Link Copied!</span>
                ) : (
                  <Copy size={11} style={{ opacity: 0.7 }} />
                )}
              </button>
            )}
          </div>

          {profile.bio && (
            <p className="profile-bio">
              {profile.bio}
            </p>
          )}

          <div className="profile-stats-row">
            <div
              className={`profile-stat-box cursor-pointer ${activeTab === 'reviews' ? 'stat-active' : ''}`}
              onClick={() => setActiveTab('reviews')}
              role="button"
              tabIndex={0}
              title="View reviews tab"
            >
              <strong>{profile.reviewsCount}</strong>
              <span>Reviews</span>
            </div>
            <div
              className={`profile-stat-box cursor-pointer ${activeTab === 'collections' ? 'stat-active' : ''}`}
              onClick={() => setActiveTab('collections')}
              role="button"
              tabIndex={0}
              title="View collections tab"
            >
              <strong>{collections.length || profile.listsCount}</strong>
              <span>Collections</span>
            </div>
            <div
              className="profile-stat-box cursor-pointer"
              onClick={() => void handleOpenSocialModal('followers')}
              role="button"
              tabIndex={0}
              title="View followers"
            >
              <strong>{profile.followersCount}</strong>
              <span>Followers</span>
            </div>
            <div
              className="profile-stat-box cursor-pointer"
              onClick={() => void handleOpenSocialModal('following')}
              role="button"
              tabIndex={0}
              title="View following"
            >
              <strong>{profile.followingCount}</strong>
              <span>Following</span>
            </div>
          </div>

          {isSelf && (currentUser?.listenerCode || profile.listenerCode) && (
            <div className="profile-listener-code-card">
              <div className="listener-code-header-flex">
                <div className="listener-code-tag-group">
                  <Key size={14} style={{ color: 'var(--accent-gold)' }} />
                  <strong>Your Unique Listener Code</strong>
                  <span className={`listener-code-badge ${profile.isPrivate ? 'is-private' : 'is-public'}`}>
                    {profile.isPrivate ? 'Private Account' : 'Public Account'}
                  </span>
                </div>
                <div className="listener-code-btns">
                  <button
                    type="button"
                    className="listener-code-copy-btn"
                    onClick={() => handleCopyListenerCode(currentUser?.listenerCode || profile.listenerCode || '')}
                    title="Copy unique Listener Code"
                  >
                    {copiedCode ? <Check size={13} style={{ color: '#10b981' }} /> : <Copy size={13} />}
                    <span>{copiedCode ? 'Code Copied' : 'Copy Code'}</span>
                  </button>
                  <button
                    type="button"
                    className="listener-code-copy-btn"
                    onClick={handleShareProfile}
                    title="Copy direct profile link"
                  >
                    {copiedLink ? <Check size={13} style={{ color: '#10b981' }} /> : <Share2 size={13} />}
                    <span>{copiedLink ? 'Link Copied' : 'Copy Link'}</span>
                  </button>
                </div>
              </div>
              <div className="listener-code-badge-number">
                <code>{currentUser?.listenerCode || profile.listenerCode}</code>
              </div>
              <p className="listener-code-description">
                {profile.isPrivate
                  ? '🔒 Your account is private and completely unlisted from public username searches. Friends can locate and follow your account only by searching this random Listener Code or opening your direct profile link.'
                  : '🌐 Your account is public. Other listeners can discover your profile by username or by entering this Listener Code.'}
              </p>
            </div>
          )}
        </div>

        <div className="profile-hero-actions">
          {!isSelf ? (
            <>
              <button
                type="button"
                className={
                  followStatus === 'FOLLOWING'
                    ? 'btn-following'
                    : followStatus === 'PENDING'
                    ? 'btn-secondary'
                    : 'btn-primary'
                }
                onClick={handleToggleFollow}
                disabled={followLoading}
                title={followStatus === 'PENDING' ? 'Follow request pending — click to cancel' : undefined}
              >
                {followStatus === 'FOLLOWING' ? (
                  <>
                    <UserCheck size={16} />
                    <span>Following</span>
                  </>
                ) : followStatus === 'PENDING' ? (
                  <>
                    <Clock size={16} />
                    <span>Requested</span>
                  </>
                ) : (
                  <>
                    <UserPlus size={16} />
                    <span>Follow</span>
                  </>
                )}
              </button>

              <button
                type="button"
                className="btn-secondary"
                onClick={() => navigate(`/messages?to=${profile.username}`)}
              >
                <Send size={15} />
                <span>Message</span>
              </button>

              <button
                type="button"
                className="btn-secondary"
                onClick={handleShareProfile}
                title="Copy direct profile link to share with friends"
                style={{ display: 'inline-flex', alignItems: 'center', gap: '0.45rem' }}
              >
                {copiedLink ? <Check size={15} style={{ color: '#10b981' }} /> : <Share2 size={15} />}
                <span>{copiedLink ? 'Link Copied!' : 'Share'}</span>
              </button>
            </>
          ) : (
            <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap' }}>
              <button
                type="button"
                className="btn-secondary"
                onClick={handleShareProfile}
                title="Copy your direct profile link to share with friends"
                style={{ display: 'inline-flex', alignItems: 'center', gap: '0.5rem' }}
              >
                {copiedLink ? <Check size={15} style={{ color: '#10b981' }} /> : <Share2 size={15} />}
                <span>{copiedLink ? 'Link Copied!' : 'Share Profile'}</span>
              </button>
              <Link to="/settings" className="btn-secondary" style={{ display: 'inline-flex', alignItems: 'center', gap: '0.5rem' }}>
                <Settings size={15} />
                <span>Settings</span>
              </Link>
              <button
                type="button"
                className="btn-secondary"
                onClick={() => setIs2faModalOpen(true)}
              >
                <Shield size={15} />
                <span>2FA</span>
              </button>
            </div>
          )}
        </div>
      </header>

      {/* Pending Follow Requests for Self */}
      {isSelf && pendingRequests.length > 0 && (
        <section
          style={{
            margin: '1.5rem 0',
            padding: '1.25rem',
            background: 'var(--bg-surface)',
            borderRadius: '12px',
            border: '1px solid var(--border-subtle)',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1rem' }}>
            <Clock size={18} className="text-gold" />
            <strong style={{ fontSize: '1rem', color: 'var(--text-main)' }}>
              Follow Requests ({pendingRequests.length})
            </strong>
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
            {pendingRequests.map((req) => (
              <div
                key={req.id}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  padding: '0.75rem 1rem',
                  background: 'var(--bg-surface-elevated)',
                  borderRadius: '8px',
                  border: '1px solid var(--border-subtle)',
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                  <span className="user-avatar-sm">
                    {req.follower?.username?.charAt(0).toUpperCase()}
                  </span>
                  <Link
                    to={`/u/${req.follower?.isPrivate && req.follower?.listenerCode ? req.follower?.listenerCode : req.follower?.username}`}
                    style={{ fontWeight: 600, color: 'var(--aficio-text)' }}
                  >
                    {req.follower?.username}
                  </Link>
                </div>
                <div style={{ display: 'flex', gap: '0.5rem' }}>
                  <button
                    type="button"
                    className="btn-primary-sm"
                    onClick={() => handleAcceptRequest(req.id)}
                  >
                    <Check size={14} />
                    <span>Confirm</span>
                  </button>
                  <button
                    type="button"
                    className="btn-secondary-sm"
                    onClick={() => handleDeclineRequest(req.id)}
                  >
                    <X size={14} />
                    <span>Delete</span>
                  </button>
                </div>
              </div>
            ))}
          </div>
        </section>
      )}

      {/* If Private & Locked */}
      {isPrivateLocked ? (
        <div className="empty-catalog-state" style={{ margin: '4rem auto', maxWidth: '440px', textAlign: 'center' }}>
          <Lock size={44} className="empty-icon text-gold" style={{ margin: '0 auto 1rem auto' }} />
          <h3>This Account is Private</h3>
          <p>
            {followStatus === 'PENDING'
              ? 'Your follow request is currently pending. Reviews and collections will become visible once the account owner approves.'
              : `Follow @${profile.username} to see their logged album reviews and curated collections.`}
          </p>
        </div>
      ) : (
        <>
          {/* Tabs Bar */}
          <div className="profile-tabs-bar">
            <button
              type="button"
              className={`list-tab-btn ${activeTab === 'reviews' ? 'active' : ''}`}
              onClick={() => setActiveTab('reviews')}
            >
              <Star size={15} />
              <span>Logged Reviews ({reviews.length})</span>
            </button>
            <button
              type="button"
              className={`list-tab-btn ${activeTab === 'collections' ? 'active' : ''}`}
              onClick={() => setActiveTab('collections')}
            >
              <Bookmark size={15} />
              <span>Curated Collections ({collections.length})</span>
            </button>
          </div>

          {/* Reviews Content */}
          {activeTab === 'reviews' && (
            <section className="profile-reviews-section">
              {reviews.length === 0 ? (
                <div className="empty-catalog-state">
                  <Disc3 size={44} className="empty-icon" />
                  <h3>No album reviews logged yet</h3>
                  <p>{profile.username} has not posted any reviews yet.</p>
                </div>
              ) : (
                <div className="profile-reviews-grid">
                  {reviews.map((rev) => (
                    <article key={rev.id} className="profile-review-card">
                      <Link to={`/album/${rev.album?.id}`} className="review-album-thumb">
                        <img
                          src={rev.album?.coverArtUrl || '/record-placeholder.svg'}
                          alt={rev.album?.title}
                          onError={(e) => {
                            (e.target as HTMLImageElement).src = '/record-placeholder.svg';
                          }}
                        />
                      </Link>

                      <div className="profile-review-content">
                        <div className="profile-review-header">
                          <div>
                            <Link to={`/album/${rev.album?.id}`} className="album-link-title">
                              <h4>{rev.album?.title}</h4>
                            </Link>
                            <span className="album-artist-name">{rev.album?.artist?.name}</span>
                          </div>

                          <div className="review-stars-badge">
                            {[...Array(5)].map((_, i) => (
                              <Star
                                key={i}
                                size={13}
                                className={i < (rev.rating || 0) ? 'star-filled' : 'star-empty'}
                              />
                            ))}
                          </div>
                        </div>

                        {rev.content ? (
                          <p className="profile-review-text">{rev.content}</p>
                        ) : (
                          <p className="profile-review-text" style={{ fontStyle: 'italic', color: 'var(--text-faint)' }}>
                            Rated {rev.rating} / 5 stars
                          </p>
                        )}

                        <span className="review-date-foot">
                          Logged on {rev.createdAt ? parseUtcDate(rev.createdAt).toLocaleDateString() : 'Recently'}
                        </span>
                      </div>
                    </article>
                  ))}
                </div>
              )}
            </section>
          )}

          {/* Collections Content */}
          {activeTab === 'collections' && (
            <section className="profile-collections-section">
              {collections.length === 0 ? (
                <div className="empty-catalog-state">
                  <Bookmark size={44} className="empty-icon" />
                  <h3>No collections created yet</h3>
                  <p>{profile.username} has not created any public moodboards yet.</p>
                </div>
              ) : (
                <div className="lists-pinterest-grid" style={{ marginTop: '1.5rem' }}>
                  {collections.map((list) => {
                    const covers = listPreviewMap[list.id] || [];
                    return (
                      <Link key={list.id} to={`/lists/${list.id}`} className="list-card-pinterest">
                        <div className="list-mosaic-container">
                          {covers.length > 0 ? (
                            <div className={`mosaic-grid mosaic-${Math.min(covers.length, 4)}`}>
                              {covers.slice(0, 4).map((url, i) => (
                                <div key={i} className="mosaic-tile">
                                  <img src={url} alt="Cover preview" />
                                </div>
                              ))}
                            </div>
                          ) : (
                            <div className="mosaic-fallback">
                              <Disc3 size={36} className="spin-slow text-faint" />
                              <span>Empty Collection</span>
                            </div>
                          )}
                        </div>

                        <div className="list-card-details">
                          <div className="list-card-meta-top">
                            {(() => {
                              const isPublic = isListPublic(list);
                              return (
                                <span className={`list-visibility-tag ${isPublic ? 'tag-public' : 'tag-private'}`}>
                                  {isPublic ? <Globe size={11} /> : <Lock size={11} />}
                                  <span>{isPublic ? 'Public' : 'Private'}</span>
                                </span>
                              );
                            })()}
                            <span className="list-album-count">
                              {covers.length} {covers.length === 1 ? 'record' : 'records'}
                            </span>
                          </div>

                          <h3 className="list-card-title">{list.title}</h3>
                          {list.description && (
                            <p className="list-card-description">{list.description}</p>
                          )}
                        </div>
                      </Link>
                    );
                  })}
                </div>
              )}
            </section>
          )}
        </>
      )}

      {/* Social Follow Modal */}
      {socialModalType && (
        <div
          className="modal-backdrop social-network-backdrop"
          onMouseDown={(e) => {
            socialBackdropMouseDownRef.current = e.target === e.currentTarget;
          }}
          onMouseUp={(e) => {
            if (socialBackdropMouseDownRef.current && e.target === e.currentTarget) {
              setSocialModalType(null);
            }
            socialBackdropMouseDownRef.current = false;
          }}
          role="dialog"
          aria-modal="true"
          aria-label="Community Network"
        >
          <div className="modal-card social-network-modal" onClick={(e) => e.stopPropagation()}>
            {/* Header */}
            <div className="social-modal-header">
              <div className="social-modal-title-row">
                <div className="social-modal-badge-icon">
                  <Users size={18} />
                </div>
                <div>
                  <span className="social-modal-kicker">COMMUNITY NETWORK</span>
                  <h3 className="social-modal-title">
                    {profile.displayName || profile.username}
                  </h3>
                </div>
              </div>

              <button
                type="button"
                className="modal-close-btn"
                onClick={() => setSocialModalType(null)}
                aria-label="Close modal"
              >
                <X size={18} />
              </button>
            </div>

            {/* Segmented Tabs for Followers / Following */}
            <div className="social-modal-tabs">
              <button
                type="button"
                className={`social-modal-tab ${socialModalType === 'followers' ? 'active' : ''}`}
                onClick={() => void handleOpenSocialModal('followers')}
              >
                <Users size={15} />
                <span>Followers</span>
                <span className="social-modal-tab-count">{profile.followersCount}</span>
              </button>
              <button
                type="button"
                className={`social-modal-tab ${socialModalType === 'following' ? 'active' : ''}`}
                onClick={() => void handleOpenSocialModal('following')}
              >
                <UserCheck size={15} />
                <span>Following</span>
                <span className="social-modal-tab-count">{profile.followingCount}</span>
              </button>
            </div>

            {/* Search Filter (only when not loading, not error, and has users) */}
            {!loadingSocialUsers && !socialModalError && socialUsers.length > 0 && (
              <div className="social-modal-search-wrap">
                <Search size={14} className="social-search-icon" />
                <input
                  type="text"
                  className="social-modal-search-input"
                  placeholder={socialModalType === 'followers' ? 'Filter followers by username...' : 'Filter following by username...'}
                  value={socialSearchQuery}
                  onChange={(e) => setSocialSearchQuery(e.target.value)}
                />
                {socialSearchQuery && (
                  <button
                    type="button"
                    className="social-modal-search-clear"
                    onClick={() => setSocialSearchQuery('')}
                    aria-label="Clear filter"
                  >
                    <X size={13} />
                  </button>
                )}
              </div>
            )}

            {/* List / States */}
            <div className="social-users-scroll-list">
              {loadingSocialUsers ? (
                <div className="social-modal-state-container">
                  <Loader2 size={26} className="spin-loader" style={{ color: 'var(--accent-gold)', marginBottom: '0.75rem' }} />
                  <p>Loading {socialModalType === 'followers' ? 'followers' : 'following'}...</p>
                </div>
              ) : socialModalError ? (
                <div className="social-modal-state-container">
                  <div className="social-state-icon-badge private-badge">
                    <Lock size={26} />
                  </div>
                  <h4>Private Account</h4>
                  <p>{socialModalError}</p>
                </div>
              ) : socialUsers.length === 0 ? (
                <div className="social-modal-state-container">
                  <div className="social-state-icon-badge">
                    {socialModalType === 'followers' ? <Users size={26} /> : <UserPlus size={26} />}
                  </div>
                  <h4>{socialModalType === 'followers' ? 'No followers yet' : 'Not following anyone yet'}</h4>
                  <p>
                    {socialModalType === 'followers'
                      ? `When listeners follow @${profile.username}, they will appear here.`
                      : `@${profile.username} isn't following any other listeners yet.`}
                  </p>
                </div>
              ) : filteredSocialUsers.length === 0 ? (
                <div className="social-modal-state-container">
                  <div className="social-state-icon-badge">
                    <Search size={24} />
                  </div>
                  <h4>No matching listeners</h4>
                  <p>No listeners matching &ldquo;{socialSearchQuery}&rdquo;.</p>
                  <button
                    type="button"
                    className="social-reset-filter-btn"
                    onClick={() => setSocialSearchQuery('')}
                  >
                    Clear search
                  </button>
                </div>
              ) : (
                <div className="social-user-cards-grid">
                  {filteredSocialUsers.map((u) => {
                    const isSelfInList = currentUser?.username?.toLowerCase() === u.username?.toLowerCase();
                    const initial = (u.username || '?').charAt(0).toUpperCase();
                    const isUnfollowing = unfollowingUsername === u.username;

                    return (
                      <div key={u.id} className="social-user-card">
                        <Link
                          to={`/u/${u.isPrivate && u.listenerCode ? u.listenerCode : u.username}`}
                          className="social-user-card-body"
                          onClick={() => setSocialModalType(null)}
                        >
                          <div className="social-user-avatar-wrap">
                            <div
                              className="social-user-avatar-disc"
                              style={{ background: getAvatarGradient(u.username || '') }}
                            >
                              <span>{initial}</span>
                            </div>
                          </div>

                          <div className="social-user-info">
                            <div className="social-user-title-line">
                              <span className="social-user-handle">@{u.username}</span>
                              {isSelfInList && (
                                <span className="social-pill-you">You</span>
                              )}
                              {u.isPrivate && (
                                <span className="social-pill-private" title="Private profile">
                                  <Lock size={10} />
                                  <span>Private</span>
                                </span>
                              )}
                            </div>
                            <span className="social-user-meta-sub">Aficio Listener</span>
                          </div>
                        </Link>

                        <div className="social-user-actions-cell">
                          {isSelf && socialModalType === 'following' && !isSelfInList ? (
                            <button
                              type="button"
                              className="social-btn-unfollow"
                              onClick={(e) => handleUnfollowFromList(u.username, e)}
                              disabled={isUnfollowing}
                              title={`Unfollow @${u.username}`}
                            >
                              {isUnfollowing ? (
                                <Loader2 size={13} className="spin-loader" />
                              ) : (
                                <>
                                  <UserCheck size={13} className="icon-following" />
                                  <span className="label-following">Following</span>
                                </>
                              )}
                            </button>
                          ) : (
                            <Link
                              to={`/u/${u.username}`}
                              className="social-btn-view"
                              onClick={() => setSocialModalType(null)}
                            >
                              <span>View</span>
                              <ArrowRight size={13} />
                            </Link>
                          )}
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>

            {/* Modal Footer */}
            {!loadingSocialUsers && !socialModalError && socialUsers.length > 0 && (
              <div className="social-modal-footer">
                <span>
                  {filteredSocialUsers.length} {filteredSocialUsers.length === 1 ? 'listener' : 'listeners'}
                  {socialSearchQuery && ` (filtered from ${socialUsers.length})`}
                </span>
                <span>Aficio Community</span>
              </div>
            )}
          </div>
        </div>
      )}

      {/* 2FA Modal */}
      {is2faModalOpen && (
        <div className="modal-backdrop" onClick={() => { setIs2faModalOpen(false); setTwoFactorSetupData(null); setTwoFactorCode(''); setTwoFactorError(''); }}>
          <div className="modal-card auth-modal-card" onClick={(e) => e.stopPropagation()}>
            <button className="modal-close-btn" onClick={() => { setIs2faModalOpen(false); setTwoFactorSetupData(null); setTwoFactorCode(''); setTwoFactorError(''); }}>
              <X size={18} />
            </button>
            <div className="auth-header">
              <div className="auth-icon-badge">
                <ShieldCheck size={20} className="sparkle-gold" />
              </div>
              <h2>Two-Factor Authentication</h2>
              <p>Secure your account with TOTP (Authenticator App).</p>
            </div>
            
            <div style={{ padding: '0 2rem 2rem 2rem' }}>
              {twoFactorError && <div className="auth-error-banner" style={{ marginBottom: '1rem' }}>{twoFactorError}</div>}
              
              {!twoFactorSetupData ? (
                <div style={{ textAlign: 'center' }}>
                  <p style={{ marginBottom: '1rem', color: 'var(--text-muted)' }}>You can enable or disable 2FA for your account here.</p>
                  <div style={{ display: 'flex', gap: '1rem', justifyContent: 'center' }}>
                    <button className="btn-primary" onClick={handleStart2faSetup} disabled={is2faSubmitting}>
                      {is2faSubmitting ? 'Starting...' : 'Enable 2FA'}
                    </button>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem', flex: 1 }}>
                      <input 
                        type="text" 
                        placeholder="Current 2FA Code to disable" 
                        value={twoFactorCode} 
                        onChange={e => setTwoFactorCode(e.target.value)} 
                        className="form-field input"
                        style={{ padding: '0.75rem', borderRadius: '4px', border: '1px solid var(--border-subtle)' }}
                      />
                      <button className="btn-secondary" onClick={handleDisable2fa} disabled={is2faSubmitting || !twoFactorCode}>
                        Disable 2FA
                      </button>
                    </div>
                  </div>
                </div>
              ) : (
                <div style={{ textAlign: 'center' }}>
                  <p style={{ marginBottom: '1rem' }}>Scan this QR code with your authenticator app.</p>
                  <img src={twoFactorSetupData.qrCodeUri} alt="2FA QR Code" style={{ border: '4px solid white', borderRadius: '8px', marginBottom: '1rem' }} />
                  <p style={{ marginBottom: '1rem', fontSize: '0.85rem', color: 'var(--text-muted)' }}>Or enter this secret manually: <strong>{twoFactorSetupData.secret}</strong></p>
                  
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem', alignItems: 'center' }}>
                    <input 
                      type="text" 
                      placeholder="6-digit code" 
                      value={twoFactorCode} 
                      onChange={e => setTwoFactorCode(e.target.value)} 
                      maxLength={6}
                      style={{ padding: '0.75rem', borderRadius: '4px', border: '1px solid var(--border-subtle)', width: '100%', maxWidth: '200px', textAlign: 'center' }}
                    />
                    <button className="btn-primary" onClick={handleEnable2fa} disabled={is2faSubmitting || twoFactorCode.length < 6}>
                      {is2faSubmitting ? 'Verifying...' : 'Verify & Enable'}
                    </button>
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
