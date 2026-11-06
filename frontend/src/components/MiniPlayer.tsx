import React from 'react';
import { usePlayer } from '../context/PlayerContext';
import {
  Play,
  Pause,
  SkipBack,
  SkipForward,
  X,
  Maximize2,
  Minimize2,
  ExternalLink,
  Loader2,
  Radio,
  Volume2,
  Volume1,
  VolumeX,
  ListMusic,
  AlertCircle,
  Sparkles
} from 'lucide-react';
import { Link } from 'react-router-dom';

const FALLBACK_COVER = '/record-placeholder.svg';

function formatTime(seconds: number): string {
  if (isNaN(seconds) || seconds < 0) return '0:00';
  const mins = Math.floor(seconds / 60);
  const secs = Math.floor(seconds % 60);
  return `${mins}:${secs < 10 ? '0' : ''}${secs}`;
}

export const MiniPlayer: React.FC = () => {
  const {
    currentPlayback,
    isPlaying,
    isLoading,
    isMiniPlayerOpen,
    isExpanded,
    isQueueOpen,
    currentTime,
    duration,
    volume,
    isMuted,
    queue,
    currentAlbum,
    error,
    discoveryNotice,
    dismissDiscoveryNotice,
    iframeRef,
    togglePlay,
    seekTo,
    setVolume,
    toggleMute,
    toggleQueue,
    nextTrack,
    prevTrack,
    playTrack,
    closePlayer,
    toggleExpanded,
  } = usePlayer();

  if (!isMiniPlayerOpen) {
    return null;
  }

  const hasNext = queue.length > 1;
  const hasPrev = queue.length > 1;

  const ytVideoId = currentPlayback?.youtubeVideoId;
  const embedUrl = ytVideoId
    ? `https://www.youtube.com/embed/${ytVideoId}?autoplay=1&enablejsapi=1&origin=${encodeURIComponent(window.location.origin)}&widgetid=1&playsinline=1`
    : '';

  const handleIframeLoad = () => {
    if (iframeRef.current && iframeRef.current.contentWindow) {
      try {
        iframeRef.current.contentWindow.postMessage(
          JSON.stringify({ event: 'listening' }),
          '*'
        );
        iframeRef.current.contentWindow.postMessage(
          JSON.stringify({ event: 'command', func: 'addEventListener', args: ['onStateChange'] }),
          '*'
        );
        iframeRef.current.contentWindow.postMessage(
          JSON.stringify({ event: 'command', func: 'addEventListener', args: ['onError'] }),
          '*'
        );
      } catch (err) {
        console.warn('Iframe handshake failed:', err);
      }
    }
  };

  const progressPercent = duration > 0 ? Math.min(100, (currentTime / duration) * 100) : 0;

  return (
    <aside
      className={`aficio-miniplayer-dock ${isExpanded ? 'is-expanded' : 'is-compact'}`}
      aria-label="Audio & Video Miniplayer"
    >
      {/* 1. Queue Drawer Floating Popover */}
      {isQueueOpen && (
        <div className="miniplayer-queue-drawer">
          <div className="queue-drawer-header">
            <div className="queue-header-title">
              <ListMusic size={16} className="queue-header-icon" />
              <span>Playing Queue</span>
              <span className="queue-count-badge">{queue.length}</span>
            </div>
            <button
              type="button"
              className="queue-close-btn"
              onClick={toggleQueue}
              title="Close Queue"
            >
              <X size={14} />
            </button>
          </div>

          <div className="queue-track-list">
            {queue.map((track, idx) => {
              const isCurrent = currentPlayback && String(track.id) === String(currentPlayback.trackId);
              return (
                <div
                  key={`${track.id}-${idx}`}
                  className={`queue-track-item ${isCurrent ? 'is-active-track' : ''}`}
                  onClick={() => playTrack(track, currentAlbum, queue)}
                >
                  <div className="queue-item-left">
                    <span className="queue-item-idx">
                      {isCurrent ? (
                        <div className="queue-mini-eq">
                          <span className="q-bar bar-1"></span>
                          <span className="q-bar bar-2"></span>
                          <span className="q-bar bar-3"></span>
                        </div>
                      ) : (
                        idx + 1
                      )}
                    </span>
                    <div className="queue-item-info">
                      <span className="queue-item-title">{track.title}</span>
                      <span className="queue-item-sub">
                        {currentPlayback?.artistName || 'Unknown Artist'}
                      </span>
                    </div>
                  </div>
                  <span className="queue-item-duration">
                    {track.lengthMilliseconds ? formatTime(Math.floor(track.lengthMilliseconds / 1000)) : '--:--'}
                  </span>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* 2. Expanded Video Preview Floating Above */}
      <div className={`miniplayer-video-viewport ${isExpanded ? 'viewport-visible' : 'viewport-hidden'}`}>
        <div className="miniplayer-video-header">
          <div className="video-header-info">
            <Radio size={14} className="pulse-icon" />
            <span>YouTube Audio Stream</span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
            {ytVideoId && (
              <a
                href={`https://www.youtube.com/watch?v=${ytVideoId}`}
                target="_blank"
                rel="noreferrer"
                className="video-minimize-btn"
                title="Open video on YouTube"
              >
                <ExternalLink size={13} />
              </a>
            )}
            <button
              type="button"
              className="video-minimize-btn"
              onClick={toggleExpanded}
              title="Minimize video window"
            >
              <Minimize2 size={14} />
            </button>
          </div>
        </div>
        <div className="miniplayer-iframe-wrapper">
          {ytVideoId ? (
            <iframe
              ref={iframeRef}
              src={embedUrl}
              title={currentPlayback?.title || 'YouTube Music Player'}
              allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
              allowFullScreen
              className="miniplayer-iframe"
              onLoad={handleIframeLoad}
            />
          ) : (
            <div className="miniplayer-no-video">
              {isLoading ? (
                <div className="miniplayer-loading-video">
                  <Loader2 size={28} className="spin-loader" />
                  <span>Resolving stream...</span>
                </div>
              ) : (
                <p>No audio stream available for this track.</p>
              )}
            </div>
          )}
        </div>
      </div>

      {/* Error / Quota Notice Banner */}
      {error && (
        <div className="miniplayer-error-dock-banner">
          <AlertCircle size={15} className="error-icon" />
          <span className="error-message-text">{error}</span>
          {error.includes('Settings') && (
            <Link to="/settings" className="error-settings-link">
              Settings &rarr;
            </Link>
          )}
        </div>
      )}

      {/* Discovery Quota Notice Banner */}
      {discoveryNotice && (
        <div className="miniplayer-discovery-dock-banner">
          <Sparkles size={15} className="discovery-icon" />
          <span className="discovery-message-text">
            <strong>New Album Discovered!</strong> Used 1 of your {discoveryNotice.limit} daily options ({discoveryNotice.remaining} remaining today{discoveryNotice.limit > 5 ? ' on personal key' : ''}). Permanently cached for everyone to replay anytime!
          </span>
          <button
            type="button"
            className="discovery-dismiss-btn"
            onClick={dismissDiscoveryNotice}
            title="Dismiss notice"
          >
            <X size={13} />
          </button>
        </div>
      )}

      {/* 3. Main Glassmorphic Control Bar */}
      <div className="miniplayer-bar">
        {/* Left: Track Info & Album Art */}
        <div className="miniplayer-track-info">
          <div className={`miniplayer-art-wrap ${isPlaying ? 'is-spinning' : ''}`}>
            <img
              src={currentPlayback?.coverArtUrl || FALLBACK_COVER}
              alt={currentPlayback?.albumTitle || 'Album Art'}
              className="miniplayer-art"
              onError={(e) => {
                (e.target as HTMLImageElement).src = FALLBACK_COVER;
              }}
            />
            {isPlaying && <div className="miniplayer-vinyl-hole" />}
          </div>

          <div className="miniplayer-text-details">
            <div className="miniplayer-title-row">
              <span className="miniplayer-title" title={currentPlayback?.title || 'Playing track'}>
                {currentPlayback?.title || (isLoading ? 'Loading...' : 'No track')}
              </span>
            </div>

            <div className="miniplayer-meta-row">
              <span className="miniplayer-artist">{currentPlayback?.artistName || 'Unknown Artist'}</span>
              {currentPlayback?.albumTitle && (
                <>
                  <span className="meta-separator">•</span>
                  {currentPlayback.albumId ? (
                    <Link to={`/album/${currentPlayback.albumId}`} className="miniplayer-album-link">
                      {currentPlayback.albumTitle}
                    </Link>
                  ) : (
                    <span className="miniplayer-album">{currentPlayback.albumTitle}</span>
                  )}
                </>
              )}
            </div>
          </div>
        </div>

        {/* Center: Controls + Timeline Scrubber */}
        <div className="miniplayer-center-block">
          <div className="miniplayer-controls">
            <button
              type="button"
              className="ctrl-btn"
              onClick={prevTrack}
              disabled={!hasPrev || isLoading}
              title="Previous track"
            >
              <SkipBack size={20} />
            </button>

            <button
              type="button"
              className="ctrl-btn ctrl-play-main"
              onClick={togglePlay}
              disabled={isLoading && !currentPlayback}
              title={isPlaying ? 'Pause' : 'Play'}
            >
              {isLoading ? (
                <Loader2 size={22} className="spin-loader" />
              ) : isPlaying ? (
                <Pause size={22} />
              ) : (
                <Play size={22} className="play-icon-offset" />
              )}
            </button>

            <button
              type="button"
              className="ctrl-btn"
              onClick={nextTrack}
              disabled={!hasNext || isLoading}
              title="Next track"
            >
              <SkipForward size={20} />
            </button>
          </div>

          {/* Scrubbable Seek Bar */}
          <div className="miniplayer-timeline">
            <span className="time-label">{formatTime(currentTime)}</span>
            <div className="timeline-slider-wrap">
              <input
                type="range"
                min={0}
                max={duration || 300}
                value={currentTime}
                onChange={(e) => seekTo(Number(e.target.value))}
                className="miniplayer-seek-slider"
                style={{
                  background: `linear-gradient(to right, var(--accent-gold, #e5a93b) ${progressPercent}%, rgba(255, 255, 255, 0.15) ${progressPercent}%)`,
                }}
                aria-label="Seek track"
              />
            </div>
            <span className="time-label">{formatTime(duration)}</span>
          </div>
        </div>

        <div className="miniplayer-actions">
          {error && <span className="miniplayer-error-text" title={error}>{error}</span>}

          {/* Volume Control */}
          <div className="miniplayer-volume-wrap">
            <button
              type="button"
              className="action-btn volume-btn"
              onClick={toggleMute}
              title={isMuted ? 'Unmute' : 'Mute'}
            >
              {isMuted || volume === 0 ? (
                <VolumeX size={18} />
              ) : volume < 50 ? (
                <Volume1 size={18} />
              ) : (
                <Volume2 size={18} />
              )}
            </button>
            <div className="volume-slider-track-wrap">
              <input
                type="range"
                min={0}
                max={100}
                value={isMuted ? 0 : volume}
                onChange={(e) => setVolume(Number(e.target.value))}
                className="miniplayer-vol-slider"
                style={{
                  background: `linear-gradient(to right, var(--accent-gold, #e5a93b) ${isMuted ? 0 : volume}%, rgba(255, 255, 255, 0.16) ${isMuted ? 0 : volume}%)`,
                }}
                title={`Volume: ${isMuted ? 0 : volume}%`}
                aria-label="Volume slider"
              />
            </div>
            <span className="miniplayer-vol-text">
              {isMuted ? '0%' : `${volume}%`}
            </span>
          </div>

          {/* Queue Drawer Button */}
          <button
            type="button"
            className={`action-btn queue-btn ${isQueueOpen ? 'is-active' : ''}`}
            onClick={toggleQueue}
            title={`Up next (${queue.length} tracks)`}
          >
            <ListMusic size={18} />
            {queue.length > 0 && <span className="queue-pill-badge">{queue.length}</span>}
          </button>

          {/* Video Popout Button */}
          <button
            type="button"
            className={`action-btn video-toggle-btn ${isExpanded ? 'is-active' : ''}`}
            onClick={toggleExpanded}
            title={isExpanded ? 'Hide video window' : 'Watch video in floating player'}
            aria-label={isExpanded ? 'Hide video window' : 'Watch video in floating player'}
          >
            {isExpanded ? <Minimize2 size={18} /> : <Maximize2 size={18} />}
          </button>

          {ytVideoId && (
            <a
              href={`https://www.youtube.com/watch?v=${ytVideoId}`}
              target="_blank"
              rel="noreferrer"
              className="action-btn"
              title="Open video on YouTube"
            >
              <ExternalLink size={17} />
            </a>
          )}

          <button
            type="button"
            className="action-btn close-btn"
            onClick={closePlayer}
            title="Close player"
          >
            <X size={18} />
          </button>
        </div>

        {/* Mobile Edge Progress Line */}
        <div
          className="miniplayer-progress-line-mobile"
          style={{ width: `${progressPercent}%` }}
        />
      </div>
    </aside>
  );
};
