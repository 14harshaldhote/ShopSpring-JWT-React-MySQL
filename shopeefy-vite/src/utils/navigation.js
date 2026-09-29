/** Only accept in-app paths as a post-login destination (no "//evil.example" or full URLs). [OWASP A01:2025] */
export function safeReturnPath(path) {
  return typeof path === 'string' && path.startsWith('/') && !path.startsWith('//') && !path.startsWith('/\\')
    ? path
    : '/';
}
