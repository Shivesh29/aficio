import { useEffect, useState } from 'react';
import { api, type Album } from '../api/client';
import { AlbumCard } from '../components/AlbumCard';
import { useAuth } from '../context/useAuth';
import {
  Search,
  Sparkles,
  Disc3,
  Plus,
  Loader2,
  Flame,
  Clock,
  Music2,
} from 'lucide-react';
import { Link } from 'react-router-dom';

export const CatalogPage = () => {
  const { user, isAuthenticated } = useAuth();
  const [albums, setAlbums] = useState<Album[]>([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [filterMode, setFilterMode] = useState<'all' | 'featured' | 'recent'>('all');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [recordLimit, setRecordLimit] = useState(60);
  const [hasMoreRecords, setHasMoreRecords] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);

  useEffect(() => {
    async function loadCatalog() {
      if (!isAuthenticated) {
        setAlbums([]);
        setLoading(false);
        return;
      }
      try {
        setLoading(true);
        setError('');

        const res = await api.catalog.getMyAlbums(recordLimit, user?.username);
        // Deduplicate by musicBrainzId
        const unique = res.filter(
          (album, idx, arr) => arr.findIndex((a) => a.musicBrainzId === album.musicBrainzId) === idx
        );

        setAlbums(unique);
        setHasMoreRecords(res.length >= recordLimit);
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Unable to load catalog');
      } finally {
        setLoading(false);
      }
    }
    void loadCatalog();
  }, [isAuthenticated, recordLimit, user?.username]);

  const handleLoadMoreRecords = async () => {
    if (loadingMore || recordLimit >= 120 || !isAuthenticated) return;
    try {
      setLoadingMore(true);
      const nextLimit = Math.min(recordLimit + 30, 120);
      const res = await api.catalog.getMyAlbums(nextLimit, user?.username);
      const unique = res.filter(
        (album, idx, arr) => arr.findIndex((a) => a.musicBrainzId === album.musicBrainzId) === idx
      );
      setAlbums(unique);
      setRecordLimit(nextLimit);
      setHasMoreRecords(res.length >= nextLimit && nextLimit < 120);
    } catch (err) {
      console.warn('Failed to load more vault records:', err);
    } finally {
      setLoadingMore(false);
    }
  };

  const filteredAlbums = albums.filter((album) => {
    const q = searchQuery.toLowerCase().trim();
    const matchesSearch =
      !q ||
      album.title.toLowerCase().includes(q) ||
      (album.artist?.name && album.artist.name.toLowerCase().includes(q));

    if (!matchesSearch) return false;

    if (filterMode === 'featured') {
      return album.id % 2 === 1;
    }
    return true;
  });

  return (
    <div className="catalog-page-container">
      {/* Clean Centered Audiophile Hero */}
      <section className="catalog-hero-centered">
        <div className="hero-kicker">
          <span className="live-indicator"></span>
          <span>Personal Listening Room</span>
          <span className="hero-pill-tag">✦ SYNCED &amp; PLAYED</span>
        </div>

        <h1 className="hero-headline">
          Your personal<br />
          <span className="headline-gradient">listening vault.</span>
        </h1>

        <p className="hero-subtext">
          Every record synced and played on your account is preserved here in your private listening vault with high-fidelity streams and artwork.
        </p>

        <div className="hero-search-box">
          <Search className="hero-search-icon" size={18} />
          <input
            type="text"
            placeholder="Search your synced records by title or artist..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />
          {searchQuery && (
            <button
              type="button"
              className="hero-search-clear"
              onClick={() => setSearchQuery('')}
              title="Clear search"
            >
              ✕
            </button>
          )}
          <Link to="/search" className="hero-import-link">
            <Sparkles size={14} />
            <span>Discover &amp; Import</span>
          </Link>
        </div>

        {/* Quick Filter Chips */}
        <div className="hero-filter-chips">
          <button
            type="button"
            className={`filter-chip ${filterMode === 'all' ? 'is-active' : ''}`}
            onClick={() => setFilterMode('all')}
          >
            <Music2 size={13} />
            <span>All Records ({albums.length})</span>
          </button>
          <button
            type="button"
            className={`filter-chip ${filterMode === 'featured' ? 'is-active' : ''}`}
            onClick={() => setFilterMode('featured')}
          >
            <Flame size={13} />
            <span>Spotlight</span>
          </button>
          <button
            type="button"
            className={`filter-chip ${filterMode === 'recent' ? 'is-active' : ''}`}
            onClick={() => setFilterMode('recent')}
          >
            <Clock size={13} />
            <span>Recently Played</span>
          </button>
        </div>
      </section>

      {/* Catalog Grid Section */}
      <section className="catalog-content-section">
        <div className="catalog-header">
          <div className="section-title-wrap">
            <h2 className="section-title">
              <span>My Synced Records</span>
              <span className="title-flair"></span>
            </h2>
            <span className="count-badge">
              {filteredAlbums.length} {filteredAlbums.length === 1 ? 'record' : 'records'}
            </span>
            <p className="catalog-archive-note">
              These albums have been synced and played on your account. You can stream and replay them without consuming daily discovery quotas.
            </p>
          </div>

          <div className="catalog-actions-bar">
            <Link to="/search" className="btn-secondary add-album-cta">
              <Plus size={15} />
              <span>Import New Album</span>
            </Link>
          </div>
        </div>

        {loading ? (
          <div className="loading-state-container">
            <Loader2 size={36} className="spin-loader" />
            <p>Tuning frequencies and retrieving catalogued records...</p>
          </div>
        ) : error ? (
          <div className="error-state-container">
            <p>{error}</p>
            <button
              type="button"
              className="btn-secondary"
              onClick={() => window.location.reload()}
            >
              Try Again
            </button>
          </div>
        ) : !isAuthenticated ? (
          <div className="empty-catalog-state">
            <div className="empty-vinyl-deck">
              <Disc3 size={46} className="spin-slow" style={{ color: 'var(--accent-gold)' }} />
            </div>
            <h3>Sign in to access your listening vault</h3>
            <p>
              Your synced and played records are saved to your account. Sign in to view and stream your personal catalog.
            </p>
            <div className="empty-state-actions">
              <Link to="/search" className="btn-primary">
                <Sparkles size={16} />
                <span>Discover &amp; Import Releases</span>
              </Link>
            </div>
          </div>
        ) : filteredAlbums.length === 0 ? (
          !searchQuery ? (
            <div className="empty-catalog-state">
              <div className="empty-vinyl-deck">
                <Disc3 size={46} className="spin-slow" style={{ color: 'var(--accent-gold)' }} />
              </div>
              <h3>Your Personal Vault is Clean</h3>
              <p>
                You haven't played or synced any albums yet. Search any artist or album from the open music graph to stream it, and it will be permanently catalogued here in your listening room.
              </p>
              <div className="empty-state-actions">
                <Link to="/search" className="btn-primary">
                  <Sparkles size={16} />
                  <span>Discover &amp; Import First Record</span>
                </Link>
              </div>
            </div>
          ) : (
            <div className="empty-catalog-state">
              <Disc3 size={54} className="empty-icon" />
              <h3>No matching releases found</h3>
              <p>
                Couldn't find anything matching "{searchQuery}" in your synced vault. Try discovering and importing new releases.
              </p>
              <Link to="/search" className="btn-primary">
                <Sparkles size={16} />
                <span>Search &amp; Import Releases</span>
              </Link>
            </div>
          )
        ) : (
          <>
            <div className="albums-grid-layout">
              {filteredAlbums.map((album) => (
                <AlbumCard key={album.id} album={album} />
              ))}
            </div>

            {!searchQuery && hasMoreRecords && (
              <div className="catalog-load-more-section">
                <button
                  type="button"
                  className="catalog-load-more-btn"
                  disabled={loadingMore}
                  onClick={handleLoadMoreRecords}
                >
                  {loadingMore ? (
                    <>
                      <Loader2 size={16} className="spin-loader" />
                      <span>Retrieving next vault records...</span>
                    </>
                  ) : (
                    <>
                      <Disc3 size={16} />
                      <span>Load More Synced Records (+30)</span>
                    </>
                  )}
                </button>
              </div>
            )}
          </>
        )}
      </section>
    </div>
  );
};
