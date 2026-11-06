import { useEffect, useState } from 'react';
import { api, type FeedRelease, type Album } from '../api/client';
import { useAuth } from '../context/useAuth';
import { usePlayer } from '../context/PlayerContext';
import {
  Loader2,
  Disc3,
  Play,
  Pause,
  Plus,
  ArrowRight,
  Music2,
  Check,
} from 'lucide-react';
import { Link } from 'react-router-dom';

export const ActivityFeedPage = () => {
  const { isAuthenticated } = useAuth();
  const { playTrack, currentPlayback, isPlaying, togglePlay } = usePlayer();

  const [feedReleases, setFeedReleases] = useState<FeedRelease[]>([]);
  const [starterAlbums, setStarterAlbums] = useState<Album[]>([]);
  const [loadingReleases, setLoadingReleases] = useState(true);
  const [syncingMbid, setSyncingMbid] = useState<string | null>(null);
  const [albumScopeFilter, setAlbumScopeFilter] = useState<'all' | 'ready' | 'discover'>('all');

  // Load personalized release feed
  useEffect(() => {
    if (!isAuthenticated) return;

    let isMounted = true;
    async function loadReleases() {
      try {
        setLoadingReleases(true);
        const data = await api.catalog.getFeed(16);
        if (isMounted) {
          setFeedReleases(data);
          // If user has 0 releases in feed (brand new user), fetch 4 starter albums to help them tune their radar
          if (data.length === 0) {
            const allCommunity = await api.catalog.getAlbums(true).catch(() => []);
            if (isMounted) {
              setStarterAlbums(allCommunity.slice(0, 4));
            }
          }
        }
      } catch (err) {
        console.warn('Failed to load release feed:', err);
      } finally {
        if (isMounted) setLoadingReleases(false);
      }
    }

    void loadReleases();
    return () => {
      isMounted = false;
    };
  }, [isAuthenticated]);

  const handleSyncRelease = async (release: FeedRelease) => {
    if (!release.musicBrainzId) return;
    setSyncingMbid(release.musicBrainzId);
    try {
      const synced = await api.catalog.syncAlbum(release.musicBrainzId);
      // Strictly sync into catalog without autoplay - playing is left up to the user
      setFeedReleases((prev) =>
        prev.map((r) =>
          r.musicBrainzId === release.musicBrainzId
            ? {
                ...r,
                id: synced.id,
                coverArtUrl: synced.coverArtUrl || r.coverArtUrl,
                inCatalog: true,
              }
            : r
        )
      );
    } catch (err) {
      alert(err instanceof Error ? err.message : 'Failed to sync album');
    } finally {
      setSyncingMbid(null);
    }
  };

  const handlePlayRelease = async (release: FeedRelease) => {
    if (!release.inCatalog || !release.id) return;

    if (currentPlayback && String(currentPlayback.albumId) === String(release.id)) {
      togglePlay();
      return;
    }

    try {
      const tracks = await api.catalog.getTracks(release.id);
      if (tracks && tracks.length > 0) {
        await playTrack(
          tracks[0],
          {
            id: release.id,
            title: release.title,
            musicBrainzId: release.musicBrainzId,
            coverArtUrl: release.coverArtUrl,
            artist: { id: 0, name: release.artistName, musicBrainzId: release.artistMusicBrainzId },
          },
          tracks
        );
      }
    } catch (err) {
      console.warn('Failed to play feed release:', err);
    }
  };

  const handlePlayStarter = async (starter: Album) => {
    if (currentPlayback && String(currentPlayback.albumId) === String(starter.id)) {
      togglePlay();
      return;
    }
    try {
      const tracks = await api.catalog.getTracks(starter.id);
      if (tracks && tracks.length > 0) {
        await playTrack(tracks[0], starter, tracks);
        // Refresh feed after playing starter album to populate releases!
        setTimeout(async () => {
          const fresh = await api.catalog.getFeed(16).catch(() => []);
          if (fresh.length > 0) {
            setFeedReleases(fresh);
          }
        }, 1200);
      }
    } catch (err) {
      console.warn('Failed to stream starter album:', err);
    }
  };

  if (!isAuthenticated) {
    return (
      <div className="empty-catalog-state" style={{ margin: '6rem auto' }}>
        <Disc3 size={40} style={{ color: 'var(--accent-gold)' }} />
        <h3>Sign in to view your feed</h3>
        <p>Your feed curates new and unplayed releases from artists in your library.</p>
        <Link to="/search" className="btn-primary" style={{ marginTop: '1.25rem' }}>
          <span>Discover Music</span>
        </Link>
      </div>
    );
  }

  const validAlbumReleases = feedReleases.filter(
    (r) => r.trackCount == null || r.trackCount >= 5
  );
  const readyCount = validAlbumReleases.filter((r) => r.inCatalog).length;
  const discoverCount = validAlbumReleases.filter((r) => !r.inCatalog).length;

  const displayedReleases = validAlbumReleases.filter((r) => {
    if (albumScopeFilter === 'ready') return r.inCatalog;
    if (albumScopeFilter === 'discover') return !r.inCatalog;
    return true;
  });

  return (
    <div className="activity-feed-page">
      {/* Header */}
      <section className="feed-header-section">
        <div className="feed-header-left">
          <h1 className="feed-title">Feed</h1>
          <p className="feed-subtitle">
            Releases and recommendations based on your listening history.
          </p>
        </div>
      </section>

      {/* Personalized Artist Releases */}
      <section className="feed-content-section">
        {loadingReleases ? (
          <div className="loading-state-container feed-loading-deck">
            <Loader2 size={32} className="spin-loader" />
            <p className="feed-loading-text">Loading feed...</p>
          </div>
        ) : validAlbumReleases.length === 0 ? (
          <div className="empty-catalog-state">
            <Disc3 size={40} style={{ color: 'var(--accent-gold)' }} />
            <h3>Your feed is empty</h3>
            <p>
              Listen to or add albums to discover more releases from your favorite artists.
            </p>

            {starterAlbums.length > 0 && (
              <div className="feed-starter-section" style={{ width: '100%', maxWidth: '900px' }}>
                <div className="feed-starter-header">
                  <h4>Popular Albums to Get Started:</h4>
                </div>
                <div className="feed-starter-grid">
                  {starterAlbums.map((album) => (
                    <div key={album.id} className="feed-release-card">
                      <div className="feed-card-media">
                        <img
                          src={album.coverArtUrl || '/record-placeholder.svg'}
                          alt={album.title}
                          className="feed-card-cover"
                          onError={(e) => {
                            (e.target as HTMLImageElement).src = '/record-placeholder.svg';
                          }}
                        />
                        <div className="feed-card-play-overlay">
                          <button
                            type="button"
                            className="feed-play-circle-btn"
                            onClick={() => handlePlayStarter(album)}
                            title="Stream to activate feed"
                          >
                            <Play size={20} className="play-icon-offset" />
                          </button>
                        </div>
                      </div>
                      <div className="feed-card-body">
                        <span className="feed-artist-name">{album.artist?.name}</span>
                        <h4 className="feed-album-title">{album.title}</h4>
                        <button
                          type="button"
                          className="btn-primary-sm"
                          style={{ marginTop: 'auto' }}
                          onClick={() => handlePlayStarter(album)}
                        >
                          <Play size={13} />
                          <span>Stream Now</span>
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        ) : (
          <>
            {/* Filter Controls */}
            <div className="feed-subfilter-bar">
              <button
                type="button"
                className={`feed-subfilter-btn ${albumScopeFilter === 'all' ? 'active' : ''}`}
                onClick={() => setAlbumScopeFilter('all')}
              >
                <span>All</span>
                <span className="feed-subfilter-count">{validAlbumReleases.length}</span>
              </button>
              <button
                type="button"
                className={`feed-subfilter-btn ${albumScopeFilter === 'ready' ? 'active' : ''}`}
                onClick={() => setAlbumScopeFilter('ready')}
              >
                <span>In Library</span>
                <span className="feed-subfilter-count">{readyCount}</span>
              </button>
              <button
                type="button"
                className={`feed-subfilter-btn ${albumScopeFilter === 'discover' ? 'active' : ''}`}
                onClick={() => setAlbumScopeFilter('discover')}
              >
                <span>Discover</span>
                <span className="feed-subfilter-count">{discoverCount}</span>
              </button>
            </div>

            {displayedReleases.length === 0 ? (
              <div className="empty-catalog-state" style={{ margin: '3rem auto' }}>
                <p>No albums match the selected filter.</p>
                <button
                  type="button"
                  className="btn-secondary-sm"
                  style={{ marginTop: '0.75rem' }}
                  onClick={() => setAlbumScopeFilter('all')}
                >
                  Show All Albums
                </button>
              </div>
            ) : (
              <div className="feed-releases-grid">
                {displayedReleases.map((release, idx) => {
                  const isCurrentlyPlaying =
                    currentPlayback &&
                    release.id &&
                    String(currentPlayback.albumId) === String(release.id) &&
                    isPlaying;
                  const isSyncingThis = syncingMbid === release.musicBrainzId;

                  return (
                    <div key={release.musicBrainzId || idx} className="feed-release-card">
                      {/* Media Thumbnail */}
                      <div className="feed-card-media">
                        <img
                          src={release.coverArtUrl || '/record-placeholder.svg'}
                          alt={release.title}
                          className="feed-card-cover"
                          onError={(e) => {
                            (e.target as HTMLImageElement).src = '/record-placeholder.svg';
                          }}
                        />
                        <div className="feed-card-play-overlay">
                          {release.inCatalog && release.id ? (
                            <button
                              type="button"
                              className="feed-play-circle-btn"
                              onClick={() => handlePlayRelease(release)}
                              title={isCurrentlyPlaying ? 'Pause' : 'Play'}
                            >
                              {isCurrentlyPlaying ? (
                                <Pause size={20} />
                              ) : (
                                <Play size={20} className="play-icon-offset" />
                              )}
                            </button>
                          ) : (
                            <button
                              type="button"
                              className="feed-sync-circle-btn"
                              disabled={isSyncingThis}
                              onClick={() => handleSyncRelease(release)}
                              title="Add to library"
                            >
                              {isSyncingThis ? (
                                <Loader2 size={20} className="spin-loader" />
                              ) : (
                                <Plus size={20} />
                              )}
                            </button>
                          )}
                        </div>
                      </div>

                      {/* Card Body */}
                      <div className="feed-card-body">
                        {release.reason && (
                          <span className="feed-reason-tag" title={release.reason}>
                            <Music2 size={12} />
                            <span>{release.reason}</span>
                          </span>
                        )}

                        <span className="feed-artist-name">{release.artistName}</span>
                        <h4 className="feed-album-title">{release.title}</h4>

                        {/* Card Footer */}
                        <div className="feed-card-footer">
                          {release.inCatalog ? (
                            <span className="feed-catalog-badge">
                              <Check size={12} />
                              <span>
                                {release.trackCount
                                  ? `${release.trackCount} tracks`
                                  : 'In Library'}
                              </span>
                            </span>
                          ) : (
                            <span className="feed-sync-badge">
                              {release.trackCount ? `${release.trackCount} tracks` : 'Album'}
                              {release.date ? ` • ${release.date.substring(0, 4)}` : ''}
                            </span>
                          )}

                          <div className="feed-actions-row">
                            {release.inCatalog && release.id ? (
                              <>
                                <button
                                  type="button"
                                  className="btn-play-sm"
                                  onClick={() => handlePlayRelease(release)}
                                  title={isCurrentlyPlaying ? 'Pause' : 'Play'}
                                >
                                  {isCurrentlyPlaying ? <Pause size={12} /> : <Play size={12} />}
                                  <span>{isCurrentlyPlaying ? 'Pause' : 'Play'}</span>
                                </button>
                                <Link to={`/album/${release.id}`} className="btn-secondary-sm">
                                  <span>View</span>
                                  <ArrowRight size={12} />
                                </Link>
                              </>
                            ) : (
                              <button
                                type="button"
                                className="btn-primary-sm"
                                disabled={isSyncingThis}
                                onClick={() => handleSyncRelease(release)}
                                title="Add album to your library"
                              >
                                {isSyncingThis ? (
                                  <>
                                    <Loader2 size={12} className="spin-loader" />
                                    <span>Adding...</span>
                                  </>
                                ) : (
                                  <>
                                    <Plus size={12} />
                                    <span>Add</span>
                                  </>
                                )}
                              </button>
                            )}
                          </div>
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </>
        )}
      </section>
    </div>
  );
};
