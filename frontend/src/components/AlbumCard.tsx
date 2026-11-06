import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { api, type Album } from '../api/client';
import { usePlayer } from '../context/PlayerContext';
import { Play, Pause, Loader2 } from 'lucide-react';

interface AlbumCardProps {
  album: Album;
  rating?: number;
  featured?: boolean;
}

const FALLBACK_COVER = '/record-placeholder.svg';

export const AlbumCard: React.FC<AlbumCardProps> = ({ album, rating }) => {
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
    <Link to={`/album/${album.id}`} className="album-card-component">
      <div className="album-artwork-wrapper">
        <img
          src={album.coverArtUrl || FALLBACK_COVER}
          alt={`${album.title} cover`}
          loading="lazy"
          className="album-cover-img"
          onError={(e) => {
            (e.target as HTMLImageElement).src = FALLBACK_COVER;
          }}
        />

        {rating && (
          <div className="album-rating-tag">
            ★ {rating.toFixed(1)}
          </div>
        )}

        {/* Quick Play Button on hover */}
        <button
          type="button"
          className={`album-quick-play-btn ${isCurrentAlbum ? 'is-visible' : ''}`}
          onClick={handleQuickPlay}
          disabled={loadingTracks}
          title={isThisAlbumPlaying ? `Pause ${album.title}` : `Play ${album.title}`}
          aria-label={isThisAlbumPlaying ? 'Pause album' : 'Play album'}
        >
          {loadingTracks ? (
            <Loader2 size={18} className="spin-loader" />
          ) : isThisAlbumPlaying ? (
            <Pause size={18} />
          ) : (
            <Play size={18} className="play-icon-offset" />
          )}
        </button>
      </div>

      <div className="album-card-info">
        <h3 className="album-title" title={album.title || 'Untitled Album'}>
          {album.title || 'Untitled Album'}
        </h3>
        <p className="album-artist" title={album.artist?.name || 'Unknown Artist'}>
          {album.artist?.name || 'Unknown Artist'}
        </p>
      </div>
    </Link>
  );
};
