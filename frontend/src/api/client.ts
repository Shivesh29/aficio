// API Client for Aficio Backend

const TOKEN_KEY = 'aficio_jwt_token';
export const YOUTUBE_API_KEY_STORAGE = 'aficio_custom_youtube_api_key';

export function getStoredToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setStoredToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token);
}

export function clearStoredToken() {
  localStorage.removeItem(TOKEN_KEY);
}

export function getCustomYouTubeApiKey(): string | null {
  if (typeof window === 'undefined') return null;
  return localStorage.getItem(YOUTUBE_API_KEY_STORAGE);
}

export function setCustomYouTubeApiKey(key: string) {
  if (typeof window === 'undefined') return;
  if (key && key.trim()) {
    localStorage.setItem(YOUTUBE_API_KEY_STORAGE, key.trim());
  } else {
    localStorage.removeItem(YOUTUBE_API_KEY_STORAGE);
  }
}

export function clearCustomYouTubeApiKey() {
  if (typeof window === 'undefined') return;
  localStorage.removeItem(YOUTUBE_API_KEY_STORAGE);
}

async function request<T>(url: string, options: RequestInit = {}): Promise<T> {
  const token = getStoredToken();
  const customYtKey = getCustomYouTubeApiKey();
  const headers = new Headers(options.headers || {});

  if (!headers.has('Content-Type') && !(options.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json');
  }

  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  if (customYtKey) {
    headers.set('X-YouTube-API-Key', customYtKey);
  }

  const response = await fetch(url, { ...options, headers });
  const text = await response.text();

  if (!response.ok) {
    if (response.status === 401) {
      clearStoredToken();
      if (typeof window !== 'undefined') {
        window.dispatchEvent(new CustomEvent('aficio:unauthorized'));
      }
    }
    let errorMessage = `HTTP error ${response.status}`;
    try {
      const errorData = JSON.parse(text);
      if (errorData.message) {
        errorMessage = errorData.message;
        if (errorData.fieldErrors && Object.keys(errorData.fieldErrors).length > 0) {
          const details = Object.entries(errorData.fieldErrors)
            .map(([_, msg]) => `${msg}`)
            .join(', ');
          errorMessage += `: ${details}`;
        }
      }
    } catch {
      if (text) {
        const cleaned = text
          .replace(/<!--[\s\S]*?-->/g, '')
          .replace(/<[^>]+>/g, ' ')
          .replace(/\s+/g, ' ')
          .trim();
        errorMessage = cleaned && cleaned.length < 150 ? cleaned : `Request failed (HTTP ${response.status})`;
      }
    }
    throw new Error(errorMessage);
  }

  return text ? JSON.parse(text) : ({} as T);
}

// Data Types
export interface Artist {
  id: number;
  name: string;
  musicBrainzId: string | null;
}

export interface Album {
  id: number;
  title: string;
  musicBrainzId: string;
  coverArtUrl: string | null;
  artist: Artist;
}

export interface Track {
  id: number;
  title: string;
  position: number;
  lengthMilliseconds: number | null;
  musicBrainzId: string;
}

export interface TrackPlaybackResponse {
  trackId: number;
  title: string;
  position: number;
  lengthMilliseconds: number | null;
  artistName: string;
  albumTitle: string;
  albumId: number | null;
  coverArtUrl: string | null;
  youtubeVideoId: string | null;
  searchQuery?: string | null;
  source: string | null;
  newlyDiscovered?: boolean;
  dailyDiscoveriesUsed?: number;
  dailyDiscoveriesLimit?: number;
  remainingDiscoveries?: number;
}

export interface UserQuotaResponse {
  clientId: string;
  isOwner: boolean;
  hasCustomKey: boolean;
  dailyLimit: number;
  usedToday: number;
  remaining: number;
  isExhausted: boolean;
  unlimited: boolean;
}

export interface UserApiKeyStatusResponse {
  hasCloudKey: boolean;
  storagePreference: 'CLOUD' | 'LOCAL' | 'NONE';
  maskedKey: string | null;
}

export interface User {
  id: number;
  username: string;
  email?: string;
  displayName?: string;
  bio?: string;
  avatarUrl?: string;
  isPrivate?: boolean;
}

export interface UserProfile {
  id: number;
  username: string;
  displayName?: string;
  bio?: string;
  avatarUrl?: string;
  isPrivate: boolean;
  followersCount: number;
  followingCount: number;
  reviewsCount: number;
  listsCount: number;
}

export interface UserSettings {
  username: string;
  email: string;
  displayName?: string;
  bio?: string;
  avatarUrl?: string;
  isPrivate: boolean;
  twoFactorEnabled: boolean;
}

export interface UpdateSettingsRequest {
  displayName?: string;
  bio?: string;
  avatarUrl?: string;
  isPrivate?: boolean;
}

export interface Review {
  id: number;
  user: User;
  album: Album;
  rating?: number | null;
  content?: string | null;
  createdAt: string;
}

export interface ReviewComment {
  id: number;
  user: User;
  content: string;
  createdAt: string;
}

export interface MusicList {
  id: number;
  title: string;
  description: string;
  public?: boolean;
  isPublic?: boolean;
  createdAt: string;
  owner: User;
}

export function isListPublic(list: Partial<MusicList> | null | undefined): boolean {
  if (!list) return false;
  if (list.isPublic !== undefined) return Boolean(list.isPublic);
  if (list.public !== undefined) return Boolean(list.public);
  return false;
}

export interface MusicListItem {
  id: number;
  list: MusicList;
  album: Album;
  position: number;
}

export interface DirectMessage {
  id: number;
  sender: User;
  recipient: User;
  content: string;
  sentAt: string;
  isRead?: boolean;
  readAt?: string | null;
}

export interface ConversationSummary {
  userId: number;
  username: string;
  lastMessage: string;
  lastSentAt: string;
  fromMe: boolean;
  unreadCount?: number;
}

export interface DmRecipientStatus {
  userExists: boolean;
  username: string;
  userId?: number | null;
  canMessage: boolean;
  followStatus: 'ACCEPTED' | 'PENDING' | 'NONE' | 'SELF';
  isFollowedBy: boolean;
  reason?: string | null;
}

export interface Notification {
  id: number;
  type: string;
  message: string;
  data?: any;
  read: boolean;
  createdAt: string;
}

export interface MusicBrainzRecording {
  id: string;
  title: string;
  length?: number;
  score?: number;
  'artist-credit'?: Array<{ name: string; artist?: { id: string; name: string } }>;
  releases?: MusicBrainzReleaseResult[];
}

export interface MusicBrainzReleaseGroup {
  id: string;
  title?: string;
  'primary-type'?: string;
  'secondary-types'?: string[];
  'first-release-date'?: string;
  disambiguation?: string;
}

export interface MusicBrainzReleaseResult {
  id: string;
  title: string;
  date?: string;
  country?: string;
  status?: string;
  type?: string;
  "artist-credit"?: Array<{ name: string; artist?: { id: string; name: string } }>;
  "release-group"?: MusicBrainzReleaseGroup;
  'track-count'?: number;
}

export interface FeedRelease {
  id: number | null;
  title: string;
  musicBrainzId: string;
  coverArtUrl: string | null;
  artistName: string;
  artistMusicBrainzId: string | null;
  date?: string | null;
  trackCount?: number | null;
  inCatalog: boolean;
  reason: string;
}

export interface MusicBrainzSearchResponse {
  releases?: MusicBrainzReleaseResult[];
}

export interface MusicBrainzArtist {
  id: string;
  name: string;
  country?: string;
  disambiguation?: string;
  type?: string;
}

export const api = {
  // Auth
  auth: {
    login: (usernameOrEmail: string, password: string) =>
      request<{ token: string; requires2fa: boolean }>('/users/login', {
        method: 'POST',
        body: JSON.stringify({ username: usernameOrEmail, usernameOrEmail, password }),
      }),
    googleLogin: (idToken: string) =>
      request<{ token: string; requires2fa: boolean }>('/users/oauth/google', {
        method: 'POST',
        body: JSON.stringify({ idToken }),
      }),
    register: (username: string, email: string, password: string) =>
      request<{ id: number; username: string; email: string; verificationUrl?: string }>('/users/register', {
        method: 'POST',
        body: JSON.stringify({ username, email, password }),
      }),
    verifyEmail: (token: string) =>
      request<{ success: boolean; message: string }>(`/users/verify-email?token=${token}`),
    resendVerification: (email: string) =>
      request<{ success: boolean; message: string }>('/users/resend-verification', {
        method: 'POST',
        body: JSON.stringify({ email }),
      }),
    me: () => request<{ id: number; username: string; email: string; displayName?: string; bio?: string; avatarUrl?: string; isPrivate: boolean; twoFactorEnabled: boolean }>('/users/me'),
    verify2fa: (code: string, tempToken: string) =>
      request<{ token: string; requires2fa: boolean }>('/users/2fa/verify', {
        method: 'POST',
        headers: { 'Authorization': `Bearer ${tempToken}` },
        body: JSON.stringify({ code }),
      }),
    setup2fa: () =>
      request<{ secret: string; qrCodeUri: string }>('/users/2fa/setup', { method: 'POST' }),
    enable2fa: (code: string) =>
      request<void>('/users/2fa/enable', {
        method: 'POST',
        body: JSON.stringify({ code }),
      }),
    disable2fa: (code: string) =>
      request<void>('/users/2fa/disable', {
        method: 'POST',
        body: JSON.stringify({ code }),
      }),
    getSettings: () => request<UserSettings>('/users/settings'),
    updateSettings: (data: UpdateSettingsRequest) =>
      request<UserSettings>('/users/settings', {
        method: 'PUT',
        body: JSON.stringify(data),
      }),
    updateUsername: (username: string) =>
      request<{ username: string; token: string; message: string }>('/users/settings/username', {
        method: 'PUT',
        body: JSON.stringify({ username }),
      }),
    changePassword: (currentPassword: string, newPassword: string) =>
      request<{ success: boolean; message: string }>('/users/settings/password', {
        method: 'PUT',
        body: JSON.stringify({ currentPassword, newPassword }),
      }),
    deleteAccount: (password: string) =>
      request<void>('/users/account', {
        method: 'DELETE',
        body: JSON.stringify({ password }),
      }),
    getApiKeyStatus: () => request<UserApiKeyStatusResponse>('/users/api-key'),
    saveApiKey: (apiKey: string, storagePreference: 'CLOUD' | 'LOCAL' | 'NONE') =>
      request<UserApiKeyStatusResponse>('/users/api-key', {
        method: 'PUT',
        body: JSON.stringify({ apiKey, storagePreference }),
      }),
    deleteApiKey: () =>
      request<UserApiKeyStatusResponse>('/users/api-key', {
        method: 'DELETE',
      }),
  },

  // Catalog
  catalog: {
    getAlbums: (syncedOnly?: boolean, limit: number = 60) =>
      request<Album[]>(syncedOnly ? `/albums?syncedOnly=true&limit=${limit}` : `/albums?limit=${limit}`),
    getMyAlbums: (limit: number = 60, username?: string) =>
      request<Album[]>(`/albums/my?limit=${limit}${username ? `&username=${encodeURIComponent(username)}` : ''}`),
    getFeed: (limit?: number) => request<FeedRelease[]>(limit ? `/albums/feed?limit=${limit}` : '/albums/feed'),
    getAlbumById: (id: number | string) => request<Album>(`/albums/${id}`),
    getTracks: (albumId: number | string) => request<Track[]>(`/tracks?albumId=${albumId}`),
    getTrackPlayback: (trackId: number | string) => {
      const customKey = getCustomYouTubeApiKey();
      const headers: Record<string, string> = {};
      if (customKey) {
        headers['X-YouTube-API-Key'] = customKey;
      }
      const queryParam = customKey ? `?apiKey=${encodeURIComponent(customKey)}` : '';
      return request<TrackPlaybackResponse>(`/tracks/${trackId}/playback${queryParam}`, { headers });
    },
    getUserQuota: () => {
      const customKey = getCustomYouTubeApiKey();
      const headers: Record<string, string> = {};
      if (customKey) {
        headers['X-YouTube-API-Key'] = customKey;
      }
      const queryParam = customKey ? `?apiKey=${encodeURIComponent(customKey)}` : '';
      return request<UserQuotaResponse>(`/catalog/quota${queryParam}`, { headers });
    },
    updateTrackYoutubeId: (trackId: number | string, youtubeVideoId: string) =>
      request<void>(`/tracks/${trackId}/youtube-video-id`, {
        method: 'PUT',
        body: JSON.stringify({ youtubeVideoId }),
      }),
    syncAlbum: (musicBrainzId: string) =>
      request<Album>(`/albums/${musicBrainzId}/sync`, { method: 'POST' }),
    searchMusicBrainz: (query: string, type: 'album' | 'artist' | 'track' | 'artist-releases') =>
      request<any>(`/musicbrainz/search?query=${encodeURIComponent(query)}&type=${type}`),
    getArtistReleases: (artistMbid: string) =>
      request<{ count: number; releases?: MusicBrainzReleaseResult[] }>(
        `/musicbrainz/artists/${artistMbid}/releases`
      ),
    getArtistDetails: (artistMbid: string) =>
      request<MusicBrainzArtist>(`/musicbrainz/artists/${artistMbid}`),
  },

  // Reviews
  reviews: {
    getAlbumReviews: (albumId: number | string) =>
      request<Review[]>(`/api/reviews/album/${albumId}`),
    getUserReviews: (userId: number | string) =>
      request<Review[]>(`/api/reviews/user/${userId}`),
    createReview: (albumId: number, rating?: number, content?: string) =>
      request<Review>('/api/reviews', {
        method: 'POST',
        body: JSON.stringify({ albumId, rating, content }),
      }),
    rateAlbum: (albumId: number, rating: number) =>
      request<Review>('/api/reviews/rate', {
        method: 'POST',
        body: JSON.stringify({ albumId, rating }),
      }),
    removeRating: (albumId: number) =>
      request<void>(`/api/reviews/rate/album/${albumId}`, { method: 'DELETE' }),
    deleteReview: (reviewId: number) =>
      request<void>(`/api/reviews/${reviewId}`, { method: 'DELETE' }),
    getLikes: (reviewId: number) => request<number>(`/api/reviews/${reviewId}/likes`),
    isLiked: (reviewId: number) => request<boolean>(`/api/reviews/${reviewId}/is-liked`),
    likeReview: (reviewId: number) =>
      request<void>(`/api/reviews/${reviewId}/like`, { method: 'POST' }),
    unlikeReview: (reviewId: number) =>
      request<void>(`/api/reviews/${reviewId}/like`, { method: 'DELETE' }),
    getComments: (reviewId: number) =>
      request<ReviewComment[]>(`/api/reviews/${reviewId}/comments`),
    addComment: (reviewId: number, content: string) =>
      request<ReviewComment>(`/api/reviews/${reviewId}/comments`, {
        method: 'POST',
        body: JSON.stringify({ content }),
      }),
  },

  // Lists / Collections
  lists: {
    getPublicLists: () => request<MusicList[]>('/api/lists'),
    getMyLists: () => request<MusicList[]>('/api/lists/mine'),
    getUserLists: (username: string) => request<MusicList[]>(`/api/lists/user/${username}`),
    getListById: (listId: number | string) => request<MusicList>(`/api/lists/${listId}`),
    getListAlbums: (listId: number | string) =>
      request<MusicListItem[]>(`/api/lists/${listId}/albums`),
    createList: (title: string, description: string, isPublic: boolean) =>
      request<MusicList>('/api/lists', {
        method: 'POST',
        body: JSON.stringify({ title, description, isPublic }),
      }),
    addAlbumToList: (listId: number, albumId: number) =>
      request<MusicListItem>(`/api/lists/${listId}/albums/${albumId}`, { method: 'POST' }),
    removeAlbumFromList: (listId: number, albumId: number) =>
      request<void>(`/api/lists/${listId}/albums/${albumId}`, { method: 'DELETE' }),
    deleteList: (listId: number | string) =>
      request<void>(`/api/lists/${listId}`, { method: 'DELETE' }),
    updateList: (listId: number | string, title: string, description: string, isPublic: boolean) =>
      request<MusicList>(`/api/lists/${listId}`, {
        method: 'PUT',
        body: JSON.stringify({ title, description, isPublic }),
      }),
  },

  // Social & Profiles
  social: {
    getUserProfile: (username: string) => request<UserProfile>(`/users/${username}`),
    getFollowers: (username: string) => request<any[]>(`/api/users/${username}/followers`),
    getFollowing: (username: string) => request<any[]>(`/api/users/${username}/following`),
    isFollowing: (username: string) => request<boolean>(`/api/users/${username}/is-following`),
    getFollowStatus: (username: string) =>
      request<{ status: 'NONE' | 'PENDING' | 'FOLLOWING'; following: boolean; pending: boolean }>(
        `/api/users/${username}/follow-status`
      ),
    getPendingFollowRequests: () => request<any[]>('/api/users/follow-requests/pending'),
    acceptFollowRequest: (requestId: number) =>
      request<any>(`/api/users/follow-requests/${requestId}/accept`, { method: 'POST' }),
    declineFollowRequest: (requestId: number) =>
      request<void>(`/api/users/follow-requests/${requestId}/decline`, { method: 'POST' }),
    follow: (username: string) =>
      request<{ id: number; status: string }>(`/api/users/${username}/follow`, { method: 'POST' }),
    unfollow: (username: string) =>
      request<void>(`/api/users/${username}/follow`, { method: 'DELETE' }),
    getFeed: (page: number, size: number) =>
      request<any[]>(`/api/feed?page=${page}&size=${size}`),
  },

  // Messages
  messages: {
    getConversations: () => request<ConversationSummary[]>('/api/messages/conversations'),
    getConversation: (username: string) =>
      request<DirectMessage[]>(`/api/messages/${username}`),
    checkRecipient: (username: string) =>
      request<DmRecipientStatus>(`/api/messages/${username}/check`),
    sendMessage: (username: string, content: string) =>
      request<DirectMessage>(`/api/messages/${username}`, {
        method: 'POST',
        body: JSON.stringify({ content }),
      }),
    markAsRead: (username: string) =>
      request<void>(`/api/messages/${username}/read`, { method: 'POST' }),
    getUnreadCount: () =>
      request<{ unreadCount: number }>('/api/messages/unread-count'),
  },

  notifications: {
    getNotifications: () => request<Notification[]>('/api/notifications'),
    markAsRead: (id: number) =>
      request<void>(`/api/notifications/${id}/read`, { method: 'POST' }),
  },
};
