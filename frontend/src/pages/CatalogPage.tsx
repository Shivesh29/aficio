import { useEffect, useState } from 'react';
import { api, type Album } from '../api/client';
import { AlbumCard } from '../components/AlbumCard';
import { useAuth } from '../context/useAuth';
import {
  Search,
  Disc3,
  Plus,
  Loader2,
} from 'lucide-react';
import { Link } from 'react-router-dom';

export const CatalogPage = () => {
  const { user, isAuthenticated } = useAuth();
  const [albums, setAlbums] = useState<Album[]>([]);
  const [searchQuery, setSearchQuery] = useState('');
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
    if (!q) return true;
    return (
      album.title.toLowerCase().includes(q) ||
      (album.artist?.name && album.artist.name.toLowerCase().includes(q))
    );
  });

  return (
    <div className="catalog-page-container">
      {/* Clean Minimalist Header */}
      <header className="catalog-page-header">
        <div className="catalog-title-row">
          <div>
            <h1 className="catalog-title">Albums</h1>
            <p className="catalog-subtitle">
              {albums.length} {albums.length === 1 ? 'album' : 'albums'} in your collection
            </p>
          </div>
          <div className="catalog-header-actions">
            <Link to="/search" className="btn-secondary add-album-cta">
              <Plus size={15} />
              <span>Discover</span>
            </Link>
          </div>
        </div>

        {/* Search & Filter Toolbar */}
        <div className="catalog-toolbar">
          <div className="catalog-search-input-wrap">
            <Search className="catalog-search-icon" size={16} />
            <input
              type="text"
              placeholder="Filter by title or artist..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
            />
            {searchQuery && (
              <button
                type="button"
                className="hero-search-clear"
                onClick={() => setSearchQuery('')}
                title="Clear filter"
              >
                ✕
              </button>
            )}
          </div>
        </div>
      </header>

      {/* Catalog Grid Section */}
      <section className="catalog-content-section">
        {loading ? (
          <div className="loading-state-container">
            <Loader2 size={32} className="spin-loader" />
            <p>Loading albums...</p>
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
            <Disc3 size={40} style={{ color: 'var(--accent-gold)' }} />
            <h3>Sign in to view your collection</h3>
            <p>Your saved and played albums will appear here.</p>
            <div className="empty-state-actions">
              <Link to="/search" className="btn-primary">
                <span>Discover Music</span>
              </Link>
            </div>
          </div>
        ) : filteredAlbums.length === 0 ? (
          !searchQuery ? (
            <div className="empty-catalog-state">
              <Disc3 size={40} style={{ color: 'var(--accent-gold)' }} />
              <h3>Your library is empty</h3>
              <p>Search for artists or albums to start listening.</p>
              <div className="empty-state-actions">
                <Link to="/search" className="btn-primary">
                  <span>Discover Music</span>
                </Link>
              </div>
            </div>
          ) : (
            <div className="empty-catalog-state">
              <Disc3 size={40} className="empty-icon" />
              <h3>No matching albums found</h3>
              <p>Couldn't find anything matching "{searchQuery}".</p>
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
                      <span>Loading...</span>
                    </>
                  ) : (
                    <>
                      <span>Load More</span>
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
