export const PASSWORD_MIN = 10;
export const PASSWORD_MAX = 128;

/** Mirrors the server's checks that can run in the browser; the server stays the authority. */
export function checkPassword(password = '', email = '', names = []) {
  const normalized = password.normalize('NFKC');
  const length = [...normalized].length;
  const lower = normalized.toLowerCase();
  const local = (email || '').toLowerCase().split('@')[0];
  const personal =
    lower.length > 0 &&
    !(local.length >= 4 && lower.includes(local)) &&
    !names.some((name) => name && name.length >= 4 && lower.includes(name.toLowerCase()));
  const result = { length: length >= PASSWORD_MIN && length <= PASSWORD_MAX, personal };
  return { ...result, ok: result.length && result.personal };
}
