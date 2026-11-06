import { useEffect, useState, type FormEvent } from 'react';
import { useParams, Link } from 'react-router-dom';
import { api, isListPublic, type Album, type Track, type Review, type ReviewComment, type MusicList } from '../api/client';
import { useAuth } from '../context/useAuth';
import { usePlayer } from '../context/PlayerContext';
import { ConfirmModal } from '../components/ConfirmModal';
import { parseUtcDate } from '../utils/date';
import {
  Star,
  Disc3,
  Music,
  Bookmark,
  MessageSquare,
  Heart,
  Play,
  Pause,
  X,
  Send,
  Loader2,
  Plus,
  Globe,
  Lock,
  Trash2
} from 'lucide-react';

const FALLBACK_COVER = '/record-placeholder.svg';

function formatDuration(milliseconds: number | null): string {
  if (!milliseconds) return '--:--';
  const totalSeconds = Math.floor(milliseconds / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${seconds.toString().padStart(2, '0')}`;
}

export const AlbumDetailPage = () => {
  const { id } = useParams<{ id: string }>();
  const { user, isAuthenticated, openAuthModal } = useAuth();
  const { playTrack, togglePlay, currentTrackId, isPlaying: isPlayerPlaying, isLoading: isPlayerLoading } = usePlayer();

  const [album, setAlbum] = useState<Album | null>(null);
  const [tracks, setTracks] = useState<Track[]>([]);
  const [reviews, setReviews] = useState<Review[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // Modals & form state
  const [isReviewModalOpen, setIsReviewModalOpen] = useState(false);
  const [isListModalOpen, setIsListModalOpen] = useState(false);
  const [userRating, setUserRating] = useState(5);
  const [reviewContent, setReviewContent] = useState('');
  const [submittingReview, setSubmittingReview] = useState(false);
  const [deletingReview, setDeletingReview] = useState(false);
  const [isDeleteReviewModalOpen, setIsDeleteReviewModalOpen] = useState(false);
  const [mobileTab, setMobileTab] = useState<'tracks' | 'reviews'>('tracks');

  // Quick star rating state
  const [hoveredStar, setHoveredStar] = useState<number | null>(null);
  const [quickRatingLoading, setQuickRatingLoading] = useState(false);

  // Identify if current logged-in user already has a review or rating for this album
  const userReview = reviews.find(
    (r) =>
      (user?.id && r.user?.id === user.id) ||
      (user?.username && r.user?.username?.toLowerCase() === user.username.toLowerCase())
  );
  const currentUserRating = userReview?.rating ?? null;
  const hasWrittenReview = Boolean(userReview?.content && userReview.content.trim().length > 0);

  // Ratings (for community average score and rating count)
  const ratingsList = reviews.filter((r) => r.rating != null && r.rating > 0);
  const writtenReviewsList = reviews.filter((r) => r.content && r.content.trim().length > 0);

  // User lists for adding album
  const [userLists, setUserLists] = useState<MusicList[]>([]);
  const [selectedListId, setSelectedListId] = useState<number | null>(null);
  const [listSuccessMsg, setListSuccessMsg] = useState('');
  const [listModalTab, setListModalTab] = useState<'existing' | 'create'>('existing');
  const [newCollectionTitle, setNewCollectionTitle] = useState('');
  const [newCollectionDesc, setNewCollectionDesc] = useState('');
  const [newCollectionIsPublic, setNewCollectionIsPublic] = useState(true);
  const [creatingList, setCreatingList] = useState(false);

  // Comment drawers
  const [expandedCommentsReviewId, setExpandedCommentsReviewId] = useState<number | null>(null);
  const [commentsMap, setCommentsMap] = useState<Record<number, ReviewComment[]>>({});
  const [newCommentContent, setNewCommentContent] = useState('');
  const [likesMap, setLikesMap] = useState<Record<number, number>>({});
  const [likedByUserMap, setLikedByUserMap] = useState<Record<number, boolean>>({});

  useEffect(() => {
    async function loadAlbumData() {
      if (!id) return;
      try {
        setLoading(true);
        const [albumData, tracksData, reviewsData] = await Promise.all([
          api.catalog.getAlbumById(id, user?.username),
          api.catalog.getTracks(id),
          api.reviews.getAlbumReviews(id).catch(() => []),
        ]);
        setAlbum(albumData);
        setTracks(tracksData);
        setReviews(reviewsData);

        // Fetch likes count and isLiked status for reviews
        const likesPromises = reviewsData.map(async (r) => {
          try {
            const count = await api.reviews.getLikes(r.id);
            return { id: r.id, count };
          } catch {
            return { id: r.id, count: 0 };
          }
        });
        const likesRes = await Promise.all(likesPromises);
        const lMap: Record<number, number> = {};
        likesRes.forEach((item) => {
          lMap[item.id] = item.count;
        });
        setLikesMap(lMap);

        // Fetch isLiked for each review (only if authenticated)
        const likedPromises = reviewsData.map(async (r) => {
          try {
            const liked = await api.reviews.isLiked(r.id);
            return { id: r.id, liked };
          } catch {
            return { id: r.id, liked: false };
          }
        });
        const likedRes = await Promise.all(likedPromises);
        const likedMap: Record<number, boolean> = {};
        likedRes.forEach((item) => {
          likedMap[item.id] = item.liked;
        });
        setLikedByUserMap(likedMap);
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Album not found');
      } finally {
        setLoading(false);
      }
    }
    void loadAlbumData();
  }, [id, user?.username]);

  // Calculate average rating across all ratings
  const averageRating =
    ratingsList.length > 0
      ? (ratingsList.reduce((acc, r) => acc + (r.rating || 0), 0) / ratingsList.length).toFixed(1)
      : null;

  // Total runtime
  const totalMilliseconds = tracks.reduce((acc, t) => acc + (t.lengthMilliseconds || 0), 0);
  const totalMinutes = Math.floor(totalMilliseconds / 60000);

  const handleQuickStar = async (starVal: number) => {
    if (!isAuthenticated) {
      openAuthModal('login');
      return;
    }
    if (!album) return;

    setQuickRatingLoading(true);
    try {
      const updated = await api.reviews.rateAlbum(album.id, starVal);
      setReviews((prev) => {
        const filtered = prev.filter(
          (r) =>
            r.id !== updated.id &&
            r.user?.id !== updated.user?.id &&
            r.user?.username?.toLowerCase() !== updated.user?.username?.toLowerCase()
        );
        return [updated, ...filtered];
      });
    } catch (err) {
      alert(err instanceof Error ? err.message : 'Could not save rating');
    } finally {
      setQuickRatingLoading(false);
    }
  };

  const handleClearRating = async () => {
    if (!isAuthenticated || !album) return;

    setQuickRatingLoading(true);
    try {
      await api.reviews.removeRating(album.id);
      setReviews((prev) => {
        return prev
          .map((r) => {
            const isMine =
              (user?.id && r.user?.id === user.id) ||
              (user?.username && r.user?.username?.toLowerCase() === user.username.toLowerCase());
            if (!isMine) return r;
            if (r.content && r.content.trim().length > 0) {
              return { ...r, rating: null };
            }
            return null;
          })
          .filter(Boolean) as Review[];
      });
    } catch (err) {
      alert(err instanceof Error ? err.message : 'Could not clear rating');
    } finally {
      setQuickRatingLoading(false);
    }
  };

  const handleOpenReviewModal = () => {
    if (!isAuthenticated) {
      openAuthModal('login');
      return;
    }
    if (userReview) {
      setUserRating(userReview.rating || 5);
      setReviewContent(userReview.content || '');
    } else {
      setUserRating(5);
      setReviewContent('');
    }
    setIsReviewModalOpen(true);
  };

  const handleOpenListModal = async () => {
    if (!isAuthenticated) {
      openAuthModal('login');
      return;
    }
    try {
      const lists = await api.lists.getMyLists();
      setUserLists(lists);
      if (lists.length > 0) {
        setSelectedListId(lists[0].id);
        setListModalTab('existing');
      } else {
        setListModalTab('create');
      }
      setIsListModalOpen(true);
    } catch {
      alert('Could not fetch your lists.');
    }
  };

  const handleAddToList = async () => {
    if (!selectedListId || !album) return;
    try {
      await api.lists.addAlbumToList(selectedListId, album.id);
      setListSuccessMsg('Added to collection successfully!');
      setTimeout(() => {
        setIsListModalOpen(false);
        setListSuccessMsg('');
      }, 1200);
    } catch (err) {
      alert(err instanceof Error ? err.message : 'Could not add to list');
    }
  };

  const handleCreateAndAddToList = async (e: FormEvent) => {
    e.preventDefault();
    if (!newCollectionTitle.trim() || !album) return;
    setCreatingList(true);
    try {
      const createdList = await api.lists.createList(
        newCollectionTitle.trim(),
        newCollectionDesc.trim(),
        newCollectionIsPublic
      );
      await api.lists.addAlbumToList(createdList.id, album.id);
      setUserLists((prev) => [createdList, ...prev]);
      setSelectedListId(createdList.id);
      setListSuccessMsg(`Created "${createdList.title}" and saved album!`);
      setTimeout(() => {
        setIsListModalOpen(false);
        setListSuccessMsg('');
        setNewCollectionTitle('');
        setNewCollectionDesc('');
        setNewCollectionIsPublic(true);
      }, 1200);
    } catch (err) {
      alert(err instanceof Error ? err.message : 'Could not create collection and save album');
    } finally {
      setCreatingList(false);
    }
  };

  const handleSaveReview = async (e: FormEvent) => {
    e.preventDefault();
    if (!album) return;
    setSubmittingReview(true);
    try {
      const newReview = await api.reviews.createReview(album.id, userRating, reviewContent);
      // Update reviews list (replace if already exists, else prepend)
      setReviews((prev) => {
        const filtered = prev.filter(
          (r) =>
            r.id !== newReview.id &&
            r.user?.id !== newReview.user?.id &&
            r.user?.username?.toLowerCase() !== newReview.user?.username?.toLowerCase()
        );
        return [newReview, ...filtered];
      });
      setIsReviewModalOpen(false);
    } catch (err) {
      alert(err instanceof Error ? err.message : 'Failed to save review');
    } finally {
      setSubmittingReview(false);
    }
  };

  const handleConfirmDeleteReview = async () => {
    if (!userReview) return;
    setDeletingReview(true);
    try {
      await api.reviews.deleteReview(userReview.id);
      setReviews((prev) => prev.filter((r) => r.id !== userReview.id));
      setIsDeleteReviewModalOpen(false);
      setIsReviewModalOpen(false);
      setUserRating(5);
      setReviewContent('');
    } catch (err) {
      alert(err instanceof Error ? err.message : 'Failed to delete review');
    } finally {
      setDeletingReview(false);
    }
  };

  const handleToggleLike = async (reviewId: number) => {
    if (!isAuthenticated) {
      openAuthModal('login');
      return;
    }
    const isLiked = likedByUserMap[reviewId];
    try {
      if (isLiked) {
        await api.reviews.unlikeReview(reviewId);
        setLikesMap((prev) => ({ ...prev, [reviewId]: Math.max(0, (prev[reviewId] || 1) - 1) }));
        setLikedByUserMap((prev) => ({ ...prev, [reviewId]: false }));
      } else {
        await api.reviews.likeReview(reviewId);
        setLikesMap((prev) => ({ ...prev, [reviewId]: (prev[reviewId] || 0) + 1 }));
        setLikedByUserMap((prev) => ({ ...prev, [reviewId]: true }));
      }
    } catch (err) {
      console.error('Like toggle error:', err);
    }
  };

  const handleToggleComments = async (reviewId: number) => {
    if (expandedCommentsReviewId === reviewId) {
      setExpandedCommentsReviewId(null);
      return;
    }
    setExpandedCommentsReviewId(reviewId);
    try {
      const comments = await api.reviews.getComments(reviewId);
      setCommentsMap((prev) => ({ ...prev, [reviewId]: comments }));
    } catch (err) {
      console.error('Failed to load comments', err);
    }
  };

  const handlePostComment = async (reviewId: number) => {
    if (!isAuthenticated) {
      openAuthModal('login');
      return;
    }
    if (!newCommentContent.trim()) return;

    try {
      const comment = await api.reviews.addComment(reviewId, newCommentContent.trim());
      setCommentsMap((prev) => ({
        ...prev,
        [reviewId]: [...(prev[reviewId] || []), comment],
      }));
      setNewCommentContent('');
    } catch {
      alert('Could not post comment');
    }
  };

  if (loading) {
    return (
      <div className="dossier-loading">
        <Loader2 size={36} className="spin-loader" />
        <p>Loading album...</p>
      </div>
    );
  }

  if (error || !album) {
    return (
      <div className="dossier-error">
        <h3>Unable to load album</h3>
        <p>{error || 'This release does not exist in our catalog.'}</p>
        <Link to="/" className="btn-secondary">
          Back to Catalog
        </Link>
      </div>
    );
  }

  return (
    <div className="album-detail-page">
      {/* Top Breadcrumbs */}
      <div className="dossier-nav-breadcrumb">
        <Link to="/">Catalog</Link>
        <span>/</span>
        <span>{album.artist?.name || 'Artist'}</span>
        <span>/</span>
        <span className="current">{album.title}</span>
      </div>

      {/* Mobile Hero View (Active on mobile screens < 768px) */}
      <div className="album-mobile-hero">
        <div className="mobile-hero-cover-container">
          <div
            className="mobile-hero-cover-ambient"
            style={{ backgroundImage: `url(${album.coverArtUrl || FALLBACK_COVER})` }}
            aria-hidden="true"
          />
          <img
            src={album.coverArtUrl || FALLBACK_COVER}
            alt={`${album.title} cover`}
            className="mobile-hero-cover-img"
            onError={(e) => {
              (e.target as HTMLImageElement).src = FALLBACK_COVER;
            }}
          />
        </div>

        <div className="mobile-hero-meta">
          <h1 className="mobile-hero-title">{album.title}</h1>
          <p className="mobile-hero-artist">
            By{' '}
            {album.artist?.musicBrainzId ? (
              <Link to={`/artist/${album.artist.musicBrainzId}`} className="artist-link-gold" title={`View releases by ${album.artist.name}`}>
                <strong>{album.artist?.name}</strong>
              </Link>
            ) : (
              <strong>{album.artist?.name || 'Unknown Artist'}</strong>
            )}
          </p>

          <div className="mobile-hero-stats-row">
            <div className="mobile-stat-score">
              <Star size={13} className="star-gold" />
              <span>{averageRating ? `${averageRating} / 5` : 'Unrated'}</span>
              {ratingsList.length > 0 && <span className="stat-count">({ratingsList.length})</span>}
            </div>
            <span className="dot">•</span>
            <span>{tracks.length} tracks</span>
            {totalMinutes > 0 && (
              <>
                <span className="dot">•</span>
                <span>{totalMinutes} min</span>
              </>
            )}
          </div>
        </div>

        {/* Mobile Action Controls */}
        <div className="mobile-hero-actions">
          {tracks.length > 0 && (
            <button
              type="button"
              className="mobile-hero-play-btn"
              onClick={() => {
                const isCurrentAlbum = tracks.some((t) => t.id === currentTrackId);
                if (isCurrentAlbum) {
                  togglePlay();
                } else if (album) {
                  playTrack(tracks[0], album, tracks);
                }
              }}
            >
              {isPlayerPlaying && tracks.some((t) => t.id === currentTrackId) ? (
                <>
                  <Pause size={18} />
                  <span>Pause Album</span>
                </>
              ) : (
                <>
                  <Play size={18} className="play-icon-offset" />
                  <span>Play Album</span>
                </>
              )}
            </button>
          )}

          {/* Quick Actions Grid: Star Rating + Review + Add to Collection */}
          <div className="mobile-hero-secondary-grid">
            <div className="mobile-quick-star-strip">
              <div className="mobile-star-strip-label">
                <Star size={12} style={currentUserRating ? { fill: 'var(--accent-gold)', color: 'var(--accent-gold)' } : undefined} />
                <span>{currentUserRating ? `You: ${currentUserRating}★` : 'Rate'}</span>
                {currentUserRating && (
                  <button
                    type="button"
                    className="mobile-star-clear-btn"
                    onClick={handleClearRating}
                    disabled={quickRatingLoading}
                    title="Clear rating"
                  >
                    ×
                  </button>
                )}
              </div>
              <div className="mobile-stars-buttons">
                {[1, 2, 3, 4, 5].map((starVal) => {
                  const isFilled = (hoveredStar ?? currentUserRating ?? 0) >= starVal;
                  return (
                    <button
                      key={starVal}
                      type="button"
                      className="mobile-star-tap-btn"
                      onClick={() => handleQuickStar(starVal)}
                      disabled={quickRatingLoading}
                      title={`Rate ${starVal} star${starVal > 1 ? 's' : ''}`}
                    >
                      <Star size={17} className={isFilled ? 'star-gold-fill' : 'star-gold-outline'} />
                    </button>
                  );
                })}
              </div>
            </div>

            <button
              type="button"
              className="mobile-action-pill-btn"
              onClick={handleOpenReviewModal}
              style={hasWrittenReview ? { borderColor: 'rgba(230, 161, 71, 0.45)', color: 'var(--accent-gold)' } : undefined}
            >
              <MessageSquare size={16} />
              <span>{hasWrittenReview ? 'Edit Review' : 'Review'}</span>
            </button>

            <button
              type="button"
              className="mobile-action-pill-btn"
              onClick={handleOpenListModal}
            >
              <Bookmark size={16} />
              <span>Collect</span>
            </button>
          </div>
        </div>

        {/* Mobile Segmented Tab Switcher */}
        <div className="mobile-dossier-segmented-tabs">
          <button
            type="button"
            className={`mobile-tab-btn ${mobileTab === 'tracks' ? 'is-active-tab' : ''}`}
            onClick={() => setMobileTab('tracks')}
          >
            <Music size={15} />
            <span>Tracklist ({tracks.length})</span>
          </button>
          <button
            type="button"
            className={`mobile-tab-btn ${mobileTab === 'reviews' ? 'is-active-tab' : ''}`}
            onClick={() => setMobileTab('reviews')}
          >
            <MessageSquare size={15} />
            <span>Reviews ({writtenReviewsList.length})</span>
          </button>
        </div>
      </div>

      {/* Album hero and content */}
      <div className="dossier-main-layout">
        {/* Cover and actions */}
        <div className="dossier-sidebar">
          <div className="dossier-cover-container">
            <div
              className="dossier-cover-ambient"
              style={{ backgroundImage: `url(${album.coverArtUrl || FALLBACK_COVER})` }}
              aria-hidden="true"
            />
            <img
              src={album.coverArtUrl || FALLBACK_COVER}
              alt={`${album.title} cover`}
              className="dossier-cover-img"
              onError={(e) => {
                (e.target as HTMLImageElement).src = FALLBACK_COVER;
              }}
            />
          </div>

          <div className="dossier-actions-stack">
            {tracks.length > 0 && (
              <button
                type="button"
                className="dossier-btn-primary"
                onClick={() => {
                  const isCurrentAlbum = tracks.some((t) => t.id === currentTrackId);
                  if (isCurrentAlbum) {
                    togglePlay();
                  } else if (album) {
                    playTrack(tracks[0], album, tracks);
                  }
                }}
              >
                {isPlayerPlaying && tracks.some((t) => t.id === currentTrackId) ? (
                  <>
                    <Pause size={16} />
                    <span>Pause Album</span>
                  </>
                ) : (
                  <>
                    <Play size={16} className="play-icon-offset" />
                    <span>Play Album</span>
                  </>
                )}
              </button>
            )}
            {/* Quick rating */}
            <div className={`dossier-star-rating-box ${currentUserRating ? 'rated' : ''}`}>
              <div className="star-rating-header">
                <span className={`star-rating-label ${currentUserRating ? 'is-rated' : ''}`}>
                  <Star size={13} style={currentUserRating ? { fill: 'var(--accent-gold)', color: 'var(--accent-gold)' } : undefined} />
                  <span>{currentUserRating ? `Your Rating: ${currentUserRating} / 5` : 'Rate this album'}</span>
                </span>
                {currentUserRating && (
                  <button
                    type="button"
                    className="star-clear-btn"
                    onClick={handleClearRating}
                    disabled={quickRatingLoading}
                    title="Remove your star rating"
                  >
                    Clear
                  </button>
                )}
              </div>
              <div className="interactive-stars-row">
                {[1, 2, 3, 4, 5].map((starVal) => {
                  const isFilled = (hoveredStar ?? currentUserRating ?? 0) >= starVal;
                  return (
                    <button
                      key={starVal}
                      type="button"
                      className="quick-star-btn"
                      onMouseEnter={() => setHoveredStar(starVal)}
                      onMouseLeave={() => setHoveredStar(null)}
                      onClick={() => handleQuickStar(starVal)}
                      disabled={quickRatingLoading}
                      title={`Rate ${starVal} star${starVal > 1 ? 's' : ''}`}
                    >
                      <Star
                        size={22}
                        className={isFilled ? 'star-gold-fill' : 'star-gold-outline'}
                      />
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Log / review */}
            <button
              type="button"
              className="dossier-btn-secondary"
              onClick={handleOpenReviewModal}
              style={hasWrittenReview ? { borderColor: 'rgba(230, 161, 71, 0.45)', color: 'var(--accent-gold)' } : undefined}
            >
              <MessageSquare size={16} />
              <span>{hasWrittenReview ? 'Edit Review' : 'Log / Review'}</span>
            </button>

            {/* Add to collection */}
            <button
              type="button"
              className="dossier-btn-secondary"
              onClick={handleOpenListModal}
            >
              <Bookmark size={16} />
              <span>Add to Collection</span>
            </button>
          </div>

          <div className="dossier-meta-card">
            <h4>Album details</h4>
            <div className="meta-spec-row">
              <span>Artist</span>
              <strong>{album.artist?.name || 'Unknown'}</strong>
            </div>
            <div className="meta-spec-row">
              <span>Tracks</span>
              <strong>{tracks.length} tracks</strong>
            </div>
            <div className="meta-spec-row">
              <span>Runtime</span>
              <strong>{totalMinutes > 0 ? `${totalMinutes} minutes` : 'Unknown'}</strong>
            </div>
          </div>
        </div>

        {/* Metadata, tracklist, and community */}
        <div className="dossier-content">
          <header className="dossier-header">
            <div className="dossier-kicker">Album</div>
            <h1 className="dossier-title">{album.title}</h1>
            <p className="dossier-artist-subtitle">
              {album.artist?.musicBrainzId ? (
                <Link to={`/artist/${album.artist.musicBrainzId}`} className="artist-link-gold" title={`View releases by ${album.artist.name}`}>
                  <strong>{album.artist?.name || 'Unknown Artist'}</strong>
                </Link>
              ) : (
                <strong>{album.artist?.name}</strong>
              )}
            </p>

            <div className="album-meta-line">
              Year unavailable <span>•</span> {tracks.length} tracks <span>•</span> {totalMinutes > 0 ? `${totalMinutes} min` : 'Runtime unavailable'}
            </div>

            {/* Community score */}
            <div className="dossier-rating-banner">
              <div className="rating-score-box">
                <Star size={24} className="star-gold" />
                <span className="rating-number">{averageRating || '—'}</span>
                <span className="rating-max">/ 5.0</span>
              </div>
              <div className="rating-count-info">
                <strong>
                  {ratingsList.length} {ratingsList.length === 1 ? 'Rating' : 'Ratings'}
                  {writtenReviewsList.length > 0 &&
                    ` • ${writtenReviewsList.length} ${writtenReviewsList.length === 1 ? 'Review' : 'Reviews'}`}
                </strong>
                <p>Community score</p>
              </div>
            </div>
          </header>

          {/* Tracklist Section */}
          <section className={`dossier-tracklist-section ${mobileTab === 'tracks' ? 'tab-visible' : 'tab-hidden'}`}>
            <div className="section-subheading">
              <h3>
                <Music size={16} />
                <span>Tracklist</span>
              </h3>
              <span className="track-stats">{tracks.length} tracks</span>
            </div>

            <div className="track-table">
              <div className="track-table-header" aria-hidden="true">
                <span>#</span>
                <span>Title</span>
                <span>Time</span>
                <span />
              </div>
              {tracks.length === 0 ? (
                <div className="empty-tracks-msg">
                  No individual tracks catalogued for this release.
                </div>
              ) : (
                tracks.map((track) => {
                  const isCurrent = currentTrackId === track.id;
                  return (
                    <div
                      key={track.id}
                      className={`track-item-row ${isCurrent ? 'is-playing-row' : ''}`}
                      onClick={() => {
                        if (isCurrent) {
                          togglePlay();
                        } else if (album) {
                          playTrack(track, album, tracks);
                        }
                      }}
                    >
                      <span className="track-pos">{String(track.position).padStart(2, '0')}</span>
                      <span className="track-name">{track.title}</span>
                      <span className="track-time">{formatDuration(track.lengthMilliseconds)}</span>
                      <button
                        type="button"
                        className={`track-play-preview-btn ${isCurrent ? 'is-active' : ''}`}
                        onClick={(e) => {
                          e.stopPropagation();
                          if (isCurrent) {
                            togglePlay();
                          } else if (album) {
                            playTrack(track, album, tracks);
                          }
                        }}
                        title={isCurrent && isPlayerPlaying ? `Pause "${track.title}"` : `Play "${track.title}"`}
                      >
                        {isCurrent && isPlayerLoading ? (
                          <Loader2 size={12} className="spin-loader" />
                        ) : isCurrent && isPlayerPlaying ? (
                          <div className="mini-equalizer-bars">
                            <span className="eq-bar bar-1"></span>
                            <span className="eq-bar bar-2"></span>
                            <span className="eq-bar bar-3"></span>
                          </div>
                        ) : (
                          <Play size={12} className="play-icon-offset" />
                        )}
                      </button>
                    </div>
                  );
                })
              )}
            </div>
          </section>
        </div>
      </div>

      {/* Community Reviews Section - Down at the bottom */}
      <section className={`dossier-reviews-section ${mobileTab === 'reviews' ? 'tab-visible' : 'tab-hidden'}`}>
            <div className="section-subheading">
              <h3>
                <MessageSquare size={16} />
                <span>Reviews ({writtenReviewsList.length})</span>
              </h3>
              <button
                type="button"
                className="btn-text-gold"
                onClick={handleOpenReviewModal}
              >
                {hasWrittenReview
                  ? `★ Edit your review (${userReview?.rating ? `${userReview.rating}/5` : 'No rating'})`
                  : '+ Write a review'}
              </button>
            </div>

            {writtenReviewsList.length === 0 ? (
              <div className="empty-reviews-box">
                <Disc3 size={32} className="empty-disc" />
                <h4>No reviews yet</h4>
                <p>
                  {ratingsList.length > 0
                    ? `${ratingsList.length} ${ratingsList.length === 1 ? 'listener has' : 'listeners have'} rated this record. Be the first to share your written review for ${album.title}.`
                    : `Be the first to share your thoughts, impressions, and rating for ${album.title}.`}
                </p>
                <button
                  type="button"
                  className="dossier-btn-primary-sm"
                  onClick={handleOpenReviewModal}
                >
                  Write the First Review
                </button>
              </div>
            ) : (
              <div className="reviews-feed-list">
                {writtenReviewsList.map((rev) => {
                  const comments = commentsMap[rev.id] || [];
                  const isCommentsExpanded = expandedCommentsReviewId === rev.id;
                  const isLiked = likedByUserMap[rev.id];
                  const likesCount = likesMap[rev.id] || 0;
                  const isOwnReview =
                    (user?.id && rev.user?.id === user.id) ||
                    (user?.username && rev.user?.username?.toLowerCase() === user.username.toLowerCase());

                  return (
                    <article key={rev.id} className="review-card">
                      <div className="review-card-header">
                        <Link to={`/u/${rev.user?.username}`} className="reviewer-info">
                          <span className="reviewer-avatar">
                            {rev.user?.username?.charAt(0).toUpperCase() || 'U'}
                          </span>
                          <div>
                            <div style={{ display: 'flex', alignItems: 'center', gap: '0.45rem' }}>
                              <span className="reviewer-username">{rev.user?.username || 'Listener'}</span>
                              {isOwnReview && (
                                <span style={{
                                  fontSize: '0.65rem',
                                  padding: '1px 6px',
                                  borderRadius: '999px',
                                  background: 'var(--bg-surface-elevated)',
                                  color: 'var(--accent-gold)',
                                  border: '1px solid var(--border-subtle)',
                                  fontWeight: 700,
                                  textTransform: 'uppercase',
                                  letterSpacing: '0.04em'
                                }}>
                                  You
                                </span>
                              )}
                            </div>
                            <span className="review-date">
                              {rev.createdAt ? parseUtcDate(rev.createdAt).toLocaleDateString() : 'Recently'}
                            </span>
                          </div>
                        </Link>

                        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                          {isOwnReview && (
                            <button
                              type="button"
                              onClick={handleOpenReviewModal}
                              style={{
                                background: 'transparent',
                                border: 'none',
                                color: 'var(--accent-gold)',
                                fontSize: '0.75rem',
                                fontWeight: 600,
                                cursor: 'pointer',
                                textDecoration: 'underline'
                              }}
                            >
                              Edit
                            </button>
                          )}
                          <div className="review-stars-badge">
                            {[...Array(5)].map((_, i) => (
                              <Star
                                key={i}
                                size={14}
                                className={i < (rev.rating || 0) ? 'star-filled' : 'star-empty'}
                              />
                            ))}
                          </div>
                        </div>
                      </div>

                      <div className="review-body-text">
                        <p>{rev.content}</p>
                      </div>

                      <div className="review-card-footer">
                        <button
                          type="button"
                          className={`review-action-btn ${isLiked ? 'active' : ''}`}
                          onClick={() => handleToggleLike(rev.id)}
                        >
                          <Heart size={14} className={isLiked ? 'fill-crimson' : ''} />
                          <span>{likesCount} Likes</span>
                        </button>

                        <button
                          type="button"
                          className="review-action-btn"
                          onClick={() => handleToggleComments(rev.id)}
                        >
                          <MessageSquare size={14} />
                          <span>{comments.length > 0 ? `${comments.length} Comments` : 'Comment'}</span>
                        </button>
                      </div>

                      {/* Expandable Comments Drawer */}
                      {isCommentsExpanded && (
                        <div className="review-comments-box">
                          {comments.length > 0 && (
                            <div className="comments-list">
                              {comments.map((c) => (
                                <div key={c.id} className="comment-item">
                                  <span className="comment-author">
                                    <Link to={`/u/${c.user?.username}`}>{c.user?.username}</Link>:
                                  </span>
                                  <span className="comment-text">{c.content}</span>
                                </div>
                              ))}
                            </div>
                          )}

                          <div className="comment-input-row">
                            <input
                              type="text"
                              placeholder="Add to the conversation..."
                              value={newCommentContent}
                              onChange={(e) => setNewCommentContent(e.target.value)}
                              onKeyDown={(e) => {
                                if (e.key === 'Enter') void handlePostComment(rev.id);
                              }}
                            />
                            <button
                              type="button"
                              className="comment-send-btn"
                              onClick={() => void handlePostComment(rev.id)}
                            >
                              <Send size={14} />
                            </button>
                          </div>
                        </div>
                      )}
                    </article>
                  );
                })}
              </div>
            )}
          </section>

      {/* Review Modal */}
      {isReviewModalOpen && (
        <div className="modal-backdrop" onClick={() => setIsReviewModalOpen(false)}>
          <div className="modal-card review-modal" onClick={(e) => e.stopPropagation()}>
            <button
              className="modal-close-btn"
              onClick={() => setIsReviewModalOpen(false)}
            >
              <X size={18} />
            </button>

            <div className="review-modal-header">
              <span className="kicker">{hasWrittenReview ? 'Your Review' : 'Album Review'}</span>
              <h3>{hasWrittenReview ? `Edit Review for ${album.title}` : `Write Review for ${album.title}`}</h3>
              <p>
                {hasWrittenReview
                  ? 'Update your written review thoughts or modify your star rating.'
                  : 'Share your impressions, commentary, and rating on this release.'}
              </p>
            </div>

            <form onSubmit={handleSaveReview} className="review-form">
              <div className="star-rating-selector">
                <label>Star Rating</label>
                <div className="interactive-stars">
                  {[1, 2, 3, 4, 5].map((starVal) => (
                    <button
                      key={starVal}
                      type="button"
                      onClick={() => setUserRating(starVal)}
                      className={`star-select-btn ${userRating >= starVal ? 'selected' : ''}`}
                    >
                      <Star size={24} />
                    </button>
                  ))}
                  <span className="selected-rating-label">{userRating} / 5</span>
                </div>
              </div>

              <div className="form-field">
                <label>Review & Notes</label>
                <textarea
                  rows={5}
                  placeholder="What makes this record special? How was the production, sequencing, atmosphere?"
                  value={reviewContent}
                  onChange={(e) => setReviewContent(e.target.value)}
                  required
                ></textarea>
              </div>

              <div style={{ display: 'flex', gap: '0.75rem', marginTop: '0.5rem', alignItems: 'center' }}>
                <button
                  type="submit"
                  className="btn-primary"
                  style={{ flex: 1 }}
                  disabled={submittingReview || deletingReview || !reviewContent.trim()}
                >
                  {submittingReview
                    ? (hasWrittenReview ? 'Updating...' : 'Publishing...')
                    : (hasWrittenReview ? 'Update Review' : 'Publish Review')}
                </button>
                {userReview && (
                  <button
                    type="button"
                    onClick={() => setIsDeleteReviewModalOpen(true)}
                    disabled={submittingReview || deletingReview}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: '0.4rem',
                      padding: '0.7rem 1rem',
                      borderRadius: 'var(--radius-sm, 6px)',
                      border: '1px solid rgba(244, 63, 94, 0.4)',
                      background: 'rgba(244, 63, 94, 0.12)',
                      color: '#f43f5e',
                      fontSize: '0.85rem',
                      fontWeight: 600,
                      cursor: 'pointer',
                      transition: 'all 0.2s ease',
                      whiteSpace: 'nowrap'
                    }}
                    title="Delete your review and rating"
                  >
                    <Trash2 size={15} />
                    <span>{deletingReview ? 'Deleting...' : 'Delete'}</span>
                  </button>
                )}
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Add to List Modal */}
      {isListModalOpen && (
        <div className="modal-backdrop" onClick={() => setIsListModalOpen(false)}>
          <div className="modal-card list-select-modal" onClick={(e) => e.stopPropagation()}>
            <button
              className="modal-close-btn"
              onClick={() => setIsListModalOpen(false)}
            >
              <X size={18} />
            </button>

            <div className="review-modal-header">
              <span className="kicker">Curate</span>
              <h3>Add to Collection</h3>
              <p>Pin {album.title} to one of your aesthetic music lists.</p>
            </div>

            {/* Sub-tabs: Existing vs Create New */}
            <div className="modal-sub-tabs">
              <button
                type="button"
                className={`modal-sub-tab-btn ${listModalTab === 'existing' ? 'active' : ''}`}
                onClick={() => setListModalTab('existing')}
                disabled={userLists.length === 0}
              >
                <Bookmark size={14} />
                <span>Existing Collections ({userLists.length})</span>
              </button>
              <button
                type="button"
                className={`modal-sub-tab-btn ${listModalTab === 'create' ? 'active' : ''}`}
                onClick={() => setListModalTab('create')}
              >
                <Plus size={14} />
                <span>New Collection</span>
              </button>
            </div>

            {listSuccessMsg ? (
              <div className="modal-success-banner">{listSuccessMsg}</div>
            ) : listModalTab === 'existing' && userLists.length > 0 ? (
              <div className="list-selection-form">
                <div className="form-field">
                  <label>Select Collection</label>
                  <select
                    value={selectedListId || ''}
                    onChange={(e) => setSelectedListId(Number(e.target.value))}
                  >
                    {userLists.map((l) => (
                      <option key={l.id} value={l.id}>
                        {l.title} ({isListPublic(l) ? 'Public' : 'Private'})
                      </option>
                    ))}
                  </select>
                </div>

                <button
                  type="button"
                  className="btn-primary"
                  onClick={handleAddToList}
                >
                  Save to Collection
                </button>

                <button
                  type="button"
                  className="link-switch-tab-btn"
                  onClick={() => setListModalTab('create')}
                >
                  <Plus size={13} /> Or create a new collection for this album
                </button>
              </div>
            ) : (
              <form onSubmit={handleCreateAndAddToList} className="auth-form" style={{ marginTop: '0.75rem' }}>
                <div className="form-field">
                  <label>Collection Title</label>
                  <input
                    type="text"
                    placeholder="e.g. Midnight Drives & Ambient Textures"
                    value={newCollectionTitle}
                    onChange={(e) => setNewCollectionTitle(e.target.value)}
                    required
                    autoFocus
                  />
                </div>

                <div className="form-field">
                  <label>Description (Optional)</label>
                  <textarea
                    rows={2}
                    placeholder="What is the story or theme for this collection?"
                    value={newCollectionDesc}
                    onChange={(e) => setNewCollectionDesc(e.target.value)}
                  />
                </div>

                <div className="form-field">
                  <label>Privacy & Visibility</label>
                  <div className="visibility-options-grid compact">
                    <button
                      type="button"
                      className={`visibility-option-card ${newCollectionIsPublic ? 'selected' : ''}`}
                      onClick={() => setNewCollectionIsPublic(true)}
                    >
                      <div className="visibility-option-icon globe">
                        <Globe size={16} />
                      </div>
                      <div className="visibility-option-content">
                        <div className="visibility-header-line">
                          <strong>Public Collection</strong>
                          <span className="badge-pill public">Public</span>
                        </div>
                        <p>Visible to everyone in Public Collections and on your profile.</p>
                      </div>
                    </button>

                    <button
                      type="button"
                      className={`visibility-option-card ${!newCollectionIsPublic ? 'selected' : ''}`}
                      onClick={() => setNewCollectionIsPublic(false)}
                    >
                      <div className="visibility-option-icon lock">
                        <Lock size={16} />
                      </div>
                      <div className="visibility-option-content">
                        <div className="visibility-header-line">
                          <strong>Private Collection</strong>
                          <span className="badge-pill private">Private</span>
                        </div>
                        <p>Only visible to you. Kept secret from public lists.</p>
                      </div>
                    </button>
                  </div>
                </div>

                <button
                  type="submit"
                  className="btn-primary"
                  disabled={creatingList || !newCollectionTitle.trim()}
                >
                  {creatingList ? 'Creating...' : `Create Collection & Save "${album.title}"`}
                </button>
              </form>
            )}
          </div>
        </div>
      )}

      {/* In-App Confirmation Modal: Delete Review */}
      <ConfirmModal
        isOpen={isDeleteReviewModalOpen}
        title="Delete Review?"
        message="Are you sure you want to delete your review? This will remove your star rating, review notes, and all community comments on this review."
        confirmText={deletingReview ? 'Deleting...' : 'Delete Review'}
        variant="danger"
        isLoading={deletingReview}
        onConfirm={handleConfirmDeleteReview}
        onCancel={() => setIsDeleteReviewModalOpen(false)}
      />
    </div>
  );
};
