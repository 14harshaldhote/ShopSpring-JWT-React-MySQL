/**
 * Turns an axios error carrying an RFC 9457 problem response into text for the user. Only the
 * server's safe `detail` is shown, never raw error objects or stack traces.       [OWASP A10:2025]
 */

export function statusOf(error) {
  return error?.response?.status ?? null;
}

export function retryAfterSeconds(error) {
  const value = error?.response?.headers?.['retry-after'];
  const seconds = Number.parseInt(value, 10);
  return Number.isFinite(seconds) && seconds > 0 ? seconds : null;
}

function waitText(seconds) {
  if (!seconds) return 'a moment';
  if (seconds < 90) return `${seconds} second${seconds === 1 ? '' : 's'}`;
  return `about ${Math.ceil(seconds / 60)} minutes`;
}

export function errorMessage(error, fallback = 'Something went wrong. Please try again.') {
  if (!error) return null;
  if (typeof error === 'string') return error;
  if (!error.response) {
    if (!error.isAxiosError) return error.message || fallback;
    return error.code === 'ECONNABORTED'
      ? 'The server took too long to answer. Please try again.'
      : "Can't reach the server. Check your connection and try again.";
  }
  const { status, data } = error.response;
  if (status === 429) {
    return `Too many requests. Please wait ${waitText(retryAfterSeconds(error))} and try again.`;
  }
  if (data && typeof data.detail === 'string' && data.detail) {
    return data.detail;
  }
  if (status === 401) return 'Please sign in to continue.';
  if (status === 403) return "You don't have permission to do that.";
  if (status === 404) return 'Not found.';
  return fallback;
}

/** Per-field validation messages from a 400 problem response ({ errors: { field: message } }). */
export function fieldErrors(error) {
  const errors = error?.response?.data?.errors;
  return errors && typeof errors === 'object' ? errors : {};
}
