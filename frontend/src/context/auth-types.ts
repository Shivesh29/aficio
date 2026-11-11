import { createContext } from 'react';

export interface AuthUser {
  id: number;
  username: string;
  email?: string;
  displayName?: string;
  bio?: string;
  avatarUrl?: string;
  isPrivate?: boolean;
  twoFactorEnabled?: boolean;
  listenerCode?: string;
}

export interface AuthContextType {
  user: AuthUser | null;
  token: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (usernameOrEmail: string, password: string) => Promise<{ requires2fa: boolean; tempToken?: string }>;
  loginWithGoogle: (idToken: string) => Promise<{ requires2fa: boolean; tempToken?: string } | void>;
  finalizeLogin: (username: string, token: string) => void;
  register: (username: string, email: string, password: string) => Promise<{ id: number; username: string; email: string; verificationUrl?: string }>;
  logout: () => void;
  refreshUser: (newToken?: string) => Promise<void>;
  isAuthModalOpen: boolean;
  authModalTab: 'login' | 'register';
  openAuthModal: (tab?: 'login' | 'register') => void;
  closeAuthModal: () => void;
  unreadNotificationCount: number;
  notifications: Notification[];
  markNotificationRead: (id: number) => Promise<void>;
  loadNotifications: () => Promise<void>;
}

export interface Notification {
  id: number;
  type: string;
  message: string;
  data?: any;
  read: boolean;
  createdAt: string;
}

export const AuthContext = createContext<AuthContextType | undefined>(undefined);
