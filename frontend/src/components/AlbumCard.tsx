import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { api, type Album } from '../api/client';
import { usePlayer } from '../context/PlayerContext';
import { Disc, Music, Play, Pause, Loader2 } from 'lucide-react';

interface AlbumCardProps {
  album: Album;
  rating?: number;
  featured?: boolean;
}

const FALLBACK_COVER = '/record-placeholder.svg';

export const AlbumCard: React.FC<AlbumCardProps> = ({ album, rating, featured = false }) => {
  const { currentPlayback, isPlaying, playTrack, togglePlay } = usePlayer();
  const [loadingTracks, setLoadingTracks] = useState(false);

  const isCurrentAlbum = currentPlayback && String(currentPlayback.albumId) === String(album.id);
  const isThisAlbumPlaying = isCurrentAlbum && isPlaying;

  const handleQuickPlay = async (e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();

    if (isCurrentAlbum) {
      togglePlay();
      return;
    }

    try {
      setLoadingTracks(true);
      const tracks = await api.catalog.getTracks(album.id);
      if (tracks && tracks.length > 0) {
        await playTrack(tracks[0], album, tracks);
      }
    } catch (err) {
      console.warn('Quick play album failed:', err);
    } finally {
      setLoadingTracks(false);
    }
  };

  return (
    <Link to={`/album/${album.id}`} className={`album-card-component ${featured ? 'featured' : ''}`}>
      <div className="album-artwork-wrapper">
        {/* Vinyl record disc peeking out behind the jacket */}
        <div className={`vinyl-disc ${isThisAlbumPlaying ? 'is-spinning-disc' : ''}`}>
          <div className="vinyl-groove"></div>
          <div className="vinyl-center-label">
            <Disc size={14} />
          </div>
        </div>

        {/* Album Jacket Cover */}
        <div className="album-jacket">
          <img
            src={album.coverArtUrl || FALLBACK_COVER}
            alt={`${album.title} cover`}
            loading="lazy"
            onError={(e) => {
              (e.target as HTMLImageElement).src = FALLBACK_COVER;
            }}
          />
          <div className="jacket-sheen"></div>

          {rating && (
            <div className="album-rating-tag">
              ★ {rating.toFixed(1)}
            </div>
          )}

          {/* Quick Play Hover Button */}
          <button
            type="button"
            className={`album-quick-play-btn ${isCurrentAlbum ? 'is-visible' : ''}`}
            onClick={handleQuickPlay}
            disabled={loadingTracks}
            title={isThisAlbumPlaying ? `Pause ${album.title}` : `Play ${album.title}`}
            aria-label={isThisAlbumPlaying ? 'Pause album' : 'Play album'}
          >
            {loadingTracks ? (
              <Loader2 size={16} className="spin-loader" />
            ) : isThisAlbumPlaying ? (
              <Pause size={16} />
            ) : (
              <Play size={16} className="play-icon-offset" />
            )}
          </button>
        </div>
      </div>

      <div className="album-card-info">
        <h3 className="album-title" title={album.title || 'Untitled Album'}>
          {album.title || 'Untitled Album'}
        </h3>
        <p className="album-artist" title={album.artist?.name || 'Unknown Artist'}>
          <Music size={12} className="inline-icon" />
          <span>{album.artist?.name || 'Unknown Artist'}</span>
        </p>
      </div>
    </Link>
  );
};
