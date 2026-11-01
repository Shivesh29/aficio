import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, type MusicBrainzReleaseResult, type MusicBrainzArtist, type MusicBrainzRecording } from '../api/client';
import { Search, Sparkles, Disc, Check, ArrowRight, Loader2, Globe, User, Mic } from 'lucide-react';

type SearchType = 'album' | 'artist' | 'track';
type SearchResults = MusicBrainzReleaseResult[] | MusicBrainzArtist[] | MusicBrainzRecording[];

export const SearchImportPage = () => {
  const navigate = useNavigate();

  const [query, setQuery] = useState('');
  const [searchType, setSearchType] = useState<SearchType>('artist');
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
    if (searchType === 'album') {
      const release = result as MusicBrainzReleaseResult;
      const artistName = release['artist-credit']?.[0]?.name || 'Unknown Artist';
      const isSynced = syncedMap[release.id];
      const isSyncing = syncingId === release.id;

      return (
        <div key={release.id} className="release-result-card">
          <div className="release-icon-frame">
            <Disc size={28} className="spin-slow text-gold" />
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
              <button type="button" className="btn-success" onClick={() => navigate(`/album/${isSynced}`)}>
                <Check size={14} /><span>View Dossier</span><ArrowRight size={13} />
              </button>
            ) : (
              <button type="button" className="btn-primary-sm" disabled={isSyncing} onClick={() => handleSyncAlbum(release)}>
                {isSyncing ? (<><Loader2 size={14} className="spin-loader" /><span>Enriching...</span></>)
                            : (<><Sparkles size={14} /><span>Sync to Aficio</span></>)}
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
            <Mic size={28} className="text-gold" />
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
                <button type="button" className="btn-success" onClick={() => navigate(`/album/${isSynced}`)}>
                  <Check size={14} /><span>View Dossier</span><ArrowRight size={13} />
                </button>
              ) : (
                <button
                  type="button"
                  className="btn-primary-sm"
                  disabled={isSyncing}
                  onClick={() => handleSyncAlbum(trackRelease)}
                  title={`Import ${trackRelease.title}`}
                >
                  {isSyncing ? (<><Loader2 size={14} className="spin-loader" /><span>Enriching...</span></>)
                              : (<><Sparkles size={14} /><span>Sync Album</span></>)}
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

  return (
    <div className="search-import-page">
      <section className="search-hero-section">
        <div className="kicker">Global Music Catalog</div>
        <h1>Discover & Import Music</h1>
        <p>
          Search for albums, artists, or tracks worldwide, and import into Aficio.
        </p>

        <form onSubmit={handleSearch} className="search-form-full">
          <Search size={20} className="search-input-icon" />
          <input
            type="text"
            placeholder={`Search ${searchType}s (e.g. In Rainbows, Radiohead, Kid A)...`}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            autoFocus
          />
          <select
            value={searchType}
            onChange={(e) => setSearchType(e.target.value as SearchType)}
            className="search-type-select"
          >
            <option value="artist">Artist</option>
            <option value="album">Album</option>
            <option value="track">Track</option>
          </select>
          <button type="submit" className="btn-primary" disabled={searching}>
            {searching ? <Loader2 size={16} className="spin-loader" /> : <Sparkles size={16} />}
            <span>{searching ? 'Searching...' : 'Search Catalog'}</span>
          </button>
        </form>
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
