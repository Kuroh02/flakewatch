import type { ReactNode } from 'react';
import type { ApiError } from '../lib/api';

/** Shown while data loads (FR-UI-007). */
export function Loading({ what = 'data' }: { what?: string }) {
  return (
    <div className="state" role="status" aria-live="polite">
      <span className="spinner" aria-hidden="true" />
      Loading {what}…
    </div>
  );
}

export function Empty({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="state state-empty">
      <p className="state-title">{title}</p>
      {children && <div className="state-hint">{children}</div>}
    </div>
  );
}

export function ErrorState({ error, onRetry }: { error: ApiError; onRetry: () => void }) {
  return (
    <div className="state state-error" role="alert">
      <p className="state-title">We could not load this page.</p>
      <p className="state-hint">{error.message}</p>
      <button type="button" className="button" onClick={onRetry}>
        Try again
      </button>
    </div>
  );
}

export function NotFound() {
  return (
    <div className="state state-empty">
      <h1>Page not found</h1>
      <p className="state-hint">
        This page doesn't exist, or the project is private. <a href="/">Go to the home page</a>
      </p>
    </div>
  );
}
