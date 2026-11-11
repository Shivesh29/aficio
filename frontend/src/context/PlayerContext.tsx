import React, { createContext, useContext, useState, useRef, useCallback, useEffect } from 'react';
import { api, type Album, type Track, type TrackPlaybackResponse } from '../api/client';
import { useAuth } from './useAuth';

interface PlayerContextType {
  currentPlayback: TrackPlaybackResponse | null;
  currentTrackId: number | null;
  isPlaying: boolean;
  isLoading: boolean;
  isMiniPlayerOpen: boolean;
  isExpanded: boolean;
  isQueueOpen: boolean;
  currentTime: number;
  duration: number;
  volume: number;
  isMuted: boolean;
  queue: Track[];
  currentAlbum: Album | null;
  currentIndex: number;
  error: string | null;
  iframeRef: React.RefObject<HTMLIFrameElement | null>;
  playTrack: (track: Track, album?: Album | null, tracklist?: Track[]) => Promise<void>;
  togglePlay: () => void;
  seekTo: (seconds: number) => void;
  setVolume: (vol: number) => void;
  toggleMute: () => void;
  toggleQueue: () => void;
  setIsQueueOpen: (open: boolean) => void;
  nextTrack: () => Promise<void>;
  prevTrack: () => Promise<void>;
  closePlayer: () => void;
  toggleExpanded: () => void;
  setIsPlaying: (playing: boolean) => void;
  isGuestPromptOpen: boolean;
  closeGuestPrompt: () => void;
}

const PlayerContext = createContext<PlayerContextType | undefined>(undefined);

