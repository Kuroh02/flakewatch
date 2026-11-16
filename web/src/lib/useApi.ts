import { useCallback, useEffect, useState } from 'react';
import { ApiError, apiGet } from './api';

export type ApiState<T> =
  | { status: 'loading' }
  | { status: 'error'; error: ApiError }
  | { status: 'ready'; data: T };

/** Loads one API path. Changing the path reloads; the previous request is cancelled. */
export function useApi<T>(path: string | null): ApiState<T> & { reload: () => void } {
  const [state, setState] = useState<ApiState<T>>({ status: 'loading' });
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    if (path === null) {
      return;
    }
    const controller = new AbortController();
    setState({ status: 'loading' });
    apiGet<T>(path, controller.signal)
      .then((data) => setState({ status: 'ready', data }))
      .catch((e: unknown) => {
        if (controller.signal.aborted) {
          return;
        }
        setState({ status: 'error', error: e instanceof ApiError ? e : new ApiError(0, String(e)) });
      });
    return () => controller.abort();
  }, [path, attempt]);

  const reload = useCallback(() => setAttempt((n) => n + 1), []);
  return { ...state, reload };
}
