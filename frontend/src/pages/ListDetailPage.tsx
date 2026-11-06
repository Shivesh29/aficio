import { useEffect, useState } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { api, isListPublic, type MusicList, type MusicListItem } from '../api/client';
import { useAuth } from '../context/useAuth';
import { AlbumCard } from '../components/AlbumCard';
import { ConfirmModal } from '../components/ConfirmModal';
import { Bookmark, User, Globe, Lock, ArrowLeft, Plus, Loader2, Trash2 } from 'lucide-react';

export const ListDetailPage = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { user, isAuthenticated } = useAuth();

  const [list, setList] = useState<MusicList | null>(null);
  const [items, setItems] = useState<MusicListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState('');

  // Confirmation Modals State
  const [isDeleteCollectionModalOpen, setIsDeleteCollectionModalOpen] = useState(false);
  const [albumToRemove, setAlbumToRemove] = useState<{ id: number; title: string } | null>(null);
  const [removingAlbum, setRemovingAlbum] = useState(false);
  const [actionError, setActionError] = useState('');

  useEffect(() => {
    async function loadListDetails() {
      if (!id) return;
      try {
        setLoading(true);
        const [listData, itemsData] = await Promise.all([
          api.lists.getListById(id),
          api.lists.getListAlbums(id),
        ]);
        setList(listData);
        setItems(itemsData);
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Could not load collection');
      } finally {
        setLoading(false);
      }
    }
    void loadListDetails();
  }, [id]);

  // Determine if the current authenticated user owns/created this collection
  const isOwner = Boolean(
    isAuthenticated &&
    user &&
    list?.owner &&
    (user.id === list.owner.id || (user.username && list.owner.username && user.username.toLowerCase() === list.owner.username.toLowerCase()))
  );

  const handleConfirmDeleteCollection = async () => {
    if (!isOwner || !id || !list) return;
    setDeleting(true);
    setActionError('');
    try {
      await api.lists.deleteList(id);
      setIsDeleteCollectionModalOpen(false);
      navigate('/lists');
    } catch (err) {
      setActionError(err instanceof Error ? err.message : 'Failed to delete collection');
      setDeleting(false);
    }
  };

  const handleConfirmRemoveAlbum = async () => {
    if (!isOwner || !id || !albumToRemove) return;
    setRemovingAlbum(true);
    try {
      await api.lists.removeAlbumFromList(Number(id), albumToRemove.id);
      setItems((prev) => prev.filter((item) => item.album?.id !== albumToRemove.id));
      setAlbumToRemove(null);
    } catch (err) {
      setActionError(err instanceof Error ? err.message : 'Failed to remove album from collection');
    } finally {
      setRemovingAlbum(false);
    }
  };

  if (loading) {
    return (
      <div className="dossier-loading">
        <Loader2 size={36} className="spin-loader" />
        <p>Loading list...</p>
      </div>
    );
  }

  if (error || !list) {
    return (
      <div className="dossier-error">
        <h3>List not found</h3>
        <p>{error || 'This list may be private or does not exist.'}</p>
        <Link to="/lists" className="btn-secondary">
          <ArrowLeft size={14} /> Back to Lists
        </Link>
      </div>
    );
  }

  const isPublic = isListPublic(list);

  return (
    <div className="list-detail-page">
      {/* Breadcrumb */}
      <div className="dossier-nav-breadcrumb">
        <Link to="/lists">Lists</Link>
        <span>/</span>
        <span className="current">{list.title}</span>
      </div>

      {/* Hero Header */}
      <header className="list-hero-header">
        <div className="list-hero-top-row">
          <div className="list-hero-meta">
            <span className={`list-visibility-tag ${isPublic ? 'tag-public' : 'tag-private'}`}>
              {isPublic ? <Globe size={12} /> : <Lock size={12} />}
              <span>{isPublic ? 'Public' : 'Private'}</span>
            </span>
            <span className="meta-dot">•</span>
            <span>{items.length} {items.length === 1 ? 'Album' : 'Albums'}</span>
          </div>

          {/* Owner Actions */}
          {isOwner && (
            <div className="collection-owner-actions-bar">
              <Link to="/search" className="btn-secondary-sm">
                <Plus size={14} /> Add Albums
              </Link>
              <button
                type="button"
                className="btn-danger-outline-sm"
                onClick={() => setIsDeleteCollectionModalOpen(true)}
                disabled={deleting}
                title="Delete this list"
              >
                <Trash2 size={13} />
                <span>Delete List</span>
              </button>
            </div>
          )}
        </div>

        <h1 className="list-hero-title">{list.title}</h1>

        {list.description && (
          <p className="list-hero-description">{list.description}</p>
        )}

        <div className="list-hero-author">
          <User size={15} />
          <span>
            List by{' '}
            <Link to={`/u/${list.owner?.username}`}>
              <strong>{list.owner?.username}</strong>
            </Link>
          </span>
        </div>
      </header>

      {/* Collection Albums Grid */}
      <section className="list-albums-section">
        <div className="section-subheading">
          <h3>
            <Bookmark size={16} />
            <span>Albums</span>
          </h3>
          {isOwner && (
            <Link to="/search" className="btn-secondary-sm">
              <Plus size={14} /> Add Albums
            </Link>
          )}
        </div>

        {items.length === 0 ? (
          <div className="empty-catalog-state">
            <Bookmark size={44} className="empty-icon" />
            <h3>This list is empty</h3>
            <p>
              {isOwner
                ? 'Browse the catalog to add albums into this moodboard.'
                : `@${list.owner?.username || 'The curator'} has not added any releases to this collection yet.`}
            </p>
            {isOwner && (
              <Link to="/search" className="btn-primary">
                Explore Catalog & Add
              </Link>
            )}
          </div>
        ) : (
          <div className="albums-grid-layout">
            {items.map((item) => (
              <div key={item.id} className="collection-album-item-wrap">
                <AlbumCard album={item.album} />
                {isOwner && item.album?.id && (
                  <button
                    type="button"
                    className="collection-item-remove-btn"
                    title={`Remove "${item.album?.title || 'album'}" from collection`}
                    onClick={() =>
                      setAlbumToRemove({
                        id: item.album.id,
                        title: item.album?.title || 'this release',
                      })
                    }
                  >
                    <Trash2 size={12} />
                    <span>Remove</span>
                  </button>
                )}
              </div>
            ))}
          </div>
        )}
      </section>

      {/* In-App Confirmation Modal: Delete Entire Collection */}
      <ConfirmModal
        isOpen={isDeleteCollectionModalOpen}
        title="Delete Collection?"
        message={
          <>
            Are you sure you want to permanently delete the collection{' '}
            <strong>"{list.title}"</strong>? All track listings and curated releases in
            this collection will be removed. This action cannot be undone.
            {actionError && (
              <div style={{ marginTop: '0.75rem', color: '#f43f5e', fontSize: '0.85rem' }}>
                {actionError}
              </div>
            )}
          </>
        }
        confirmText={deleting ? 'Deleting...' : 'Delete Collection'}
        variant="danger"
        isLoading={deleting}
        onConfirm={handleConfirmDeleteCollection}
        onCancel={() => {
          setIsDeleteCollectionModalOpen(false);
          setActionError('');
        }}
      />

      {/* In-App Confirmation Modal: Remove Album from Collection */}
      <ConfirmModal
        isOpen={Boolean(albumToRemove)}
        title="Remove Album?"
        message={
          <>
            Remove <strong>"{albumToRemove?.title}"</strong> from{' '}
            <strong>"{list.title}"</strong>? You can add it back at any time.
            {actionError && (
              <div style={{ marginTop: '0.75rem', color: '#f43f5e', fontSize: '0.85rem' }}>
                {actionError}
              </div>
            )}
          </>
        }
        confirmText={removingAlbum ? 'Removing...' : 'Remove Release'}
        variant="danger"
        isLoading={removingAlbum}
        onConfirm={handleConfirmRemoveAlbum}
        onCancel={() => {
          setAlbumToRemove(null);
          setActionError('');
        }}
      />
    </div>
  );
};
