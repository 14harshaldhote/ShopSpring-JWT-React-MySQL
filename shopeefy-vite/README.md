# ShopSpring web app

React 19, Vite 8, MUI 9, Tailwind CSS 4, Redux Toolkit 2 and React Router 8. The project
overview, architecture and security design are in the [root README](../README.md).

```bash
npm ci
npm run dev       # http://localhost:5173, proxies /api, /auth, /oauth2, /login/oauth2 and /dev to :8080
npm run lint      # ESLint, zero warnings allowed
npm run build     # production bundle in dist/
npm run preview   # serves dist/ with the production Content-Security-Policy
```

## How the app handles security

| What | How | Tag |
|---|---|---|
| Access token | kept in a module variable in `src/api/client.js`, never in `localStorage` or `sessionStorage`, and sent only to `/api/*` | `A07` |
| Refresh token | an HttpOnly cookie the app can't read; `/auth/*` calls send it with `withCredentials` | `A07` |
| Page reload | the app calls `POST /auth/refresh` once at start-up to get a new access token from the cookie | `A07` |
| Expired or revoked token | the first `401` triggers one shared refresh, then the request is replayed once; a failed refresh signs out locally | `A07` |
| Two tabs refreshing at once | the server answers the loser with `409`; the app retries with the cookie the winner already received | `A07` |
| Throttled refresh | a `429` on refresh is not a sign-out: the app waits for `Retry-After` (up to 5 s) and tries once more | `A07` |
| CSRF | every request carries `X-Requested-With: XMLHttpRequest`, which the API requires on cookie endpoints | `A01` |
| OAuth2 | `/oauth2/callback` receives no token in its URL; it calls `/auth/refresh` to pick up the session the API created | `A07` |
| XSS | React escapes all text (reviews included); no `dangerouslySetInnerHTML`; the production CSP has no `'unsafe-inline'` or `'unsafe-eval'` in `script-src` | `A05` |
| Prices | the UI never sends a price; carts, orders and payment amounts come from the API | `A06` |
| Honeytoken | `public/robots.txt` advertises `/api/internal/backup`, which only scanners visit | `A09` |

In Docker, nginx (`nginx/`) serves the build, adds the security headers and proxies the API, so
browser and API share one origin and no CORS is needed.
