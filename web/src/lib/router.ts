import { useSyncExternalStore } from 'react';

/** A tiny client-side router: the browser URL is the state (FR-UI-007). */
const NAVIGATE_EVENT = 'flakewatch:navigate';

function subscribe(onChange: () => void) {
  window.addEventListener('popstate', onChange);
  window.addEventListener(NAVIGATE_EVENT, onChange);
  return () => {
    window.removeEventListener('popstate', onChange);
    window.removeEventListener(NAVIGATE_EVENT, onChange);
  };
}

function snapshot() {
  return window.location.pathname + window.location.search;
}

/** The current path and query string. Re-renders on every navigation. */
export function useLocation(): { pathname: string; search: string } {
  const current = useSyncExternalStore(subscribe, snapshot);
  const q = current.indexOf('?');
  return q === -1 ? { pathname: current, search: '' } : { pathname: current.slice(0, q), search: current.slice(q + 1) };
}

export function navigate(to: string, options: { replace?: boolean } = {}) {
  if (to === snapshot()) {
    return;
  }
  if (options.replace) {
    window.history.replaceState(null, '', to);
  } else {
    window.history.pushState(null, '', to);
    window.scrollTo?.(0, 0);
  }
  window.dispatchEvent(new Event(NAVIGATE_EVENT));
}
