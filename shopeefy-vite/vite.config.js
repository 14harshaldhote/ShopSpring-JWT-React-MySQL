import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

const BACKEND_URL = process.env.BACKEND_URL ?? 'http://localhost:8080';

// Everything the Spring Boot backend serves. The browser only ever talks to this origin, so the
// refresh cookie stays first-party and no CORS is involved. changeOrigin stays false so the backend
// sees the browser's own Host/Origin (its CSRF check compares them).
const target = { target: BACKEND_URL, changeOrigin: false };
const proxy = {
  '/api': target,
  '/auth': target,
  // /oauth2/callback is a page of this app; everything else under /oauth2 belongs to the backend.
  '^/oauth2/(?!callback(?:[/?#]|$))': target,
  '/login/oauth2': target,
  '/.well-known': target,
  '/dev': target,
};

// The policy the production build is served with; `npm run preview` applies it so the bundle can
// be checked locally. (The dev server can't use it: Vite's HMR client needs an inline script.)
const CONTENT_SECURITY_POLICY = [
  "default-src 'self'",
  "script-src 'self' https://checkout.razorpay.com",
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' https: data:",
  "connect-src 'self'",
  'frame-src https://api.razorpay.com https://checkout.razorpay.com',
  "frame-ancestors 'none'",
  "base-uri 'self'",
  "form-action 'self'",
].join('; ');

// Long-lived vendor chunks, so a code change doesn't invalidate the whole library download.
function vendorChunk(moduleId) {
  if (!moduleId.includes('node_modules')) return null;
  if (/node_modules[\\/](@mui|@emotion)[\\/]/.test(moduleId)) return 'mui';
  return 'vendor';
}

export default defineConfig({
  plugins: [react(), tailwindcss()],
  build: {
    rolldownOptions: {
      output: { codeSplitting: { groups: [{ name: vendorChunk }] } },
    },
  },
  server: {
    port: 5173,
    strictPort: true,
    proxy,
  },
  preview: {
    port: 4173,
    strictPort: true,
    proxy,
    headers: {
      'Content-Security-Policy': CONTENT_SECURITY_POLICY,
      'X-Content-Type-Options': 'nosniff',
      'Referrer-Policy': 'strict-origin-when-cross-origin',
    },
  },
});
