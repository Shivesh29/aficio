import { useEffect, useState } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { api, isListPublic, type UserProfile, type Review, type MusicList, type MusicListItem, type UserQuotaResponse } from '../api/client';
import { useAuth } from '../context/useAuth';
import { parseUtcDate } from '../utils/date';
import {
  Star,
  UserPlus,
  UserCheck,
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
} from 'lucide-react';

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
  const [quota, setQuota] = useState<UserQuotaResponse | null>(null);

  // Follow list modal state
  const [socialModalType, setSocialModalType] = useState<'followers' | 'following' | null>(null);
  const [socialUsers, setSocialUsers] = useState<any[]>([]);
  const [loadingSocialUsers, setLoadingSocialUsers] = useState(false);
  const [socialModalError, setSocialModalError] = useState('');

  // 2FA state
  const [is2faModalOpen, setIs2faModalOpen] = useState(false);
  const [twoFactorSetupData, setTwoFactorSetupData] = useState<{ secret: string; qrCodeUri: string } | null>(null);
  const [twoFactorCode, setTwoFactorCode] = useState('');
  const [is2faSubmitting, setIs2faSubmitting] = useState(false);
  const [twoFactorError, setTwoFactorError] = useState('');

  const isSelf = currentUser && username && currentUser.username.toLowerCase() === username.toLowerCase();

  useEffect(() => {
    async function loadUserProfile() {
      if (!username) return;
      try {
        setLoading(true);
        setError('');
        const prof = await api.social.getUserProfile(username);
        setProfile(prof);

        // Fetch user reviews
        const userRevs = await api.reviews.getUserReviews(prof.id).catch(() => []);
        setReviews(userRevs);

        // Fetch user collections
        const userLists = await api.lists.getUserLists(username).catch(() => []);
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
        if (isAuthenticated && !isSelf) {
          const statusRes = await api.social.getFollowStatus(username).catch(() => ({
            status: 'NONE' as const,
            following: false,
            pending: false,
          }));
          setFollowStatus(statusRes.status);
        }

        // If viewing self, load quota and any pending follow requests
        if (isAuthenticated && isSelf) {
          api.catalog.getUserQuota().then((q) => setQuota(q)).catch(() => {});
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
  }, [username, isAuthenticated, isSelf]);

  const handleToggleFollow = async () => {
    if (!isAuthenticated) {
      openAuthModal('login');
      return;
    }
    if (!username) return;

    setFollowLoading(true);
    try {
      if (followStatus === 'FOLLOWING' || followStatus === 'PENDING') {
        await api.social.unfollow(username);
        if (followStatus === 'FOLLOWING') {
          setProfile((prev) => (prev ? { ...prev, followersCount: Math.max(0, prev.followersCount - 1) } : null));
        }
        setFollowStatus('NONE');
      } else {
        const res = await api.social.follow(username);
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

  const handleOpenSocialModal = async (type: 'followers' | 'following') => {
    if (!username) return;
    setSocialModalType(type);
    setLoadingSocialUsers(true);
    setSocialModalError('');
    try {
      if (type === 'followers') {
        const list = await api.social.getFollowers(username);
        setSocialUsers(list.map((f) => f.follower).filter(Boolean));
      } else {
        const list = await api.social.getFollowing(username);
        setSocialUsers(list.map((f) => f.following).filter(Boolean));
      }
    } catch (err) {
      setSocialModalError(err instanceof Error ? err.message : 'Unable to view list for private account.');
      setSocialUsers([]);
    } finally {
      setLoadingSocialUsers(false);
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
              <span className="profile-role-badge" style={{ display: 'inline-flex', alignItems: 'center', gap: '0.35rem', background: 'rgba(230, 161, 71, 0.15)', color: 'var(--accent-gold)' }}>
                <Lock size={12} />
                <span>Private</span>
              </span>
            )}
          </div>

          {profile.bio && (
            <p className="profile-bio">
              {profile.bio}
            </p>
          )}

          <div className="profile-stats-row">
            <div
              className="profile-stat-box cursor-pointer"
              onClick={() => setActiveTab('reviews')}
            >
              <strong>{profile.reviewsCount}</strong>
              <span>Reviews</span>
            </div>
            <div
              className="profile-stat-box cursor-pointer"
              onClick={() => setActiveTab('collections')}
            >
              <strong>{collections.length || profile.listsCount}</strong>
              <span>Collections</span>
            </div>
            <div
              className="profile-stat-box cursor-pointer"
              onClick={() => void handleOpenSocialModal('followers')}
            >
              <strong>{profile.followersCount}</strong>
              <span>Followers</span>
            </div>
            <div
              className="profile-stat-box cursor-pointer"
              onClick={() => void handleOpenSocialModal('following')}
            >
              <strong>{profile.followingCount}</strong>
              <span>Following</span>
            </div>
          </div>

          {isSelf && quota && (
            <div className="profile-quota-card">
              <div className="profile-quota-top">
                <div className="quota-title-flex">
                  <span className="quota-badge-label">
                    {quota.unlimited
                      ? (quota.isOwner ? '⚡ Dev / Owner Tier' : '🔑 Custom Key Tier')
                      : quota.hasCustomKey
                      ? '🔑 Personal Key Tier (10k Quota)'
                      : 'Daily Discovery Limit'}
                  </span>
                  {quota.isExhausted && (
                    <span className="quota-exhausted-pill">Limit Reached</span>
                  )}
                </div>
                <span className="quota-fraction-text">
                  {quota.unlimited ? 'Unlimited' : `${quota.usedToday} / ${quota.dailyLimit} Used Today`}
                </span>
              </div>

              {!quota.unlimited && (
                <div className="profile-quota-bar-track">
                  <div
                    className={`profile-quota-bar-fill ${quota.isExhausted ? 'is-exhausted' : quota.usedToday >= (quota.dailyLimit * 0.8) ? 'is-warning' : ''}`}
                    style={{ width: `${Math.min(100, (quota.usedToday / quota.dailyLimit) * 100)}%` }}
                  />
                </div>
              )}

              <div className="profile-quota-footer">
                <p>
                  {quota.unlimited
                    ? (quota.isOwner ? 'Unlimited new album discoveries enabled for dev account.' : 'Your personal YouTube API key is active with unlimited discoveries.')
                    : quota.hasCustomKey
                    ? quota.isExhausted
                      ? "You've reached your daily personal API key discovery limit (99/99 albums). Your Google Cloud 10,000 quota resets at midnight Pacific Time. Replaying catalogued & cached albums remains completely free."
                      : `${quota.remaining} new album ${quota.remaining === 1 ? 'discovery' : 'discoveries'} remaining today (10,000 Google Cloud quota). Replaying already catalogued albums is always free and unlimited.`
                    : quota.isExhausted
                    ? `You've used today's ${quota.dailyLimit} new album discoveries. Replaying catalogued & cached albums remains completely free. Resets at midnight UTC, or add a personal API key in Settings for 99 daily discoveries.`
                    : `${quota.remaining} new album ${quota.remaining === 1 ? 'discovery' : 'discoveries'} remaining today. Replaying already catalogued albums is always free and unlimited.`}
                </p>
                {!quota.unlimited && (
                  <Link to="/settings" className="quota-settings-link">
                    Settings &rarr;
                  </Link>
                )}
              </div>
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
            </>
          ) : (
            <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap' }}>
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
            background: 'var(--aficio-surface-card, #1c1917)',
            borderRadius: '12px',
            border: '1px solid var(--aficio-border, #292524)',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1rem' }}>
            <Clock size={18} className="text-gold" />
            <strong style={{ fontSize: '1rem', color: 'var(--aficio-text)' }}>
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
                  background: 'var(--aficio-surface, #0c0a09)',
                  borderRadius: '8px',
                  border: '1px solid var(--aficio-border, #292524)',
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                  <span className="user-avatar-sm">
                    {req.follower?.username?.charAt(0).toUpperCase()}
                  </span>
                  <Link
                    to={`/u/${req.follower?.username}`}
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
        <div className="modal-backdrop" onClick={() => setSocialModalType(null)}>
          <div className="modal-card social-users-modal" onClick={(e) => e.stopPropagation()}>
            <button
              className="modal-close-btn"
              onClick={() => setSocialModalType(null)}
              aria-label="Close modal"
            >
              <X size={18} />
            </button>

            <div className="review-modal-header">
              <span className="kicker">Network</span>
              <h3>{socialModalType === 'followers' ? 'Followers' : 'Following'}</h3>
              <p>Listeners connected with {profile.displayName || profile.username}</p>
            </div>

            <div className="social-users-scroll-list">
              {loadingSocialUsers ? (
                <div className="loading-state-container" style={{ padding: '2rem' }}>
                  <Loader2 size={24} className="spin-loader" />
                  <p>Loading community...</p>
                </div>
              ) : socialModalError ? (
                <div style={{ textAlign: 'center', padding: '2rem', color: 'var(--aficio-text-secondary)' }}>
                  <Lock size={32} style={{ margin: '0 auto 0.75rem auto', color: 'var(--aficio-gold, #eab308)' }} />
                  <p>{socialModalError}</p>
                </div>
              ) : socialUsers.length === 0 ? (
                <p className="sidebar-empty-hint">No listeners to show.</p>
              ) : (
                socialUsers.map((u) => (
                  <div key={u.id} className="social-user-row">
                    <span className="user-avatar-sm">
                      {u.username?.charAt(0).toUpperCase()}
                    </span>
                    <div className="social-user-text">
                      <Link
                        to={`/u/${u.username}`}
                        onClick={() => setSocialModalType(null)}
                      >
                        <strong>{u.username}</strong>
                      </Link>
                    </div>
                  </div>
                ))
              )}
            </div>
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
                  <p style={{ marginBottom: '1rem', color: 'var(--aficio-text-secondary)' }}>You can enable or disable 2FA for your account here.</p>
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
                        style={{ padding: '0.75rem', borderRadius: '4px', border: '1px solid var(--aficio-border)' }}
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
                  <p style={{ marginBottom: '1rem', fontSize: '0.85rem', color: 'var(--aficio-text-secondary)' }}>Or enter this secret manually: <strong>{twoFactorSetupData.secret}</strong></p>
                  
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem', alignItems: 'center' }}>
                    <input 
                      type="text" 
                      placeholder="6-digit code" 
                      value={twoFactorCode} 
                      onChange={e => setTwoFactorCode(e.target.value)} 
                      maxLength={6}
                      style={{ padding: '0.75rem', borderRadius: '4px', border: '1px solid var(--aficio-border)', width: '100%', maxWidth: '200px', textAlign: 'center' }}
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
