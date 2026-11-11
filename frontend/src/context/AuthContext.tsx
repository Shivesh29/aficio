import React, { useState, useEffect, useRef, useCallback } from 'react';
import { api, getStoredToken, setStoredToken, clearStoredToken, type Notification } from '../api/client';
import { AuthContext, type AuthUser } from './auth-types';

// Re-export useAuth for backwards compatibility
export { useAuth } from './useAuth';

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [token, setToken] = useState<string | null>(getStoredToken());
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [isAuthModalOpen, setIsAuthModalOpen] = useState(false);
  const [authModalTab, setAuthModalTab] = useState<'login' | 'register'>('login');
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [unreadNotificationCount, setUnreadNotificationCount] = useState(0);
  const wsActiveRef = useRef(false);

  const loadNotifications = useCallback(async () => {
    if (!token) return;
    try {
      const data = await api.notifications.getNotifications();
      setNotifications(data);
      const unread = data.filter((n) => !n.read).length;
      setUnreadNotificationCount(unread);
    } catch {
      // ignore
    }
  }, [token]);

  const markNotificationRead = async (id: number) => {
    await api.notifications.markAsRead(id);
    setNotifications((prev) => prev.map((n) => (n.id === id ? { ...n, read: true } : n)));
    setUnreadNotificationCount((prev) => Math.max(0, prev - 1));
  };

  useEffect(() => {
    async function restoreSession() {
      const stored = getStoredToken();
      if (stored) {
        try {
          const me = await api.auth.me();
          setUser({
            id: me.id,
            username: me.username,
            email: me.email,
            displayName: me.displayName,
            bio: me.bio,
            avatarUrl: me.avatarUrl,
            isPrivate: me.isPrivate,
            twoFactorEnabled: me.twoFactorEnabled,
            listenerCode: me.listenerCode,
          });
        } catch {
          clearStoredToken();
          setToken(null);
          setUser(null);
        }
      }
      setIsLoading(false);
    }
    void restoreSession();
  }, []);

  useEffect(() => {
    const handleUnauthorized = () => {
      setToken(null);
      setUser(null);
      setNotifications([]);
      setUnreadNotificationCount(0);
    };
    window.addEventListener('aficio:unauthorized', handleUnauthorized);
    return () => window.removeEventListener('aficio:unauthorized', handleUnauthorized);
  }, []);

  useEffect(() => {
    if (user) {
      void loadNotifications();
    } else {
      setNotifications([]);
      setUnreadNotificationCount(0);
    }
  }, [user, loadNotifications]);

  // WebSocket connection for notifications
  useEffect(() => {
    if (!user || !token) return;

    const accessToken = token;
    let stompClient: any = null;
    let active = true;

    async function connectWs() {
      try {
        const SockJS = (await import('sockjs-client')).default;
        const Stomp = (await import('stompjs')).default;

        const socket = new SockJS('/notification-ws?access_token=' + encodeURIComponent(accessToken));
        stompClient = Stomp.over(socket);
        stompClient.connect({}, () => {
          if (!active || !user) return;
          wsActiveRef.current = true;
          const destination = `/user/queue/notifications`;
          stompClient.subscribe(destination, (message: any) => {
            if (!active) return;
            const incoming: Notification = JSON.parse(message.body);
            if (!incoming.createdAt) {
              incoming.createdAt = new Date().toISOString();
            }
            setNotifications((prev) => [incoming, ...prev]);
            setUnreadNotificationCount((prev) => prev + 1);
          });
        }, () => {
          if (!active) return;
          wsActiveRef.current = false;
        });
      } catch {
        wsActiveRef.current = false;
      }
    }

    void connectWs();

    return () => {
      active = false;
      wsActiveRef.current = false;
      if (stompClient) {
        try { stompClient.disconnect(); } catch {}
      }
    };
  }, [user, token]);

  const login = async (usernameOrEmail: string, password: string) => {
    const res = await api.auth.login(usernameOrEmail, password);
    if (res.requires2fa) {
      return { requires2fa: true, tempToken: res.token };
    }
    finalizeLogin(usernameOrEmail, res.token);
    return { requires2fa: false };
  };

  const loginWithGoogle = async (idToken: string) => {
    const res = await api.auth.googleLogin(idToken);
    if (res.requires2fa) {
      return { requires2fa: true, tempToken: res.token };
    }
    finalizeLogin('', res.token);
    return { requires2fa: false };
  };

  const finalizeLogin = async (username: string, newToken: string) => {
    setStoredToken(newToken);
    setToken(newToken);
    try {
      const me = await api.auth.me();
      setUser({
        id: me.id,
        username: me.username,
        email: me.email,
        displayName: me.displayName,
        bio: me.bio,
        avatarUrl: me.avatarUrl,
        isPrivate: me.isPrivate,
        twoFactorEnabled: me.twoFactorEnabled,
        listenerCode: me.listenerCode,
      });
    } catch {
      setUser({ id: 0, username });
    }
    setIsAuthModalOpen(false);
  };

  const refreshUser = async (newToken?: string) => {
    if (newToken) {
      setStoredToken(newToken);
      setToken(newToken);
    }
    try {
      const me = await api.auth.me();
      setUser({
        id: me.id,
        username: me.username,
        email: me.email,
        displayName: me.displayName,
        bio: me.bio,
        avatarUrl: me.avatarUrl,
        isPrivate: me.isPrivate,
        twoFactorEnabled: me.twoFactorEnabled,
        listenerCode: me.listenerCode,
      });
    } catch {
      // ignore
    }
  };

  const register = async (username: string, email: string, password: string) => {
    return await api.auth.register(username, email, password);
  };

  const logout = () => {
    clearStoredToken();
    setToken(null);
    setUser(null);
    setNotifications([]);
    setUnreadNotificationCount(0);
  };

  const openAuthModal = (tab: 'login' | 'register' = 'login') => {
    setAuthModalTab(tab);
    setIsAuthModalOpen(true);
  };

  const closeAuthModal = () => {
    setIsAuthModalOpen(false);
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        isAuthenticated: !!user,
        isLoading,
        login,
        loginWithGoogle,
        finalizeLogin,
        register,
        logout,
        refreshUser,
        isAuthModalOpen,
        authModalTab,
        openAuthModal,
        closeAuthModal,
        unreadNotificationCount,
        notifications,
        markNotificationRead,
        loadNotifications,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};
