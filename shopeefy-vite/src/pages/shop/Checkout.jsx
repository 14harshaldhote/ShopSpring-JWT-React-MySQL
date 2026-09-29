import { useEffect, useState } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { Link, useNavigate } from 'react-router';
import Button from '@mui/material/Button';
import TextField from '@mui/material/TextField';
import Radio from '@mui/material/Radio';
import Paper from '@mui/material/Paper';
import { api } from '../../api/client';
import { fieldErrors } from '../../api/errors';
import { EmptyState, ErrorAlert, Loading, PageTitle } from '../../components/Feedback';
import { AddressBlock, CheckoutSteps } from '../../components/OrderParts';
import { useApi } from '../../hooks/useApi';
import { fetchCart, selectCart, selectCartLoaded } from '../../store/cartSlice';
import { selectUser } from '../../store/authSlice';
import { CartTotals } from './Cart';

// Same allow-list rules as the server, so mistakes show up before submitting. [OWASP A05:2025]
const FIELDS = [
  {
    name: 'firstName',
    label: 'First name',
    autoComplete: 'given-name',
    max: 60,
    pattern: /^[\p{L} .'-]+$/u,
    hint: 'Letters only',
  },
  {
    name: 'lastName',
    label: 'Last name',
    autoComplete: 'family-name',
    max: 60,
    pattern: /^[\p{L} .'-]+$/u,
    hint: 'Letters only',
  },
  { name: 'streetAddress', label: 'Street address', autoComplete: 'street-address', max: 255, wide: true },
  {
    name: 'city',
    label: 'City',
    autoComplete: 'address-level2',
    max: 80,
    pattern: /^[\p{L} .'-]+$/u,
    hint: 'Letters only',
  },
  {
    name: 'state',
    label: 'State',
    autoComplete: 'address-level1',
    max: 80,
    pattern: /^[\p{L} .'-]+$/u,
    hint: 'Letters only',
  },
  {
    name: 'zipCode',
    label: 'PIN code',
    autoComplete: 'postal-code',
    max: 12,
    pattern: /^[0-9A-Za-z -]+$/,
    hint: 'Digits and letters only',
  },
  {
    name: 'mobile',
    label: 'Mobile number',
    autoComplete: 'tel',
    max: 20,
    pattern: /^\+?[0-9 ]{7,19}$/,
    hint: '7 to 19 digits, optional +',
  },
];

const EMPTY = Object.fromEntries(FIELDS.map((f) => [f.name, '']));

function AddressForm({ user, onSubmit, busy, error }) {
  const [form, setForm] = useState(() => ({
    ...EMPTY,
    firstName: user?.firstName ?? '',
    lastName: user?.lastName ?? '',
  }));
  const server = fieldErrors(error);
  const localError = (field) => {
    const value = form[field.name].trim();
    return value && field.pattern && !field.pattern.test(value) ? field.hint : null;
  };
  const complete = FIELDS.every((f) => form[f.name].trim() && !localError(f));

  const submit = (event) => {
    event.preventDefault();
    if (!complete) return;
    onSubmit(Object.fromEntries(Object.entries(form).map(([k, v]) => [k, v.trim()])));
  };

  return (
    <form onSubmit={submit} className="grid gap-4 sm:grid-cols-2" noValidate data-testid="address-form">
      {FIELDS.map((field) => {
        const message = localError(field) || server[field.name];
        return (
          <TextField
            key={field.name}
            label={field.label}
            value={form[field.name]}
            onChange={(e) => setForm((f) => ({ ...f, [field.name]: e.target.value }))}
            autoComplete={field.autoComplete}
            required
            fullWidth
            error={Boolean(message)}
            helperText={message || ' '}
            sx={field.wide ? { gridColumn: '1 / -1' } : undefined}
            slotProps={{ htmlInput: { maxLength: field.max, 'data-testid': `address-${field.name}` } }}
          />
        );
      })}
      <div className="sm:col-span-2">
        <Button
          type="submit"
          variant="contained"
          size="large"
          disabled={!complete || busy}
          data-testid="address-submit"
        >
          {busy ? 'Placing order…' : 'Deliver here and continue'}
        </Button>
      </div>
    </form>
  );
}

export default function Checkout() {
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const user = useSelector(selectUser);
  const cart = useSelector(selectCart);
  const cartLoaded = useSelector(selectCartLoaded);
  const addresses = useApi('/api/users/me/addresses');
  const [choice, setChoice] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    dispatch(fetchCart());
  }, [dispatch]);

  const saved = addresses.data ?? [];
  const selected = choice ?? (saved.length > 0 ? saved[0].id : 'new');

  const placeOrder = async (body) => {
    setBusy(true);
    setError(null);
    try {
      const { data: order } = await api.post('/api/orders/', body);
      dispatch(fetchCart());
      navigate(`/checkout/${order.id}`, { replace: true });
    } catch (err) {
      setError(err);
      setBusy(false);
    }
  };

  if (!cartLoaded || addresses.loading) return <Loading />;
  if (!cart?.cartItems?.length) {
    return (
      <EmptyState
        title="Nothing to check out"
        action={
          <Button component={Link} to="/products" variant="contained">
            Browse products
          </Button>
        }
      >
        Your cart is empty.
      </EmptyState>
    );
  }

  return (
    <div>
      <PageTitle>Checkout</PageTitle>
      <CheckoutSteps active={0} />
      <div className="grid gap-6 lg:grid-cols-[1fr_340px]">
        <Paper sx={{ p: 3 }}>
          <h2 className="mb-4 text-lg font-semibold">Delivery address</h2>
          <ErrorAlert error={error} sx={{ mb: 2 }} data-testid="checkout-error" />
          {saved.length > 0 && (
            <div className="mb-5 space-y-3" data-testid="saved-addresses">
              {saved.map((address) => (
                <label
                  key={address.id}
                  className={`flex cursor-pointer gap-3 rounded-lg border p-3 ${selected === address.id ? 'border-indigo-500 bg-indigo-50/50' : 'border-slate-200'}`}
                  data-testid="saved-address"
                >
                  <Radio checked={selected === address.id} onChange={() => setChoice(address.id)} size="small" />
                  <div className="flex-1">
                    <AddressBlock address={address} />
                    {selected === address.id && (
                      <Button
                        variant="contained"
                        sx={{ mt: 2 }}
                        disabled={busy}
                        onClick={() => placeOrder({ addressId: address.id })}
                        data-testid="address-use-saved"
                      >
                        {busy ? 'Placing order…' : 'Deliver here and continue'}
                      </Button>
                    )}
                  </div>
                </label>
              ))}
              <label
                className={`flex cursor-pointer items-center gap-3 rounded-lg border p-3 ${selected === 'new' ? 'border-indigo-500 bg-indigo-50/50' : 'border-slate-200'}`}
              >
                <Radio
                  checked={selected === 'new'}
                  onChange={() => setChoice('new')}
                  size="small"
                  data-testid="address-new"
                />
                <span className="text-sm font-medium">Use a new address</span>
              </label>
            </div>
          )}
          {selected === 'new' && <AddressForm user={user} onSubmit={placeOrder} busy={busy} error={error} />}
        </Paper>
        <CartTotals cart={cart}>
          <p className="text-xs text-slate-500">Placing the order reserves the stock for 30 minutes while you pay.</p>
        </CartTotals>
      </div>
    </div>
  );
}
