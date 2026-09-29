import { useState } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { Link, useNavigate, useParams } from 'react-router';
import Button from '@mui/material/Button';
import Alert from '@mui/material/Alert';
import Rating from '@mui/material/Rating';
import TextField from '@mui/material/TextField';
import Breadcrumbs from '@mui/material/Breadcrumbs';
import AddShoppingCart from '@mui/icons-material/AddShoppingCart';
import ProductImage from '../../components/ProductImage';
import Price from '../../components/Price';
import { ErrorAlert, Loading } from '../../components/Feedback';
import { useApi } from '../../hooks/useApi';
import { selectUser } from '../../store/authSlice';
import { cartApi, cartReceived } from '../../store/cartSlice';
import { categoryName } from '../../config/catalog';
import Reviews from './Reviews';

const SIZE_ORDER = ['XS', 'S', 'M', 'L', 'XL', 'XXL'];
const sizeRank = (name) => (SIZE_ORDER.includes(name) ? SIZE_ORDER.indexOf(name) : SIZE_ORDER.length);

function SizePicker({ sizes, value, onChange }) {
  const ordered = [...sizes].sort((a, b) => sizeRank(a.name) - sizeRank(b.name) || a.name.localeCompare(b.name));
  return (
    <div className="flex flex-wrap gap-2" role="radiogroup" aria-label="Size">
      {ordered.map((size) => {
        const soldOut = size.quantity <= 0;
        const selected = value === size.name;
        return (
          <button
            key={size.name}
            type="button"
            role="radio"
            aria-checked={selected}
            disabled={soldOut}
            onClick={() => onChange(size.name)}
            data-testid={`size-${size.name}`}
            className={`flex min-w-16 flex-col items-center rounded-lg border px-3 py-2 text-sm transition ${
              selected
                ? 'border-indigo-600 bg-indigo-50 text-indigo-700 ring-1 ring-indigo-600'
                : 'border-slate-300 bg-white hover:border-slate-500'
            } disabled:cursor-not-allowed disabled:border-slate-200 disabled:bg-slate-50 disabled:text-slate-400`}
          >
            <span className="font-semibold">{size.name}</span>
            <span className="text-[11px]">
              {soldOut ? 'Sold out' : size.quantity <= 5 ? `Only ${size.quantity} left` : `${size.quantity} in stock`}
            </span>
          </button>
        );
      })}
    </div>
  );
}

function BuyBox({ product }) {
  const user = useSelector(selectUser);
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const [size, setSize] = useState(null);
  const [quantity, setQuantity] = useState(1);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [added, setAdded] = useState(false);

  const stock = product.sizes.find((s) => s.name === size)?.quantity ?? 0;
  const maxQuantity = Math.max(1, Math.min(10, stock || 10));

  const add = async () => {
    if (!user) {
      navigate('/login', { state: { from: `/product/${product.id}` } });
      return;
    }
    setBusy(true);
    setError(null);
    setAdded(false);
    try {
      const { data } = await cartApi.add({ productId: product.id, size, quantity: Math.min(quantity, maxQuantity) });
      dispatch(cartReceived(data));
      setAdded(true);
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="space-y-4">
      <div>
        <p className="mb-2 text-sm font-semibold text-slate-800">Choose a size</p>
        <SizePicker
          sizes={product.sizes}
          value={size}
          onChange={(value) => {
            setSize(value);
            setAdded(false);
          }}
        />
      </div>
      <div className="flex flex-wrap items-center gap-3">
        <TextField
          select
          size="small"
          label="Quantity"
          value={Math.min(quantity, maxQuantity)}
          onChange={(e) => setQuantity(Number(e.target.value))}
          slotProps={{ select: { native: true }, htmlInput: { 'data-testid': 'quantity-select' } }}
          sx={{ width: 110 }}
        >
          {Array.from({ length: maxQuantity }, (_, i) => i + 1).map((n) => (
            <option key={n} value={n}>
              {n}
            </option>
          ))}
        </TextField>
        <Button
          variant="contained"
          size="large"
          startIcon={<AddShoppingCart />}
          disabled={!size || busy || product.quantity === 0}
          onClick={add}
          data-testid="add-to-cart"
        >
          {product.quantity === 0 ? 'Out of stock' : busy ? 'Adding…' : size ? 'Add to cart' : 'Select a size'}
        </Button>
      </div>
      <ErrorAlert error={error} data-testid="add-to-cart-error" />
      {added && (
        <Alert
          severity="success"
          data-testid="add-to-cart-success"
          action={
            <Button component={Link} to="/cart" color="inherit" size="small" data-testid="go-to-cart">
              View cart
            </Button>
          }
        >
          Added to your cart.
        </Alert>
      )}
    </div>
  );
}

export default function ProductDetails() {
  const { productId } = useParams();
  const id = /^\d{1,12}$/.test(productId ?? '') ? productId : null;
  const { data: product, error, loading } = useApi(id ? `/api/products/id/${id}` : null);

  if (!id) return <Alert severity="warning">That product link isn't valid.</Alert>;
  if (loading) return <Loading />;
  if (error) return <ErrorAlert error={error} />;
  if (!product) return null;

  const rating = Number(product.averageRating ?? 0);

  return (
    <div className="space-y-10" data-testid="product-details" data-product-id={product.id}>
      <Breadcrumbs sx={{ fontSize: 14 }}>
        <Link to="/" className="hover:underline">
          Home
        </Link>
        {product.category && (
          <Link to={`/products/${product.category.name}`} className="capitalize hover:underline">
            {product.category.topLevel} · {categoryName(product.category.name)}
          </Link>
        )}
        <span className="line-clamp-1 text-slate-500">{product.title}</span>
      </Breadcrumbs>

      <div className="grid gap-8 md:grid-cols-2">
        <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white">
          <ProductImage
            src={product.imageUrl}
            title={product.title}
            className="aspect-[4/5] w-full object-cover object-top"
          />
        </div>
        <div className="space-y-5">
          <div>
            <p className="text-sm font-semibold uppercase tracking-wide text-slate-500">{product.brand}</p>
            <h1 className="mt-1 text-2xl font-bold text-slate-900" data-testid="product-title">
              {product.title}
            </h1>
            <a href="#reviews" className="mt-2 flex items-center gap-2 text-sm text-slate-500">
              <Rating value={rating} precision={0.1} readOnly size="small" />
              {product.ratingCount > 0
                ? `${rating.toFixed(1)} · ${product.ratingCount} ratings · ${product.reviewCount} reviews`
                : 'No ratings yet'}
            </a>
          </div>
          <Price
            price={product.price}
            discountedPrice={product.discountedPrice}
            discountPercent={product.discountPercent}
            size="lg"
          />
          <p className="text-sm text-slate-600">
            Colour: <span className="font-medium capitalize text-slate-800">{product.color}</span>
          </p>
          <BuyBox product={product} />
          <div className="border-t border-slate-200 pt-5">
            <h2 className="mb-2 font-semibold">Description</h2>
            <p className="whitespace-pre-line text-sm leading-relaxed text-slate-600">{product.description}</p>
          </div>
        </div>
      </div>

      <Reviews productId={product.id} />
    </div>
  );
}
