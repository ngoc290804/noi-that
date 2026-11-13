import { useEffect, useState } from 'react';
import { throwIfAborted } from '../utils/abort';

// Key changes hide previous results immediately; cleanup cancels stale requests.
export function useCatalogResource<T>(key: string, load: (signal: AbortSignal) => Promise<T>) {
  const [attempt, setAttempt] = useState(0);
  const [state, setState] = useState<{ key: string; attempt: number; data?: T; error?: unknown }>();
  useEffect(() => {
    const controller = new AbortController();
    // Let synchronous cleanup run before starting I/O (including StrictMode replay).
    Promise.resolve().then(() => {
      throwIfAborted(controller.signal);
      return load(controller.signal);
    }).then(
      (data) => { if (!controller.signal.aborted) setState({ key, attempt, data }); },
      (error: unknown) => { if (!controller.signal.aborted) setState({ key, attempt, error }); },
    );
    return () => controller.abort();
  }, [key, attempt, load]);
  const current = state?.key === key && state.attempt === attempt ? state : undefined;
  return { data: current?.data, previousData: state?.data, error: current?.error, retry: () => setAttempt((value) => value + 1) };
}
