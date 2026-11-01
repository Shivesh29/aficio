import { Link, useLocation } from 'react-router-dom';
import { useAuth } from '../context/useAuth';
import { api } from '../api/client';
import { Disc3, Search, MessageSquare, LogOut, Bookmark, PlusCircle, Bell, Activity, Settings } from 'lucide-react';
import { useState, useEffect, useRef } from 'react';
import { formatNotificationTime } from '../utils/date';

export const Navbar = () => {
  const { user, isAuthenticated, logout, openAuthModal, unreadNotificationCount, notifications, markNotificationRead, loadNotifications } = useAuth();
  const location = useLocation();
  const [showNotifications, setShowNotifications] = useState(false);
  const [unreadDmCount, setUnreadDmCount] = useState(0);
  const notifRef = useRef<HTMLDivElement>(null);

  const isActive = (path: string) => {
    if (path === '/' && location.pathname === '/') return true;
    if (path !== '/' && location.pathname.startsWith(path)) return true;
    return false;
  };

  useEffect(() => {
    if (!isAuthenticated) {
      setUnreadDmCount(0);
      return;
    }
    let active = true;
    api.messages
      .getUnreadCount()
      .then((res) => {
        if (active) setUnreadDmCount(res.unreadCount);
      })
      .catch(() => {});

    return () => {
      active = false;
    };
  }, [isAuthenticated, location.pathname]);

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (notifRef.current && !notifRef.current.contains(event.target as Node)) {
        setShowNotifications(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  return (
    <header className="site-navbar">
      <div className="navbar-container">
        {/* Brand */}
        <Link to="/" className="brand-wordmark">
          <Disc3 className="brand-vinyl-icon" size={24} />
          <span>aficio<em>.</em></span>
        </Link>

        {/* Primary Navigation */}
        <nav className="navbar-links">
          <Link to="/" className={`nav-link ${isActive('/') ? 'active' : ''}`}>
            Catalog
          </Link>
          <Link to="/lists" className={`nav-link ${isActive('/lists') ? 'active' : ''}`}>
            <Bookmark size={15} />
            Collections
          </Link>
          <Link to="/search" className={`nav-link ${isActive('/search') ? 'active' : ''}`}>
            <Search size={15} />
            Discover & Import
          </Link>
          {isAuthenticated && (
            <Link to="/feed" className={`nav-link ${isActive('/feed') ? 'active' : ''}`}>
              <Activity size={15} />
              Feed
            </Link>
          )}
          {isAuthenticated && (
            <Link to="/messages" className={`nav-link ${isActive('/messages') ? 'active' : ''}`}>
              <MessageSquare size={15} />
              <span>Messages</span>
              {unreadDmCount > 0 && (
                <span className="nav-badge-count" title={`${unreadDmCount} unread message(s)`}>
                  {unreadDmCount > 99 ? '99+' : unreadDmCount}
                </span>
              )}
            </Link>
          )}
        </nav>

        {/* Right Actions */}
        <div className="navbar-actions">
          <Link to="/search" className="quick-search-btn" title="Search music graph">
            <Search size={16} />
            <span>Search releases...</span>
            <kbd>⌘K</kbd>
          </Link>

          {isAuthenticated && user ? (
            <div className="user-controls">
              <div className="notification-wrapper" ref={notifRef}>
                <button
                  type="button"
                  className="icon-action-btn"
                  onClick={() => {
                    setShowNotifications(!showNotifications);
                    if (!showNotifications) loadNotifications();
                  }}
                  title="Notifications"
                  aria-label="Notifications"
                >
                  <Bell size={16} />
                  {unreadNotificationCount > 0 && (
                    <span className="notification-badge">{unreadNotificationCount}</span>
                  )}
                </button>
                {showNotifications && (
                  <div className="notification-dropdown">
                    <div className="notification-dropdown-header">
                      <strong>Notifications</strong>
                    </div>
                    <div className="notification-dropdown-list">
                      {notifications.length === 0 ? (
                        <div className="notification-empty">No notifications yet</div>
                      ) : (
                        notifications.slice(0, 10).map((n) => (
                          <div
                            key={n.id}
                            className={`notification-item ${n.read ? 'read' : 'unread'}`}
                            onClick={() => markNotificationRead(n.id)}
                          >
                            <div className="notification-message">{n.message}</div>
                            <div className="notification-time">
                              {formatNotificationTime(n.createdAt)}
                            </div>
                          </div>
                        ))
                      )}
                    </div>
                  </div>
                )}
              </div>
              <Link to={`/u/${user.username}`} className="user-profile-chip" title="View your profile">
                <span className="user-avatar-initial">
                  {user.username.charAt(0).toUpperCase()}
                </span>
                <span className="user-name-text">{user.username}</span>
              </Link>
              <Link to="/settings" className="icon-action-btn" title="Account settings" aria-label="Account settings">
                <Settings size={16} />
              </Link>
              <button
                type="button"
                onClick={logout}
                className="icon-action-btn logout"
                title="Sign out"
                aria-label="Sign out"
              >
                <LogOut size={16} />
              </button>
            </div>
          ) : (
            <div className="guest-controls">
              <button
                type="button"
                className="btn-text"
                onClick={() => openAuthModal('login')}
              >
                Sign In
              </button>
              <button
                type="button"
                className="btn-primary"
                onClick={() => openAuthModal('register')}
              >
                <PlusCircle size={15} />
                <span>Join Aficio</span>
              </button>
            </div>
          )}
        </div>
      </div>
    </header>
  );
};
