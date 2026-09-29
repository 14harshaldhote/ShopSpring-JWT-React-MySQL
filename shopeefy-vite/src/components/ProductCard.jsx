import { Link } from 'react-router';
import Rating from '@mui/material/Rating';
import ProductImage from './ProductImage';
import Price from './Price';

export default function ProductCard({ product, compact = false }) {
  const rating = Number(product.averageRating ?? 0);
  return (
    <Link
      to={`/product/${product.id}`}
      data-testid="product-card"
      data-product-id={product.id}
      className={`group flex flex-col overflow-hidden rounded-xl border border-slate-200 bg-white transition hover:-translate-y-0.5 hover:shadow-md ${compact ? 'w-48 shrink-0 sm:w-56' : ''}`}
    >
      <div className="relative aspect-[4/5] overflow-hidden bg-slate-100">
        <ProductImage
          src={product.imageUrl}
          title={product.title}
          className="h-full w-full object-cover object-top transition duration-300 group-hover:scale-105"
        />
        {product.quantity === 0 && (
          <span className="absolute left-2 top-2 rounded-full bg-slate-900/80 px-2 py-0.5 text-xs font-medium text-white">
            Out of stock
          </span>
        )}
      </div>
      <div className="flex flex-1 flex-col gap-1 p-3">
        <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">{product.brand}</p>
        <p className="line-clamp-2 text-sm text-slate-800">{product.title}</p>
        {product.ratingCount > 0 && (
          <div className="flex items-center gap-1 text-xs text-slate-500">
            <Rating value={rating} precision={0.1} size="small" readOnly />
            <span>({product.ratingCount})</span>
          </div>
        )}
        <div className="mt-auto pt-1">
          <Price
            price={product.price}
            discountedPrice={product.discountedPrice}
            discountPercent={product.discountPercent}
          />
        </div>
      </div>
    </Link>
  );
}
