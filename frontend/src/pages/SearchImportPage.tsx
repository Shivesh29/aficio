import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, type MusicBrainzReleaseResult, type MusicBrainzArtist, type MusicBrainzRecording } from '../api/client';
import { Search, Disc, Check, ArrowRight, Loader2, Globe, User, Mic, Plus, Users, Lock } from 'lucide-react';

type SearchType = 'album' | 'artist' | 'track' | 'user';
type SearchResults = MusicBrainzReleaseResult[] | MusicBrainzArtist[] | MusicBrainzRecording[] | any[];

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

export const SearchImportPage = () => {
  const navigate = useNavigate();

  const [query, setQuery] = useState('');
  const [searchType, setSearchType] = useState<SearchType>('album');
  const [results, setResults] = useState<SearchResults>([]);
  const [searching, setSearching] = useState(false);
  const [syncingId, setSyncingId] = useState<string | null>(null);
  const [syncedMap, setSyncedMap] = useState<Record<string, number>>({});
  const [error, setError] = useState('');

  const handleSearch = async (e: FormEvent) => {
    e.preventDefault();
    if (!query.trim()) return;

    setSearching(true);
    setError('');
    setResults([]);
    try {
      if (searchType === 'user') {
        const users = await api.auth.searchUsers(query.trim());
        setResults(users);
        if (users.length === 0) {
          setError(`No listeners found matching "${query.trim()}". Tip: Private accounts cannot be found by username. Ask your friend for their unique Listener Code (e.g. AF-XXXX-XXXX) or direct profile link.`);
        }
      } else {
        const response = await api.catalog.searchMusicBrainz(query.trim(), searchType);
        if (searchType === 'album') {
          const raw = (response as { releases?: MusicBrainzReleaseResult[] }).releases || [];
          const valid = raw.filter((r) => r.title && (r['track-count'] === undefined || r['track-count'] > 0));
          setResults(valid);
        } else if (searchType === 'artist') {
          setResults((response as { artists?: MusicBrainzArtist[] }).artists || []);
        } else {
          setResults((response as { recordings?: MusicBrainzRecording[] }).recordings || []);
        }

        const count = searchType === 'album'
          ? ((response as { releases?: MusicBrainzReleaseResult[] }).releases || []).filter((r) => r.title && (r['track-count'] === undefined || r['track-count'] > 0)).length
          : searchType === 'artist'
          ? (response as { artists?: MusicBrainzArtist[] }).artists?.length || 0
          : (response as { recordings?: MusicBrainzRecording[] }).recordings?.length || 0;
        if (count === 0) {
          setError(`No ${searchType}s found matching your search. Try another title.`);
        }
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : `Failed to search for ${searchType}s`);
    } finally {
      setSearching(false);
    }
  };

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

  const handleSeeArtistReleases = (artist: MusicBrainzArtist) => {
    navigate(`/artist/${artist.id}`, { state: { artist } });
  };

  const handleSearchTrackAlbum = async (trackTitle: string) => {
    setQuery(trackTitle);
    setSearchType('album');
    setSearching(true);
    setError('');
    setResults([]);
    try {
      const response = await api.catalog.searchMusicBrainz(trackTitle, 'album');
      const raw = (response as { releases?: MusicBrainzReleaseResult[] }).releases || [];
      const releases = raw.filter((r) => r.title && (r['track-count'] === undefined || r['track-count'] > 0));
      setResults(releases);
      if (releases.length === 0) {
        setError(`No albums found containing "${trackTitle}".`);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : `Failed to search albums for ${trackTitle}`);
    } finally {
      setSearching(false);
    }
  };

  const renderResultCard = (result: any) => {
    if (searchType === 'user') {
      const u = result as { id: number; username: string; displayName?: string; bio?: string; avatarUrl?: string; isPrivate?: boolean; listenerCode?: string };
      const initial = (u.username || '?').charAt(0).toUpperCase();
      const profileKey = u.isPrivate && u.listenerCode ? u.listenerCode : u.username;

      return (
        <div
          key={u.id}
          className="release-result-card cursor-pointer"
          onClick={() => navigate(`/u/${profileKey}`)}
        >
          <div className="release-icon-frame" style={{ display: 'grid', placeItems: 'center' }}>
            <div
              className="social-user-avatar-disc"
              style={{
                width: '40px',
                height: '40px',
                background: getAvatarGradient(u.username || ''),
              }}
            >
              <span>{initial}</span>
            </div>
          </div>
          <div className="release-info">
            <span className="release-artist" style={{ display: 'inline-flex', alignItems: 'center', gap: '0.35rem' }}>
              {u.isPrivate ? (
                <>
                  <Lock size={12} style={{ color: 'var(--accent-gold)' }} />
                  <span style={{ color: 'var(--accent-gold)', fontWeight: 600 }}>Private Account</span>
                </>
              ) : (
                'Public Listener'
              )}
            </span>
            <h4 className="release-title">
              @{u.username}
              {u.displayName && <span style={{ marginLeft: '0.5rem', fontSize: '0.88rem', color: 'var(--text-muted)', fontWeight: 400 }}>({u.displayName})</span>}
            </h4>
            {u.bio && (
              <div className="release-meta-tags">
                <span className="meta-tag">{u.bio}</span>
              </div>
            )}
          </div>
          <div className="release-action" onClick={(e) => e.stopPropagation()}>
            <button
              type="button"
              className="btn-primary-sm"
              onClick={() => navigate(`/u/${profileKey}`)}
              title={`View profile of @${u.username}`}
            >
              {u.isPrivate ? <Lock size={13} /> : <User size={14} />}
              <span>{u.isPrivate ? 'View & Follow' : 'View Profile'}</span>
              <ArrowRight size={13} />
            </button>
          </div>
        </div>
      );
    } else if (searchType === 'album') {
      const release = result as MusicBrainzReleaseResult;
      const artistName = release['artist-credit']?.[0]?.name || 'Unknown Artist';
      const isSynced = syncedMap[release.id];
      const isSyncing = syncingId === release.id;

      return (
        <div key={release.id} className="release-result-card">
          <div className="release-icon-frame">
            <Disc size={24} style={{ color: 'var(--text-muted)' }} />
          </div>
          <div className="release-info">
            <span className="release-artist">{artistName}</span>
            <h4 className="release-title">{release.title}</h4>
            <div className="release-meta-tags">
              {release.date && <span className="meta-tag">{release.date}</span>}
              {release.country && <span className="meta-tag">{release.country}</span>}
            </div>
          </div>
          <div className="release-action">
            {isSynced ? (
              <button type="button" className="btn-secondary" onClick={() => navigate(`/album/${isSynced}`)}>
                <Check size={14} /><span>Added</span><ArrowRight size={13} />
              </button>
            ) : (
              <button type="button" className="btn-primary-sm" disabled={isSyncing} onClick={() => handleSyncAlbum(release)}>
                {isSyncing ? (<><Loader2 size={14} className="spin-loader" /><span>Adding...</span></>)
                            : (<><Plus size={14} /><span>Add to Library</span></>)}
              </button>
            )}
          </div>
        </div>
      );
    } else if (searchType === 'artist') {
      const artist = result as MusicBrainzArtist;
      return (
        <div
          key={artist.id}
          className="release-result-card cursor-pointer"
          onClick={() => handleSeeArtistReleases(artist)}
        >
          <div className="release-icon-frame">
            <User size={28} className="text-gold" />
          </div>
          <div className="release-info">
            <span className="release-artist">Artist</span>
            <h4 className="release-title">{artist.name}</h4>
            <div className="release-meta-tags">
              {artist.type && <span className="meta-tag">{artist.type}</span>}
              {artist.country && <span className="meta-tag">{artist.country}</span>}
              {artist.disambiguation && <span className="meta-tag">{artist.disambiguation}</span>}
            </div>
          </div>
          <div className="release-action" onClick={(e) => e.stopPropagation()}>
            <button
              type="button"
              className="btn-primary-sm"
              onClick={() => handleSeeArtistReleases(artist)}
              title={`View releases by ${artist.name}`}
            >
              <Disc size={14} />
              <span>See Releases</span>
              <ArrowRight size={13} />
            </button>
          </div>
        </div>
      );
    } else {
      const track = result as MusicBrainzRecording;
      const trackArtist = track['artist-credit']?.[0]?.name || 'Unknown Artist';
      const trackRelease = track.releases && track.releases.length > 0 ? track.releases[0] : null;
      const isSynced = trackRelease ? syncedMap[trackRelease.id] : null;
      const isSyncing = trackRelease ? syncingId === trackRelease.id : false;

      return (
        <div key={track.id} className="release-result-card">
          <div className="release-icon-frame">
            <Mic size={24} style={{ color: 'var(--text-muted)' }} />
          </div>
          <div className="release-info">
            <span className="release-artist">{trackArtist}</span>
            <h4 className="release-title">{track.title}</h4>
            <div className="release-meta-tags">
              {trackRelease && <span className="meta-tag">Album: {trackRelease.title}</span>}
              {track.length && (
                <span className="meta-tag">
                  {Math.floor(track.length / 60000)}:{String(Math.floor((track.length % 60000) / 1000)).padStart(2, '0')}
                </span>
              )}
            </div>
          </div>
          <div className="release-action">
            {trackRelease ? (
              isSynced ? (
                <button type="button" className="btn-secondary" onClick={() => navigate(`/album/${isSynced}`)}>
                  <Check size={14} /><span>Added</span><ArrowRight size={13} />
                </button>
              ) : (
                <button
                  type="button"
                  className="btn-primary-sm"
                  disabled={isSyncing}
                  onClick={() => handleSyncAlbum(trackRelease)}
                  title={`Import ${trackRelease.title}`}
                >
                  {isSyncing ? (<><Loader2 size={14} className="spin-loader" /><span>Adding...</span></>)
                              : (<><Plus size={14} /><span>Add Album</span></>)}
                </button>
              )
            ) : (
              <button
                type="button"
                className="btn-primary-sm"
                onClick={() => handleSearchTrackAlbum(track.title)}
                title={`Find album for ${track.title}`}
              >
                <Disc size={14} />
                <span>Find Album</span>
                <ArrowRight size={13} />
              </button>
            )}
          </div>
        </div>
      );
    }
  };

  const getPlaceholder = () => {
    switch (searchType) {
      case 'album':
        return 'Search albums (e.g. In Rainbows, Abbey Road, Dark Side of the Moon)...';
      case 'artist':
        return 'Search artists (e.g. Radiohead, Beatles, David Bowie, Pink Floyd)...';
      case 'track':
        return 'Search tracks (e.g. Paranoid Android, Come Together, Time)...';
      case 'user':
        return 'Search public listeners by name, or paste a private Listener Code (e.g. AF-XXXX-XXXX)...';
      default:
        return 'Search releases or artists...';
    }
  };

  return (
    <div className="search-import-page">
      <section className="search-hero-section">
        <h1>Discover Music & Community</h1>
        <p>
          Search across albums, artists, tracks, and public music listeners.
        </p>

        <div className="search-pills-bar">
          <button
            type="button"
            className={`search-pill-btn ${searchType === 'album' ? 'active' : ''}`}
            onClick={() => setSearchType('album')}
          >
            <Disc size={14} />
            <span>Albums</span>
          </button>
          <button
            type="button"
            className={`search-pill-btn ${searchType === 'artist' ? 'active' : ''}`}
            onClick={() => setSearchType('artist')}
          >
            <User size={14} />
            <span>Artists</span>
          </button>
          <button
            type="button"
            className={`search-pill-btn ${searchType === 'track' ? 'active' : ''}`}
            onClick={() => setSearchType('track')}
          >
            <Mic size={14} />
            <span>Tracks</span>
          </button>
          <button
            type="button"
            className={`search-pill-btn ${searchType === 'user' ? 'active' : ''}`}
            onClick={() => setSearchType('user')}
          >
            <Users size={14} />
            <span>Listeners</span>
          </button>
        </div>

        <form onSubmit={handleSearch} className="search-form-full">
          <Search size={18} className="search-input-icon" />
          <input
            type="text"
            placeholder={getPlaceholder()}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            autoFocus
          />
          <select
            value={searchType}
            onChange={(e) => setSearchType(e.target.value as SearchType)}
            className="search-type-select"
          >
            <option value="album">Albums</option>
            <option value="artist">Artists</option>
            <option value="track">Tracks</option>
            <option value="user">Listeners</option>
          </select>
          <button type="submit" className="btn-primary" disabled={searching}>
            {searching ? <Loader2 size={16} className="spin-loader" /> : <Search size={16} />}
            <span>{searching ? 'Searching...' : 'Search'}</span>
          </button>
        </form>

        {searchType === 'user' && (
          <div className="search-privacy-hint-banner" style={{
            marginTop: '0.85rem',
            display: 'flex',
            alignItems: 'center',
            gap: '0.5rem',
            padding: '0.6rem 0.9rem',
            background: 'rgba(217, 119, 6, 0.08)',
            border: '1px solid rgba(217, 119, 6, 0.25)',
            borderRadius: '8px',
            fontSize: '0.85rem',
            color: 'var(--text-secondary, #d1d5db)',
            lineHeight: 1.4,
          }}>
            <Lock size={14} style={{ color: 'var(--accent-gold, #f59e0b)', flexShrink: 0 }} />
            <span>
              <strong style={{ color: '#fff' }}>Private accounts are unlisted.</strong> To find a private friend, search with their unique <code style={{ background: 'rgba(0,0,0,0.4)', padding: '0.15rem 0.4rem', borderRadius: '4px', color: 'var(--accent-gold, #f59e0b)', fontFamily: 'monospace' }}>Listener Code</code> or open their direct profile link.
            </span>
          </div>
        )}
      </section>

      <section className="search-results-section">
        {searching && (
          <div className="loading-state-container">
            <Loader2 size={32} className="spin-loader" />
            <p>Searching global music database...</p>
          </div>
        )}

        {error && !searching && (
          <div className="error-state-container">
            <p>{error}</p>
          </div>
        )}

        {!searching && results.length > 0 && (
          <div className="results-container">
            <div className="section-subheading">
              <h3>
                <Globe size={16} />
                <span>Found {results.length} {searchType}{results.length !== 1 ? 's' : ''}</span>
              </h3>
            </div>
            <div className="release-results-grid">
              {results.map((result) => renderResultCard(result))}
            </div>
          </div>
        )}
      </section>
    </div>
  );
};
