import React, { useEffect, useRef } from 'react';
import { AlertTriangle, Trash2, X, Loader2, type LucideIcon } from 'lucide-react';

export interface ConfirmModalProps {
  isOpen: boolean;
  title: string;
  message: React.ReactNode;
  confirmText?: string;
  cancelText?: string;
  variant?: 'danger' | 'warning' | 'primary';
  icon?: LucideIcon;
  isLoading?: boolean;
  onConfirm: () => void | Promise<void>;
  onCancel: () => void;
}

export const ConfirmModal: React.FC<ConfirmModalProps> = ({
  isOpen,
  title,
  message,
  confirmText = 'Delete',
  cancelText = 'Cancel',
  variant = 'danger',
  icon: Icon = variant === 'danger' ? Trash2 : AlertTriangle,
  isLoading = false,
  onConfirm,
  onCancel,
}) => {
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && isOpen && !isLoading) {
        onCancel();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, isLoading, onCancel]);

  const backdropMouseDownRef = useRef(false);

  if (!isOpen) return null;

  return (
    <div
      className="modal-backdrop"
      onMouseDown={(e) => { backdropMouseDownRef.current = e.target === e.currentTarget; }}
      onMouseUp={(e) => {
        if (!isLoading && backdropMouseDownRef.current && e.target === e.currentTarget) {
          onCancel();
        }
        backdropMouseDownRef.current = false;
      }}
    >
      <div
        className="modal-card confirm-modal-card"
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-labelledby="confirm-modal-title"
      >
        <button
          type="button"
          className="modal-close-btn"
          onClick={onCancel}
          disabled={isLoading}
          aria-label="Close dialog"
        >
          <X size={18} />
        </button>

        <div className="confirm-modal-header">
          <div className={`confirm-modal-icon-badge badge-${variant}`}>
            <Icon size={24} />
          </div>
          <div className="confirm-modal-text">
            <h3 id="confirm-modal-title" className="confirm-modal-title">
              {title}
            </h3>
            <div className="confirm-modal-message">{message}</div>
          </div>
        </div>

        <div className="confirm-modal-actions">
          <button
            type="button"
            className="btn-secondary"
            onClick={onCancel}
            disabled={isLoading}
          >
            {cancelText}
          </button>
          <button
            type="button"
            className={variant === 'danger' ? 'btn-danger-solid' : 'btn-primary'}
            onClick={onConfirm}
            disabled={isLoading}
          >
            {isLoading ? (
              <>
                <Loader2 size={15} className="spin-loader" />
                <span>Processing...</span>
              </>
            ) : (
              <>
                <Icon size={15} />
                <span>{confirmText}</span>
              </>
            )}
          </button>
        </div>
      </div>
    </div>
  );
};

