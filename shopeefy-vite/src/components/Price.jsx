import { formatPrice } from '../utils/format';

export default function Price({ price, discountedPrice, discountPercent, size = 'md' }) {
  const big = size === 'lg';
  const hasDiscount = discountedPrice != null && discountedPrice < price;
  return (
    <div className="flex flex-wrap items-baseline gap-x-2">
      <span className={big ? 'text-2xl font-bold' : 'font-semibold'}>
        {formatPrice(hasDiscount ? discountedPrice : price)}
      </span>
      {hasDiscount && (
        <>
          <span className={`text-slate-400 line-through ${big ? 'text-base' : 'text-sm'}`}>{formatPrice(price)}</span>
          <span className={`font-semibold text-emerald-600 ${big ? 'text-base' : 'text-sm'}`}>
            {discountPercent ?? Math.round((1 - discountedPrice / price) * 100)}% off
          </span>
        </>
      )}
    </div>
  );
}
