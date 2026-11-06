import { useEffect, useState } from 'react';
import { useParams, useNavigate, useLocation, Link } from 'react-router-dom';
import {
  api,
  type MusicBrainzArtist,
  type MusicBrainzReleaseResult,
} from '../api/client';
import {
  Disc,
  ArrowLeft,
  ArrowRight,
  Plus,
  Check,
  Loader2,
  User,
  Search,
} from 'lucide-react';

export const ArtistPage = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const location = useLocation();

  const [artist, setArtist] = useState<MusicBrainzArtist | null>(
    (location.state as { artist?: MusicBrainzArtist })?.artist || null
  );
  const [releases, setReleases] = useState<MusicBrainzReleaseResult[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [filterType, setFilterType] = useState<string>('all');
  const [searchTerm, setSearchTerm] = useState('');
  const [syncingId, setSyncingId] = useState<string | null>(null);
  const [syncedMap, setSyncedMap] = useState<Record<string, number>>({});

  useEffect(() => {
    if (!id) return;

    let isMounted = true;

    const fetchData = async () => {
      setLoading(true);
      setError('');

      try {
        // Fetch artist details and releases in parallel
        const [artistRes, releasesRes, localAlbums] = await Promise.allSettled([
          api.catalog.getArtistDetails(id),
          api.catalog.getArtistReleases(id),
          api.catalog.getAlbums(),
        ]);

        if (!isMounted) return;

        // Map existing synced albums
        if (localAlbums.status === 'fulfilled') {
          const map: Record<string, number> = {};
          for (const album of localAlbums.value) {
            if (album.musicBrainzId) {
              map[album.musicBrainzId] = album.id;
            }
          }
          setSyncedMap(map);
        }

        // Set artist metadata
        if (artistRes.status === 'fulfilled' && artistRes.value) {
          setArtist(artistRes.value);
        }

        // Set releases (filter out dead releases with 0 tracks)
        if (releasesRes.status === 'fulfilled' && releasesRes.value) {
          const raw = releasesRes.value.releases || [];
          const rels = raw.filter((r) => r.title && (r['track-count'] === undefined || r['track-count'] > 0));
          setReleases(rels);
        } else if (releasesRes.status === 'rejected') {
          setError(
            releasesRes.reason instanceof Error
              ? releasesRes.reason.message
              : 'Failed to load releases for this artist'
          );
        }
      } catch (err) {
        if (isMounted) {
          setError(err instanceof Error ? err.message : 'An error occurred while loading artist data');
        }
      } finally {
        if (isMounted) {
          setLoading(false);
        }
      }
    };

    fetchData();

    return () => {
      isMounted = false;
    };
  }, [id]);

  const handleSyncAlbum = async (release: MusicBrainzReleaseResult) => {
    if (release['track-count'] === 0) {
      alert('This release has 0 tracks listed on MusicBrainz and cannot be synced.');
      return;
    }
    setSyncingId(release.id);
    try {
      const syncedAlbum = await api.catalog.syncAlbum(release.id);
      setSyncedMap((prev) => ({ ...prev, [release.id]: syncedAlbum.id }));
    } catch (err) {
      alert(err instanceof Error ? err.message : 'Failed to sync album');
    } finally {
      setSyncingId(null);
    }
  };

  // Filter releases by release-group primary type and text search
  const filteredReleases = releases.filter((release) => {
    const rg = release['release-group'];
    const primaryType = (rg?.['primary-type'] || release.type || 'Album').toLowerCase();
    const secondaryTypes = (rg?.['secondary-types'] || []).map((s: string) => s.toLowerCase());
    const isCompilation = secondaryTypes.includes('compilation');

    if (filterType === 'album' && (primaryType !== 'album' || isCompilation)) return false;
    if (filterType === 'compilation' && !isCompilation) return false;
    if (filterType === 'single' && primaryType !== 'single') return false;
    if (filterType === 'ep' && primaryType !== 'ep') return false;

    if (searchTerm.trim()) {
      const term = searchTerm.toLowerCase();
      const titleMatch = release.title.toLowerCase().includes(term);
      const dateMatch = release.date ? release.date.includes(term) : false;
      return titleMatch || dateMatch;
    }

    return true;
  });

  const artistName = artist?.name || 'Artist';

  return (
    <div className="artist-page-container">
      {/* Top Navigation Bar */}
      <div className="artist-top-bar">
        <button
          type="button"
          onClick={() => navigate('/search')}
          className="btn-back"
          title="Back to Search"
        >
          <ArrowLeft size={16} />
          <span>Back to Search</span>
        </button>
        <div className="artist-breadcrumbs">
          <Link to="/">Home</Link>
          <span className="crumb-sep">/</span>
          <Link to="/search">Search</Link>
          <span className="crumb-sep">/</span>
          <span className="crumb-current">{artistName}</span>
        </div>
      </div>

      {/* Artist Hero Header */}
      <section className="artist-hero-section">
        <div className="artist-hero-frame">
          <div className="artist-avatar-badge">
            <User size={38} className="text-gold" />
          </div>
          <div className="artist-hero-details">
            <div className="artist-kicker-row">
              <span className="artist-kicker">Artist Profile</span>
              {artist?.type && <span className="meta-tag">{artist.type}</span>}
              {artist?.country && <span className="meta-tag">{artist.country}</span>}
            </div>
            <h1 className="artist-hero-name">{artistName}</h1>
            {artist?.disambiguation && (
              <p className="artist-disambiguation">{artist.disambiguation}</p>
            )}
          </div>
        </div>
      </section>

      {/* Releases Control & Filter Toolbar */}
      <section className="artist-releases-toolbar">
        <div className="toolbar-left">
          <h2 className="releases-heading">
            <Disc size={20} className="text-gold" />
            <span>Releases by {artistName}</span>
            {!loading && <span className="badge-count">{filteredReleases.length}</span>}
          </h2>
        </div>

        <div className="toolbar-right">
          <div className="releases-search-box">
            <Search size={15} className="search-icon" />
            <input
              type="text"
              placeholder="Filter releases..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
            />
          </div>

          <div className="filter-chips">
            <button
              type="button"
              className={`filter-chip ${filterType === 'all' ? 'active' : ''}`}
              onClick={() => setFilterType('all')}
            >
              All
            </button>
            <button
              type="button"
              className={`filter-chip ${filterType === 'album' ? 'active' : ''}`}
              onClick={() => setFilterType('album')}
            >
              Albums
            </button>
            <button
              type="button"
              className={`filter-chip ${filterType === 'compilation' ? 'active' : ''}`}
              onClick={() => setFilterType('compilation')}
            >
              Compilations
            </button>
            <button
              type="button"
              className={`filter-chip ${filterType === 'single' ? 'active' : ''}`}
              onClick={() => setFilterType('single')}
            >
              Singles
            </button>
            <button
              type="button"
              className={`filter-chip ${filterType === 'ep' ? 'active' : ''}`}
              onClick={() => setFilterType('ep')}
            >
              EPs
            </button>
          </div>
        </div>
      </section>

      {/* Results View */}
      <section className="artist-releases-section">
        {loading && (
          <div className="loading-state-container">
            <Loader2 size={32} className="spin-loader" />
            <p>Fetching all releases by {artistName}...</p>
          </div>
        )}

        {error && !loading && (
          <div className="error-state-container">
            <p>{error}</p>
          </div>
        )}

        {!loading && filteredReleases.length === 0 && !error && (
          <div className="empty-state-container" style={{ padding: '3.5rem 1rem', textAlign: 'center' }}>
            <Disc size={44} className="text-secondary" style={{ opacity: 0.35, margin: '0 auto 1rem', display: 'block' }} />
            <h3 style={{ fontSize: '1.15rem', color: 'var(--text-primary)', marginBottom: '0.5rem' }}>
              No releases available for this artist
            </h3>
            <p style={{ color: 'var(--text-secondary)', fontSize: '0.9rem', maxWidth: '440px', margin: '0 auto' }}>
              Releases will appear here once audio tracks are added to the dataset or indexed into the catalog.
            </p>
          </div>
        )}

        {!loading && filteredReleases.length > 0 && (
          <div className="release-results-grid">
            {filteredReleases.map((release) => {
              const isSynced = syncedMap[release.id];
              const isSyncing = syncingId === release.id;
              const rg = release['release-group'];
              const primaryType = rg?.['primary-type'] || release.type || 'Album';
              const isComp = (rg?.['secondary-types'] || []).some(
                (s: string) => s.toLowerCase() === 'compilation'
              );
              const displayTag = isComp ? 'Compilation' : primaryType;

              return (
                <div key={release.id} className="release-result-card">
                  <div className="release-icon-frame">
                    <Disc size={24} style={{ color: 'var(--text-muted)' }} />
                  </div>
                  <div className="release-info">
                    <span className="release-artist">{artistName}</span>
                    <h4 className="release-title">{release.title}</h4>
                    <div className="release-meta-tags">
                      {displayTag && <span className="meta-tag">{displayTag}</span>}
                      {release.date && <span className="meta-tag">{release.date}</span>}
                      {release.country && <span className="meta-tag">{release.country}</span>}
                    </div>
                  </div>
                  <div className="release-action">
                    {isSynced ? (
                      <button
                        type="button"
                        className="btn-secondary"
                        onClick={() => navigate(`/album/${isSynced}`)}
                      >
                        <Check size={14} />
                        <span>Added</span>
                        <ArrowRight size={13} />
                      </button>
                    ) : (
                      <button
                        type="button"
                        className="btn-primary-sm"
                        disabled={isSyncing}
                        onClick={() => handleSyncAlbum(release)}
                        title={`Add ${release.title} to your library`}
                      >
                        {isSyncing ? (
                          <>
                            <Loader2 size={14} className="spin-loader" />
                            <span>Adding...</span>
                          </>
                        ) : (
                          <>
                            <Plus size={14} />
                            <span>Add to Library</span>
                          </>
                        )}
                      </button>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </section>
    </div>
  );
};
