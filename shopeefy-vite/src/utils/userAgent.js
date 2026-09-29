const BROWSERS = [
  [/Edg\/(\d+)/, 'Edge'],
  [/OPR\/(\d+)/, 'Opera'],
  [/HeadlessChrome\/(\d+)/, 'Headless Chrome'],
  [/Chrome\/(\d+)/, 'Chrome'],
  [/Firefox\/(\d+)/, 'Firefox'],
  [/Version\/(\d+).*Safari/, 'Safari'],
];

const SYSTEMS = [
  [/Windows NT/, 'Windows'],
  [/iPhone|iPad/, 'iOS'],
  [/Mac OS X/, 'macOS'],
  [/Android/, 'Android'],
  [/CrOS/, 'ChromeOS'],
  [/Linux/, 'Linux'],
];

/** A short, readable device name such as "Chrome 141 on macOS". */
export function describeUserAgent(userAgent) {
  if (!userAgent) return 'Unknown device';
  let browser = null;
  for (const [pattern, name] of BROWSERS) {
    const match = userAgent.match(pattern);
    if (match) {
      browser = `${name} ${match[1]}`;
      break;
    }
  }
  const system = SYSTEMS.find(([pattern]) => pattern.test(userAgent))?.[1];
  if (!browser) return userAgent.length > 60 ? `${userAgent.slice(0, 57)}...` : userAgent;
  return system ? `${browser} on ${system}` : browser;
}

const METHODS = {
  'PASSWORD+EMAIL_OTP': 'Password + email code',
  OAUTH2_GOOGLE: 'Google',
  OAUTH2_GITHUB: 'GitHub',
  OAUTH2_DEVIDP: 'Dev identity provider',
};

export function describeAuthMethod(method) {
  if (!method) return 'Unknown';
  return METHODS[method] ?? method.replaceAll('_', ' ');
}
