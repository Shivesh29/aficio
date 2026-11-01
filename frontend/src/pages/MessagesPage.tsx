import { useEffect, useState, useRef, type FormEvent } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import { api, type DirectMessage, type ConversationSummary, type DmRecipientStatus } from '../api/client';
import { useAuth } from '../context/useAuth';
import {
  MessageSquare,
  Send,
  Loader2,
  ArrowRight,
  User,
  AlertCircle,
  UserX,
  Lock,
  Clock,
  UserPlus,
  Check,
  CheckCheck,
} from 'lucide-react';
import { parseUtcDate } from '../utils/date';

function formatMessageTime(iso: string | null | undefined): string {
  if (!iso) return '';
  const date = parseUtcDate(iso);
  return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

function formatConversationTime(iso: string | null | undefined): string {
  if (!iso) return '';
  const date = parseUtcDate(iso);
  const now = new Date();
  const isToday = date.toDateString() === now.toDateString();
  if (isToday) {
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  }
  return date.toLocaleDateString([], { month: 'short', day: 'numeric' });
}

export const MessagesPage = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const { user: currentUser, isAuthenticated, token, openAuthModal } = useAuth();

  const activeRecipient = searchParams.get('to') || '';
  const [recipientInput, setRecipientInput] = useState(activeRecipient);
  const [conversations, setConversations] = useState<ConversationSummary[]>([]);
  const [loadingConversations, setLoadingConversations] = useState(false);
  const [messages, setMessages] = useState<DirectMessage[]>([]);
  const [newMessage, setNewMessage] = useState('');
  const [loading, setLoading] = useState(false);
  const [sending, setSending] = useState(false);
  const [sendError, setSendError] = useState('');
  const [wsConnected, setWsConnected] = useState(false);

  // User search & recipient validation state
  const [searchError, setSearchError] = useState('');
  const [searchingUser, setSearchingUser] = useState(false);
  const [recipientStatus, setRecipientStatus] = useState<DmRecipientStatus | null>(null);
  const [followingAction, setFollowingAction] = useState(false);

  const messagesEndRef = useRef<HTMLDivElement>(null);

  const loadConversations = async () => {
    if (!isAuthenticated) return;
    try {
      setLoadingConversations(true);
      const data = await api.messages.getConversations();
      setConversations(data);
    } catch (err) {
      console.error('Failed to load conversations list', err);
    } finally {
      setLoadingConversations(false);
    }
  };

  useEffect(() => {
    if (!isAuthenticated) return;
    loadConversations();
  }, [isAuthenticated]);

  useEffect(() => {
    setRecipientInput(activeRecipient);
    setSearchError('');
    setSendError('');
  }, [activeRecipient]);

  const stompClientRef = useRef<any>(null);

  // WebSocket connection for real-time messages
  useEffect(() => {
    if (!isAuthenticated || !token) return;

    const accessToken = token;
    let active = true;

    async function connectWs() {
      try {
        const SockJS = (await import('sockjs-client')).default;
        const Stomp = (await import('stompjs')).default;

        const socket = new SockJS('/social-ws?access_token=' + encodeURIComponent(accessToken));
        const client = Stomp.over(socket);
        client.debug = () => {};

        const connectHeaders = {
          Authorization: 'Bearer ' + accessToken,
          access_token: accessToken,
        };

        client.connect(
          connectHeaders,
          () => {
            if (!active) {
              try { client.disconnect(); } catch {}
              return;
            }
            stompClientRef.current = client;
            setWsConnected(true);

            client.subscribe('/user/queue/dms', (message: any) => {
              if (!active) return;
              try {
                const incoming: any = JSON.parse(message.body);
                const dm: DirectMessage = {
                  id: incoming.id || Date.now(),
                  sender: { id: 0, username: incoming.senderUsername },
                  recipient: { id: 0, username: currentUser?.username || '' },
                  content: incoming.content,
                  sentAt: incoming.sentAt || new Date().toISOString(),
                  isRead: incoming.isRead || false,
                  readAt: incoming.readAt || null,
                };

                setMessages((prev) => {
                  if (prev.some((m) => m.id === dm.id)) return prev;
                  return [...prev, dm];
                });

                // If this message belongs to currently open conversation, mark as read
                if (activeRecipient && activeRecipient.toLowerCase() === incoming.senderUsername.toLowerCase()) {
                  void api.messages.markAsRead(incoming.senderUsername);
                }

                loadConversations();
              } catch (err) {
                console.error('Failed to parse incoming DM', err);
              }
            });

            client.subscribe('/user/queue/dm-read', (message: any) => {
              if (!active) return;
              try {
                const receipt: any = JSON.parse(message.body);
                setMessages((prev) =>
                  prev.map((m) => {
                    const isMine =
                      currentUser &&
                      m.sender?.username?.toLowerCase() === currentUser.username.toLowerCase();
                    if (isMine) {
                      return { ...m, isRead: true, readAt: receipt.readAt };
                    }
                    return m;
                  })
                );
              } catch (err) {
                console.error('Failed to parse incoming DM read receipt', err);
              }
            });
          },
          () => {
            if (!active) return;
            stompClientRef.current = null;
            setWsConnected(false);
          }
        );
      } catch {
        if (!active) return;
        stompClientRef.current = null;
        setWsConnected(false);
      }
    }

    void connectWs();

    return () => {
      active = false;
      if (stompClientRef.current) {
        try { stompClientRef.current.disconnect(); } catch {}
        stompClientRef.current = null;
      }
      setWsConnected(false);
    };
  }, [isAuthenticated, token, currentUser?.username]);

  // Load conversation & validate active recipient
  useEffect(() => {
    let mounted = true;
    async function loadConversation() {
      if (!activeRecipient || !isAuthenticated) {
        setRecipientStatus(null);
        return;
      }
      try {
        setLoading(true);
        setSendError('');

        // Step 1: Pre-validate user existence and follow status
        const status = await api.messages.checkRecipient(activeRecipient);
        if (!mounted) return;
        setRecipientStatus(status);

        if (!status.userExists) {
          setMessages([]);
          return;
        }

        // Step 2: Load historical messages if user exists
        const data = await api.messages.getConversation(activeRecipient);
        if (!mounted) return;
        setMessages(data);

        // Step 3: Mark conversation as read
        try {
          await api.messages.markAsRead(activeRecipient);
          setConversations((prev) =>
            prev.map((c) =>
              c.username.toLowerCase() === activeRecipient.toLowerCase()
                ? { ...c, unreadCount: 0 }
                : c
            )
          );
        } catch {
          // ignore mark as read errors
        }
      } catch (err) {
        console.error('Failed to load conversation', err);
      } finally {
        if (mounted) setLoading(false);
      }
    }
    void loadConversation();

    return () => {
      mounted = false;
    };
  }, [activeRecipient, isAuthenticated]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  // Early validation when starting chat from the input
  const handleStartChat = async (e: FormEvent) => {
    e.preventDefault();
    const target = recipientInput.trim();
    if (!target) return;

    setSearchingUser(true);
    setSearchError('');

    try {
      const status = await api.messages.checkRecipient(target);
      if (!status.userExists) {
        setSearchError(`Account "@${target}" does not exist.`);
        return;
      }
      if (status.followStatus === 'SELF') {
        setSearchError('You cannot direct message yourself.');
        return;
      }

      setSearchError('');
      setRecipientStatus(status);
      setSearchParams({ to: status.username });
    } catch (err) {
      setSearchError(err instanceof Error ? err.message : 'Error validating username.');
    } finally {
      setSearchingUser(false);
    }
  };

  const handleFollowRecipient = async () => {
    if (!activeRecipient) return;
    setFollowingAction(true);
    try {
      await api.social.follow(activeRecipient);
      const updated = await api.messages.checkRecipient(activeRecipient);
      setRecipientStatus(updated);
    } catch (err) {
      alert(err instanceof Error ? err.message : 'Could not follow user.');
    } finally {
      setFollowingAction(false);
    }
  };

  const handleSendMessage = async (e: FormEvent) => {
    e.preventDefault();
    const content = newMessage.trim();
    if (!content || !activeRecipient) return;

    if (recipientStatus && !recipientStatus.canMessage) {
      setSendError(recipientStatus.reason || 'You must have an approved follow relationship to send messages.');
      return;
    }

    setSending(true);
    setSendError('');

    try {
      const sent = await api.messages.sendMessage(activeRecipient, content);
      const newDm: DirectMessage = {
        id: sent.id,
        sender: sent.sender ?? { id: currentUser?.id || 0, username: currentUser?.username || 'You' },
        recipient: sent.recipient ?? { id: 0, username: activeRecipient },
        content: sent.content,
        sentAt: sent.sentAt,
      };
      setMessages((prev) => {
        if (prev.some((m) => m.id === newDm.id)) return prev;
        return [...prev, newDm];
      });
      setNewMessage('');
      void loadConversations();
    } catch (err) {
      setSendError(err instanceof Error ? err.message : 'Could not deliver message.');
    } finally {
      setSending(false);
    }
  };

  if (!isAuthenticated) {
    return (
      <div className="empty-catalog-state" style={{ margin: '6rem auto' }}>
        <MessageSquare size={48} className="empty-icon" />
        <h3>Sign in to access Direct Messages</h3>
        <p>You must be signed in to Aficio to talk music and message other listeners.</p>
        <button type="button" className="btn-primary" onClick={() => openAuthModal('login')}>
          Sign In
        </button>
      </div>
    );
  }

  const isUserNotFound = recipientStatus && !recipientStatus.userExists;
  const isMessageRestricted = recipientStatus && recipientStatus.userExists && !recipientStatus.canMessage;

  return (
    <div className="messages-page">
      <div className="messages-shell">
        <aside className="messages-sidebar">
          <div className="sidebar-header">
            <h3>
              <MessageSquare size={18} />
              <span>Direct Messages</span>
            </h3>
            {wsConnected && <span className="ws-status-dot" title="Live connection active" />}
          </div>

          <form onSubmit={handleStartChat} className="chat-start-form">
            <label htmlFor="user-recipient-input">Talk with a listener</label>
            <div className="recipient-input-row">
              <input
                id="user-recipient-input"
                type="text"
                placeholder="Enter username..."
                value={recipientInput}
                onChange={(e) => {
                  setRecipientInput(e.target.value);
                  if (searchError) setSearchError('');
                }}
                disabled={searchingUser}
              />
              <button
                type="submit"
                className="btn-secondary-sm"
                aria-label="Open chat"
                disabled={searchingUser || !recipientInput.trim()}
              >
                {searchingUser ? <Loader2 size={13} className="spin-loader" /> : <ArrowRight size={14} />}
              </button>
            </div>
            {searchError && (
              <div className="sidebar-search-error">
                <AlertCircle size={13} />
                <span>{searchError}</span>
              </div>
            )}
          </form>

          <div className="sidebar-conversations-wrapper">
            <div className="sidebar-section-header">
              <span>Chats</span>
              {loadingConversations && <Loader2 size={12} className="spin-loader" />}
            </div>

            <div className="sidebar-conversations-list">
              {conversations.length === 0 ? (
                <div className="sidebar-empty-hint">
                  <User size={16} />
                  <span>No conversations yet. Message a listener to begin!</span>
                </div>
              ) : (
                conversations.map((c) => {
                  const isSelected = activeRecipient.toLowerCase() === c.username.toLowerCase();
                  const hasUnread = Boolean(c.unreadCount && c.unreadCount > 0);
                  return (
                    <button
                      key={c.userId}
                      type="button"
                      className={`conversation-tab-item ${isSelected ? 'active' : ''} ${hasUnread ? 'has-unread' : ''}`}
                      onClick={() => {
                        setSearchParams({ to: c.username });
                        setRecipientInput(c.username);
                        setSearchError('');
                      }}
                    >
                      <span className="user-avatar-sm">
                        {c.username.charAt(0).toUpperCase()}
                      </span>
                      <div className="conversation-preview-info">
                        <div className="conversation-row-top">
                          <strong className="conversation-username">{c.username}</strong>
                          <span className="conversation-time">
                            {c.lastSentAt ? formatConversationTime(c.lastSentAt) : ''}
                          </span>
                        </div>
                        <div className="conversation-row-bottom">
                          <p className="conversation-snippet">
                            {c.fromMe ? 'You: ' : ''}{c.lastMessage}
                          </p>
                          {hasUnread && (
                            <span className="conversation-unread-badge" title={`${c.unreadCount} unread message(s)`}>
                              {c.unreadCount! > 99 ? '99+' : c.unreadCount}
                            </span>
                          )}
                        </div>
                      </div>
                    </button>
                  );
                })
              )}
            </div>
          </div>
        </aside>

        <main className="messages-chat-area">
          {!activeRecipient ? (
            <div className="no-active-chat">
              <MessageSquare size={44} className="text-faint" />
              <h4>Select or enter a listener to chat</h4>
              <p>Direct message any music lover to exchange tracks, opinions, and vinyl recommendations.</p>
            </div>
          ) : isUserNotFound ? (
            <div className="chat-user-not-found-state">
              <div className="error-icon-circle">
                <UserX size={40} />
              </div>
              <h3>Account Does Not Exist</h3>
              <p>
                No user was found with the username <strong>@{activeRecipient}</strong>. Please check the spelling or search another listener.
              </p>
              <button
                type="button"
                className="btn-secondary"
                onClick={() => {
                  setSearchParams({});
                  setRecipientInput('');
                  setRecipientStatus(null);
                }}
              >
                Back to Direct Messages
              </button>
            </div>
          ) : (
            <>
              <div className="chat-header">
                <div className="chat-header-user">
                  <span className="user-avatar-sm">
                    {activeRecipient.charAt(0).toUpperCase()}
                  </span>
                  <div>
                    <h4>{activeRecipient}</h4>
                    <Link to={`/u/${activeRecipient}`} className="profile-link-small">
                      View Profile
                    </Link>
                  </div>
                </div>
                {wsConnected && <span className="ws-badge">Live</span>}
              </div>

              {/* Follow restriction banner if user exists but has not approved follow */}
              {isMessageRestricted && (
                <div className="chat-restriction-banner">
                  {recipientStatus.followStatus === 'PENDING' ? (
                    <div className="restriction-layout">
                      <div className="restriction-badge pending">
                        <Clock size={16} />
                      </div>
                      <div className="restriction-text">
                        <strong>Follow Request Pending</strong>
                        <p>
                          Your follow request to <strong>@{activeRecipient}</strong> has not been approved yet. Once accepted, you will be able to exchange messages.
                        </p>
                      </div>
                    </div>
                  ) : (
                    <div className="restriction-layout">
                      <div className="restriction-badge locked">
                        <Lock size={16} />
                      </div>
                      <div className="restriction-text">
                        <strong>Follow Required to Direct Message</strong>
                        <p>
                          You must follow <strong>@{activeRecipient}</strong> and have your follow request approved before sending direct messages.
                        </p>
                      </div>
                      <button
                        type="button"
                        className="btn-primary-sm"
                        onClick={handleFollowRecipient}
                        disabled={followingAction}
                      >
                        {followingAction ? (
                          <Loader2 size={13} className="spin-loader" />
                        ) : (
                          <UserPlus size={14} />
                        )}
                        <span>{followingAction ? 'Sending Request...' : `Follow @${activeRecipient}`}</span>
                      </button>
                    </div>
                  )}
                </div>
              )}

              <div className="chat-messages-scroll">
                {loading ? (
                  <div className="loading-state-container">
                    <Loader2 size={24} className="spin-loader" />
                    <p>Fetching messages...</p>
                  </div>
                ) : messages.length === 0 ? (
                  <div className="empty-messages-prompt">
                    <p>
                      {isMessageRestricted
                        ? `Direct messaging with @${activeRecipient} will be enabled once follow is approved.`
                        : `No previous messages with ${activeRecipient}. Say hello!`}
                    </p>
                  </div>
                ) : (
                  messages.map((m) => {
                    const isMine =
                      currentUser &&
                      m.sender?.username?.toLowerCase() === currentUser.username.toLowerCase();

                    return (
                      <div
                        key={m.id}
                        className={`message-bubble-row ${isMine ? 'mine' : 'theirs'}`}
                      >
                        <div className="message-bubble">
                          <p>{m.content}</p>
                          <div className="message-meta-row">
                            <span className="message-timestamp">
                              {m.sentAt ? formatMessageTime(m.sentAt) : ''}
                            </span>
                            {isMine && (
                              <span
                                className={`message-read-status ${m.isRead ? 'read' : 'delivered'}`}
                                title={m.isRead ? 'Read' : 'Delivered'}
                              >
                                {m.isRead ? (
                                  <CheckCheck size={13} className="read-check" />
                                ) : (
                                  <Check size={13} className="delivered-check" />
                                )}
                              </span>
                            )}
                          </div>
                        </div>
                      </div>
                    );
                  })
                )}
                <div ref={messagesEndRef} />
              </div>

              {sendError && (
                <div className="chat-send-error-bar">
                  <AlertCircle size={14} />
                  <span>{sendError}</span>
                </div>
              )}

              <form onSubmit={handleSendMessage} className="chat-input-bar">
                <input
                  type="text"
                  placeholder={
                    isMessageRestricted
                      ? recipientStatus?.followStatus === 'PENDING'
                        ? 'Follow request pending approval...'
                        : `Follow @${activeRecipient} to send direct messages...`
                      : `Message ${activeRecipient}...`
                  }
                  value={newMessage}
                  onChange={(e) => {
                    setNewMessage(e.target.value);
                    if (sendError) setSendError('');
                  }}
                  disabled={sending || Boolean(isMessageRestricted)}
                  autoFocus={!isMessageRestricted}
                />
                <button
                  type="submit"
                  className="chat-send-btn"
                  disabled={sending || !newMessage.trim() || Boolean(isMessageRestricted)}
                  aria-label="Send message"
                  title={isMessageRestricted ? 'Follow request must be approved first' : 'Send message'}
                >
                  <Send size={16} />
                </button>
              </form>
            </>
          )}
        </main>
      </div>
    </div>
  );
};
