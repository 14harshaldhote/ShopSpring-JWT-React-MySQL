import { useEffect, useState } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { Link, useNavigate } from 'react-router';
import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
import TextField from '@mui/material/TextField';
import Alert from '@mui/material/Alert';
import DeleteOutlined from '@mui/icons-material/DeleteOutlined';
import ProductImage from '../../components/ProductImage';
import { EmptyState, ErrorAlert, Loading, PageTitle } from '../../components/Feedback';
import { cartApi, cartReceived, fetchCart, selectCart, selectCartLoaded } from '../../store/cartSlice';
import { formatPrice } from '../../utils/format';

export function CartTotals({ cart, children }) {
  return (
    <div className="space-y-3 rounded-xl border border-slate-200 bg-white p-5" data-testid="cart-totals">
      <h2 className="font-semibold">Price details</h2>
      <dl className="space-y-2 text-sm">
        <div className="flex justify-between">
          <dt className="text-slate-600">
            Price ({cart.totalItem} item{cart.totalItem === 1 ? '' : 's'})
          </dt>
          <dd>{formatPrice(cart.totalPrice)}</dd>
        </div>
        <div className="flex justify-between">
          <dt className="text-slate-600">Discount</dt>
          <dd className="text-emerald-600">−{formatPrice(cart.discount)}</dd>
        </div>
        <div className="flex justify-between">
          <dt className="text-slate-600">Delivery</dt>
          <dd className="text-emerald-600">Free</dd>
        </div>
        <div className="flex justify-between border-t border-slate-200 pt-2 text-base font-bold">
          <dt>Total</dt>
          <dd data-testid="cart-total">{formatPrice(cart.totalDiscountedPrice)}</dd>
        </div>
      </dl>
      {children}
    </div>
  );
}

function CartLine({ item, onChange, busy }) {
  const maxQuantity = Math.max(1, Math.min(10, Math.max(item.quantity, item.inStock ?? 10)));
  const short = item.inStock != null && item.inStock < item.quantity;
  return (
    <li
      className="flex gap-4 rounded-xl border border-slate-200 bg-white p-4"
      data-testid="cart-item"
      data-item-id={item.id}
    >
      <Link
        to={`/product/${item.product.id}`}
        className="w-24 shrink-0 overflow-hidden rounded-lg bg-slate-100 sm:w-28"
      >
        <ProductImage
          src={item.product.imageUrl}
          title={item.product.title}
          className="aspect-[4/5] w-full object-cover object-top"
        />
      </Link>
      <div className="flex min-w-0 flex-1 flex-col gap-1">
        <p className="text-xs font-semibold uppercase text-slate-500">{item.product.brand}</p>
        <Link to={`/product/${item.product.id}`} className="line-clamp-2 text-sm font-medium hover:underline">
          {item.product.title}
        </Link>
        <p className="text-sm text-slate-600">
          Size <span className="font-semibold">{item.size}</span>
          {item.product.color && <span className="capitalize"> · {item.product.color}</span>}
        </p>
        {short && (
          <p className="text-xs font-medium text-amber-700">
            {item.inStock === 0 ? 'This size is now out of stock.' : `Only ${item.inStock} left in this size.`}
          </p>
        )}
        <div className="mt-auto flex flex-wrap items-center justify-between gap-2 pt-2">
          <div className="flex items-center gap-2">
            <TextField
              select
              size="small"
              label="Qty"
              value={item.quantity}
              disabled={busy}
              onChange={(e) => onChange(item, Number(e.target.value))}
              slotProps={{ select: { native: true }, htmlInput: { 'data-testid': 'cart-item-quantity' } }}
              sx={{ width: 84 }}
            >
              {Array.from({ length: maxQuantity }, (_, i) => i + 1).map((n) => (
                <option key={n} value={n}>
                  {n}
                </option>
              ))}
            </TextField>
            <IconButton
              aria-label="Remove item"
              disabled={busy}
              onClick={() => onChange(item, 0)}
              data-testid="cart-item-remove"
            >
              <DeleteOutlined />
            </IconButton>
          </div>
          <div className="text-right">
            <p className="font-semibold">{formatPrice(item.discountedPrice)}</p>
            {item.discountedPrice < item.price && (
              <p className="text-xs text-slate-400 line-through">{formatPrice(item.price)}</p>
            )}
          </div>
        </div>
      </div>
    </li>
  );
}

export default function Cart() {
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const cart = useSelector(selectCart);
  const loaded = useSelector(selectCartLoaded);
  const [busyId, setBusyId] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    dispatch(fetchCart());
  }, [dispatch]);

  const change = async (item, quantity) => {
    setBusyId(item.id);
    setError(null);
    try {
      const { data } = quantity === 0 ? await cartApi.remove(item.id) : await cartApi.setQuantity(item.id, quantity);
      dispatch(cartReceived(data));
    } catch (err) {
      setError(err);
    } finally {
      setBusyId(null);
    }
  };

  if (!loaded) return <Loading label="Loading your cart…" />;
  const items = cart?.cartItems ?? [];
  const unavailable = items.some((i) => i.inStock != null && i.inStock < i.quantity);

  return (
    <div>
      <PageTitle>Your cart</PageTitle>
      {items.length === 0 ? (
        <EmptyState
          title="Your cart is empty"
          action={
            <Button component={Link} to="/products" variant="contained">
              Browse products
            </Button>
          }
        >
          Find something you like and add it here.
        </EmptyState>
      ) : (
        <div className="grid gap-6 lg:grid-cols-[1fr_340px]">
          <div className="space-y-3">
            <ErrorAlert error={error} />
            <ul className="space-y-3" data-testid="cart-items">
              {items.map((item) => (
                <CartLine key={item.id} item={item} onChange={change} busy={busyId === item.id} />
              ))}
            </ul>
          </div>
          <div className="space-y-3">
            <CartTotals cart={cart}>
              {unavailable && (
                <Alert severity="warning">
                  Reduce the quantity of items that are short on stock before you check out.
                </Alert>
              )}
              <Button
                variant="contained"
                size="large"
                fullWidth
                disabled={unavailable}
                onClick={() => navigate('/checkout')}
                data-testid="cart-checkout"
              >
                Check out
              </Button>
            </CartTotals>
            <p className="text-xs text-slate-500">Prices are always recalculated by the server at checkout.</p>
          </div>
        </div>
      )}
    </div>
  );
}
