import { useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { api, isListPublic, type MusicList, type MusicListItem } from '../api/client';
import { useAuth } from '../context/useAuth';
import { ConfirmModal } from '../components/ConfirmModal';
import { Bookmark, Plus, User, Disc, X, Globe, Lock, Loader2, Search, Trash2 } from 'lucide-react';

export const ListsPage = () => {
  const { isAuthenticated, openAuthModal } = useAuth();

  const [activeTab, setActiveTab] = useState<'public' | 'mine'>('public');
  const [lists, setLists] = useState<MusicList[]>([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // Album previews map for each list: listId -> array of coverUrls
  const [listPreviewMap, setListPreviewMap] = useState<Record<number, string[]>>({});

  // Create List Modal
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [isPublic, setIsPublic] = useState(true);
  const [creating, setCreating] = useState(false);

  // Delete Collection Modal State
  const [listToDelete, setListToDelete] = useState<MusicList | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState('');

  useEffect(() => {
    async function loadLists() {
      try {
        setLoading(true);
        setError('');
        let fetched: MusicList[] = [];
        if (activeTab === 'public') {
          fetched = await api.lists.getPublicLists();
        } else {
          fetched = await api.lists.getMyLists();
        }
        setLists(fetched);

        // Fetch preview albums for each list (first 4 items for Pinterest collage)
        const previews: Record<number, string[]> = {};
        await Promise.all(
          fetched.map(async (l) => {
            try {
              const items: MusicListItem[] = await api.lists.getListAlbums(l.id);
              previews[l.id] = items
                .map((item) => item.album?.coverArtUrl)
                .filter(Boolean) as string[];
            } catch {
              previews[l.id] = [];
            }
          })
        );
        setListPreviewMap(previews);
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Unable to load collections');
      } finally {
        setLoading(false);
      }
    }
    void loadLists();
  }, [activeTab]);

  const handleOpenCreateModal = () => {
    if (!isAuthenticated) {
      openAuthModal('login');
      return;
    }
    setIsCreateModalOpen(true);
  };

  const handleCreateList = async (e: FormEvent) => {
    e.preventDefault();
    if (!title.trim()) return;
    setCreating(true);
    try {
      const newList = await api.lists.createList(title.trim(), description.trim(), isPublic);
      setLists((prev) => [newList, ...prev]);
      setIsCreateModalOpen(false);
      setTitle('');
      setDescription('');
      setIsPublic(true);
    } catch (err) {
      alert(err instanceof Error ? err.message : 'Failed to create collection');
    } finally {
      setCreating(false);
    }
  };

  const handleConfirmDeleteList = async () => {
    if (!listToDelete) return;
    setIsDeleting(true);
    setDeleteError('');
    try {
      await api.lists.deleteList(listToDelete.id);
      setLists((prev) => prev.filter((l) => l.id !== listToDelete.id));
      setListToDelete(null);
    } catch (err) {
      setDeleteError(err instanceof Error ? err.message : 'Failed to delete collection');
    } finally {
      setIsDeleting(false);
    }
  };

  const filteredLists = lists.filter((list) => {
    if (!searchQuery.trim()) return true;
    const q = searchQuery.toLowerCase().trim();
    return (
      list.title?.toLowerCase().includes(q) ||
      list.description?.toLowerCase().includes(q) ||
      list.owner?.username?.toLowerCase().includes(q)
    );
  });

  return (
    <div className="lists-hub-page">
      {/* Header */}
      <section className="lists-header-section">
        <div className="lists-title-content">
          <div className="kicker">Pinterest-Style Curation</div>
          <h1>Music Collections & Moodboards</h1>
          <p>
            Curated lists of records tailored for moods, eras, Sunday mornings, or deep dives.
            Build your own archive or explore lists from other aficionados.
          </p>
        </div>

        <button type="button" className="btn-primary" onClick={handleOpenCreateModal}>
          <Plus size={16} />
          <span>New Collection</span>
        </button>
      </section>

      {/* Navigation Tabs */}
      <div className="lists-tabs-bar">
        <button
          type="button"
          className={`list-tab-btn ${activeTab === 'public' ? 'active' : ''}`}
          onClick={() => {
            setActiveTab('public');
            setSearchQuery('');
          }}
        >
          <Globe size={15} />
          <span>Public Collections</span>
        </button>
        {isAuthenticated && (
          <button
            type="button"
            className={`list-tab-btn ${activeTab === 'mine' ? 'active' : ''}`}
            onClick={() => {
              setActiveTab('mine');
              setSearchQuery('');
            }}
          >
            <Bookmark size={15} />
            <span>My Collections</span>
          </button>
        )}
      </div>

      {/* Search Bar for Collections */}
      <div className="lists-filter-bar">
        <div className="lists-search-input-wrap">
          <Search size={16} className="search-icon" />
          <input
            type="text"
            placeholder={
              activeTab === 'public'
                ? 'Search public collections by name, curator, or theme...'
                : 'Search your collections by title or description...'
            }
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />
          {searchQuery && (
            <button
              type="button"
              className="search-clear-btn"
              onClick={() => setSearchQuery('')}
              aria-label="Clear search"
            >
              <X size={14} />
            </button>
          )}
        </div>
        {searchQuery.trim() && (
          <div className="search-results-tag">
            <span>
              Found {filteredLists.length} {filteredLists.length === 1 ? 'collection' : 'collections'}
            </span>
          </div>
        )}
      </div>

      {/* Grid of collections */}
      {loading ? (
        <div className="loading-state-container">
          <Loader2 size={32} className="spin-loader" />
          <p>Gathering collections...</p>
        </div>
      ) : error ? (
        <div className="error-state-container">
          <p>{error}</p>
        </div>
      ) : lists.length === 0 ? (
        <div className="empty-catalog-state">
          <Bookmark size={44} className="empty-icon" />
          <h3>No collections found</h3>
          <p>
            {activeTab === 'mine'
              ? 'You have not created any collections yet. Start curating your first list!'
              : 'Be the first listener to publish a public collection.'}
          </p>
          <button type="button" className="btn-primary" onClick={handleOpenCreateModal}>
            <Plus size={16} /> Create Collection
          </button>
        </div>
      ) : filteredLists.length === 0 ? (
        <div className="empty-catalog-state">
          <Search size={44} className="empty-icon" />
          <h3>No matching collections</h3>
          <p>We couldn't find any collections matching "{searchQuery}". Try a different keyword.</p>
          <button type="button" className="btn-secondary" onClick={() => setSearchQuery('')}>
            Clear Search Filter
          </button>
        </div>
      ) : (
        <div className="lists-pinterest-grid">
          {filteredLists.map((list) => {
            const covers = listPreviewMap[list.id] || [];

            return (
              <Link key={list.id} to={`/lists/${list.id}`} className="list-card-pinterest">
                {/* Collage preview grid */}
                <div className="list-mosaic-container">
                  {covers.length > 0 ? (
                    <div className={`mosaic-grid mosaic-${Math.min(covers.length, 4)}`}>
                      {covers.slice(0, 4).map((url, i) => (
                        <div key={i} className="mosaic-tile">
                          <img
                            src={url}
                            alt="Cover preview"
                            onError={(e) => {
                              (e.target as HTMLImageElement).src = '/record-placeholder.svg';
                            }}
                          />
                        </div>
                      ))}
                    </div>
                  ) : (
                    <div className="mosaic-fallback">
                      <Disc size={36} className="spin-slow text-faint" />
                      <span>Empty Collection</span>
                    </div>
                  )}
                </div>

                <div className="list-card-details">
                  <div className="list-card-meta-top">
                    {(() => {
                      const isPublic = isListPublic(list);
                      return (
                        <span className={`list-visibility-tag ${isPublic ? 'tag-public' : 'tag-private'}`}>
                          {isPublic ? <Globe size={11} /> : <Lock size={11} />}
                          <span>{isPublic ? 'Public' : 'Private'}</span>
                        </span>
                      );
                    })()}
                    <span className="list-album-count">
                      {covers.length} {covers.length === 1 ? 'record' : 'records'}
                    </span>
                  </div>

                  <h3 className="list-card-title">{list.title}</h3>
                  {list.description && (
                    <p className="list-card-description">{list.description}</p>
                  )}

                  <div className="list-card-footer-row">
                    <div className="list-card-creator">
                      <User size={13} />
                      <span>Curated by {list.owner?.username || 'Aficionado'}</span>
                    </div>
                    {activeTab === 'mine' && (
                      <button
                        type="button"
                        className="list-delete-action-btn"
                        title="Delete collection"
                        onClick={(e) => {
                          e.preventDefault();
                          e.stopPropagation();
                          setListToDelete(list);
                        }}
                      >
                        <Trash2 size={13} />
                        <span>Delete</span>
                      </button>
                    )}
                  </div>
                </div>
              </Link>
            );
          })}
        </div>
      )}

      {/* Create List Modal */}
      {isCreateModalOpen && (
        <div className="modal-backdrop" onClick={() => setIsCreateModalOpen(false)}>
          <div className="modal-card create-list-modal" onClick={(e) => e.stopPropagation()}>
            <button
              className="modal-close-btn"
              onClick={() => setIsCreateModalOpen(false)}
            >
              <X size={18} />
            </button>

            <div className="review-modal-header">
              <span className="kicker">Curate</span>
              <h3>Create New Collection</h3>
              <p>Give your collection a mood, theme, or concept.</p>
            </div>

            <form onSubmit={handleCreateList} className="auth-form">
              <div className="form-field">
                <label>Collection Title</label>
                <input
                  type="text"
                  placeholder="e.g. Midnight Drives & Ambient Textures"
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  required
                  autoFocus
                />
              </div>

              <div className="form-field">
                <label>Description</label>
                <textarea
                  rows={3}
                  placeholder="What is the story or feeling behind this collection?"
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                ></textarea>
              </div>

              {/* Explicit Public / Private Selector */}
              <div className="form-field">
                <label>Privacy & Visibility</label>
                <div className="visibility-options-grid">
                  <button
                    type="button"
                    className={`visibility-option-card ${isPublic ? 'selected' : ''}`}
                    onClick={() => setIsPublic(true)}
                  >
                    <div className="visibility-option-icon globe">
                      <Globe size={18} />
                    </div>
                    <div className="visibility-option-content">
                      <div className="visibility-header-line">
                        <strong>Public Collection</strong>
                        <span className="badge-pill public">Public</span>
                      </div>
                      <p>Visible to everyone. Discoverable in the Public Collections tab and on your profile.</p>
                    </div>
                  </button>

                  <button
                    type="button"
                    className={`visibility-option-card ${!isPublic ? 'selected' : ''}`}
                    onClick={() => setIsPublic(false)}
                  >
                    <div className="visibility-option-icon lock">
                      <Lock size={18} />
                    </div>
                    <div className="visibility-option-content">
                      <div className="visibility-header-line">
                        <strong>Private Collection</strong>
                        <span className="badge-pill private">Private</span>
                      </div>
                      <p>Only visible to you. Hidden from public discovery and hidden from your public profile.</p>
                    </div>
                  </button>
                </div>
              </div>

              <button
                type="submit"
                className="btn-primary"
                disabled={creating || !title.trim()}
              >
                {creating ? 'Creating...' : isPublic ? 'Publish Public Collection' : 'Create Private Collection'}
              </button>
            </form>
          </div>
        </div>
      )}

      {/* In-App Confirmation Modal: Delete Collection */}
      <ConfirmModal
        isOpen={Boolean(listToDelete)}
        title="Delete Collection?"
        message={
          <>
            Are you sure you want to permanently delete the collection{' '}
            <strong>"{listToDelete?.title}"</strong>? This action cannot be undone.
            {deleteError && (
              <div style={{ marginTop: '0.75rem', color: '#f43f5e', fontSize: '0.85rem' }}>
                {deleteError}
              </div>
            )}
          </>
        }
        confirmText={isDeleting ? 'Deleting...' : 'Delete Collection'}
        variant="danger"
        isLoading={isDeleting}
        onConfirm={handleConfirmDeleteList}
        onCancel={() => {
          setListToDelete(null);
          setDeleteError('');
        }}
      />
    </div>
  );
};
