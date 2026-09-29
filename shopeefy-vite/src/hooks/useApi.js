import { useCallback, useEffect, useState } from 'react';
import { api } from '../api/client';

/**
 * Loads `url` (with optional query `params`) and keeps the result. Pass a null url to wait.
 * Returns { data, error, loading, reload, setData }; reload() refetches in the background.
 */
export function useApi(url, params) {
  const key = url ? JSON.stringify([url, params ?? null]) : null;
  const [state, setState] = useState({ key: null, data: undefined, error: null });
  const [version, setVersion] = useState(0);

  useEffect(() => {
    if (key === null) return undefined;
    let active = true;
    const [path, query] = JSON.parse(key);
    api.get(path, { params: query ?? undefined }).then(
      (response) => active && setState({ key, data: response.data, error: null }),
      (error) => active && setState({ key, data: undefined, error }),
    );
    return () => {
      active = false;
    };
  }, [key, version]);

  const reload = useCallback(() => setVersion((v) => v + 1), []);
  const setData = useCallback(
    (update) => setState((s) => ({ ...s, data: typeof update === 'function' ? update(s.data) : update })),
    [],
  );

  const current = key !== null && state.key === key;
  return {
    data: current ? state.data : undefined,
    error: current ? state.error : null,
    loading: key !== null && !current,
    reload,
    setData,
  };
}
