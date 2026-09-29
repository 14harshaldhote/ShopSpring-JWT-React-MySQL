import { useState } from 'react';

const PALETTE = [
  ['#eef2ff', '#4338ca'],
  ['#ecfdf5', '#047857'],
  ['#fff7ed', '#c2410c'],
  ['#fdf2f8', '#be185d'],
  ['#f0f9ff', '#0369a1'],
  ['#fefce8', '#a16207'],
];

function initials(title = '') {
  const words = title
    .replace(/[^\p{L}\p{N} ]/gu, ' ')
    .split(/\s+/)
    .filter(Boolean);
  return (
    words
      .slice(0, 2)
      .map((w) => w[0])
      .join('') || '?'
  ).toUpperCase();
}

function colorsFor(title = '') {
  let hash = 0;
  for (const ch of title) hash = (hash * 31 + ch.codePointAt(0)) >>> 0;
  return PALETTE[hash % PALETTE.length];
}

/** Local stand-in shown when a product image is missing or can't be loaded. */
export function ProductPlaceholder({ title, className = '' }) {
  const [background, foreground] = colorsFor(title);
  return (
    <svg
      className={className}
      viewBox="0 0 400 500"
      preserveAspectRatio="xMidYMid slice"
      role="img"
      aria-label={title ? `${title} (image unavailable)` : 'Image unavailable'}
      data-testid="product-image-placeholder"
    >
      <rect width="400" height="500" fill={background} />
      <g fill="none" stroke={foreground} strokeWidth="10" strokeLinecap="round" strokeLinejoin="round" opacity="0.25">
        <path d="M176 170a24 24 0 1 1 34 22c-6 3-10 8-10 14v10" />
        <path d="M200 216 96 286h208Z" />
      </g>
      <text
        x="200"
        y="380"
        textAnchor="middle"
        fontFamily="system-ui, sans-serif"
        fontSize="84"
        fontWeight="700"
        fill={foreground}
      >
        {initials(title)}
      </text>
    </svg>
  );
}

/**
 * Product photo with a local fallback: the image CDN may be slow or unreachable, and a broken
 * image icon looks like a bug. Only https URLs are used (the backend also enforces this).
 */
export default function ProductImage({ src, title, className = '' }) {
  const [failedSrc, setFailedSrc] = useState(null);
  const usable = typeof src === 'string' && src.startsWith('https://') && failedSrc !== src;
  if (!usable) return <ProductPlaceholder title={title} className={className} />;
  return (
    <img
      src={src}
      alt={title}
      loading="lazy"
      referrerPolicy="no-referrer"
      className={className}
      onError={() => setFailedSrc(src)}
    />
  );
}
