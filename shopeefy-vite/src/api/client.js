import axios from 'axios';

// [OWASP A07:2025] The access token is kept only in this module variable, never in localStorage or
// sessionStorage: an XSS payload can't steal what isn't in storage, and it disappears with the tab.
// The long-lived refresh token is an HttpOnly cookie that JavaScript can't read at all.
let accessToken = null;
const sessionListeners = new Set();

export const api = axios.create({
  baseURL: '',
  timeout: 20000,
  // [OWASP A01:2025] The backend refuses cookie (/auth) calls without this header: HTML forms and
  // other sites can't set it, so it blocks cross-site request forgery on the refresh cookie.
  headers: { 'X-Requested-With': 'XMLHttpRequest' },
});

const isAuthCall = (url = '') => url.startsWith('/auth/');
const isApiCall = (url = '') => url.startsWith('/api/');

api.interceptors.request.use((config) => {
  if (isAuthCall(config.url)) {
    config.withCredentials = true;
  } else if (accessToken && isApiCall(config.url)) {
    // The bearer token only goes to our own API, never to /auth or anywhere else.
    config.headers.Authorization = `Bearer ${accessToken}`;
  }
  return config;
});

api.interceptors.response.use(undefined, async (error) => {
  const { config, response } = error;
  if (response?.status !== 401 || !config || !isApiCall(config.url) || config.retriedAfterRefresh) {
    throw error;
  }
  // The access token expired or its session was revoked: refresh once (shared by every request
  // that failed at the same time), then replay the original request a single time.
  config.retriedAfterRefresh = true;
  try {
    await refreshSession();
  } catch {
    endSession();
    throw error;
  }
  return api(config);
});

/** Subscribe to sign-in / sign-out. The listener gets the user, or null when signed out. */
export function onSessionChange(listener) {
  sessionListeners.add(listener);
  return () => sessionListeners.delete(listener);
}

function emit(user) {
  sessionListeners.forEach((listener) => listener(user));
}

/** Stores the result of a successful sign-in or refresh (an AuthResponse). */
export function startSession(authResponse) {
  accessToken = authResponse.accessToken;
  emit(authResponse.user);
  return authResponse.user;
}

/** Forgets the session locally (the server side is handled by logout or has already ended). */
export function endSession() {
  const wasSignedIn = accessToken !== null;
  accessToken = null;
  if (wasSignedIn) {
    emit(null);
  }
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function postRefresh() {
  const { data } = await api.post('/auth/refresh');
  return startSession(data);
}

let refreshInFlight = null;

/**
 * Exchanges the refresh cookie for a new access token. Single-flight: concurrent callers share
 * one request, because each refresh rotates the cookie and a second use of the old one would look
 * like token theft to the server.
 */
export function refreshSession() {
  refreshInFlight ??= (async () => {
    try {
      return await postRefresh();
    } catch (error) {
      // 409: another tab refreshed with the same cookie a moment ago. The browser already holds
      // the rotated cookie, so wait briefly and try once more.
      if (error.response?.status === 409) {
        await sleep(300);
        return postRefresh();
      }
      throw error;
    }
  })().finally(() => {
    refreshInFlight = null;
  });
  return refreshInFlight;
}

let restored = null;

/** Called once when the app starts: restores the session from the refresh cookie, if any. Resolves to the user or null. */
export function restoreSession() {
  restored ??= refreshSession().catch(() => null);
  return restored;
}

export async function logout() {
  try {
    await api.post('/auth/logout');
  } finally {
    endSession();
  }
}