export const PlayerProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { isAuthenticated } = useAuth();
  const [currentPlayback, setCurrentPlayback] = useState<TrackPlaybackResponse | null>(null);
  const [currentTrackId, setCurrentTrackId] = useState<number | null>(null);
  const [isPlaying, setIsPlaying] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [isMiniPlayerOpen, setIsMiniPlayerOpen] = useState(false);
  const [isExpanded, setIsExpanded] = useState(false);
  const [isQueueOpen, setIsQueueOpen] = useState(false);
  const [currentTime, setCurrentTime] = useState<number>(0);
  const [isGuestPromptOpen, setIsGuestPromptOpen] = useState(false);

  const closeGuestPrompt = useCallback(() => {
    setIsGuestPromptOpen(false);
    try {
      sessionStorage.setItem('aficio_guest_prompt_dismissed', 'true');
    } catch {
      // ignore
    }
  }, []);

  useEffect(() => {
    if (isAuthenticated) {
      setIsGuestPromptOpen(false);
    }
  }, [isAuthenticated]);
  const [duration, setDuration] = useState<number>(0);
  const [volume, setVolumeState] = useState<number>(80);
  const [isMuted, setIsMuted] = useState<boolean>(false);
  const [queue, setQueue] = useState<Track[]>([]);
  const [currentAlbum, setCurrentAlbum] = useState<Album | null>(null);
  const [currentIndex, setCurrentIndex] = useState<number>(-1);
  const [error, setError] = useState<string | null>(null);

  const iframeRef = useRef<HTMLIFrameElement | null>(null);
  const queueRef = useRef<Track[]>([]);
  const currentIndexRef = useRef<number>(-1);
  const currentAlbumRef = useRef<Album | null>(null);
  const isPlayingRef = useRef<boolean>(false);
  const currentTrackIdRef = useRef<number | null>(null);

  // Sync refs to avoid stale closures in event listeners or async callbacks
  useEffect(() => {
    queueRef.current = queue;
  }, [queue]);

  useEffect(() => {
    currentIndexRef.current = currentIndex;
  }, [currentIndex]);

  useEffect(() => {
    currentAlbumRef.current = currentAlbum;
  }, [currentAlbum]);

  useEffect(() => {
    isPlayingRef.current = isPlaying;
  }, [isPlaying]);

  useEffect(() => {
    currentTrackIdRef.current = currentTrackId;
  }, [currentTrackId]);

  const currentPlaybackRef = useRef<TrackPlaybackResponse | null>(null);
  useEffect(() => {
    currentPlaybackRef.current = currentPlayback;
  }, [currentPlayback]);

  const sendIframeCommand = useCallback((func: string, args: any = '') => {
    if (iframeRef.current && iframeRef.current.contentWindow) {
      try {
        iframeRef.current.contentWindow.postMessage(
          JSON.stringify({ event: 'command', func, args }),
          '*'
        );
      } catch (err) {
        console.warn('sendIframeCommand error:', err);
      }
    }
  }, []);

  const registerIframeListeners = useCallback(() => {
    if (iframeRef.current && iframeRef.current.contentWindow) {
      try {
        iframeRef.current.contentWindow.postMessage(
          JSON.stringify({ event: 'listening' }),
          '*'
        );
        sendIframeCommand('addEventListener', ['onStateChange']);
        sendIframeCommand('addEventListener', ['onError']);
      } catch (err) {
        console.warn('registerIframeListeners error:', err);
      }
    }
  }, [sendIframeCommand]);

  const seekTo = useCallback(
    (seconds: number) => {
      const clamped = Math.max(0, Math.min(duration || 300, seconds));
      setCurrentTime(clamped);
      sendIframeCommand('seekTo', [clamped, true]);
    },
    [duration, sendIframeCommand]
  );

  const setVolume = useCallback(
    (newVol: number) => {
      const clamped = Math.max(0, Math.min(100, Math.round(newVol)));
      setVolumeState(clamped);
      if (clamped === 0) {
        setIsMuted(true);
        sendIframeCommand('mute');
      } else {
        setIsMuted(false);
        sendIframeCommand('unMute');
        sendIframeCommand('setVolume', [clamped]);
      }
    },
    [sendIframeCommand]
  );

  const toggleMute = useCallback(() => {
    if (isMuted) {
      setIsMuted(false);
      sendIframeCommand('unMute');
      sendIframeCommand('setVolume', [volume || 80]);
    } else {
      setIsMuted(true);
      sendIframeCommand('mute');
    }
  }, [isMuted, volume, sendIframeCommand]);

  const toggleQueue = useCallback(() => {
    setIsQueueOpen((prev) => !prev);
  }, []);

  // Auto advance guard, consecutive error limiter & references
  const isAdvancingRef = useRef<boolean>(false);
  const consecutiveErrorsRef = useRef<number>(0);
  const nextTrackRef = useRef<(isErrorSkip?: boolean) => Promise<void>>(async () => {});

  const handleTrackEnded = useCallback((isError: boolean = false) => {
    if (isAdvancingRef.current) return;
    isAdvancingRef.current = true;

    console.log(`[Aficio Player] Track advance triggered (isError: ${isError}) ⏭️`);
    nextTrackRef.current(isError).finally(() => {
      setTimeout(() => {
        isAdvancingRef.current = false;
      }, 1500);
    });
  }, []);

  // Timer interval for smooth progress bar update & fallback watchdog
  useEffect(() => {
    let timer: any = null;
    if (isPlaying) {
      timer = setInterval(() => {
        setCurrentTime((prev) => {
          // Watchdog: If track reached duration and hasn't advanced, trigger nextTrack
          if (duration > 0 && prev >= duration) {
            handleTrackEnded();
            return duration;
          }
          return prev + 1;
        });
      }, 1000);
    }
    return () => {
      if (timer) clearInterval(timer);
    };
  }, [isPlaying, duration, handleTrackEnded]);

  const playTrack = useCallback(
    async (track: Track, album?: Album | null, tracklist?: Track[]) => {
      try {
        setError(null);
        setIsLoading(true);
        setCurrentTime(0);
        setCurrentTrackId(track.id);
        setIsMiniPlayerOpen(true);

        if (track.lengthMilliseconds) {
          setDuration(Math.floor(track.lengthMilliseconds / 1000));
        } else {
          setDuration(0);
        }

        if (album) {
          setCurrentAlbum(album);
        }

        // Establish the active playback queue
        const activeQueue =
          tracklist && tracklist.length > 0
            ? tracklist
            : queueRef.current.length > 0
            ? queueRef.current
            : [track];

        setQueue(activeQueue);

        const foundIdx = activeQueue.findIndex(
          (t) => String(t.id) === String(track.id)
        );
        const resolvedIdx = foundIdx >= 0 ? foundIdx : 0;
        setCurrentIndex(resolvedIdx);

        // Fetch streaming video source
        const playback = await api.catalog.getTrackPlayback(track.id);

        if (!playback.youtubeVideoId) {
          setError(`Playback stream unavailable for "${track.title}" (not in local catalog).`);
          setIsLoading(false);
          setIsPlaying(false);
          return;
        }

        if (playback.lengthMilliseconds) {
          setDuration(Math.floor(playback.lengthMilliseconds / 1000));
        }

        // If the queue was only 1 track but we know the album ID, populate the rest of the album queue
        if (activeQueue.length <= 1 && playback.albumId) {
          api.catalog
            .getTracks(playback.albumId)
            .then((allTracks) => {
              if (allTracks && allTracks.length > 1) {
                setQueue(allTracks);
                const newIdx = allTracks.findIndex(
                  (t) => String(t.id) === String(track.id)
                );
                if (newIdx >= 0) setCurrentIndex(newIdx);
              }
            })
            .catch(() => {});
        }

        console.log(
          `%c[Aficio Player] Track Loaded 🎵%c "${playback.artistName} - ${playback.title}" [${playback.youtubeVideoId}] (Source: ${playback.source})`,
          'color: #a78bfa; font-weight: bold; font-size: 13px;',
          'color: #9cdcfe; font-size: 13px;'
        );

        // If iframe is already mounted in the DOM, seamlessly switch video via YouTube API without reloading iframe
        if (iframeRef.current && currentPlaybackRef.current?.youtubeVideoId) {
          sendIframeCommand('loadVideoById', [playback.youtubeVideoId]);
          sendIframeCommand('playVideo');
        }

        setCurrentPlayback(playback);
        setIsLoading(false);
        setIsPlaying(true);

        // Check if guest listener has played a couple tracks to surface community feature highlights
        if (!isAuthenticated) {
          try {
            const dismissed = sessionStorage.getItem('aficio_guest_prompt_dismissed') === 'true';
            if (!dismissed) {
              const count = parseInt(sessionStorage.getItem('aficio_guest_play_count') || '0', 10) + 1;
              sessionStorage.setItem('aficio_guest_play_count', String(count));
              if (count >= 2) {
                setIsGuestPromptOpen(true);
              }
            }
          } catch {
            // ignore
          }
        }
      } catch (err: any) {
        console.error('Failed to load track playback:', err);
        setError(err?.message || 'Could not load track');
        setIsLoading(false);
        setIsPlaying(false);
      }
    },
    [sendIframeCommand, isAuthenticated]
  );

  const togglePlay = useCallback(() => {
    if (isPlayingRef.current) {
      sendIframeCommand('pauseVideo');
      setIsPlaying(false);
    } else {
      sendIframeCommand('playVideo');
      setIsPlaying(true);
    }
  }, [sendIframeCommand]);

  const nextTrack = useCallback(async (isErrorSkip: boolean = false) => {
    const q = queueRef.current;
    if (!q || q.length === 0) return;

    let idx = currentIndexRef.current;
    if (idx < 0) {
      idx = q.findIndex(
        (t) => String(t.id) === String(currentTrackIdRef.current)
      );
    }
    const safeIdx = idx >= 0 ? idx : 0;

    // Prevent endless wrap-around loop when auto-skipping failed/restricted tracks
    if (isErrorSkip && safeIdx + 1 >= q.length) {
      console.log('[Aficio Player] Reached end of album during auto-skip, halting playback.');
      setIsPlaying(false);
      isAdvancingRef.current = false;
      setError('Reached the end of the album. Unplayable tracks were skipped.');
      return;
    }

    const nextIdx = (safeIdx + 1) % q.length;
    const nextItem = q[nextIdx];

    if (nextItem) {
      await playTrack(nextItem, currentAlbumRef.current, q);
    }
  }, [playTrack]);

  const prevTrack = useCallback(async () => {
    const q = queueRef.current;
    if (!q || q.length === 0) return;

    let idx = currentIndexRef.current;
    if (idx < 0) {
      idx = q.findIndex(
        (t) => String(t.id) === String(currentTrackIdRef.current)
      );
    }
    const safeIdx = idx >= 0 ? idx : 0;
    const prevIdx = (safeIdx - 1 + q.length) % q.length;
    const prevItem = q[prevIdx];

    if (prevItem) {
      await playTrack(prevItem, currentAlbumRef.current, q);
    }
  }, [playTrack]);

  const closePlayer = useCallback(() => {
    sendIframeCommand('stopVideo');
    sendIframeCommand('pauseVideo');
    isAdvancingRef.current = false;
    setIsMiniPlayerOpen(false);
    setIsPlaying(false);
    setCurrentPlayback(null);
    setCurrentTrackId(null);
    setCurrentIndex(-1);
    setCurrentTime(0);
    setDuration(0);
    setIsQueueOpen(false);
  }, [sendIframeCommand]);

  const toggleExpanded = useCallback(() => {
    setIsExpanded((prev) => !prev);
  }, []);

  // Update ref to latest nextTrack function
  useEffect(() => {
    nextTrackRef.current = nextTrack;
  }, [nextTrack]);

  // Keep YouTube Iframe API connection alive while playing
  useEffect(() => {
    if (!isPlaying) return;
    const interval = setInterval(() => {
      registerIframeListeners();
    }, 4000);
    return () => clearInterval(interval);
  }, [isPlaying, registerIframeListeners]);

  useEffect(() => {
    const handleMessage = (event: MessageEvent) => {
      try {
        let data: any = event.data;
        if (typeof data === 'string') {
          try {
            data = JSON.parse(data);
          } catch {
            return;
          }
        }
        if (!data || typeof data !== 'object') return;

        // When YouTube player is ready or announces initial delivery, register listeners
        if (data.event === 'onReady' || data.event === 'initialDelivery') {
          registerIframeListeners();
        }

        // Support standard onStateChange as well as infoDelivery/initialDelivery playerState updates
        let detectedState: number | undefined = undefined;
        if (typeof data.info === 'number') {
          detectedState = data.info;
        } else if (data.info && typeof data.info.playerState === 'number') {
          detectedState = data.info.playerState;
        } else if (typeof data.data === 'number' && (data.event === 'onStateChange' || data.event === 'stateChange')) {
          detectedState = data.data;
        } else if (typeof data.playerState === 'number') {
          detectedState = data.playerState;
        }

        if (detectedState === 1) {
          // 1 = playing -> successfully playing, reset consecutive error counter
          setIsPlaying(true);
          setIsLoading(false);
          consecutiveErrorsRef.current = 0;
        } else if (detectedState === 2) {
          // 2 = paused
          setIsPlaying(false);
        } else if (detectedState === 0) {
          // 0 = ended -> trigger autoplay to next track
          console.log('[Aficio Player] Track ended event received from YouTube (state 0). Auto-playing next track...');
          setIsPlaying(false);
          handleTrackEnded(false);
        }

        if (data.event === 'onError') {
          console.warn('[Aficio Player] YouTube Player error:', data.info);
          setIsPlaying(false);

          const activeTrackId = currentTrackIdRef.current;
          const handleTrackSkipFallback = () => {
            consecutiveErrorsRef.current += 1;
            const q = queueRef.current;
            const maxConsecutive = q && q.length > 0 ? Math.min(q.length, 3) : 3;

            // Break the infinite loop if multiple consecutive tracks are restricted/unplayable
            if (consecutiveErrorsRef.current >= maxConsecutive) {
              console.warn(`[Aficio Player] Halting playback loop: ${consecutiveErrorsRef.current} consecutive tracks restricted from embedding.`);
              setError('Playback stopped: Multiple tracks on this album are restricted by YouTube or the copyright owner from embedded playback.');
              isAdvancingRef.current = false;
              return;
            }

            setError('This track is restricted by YouTube or the copyright owner. Skipping...');
            // Auto-skip unplayable track after brief pause so album playback keeps flowing
            setTimeout(() => {
              handleTrackEnded(true);
            }, 1500);
          };

          if (activeTrackId) {
            setError('Track restricted by rights owner. Searching for an unofficial album release...');
            api.catalog.reportTrackRestricted(activeTrackId)
              .then((newPlayback) => {
                if (newPlayback && newPlayback.youtubeVideoId) {
                  console.log(`[Aficio Player] Auto-resolved unofficial video ID [${newPlayback.youtubeVideoId}] for track ${activeTrackId}!`);
                  currentPlaybackRef.current = newPlayback;
                  sendIframeCommand('loadVideoById', [newPlayback.youtubeVideoId]);
                  sendIframeCommand('playVideo');
                  setIsPlaying(true);
                  setError(null);
                  consecutiveErrorsRef.current = 0;
                } else {
                  handleTrackSkipFallback();
                }
              })
              .catch((err) => {
                console.warn('Failed to resolve unofficial track:', err);
                handleTrackSkipFallback();
              });
          } else {
            handleTrackSkipFallback();
          }
        } else if (data.info) {
          if (typeof data.info.currentTime === 'number') {
            setCurrentTime(Math.floor(data.info.currentTime));
          }
          if (typeof data.info.duration === 'number' && data.info.duration > 0) {
            setDuration(Math.floor(data.info.duration));
          }
        }

        // Check if YouTube iframe exposed the resolved video ID (from infoDelivery, initialDelivery, or state updates)
        const videoData = data.info?.videoData;
        const resolvedVideoId = videoData?.video_id || data.info?.videoId;
        if (
          resolvedVideoId &&
          typeof resolvedVideoId === 'string' &&
          resolvedVideoId.length === 11 &&
          currentPlaybackRef.current &&
          !currentPlaybackRef.current.youtubeVideoId
        ) {
          const activeTrackId = currentPlaybackRef.current.trackId;
          const activeTitle = currentPlaybackRef.current.title;
          currentPlaybackRef.current.youtubeVideoId = resolvedVideoId;
          setCurrentPlayback((prev) =>
            prev && String(prev.trackId) === String(activeTrackId)
              ? { ...prev, youtubeVideoId: resolvedVideoId }
              : prev
          );
          console.log(
            `%c[Aficio Player] Captured Video ID from YouTube 🎯%c "${resolvedVideoId}" for "${activeTitle}" -> Saved to DB Cache!`,
            'color: #4ade80; font-weight: bold; font-size: 13px;',
            'color: #e2e8f0; font-size: 13px;'
          );
          api.catalog.updateTrackYoutubeId(activeTrackId, resolvedVideoId).catch((err) => {
            console.warn('Failed to crowdsource cache youtube ID:', err);
          });
        }
      } catch {
        // Non-JSON or third party iframe message
      }
    };

    window.addEventListener('message', handleMessage);
    return () => window.removeEventListener('message', handleMessage);
  }, [registerIframeListeners, handleTrackEnded]);

  return (
    <PlayerContext.Provider
      value={{
        currentPlayback,
        currentTrackId,
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
        currentIndex,
        error,
        iframeRef,
        playTrack,
        togglePlay,
        seekTo,
        setVolume,
        toggleMute,
        toggleQueue,
        setIsQueueOpen,
        nextTrack,
        prevTrack,
        closePlayer,
        toggleExpanded,
        setIsPlaying,
        isGuestPromptOpen,
        closeGuestPrompt,
      }}
    >
      {children}
    </PlayerContext.Provider>
  );
};

export const usePlayer = () => {
  const context = useContext(PlayerContext);
  if (!context) {
    throw new Error('usePlayer must be used within a PlayerProvider');
  }
  return context;
};
