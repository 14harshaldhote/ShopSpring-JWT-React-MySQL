import { useState } from 'react';
import { Link } from 'react-router';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Paper from '@mui/material/Paper';
import TextField from '@mui/material/TextField';
import { api } from '../../api/client';
import { fieldErrors } from '../../api/errors';
import { ErrorAlert, PageTitle } from '../../components/Feedback';
import ProductImage from '../../components/ProductImage';
import { DEPARTMENTS } from '../../config/catalog';

const EMPTY = {
  title: '',
  brand: '',
  description: '',
  color: '',
  imageUrl: '',
  price: '',
  discountedPrice: '',
  department: 'Women',
  category: 'women_dress',
  sizes: { S: '10', M: '10', L: '10' },
};

export default function CreateProduct() {
  const [form, setForm] = useState(EMPTY);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [created, setCreated] = useState(null);

  const set =
    (field, transform = (v) => v) =>
    (event) =>
      setForm((f) => ({ ...f, [field]: transform(event.target.value) }));
  const digits = (v) => v.replace(/\D/g, '').slice(0, 8);
  const server = fieldErrors(error);
  const department = DEPARTMENTS.find((d) => d.name === form.department) ?? DEPARTMENTS[0];

  const price = Number(form.price);
  const discounted = Number(form.discountedPrice);
  const checks = {
    imageUrl: form.imageUrl && !/^https:\/\/[^\s/@]+\/\S*$/.test(form.imageUrl) ? 'Use an https:// image URL.' : null,
    color: form.color && !/^[A-Za-z ]{1,40}$/.test(form.color) ? 'Letters and spaces only.' : null,
    discountedPrice: form.discountedPrice && discounted > price ? "Can't be higher than the price." : null,
  };
  const valid =
    form.title.trim() &&
    form.brand.trim() &&
    form.description.trim() &&
    form.color.trim() &&
    form.imageUrl &&
    price >= 1 &&
    discounted >= 1 &&
    !Object.values(checks).some(Boolean) &&
    Object.values(form.sizes).some((q) => q !== '');

  const submit = async (event) => {
    event.preventDefault();
    if (!valid) return;
    setBusy(true);
    setError(null);
    setCreated(null);
    try {
      const { data } = await api.post('/api/admin/products/', [
        {
          title: form.title.trim(),
          brand: form.brand.trim(),
          description: form.description.trim(),
          color: form.color.trim(),
          imageUrl: form.imageUrl.trim(),
          price,
          discountedPrice: discounted,
          size: Object.entries(form.sizes)
            .filter(([, q]) => q !== '')
            .map(([name, q]) => ({ name, quantity: Number(q) })),
          topLevelCategory: form.department,
          secondLevelCategory: 'Clothing',
          thirdLevelCategory: form.category,
        },
      ]);
      setCreated(data[0]);
      setForm(EMPTY);
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  };

  const text = (field, label, extra = {}) => (
    <TextField
      label={label}
      value={form[field]}
      onChange={set(field, extra.transform)}
      required
      fullWidth
      error={Boolean(checks[field] || server[field])}
      helperText={checks[field] || server[field] || extra.hint || ' '}
      multiline={extra.multiline}
      minRows={extra.multiline ? 3 : undefined}
      sx={extra.wide ? { gridColumn: '1 / -1' } : undefined}
      slotProps={{ htmlInput: { maxLength: extra.max, inputMode: extra.inputMode, 'data-testid': `product-${field}` } }}
    />
  );

  return (
    <div data-testid="admin-create-product">
      <PageTitle subtitle="Every field is validated again by the server.">Add a product</PageTitle>
      {created && (
        <Alert
          severity="success"
          sx={{ mb: 3 }}
          data-testid="product-created"
          action={
            <Button component={Link} to={`/product/${created.id}`} color="inherit" size="small">
              View
            </Button>
          }
        >
          Product #{created.id} “{created.title}” was created.
        </Alert>
      )}
      <div className="grid gap-6 xl:grid-cols-[1fr_260px]">
        <Paper component="form" onSubmit={submit} sx={{ p: 3 }} noValidate>
          <div className="grid gap-x-4 gap-y-1 sm:grid-cols-2">
            {text('title', 'Title', { max: 200, wide: true })}
            {text('brand', 'Brand', { max: 80 })}
            {text('color', 'Colour', { max: 40, hint: 'e.g. black, dark blue' })}
            {text('description', 'Description', { max: 2000, multiline: true, wide: true })}
            {text('imageUrl', 'Image URL', { max: 500, wide: true, hint: 'https only' })}
            {text('price', 'Price (₹)', { transform: digits, inputMode: 'numeric' })}
            {text('discountedPrice', 'Sale price (₹)', { transform: digits, inputMode: 'numeric' })}
            <TextField
              select
              label="Department"
              value={form.department}
              onChange={(e) => {
                const next = DEPARTMENTS.find((d) => d.name === e.target.value);
                setForm((f) => ({ ...f, department: next.name, category: next.categories[0].id }));
              }}
              helperText=" "
              slotProps={{ select: { native: true }, htmlInput: { 'data-testid': 'product-department' } }}
            >
              {DEPARTMENTS.map((d) => (
                <option key={d.id} value={d.name}>
                  {d.name}
                </option>
              ))}
            </TextField>
            <TextField
              select
              label="Category"
              value={form.category}
              onChange={set('category')}
              helperText=" "
              slotProps={{ select: { native: true }, htmlInput: { 'data-testid': 'product-category' } }}
            >
              {department.categories.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </TextField>
          </div>
          <p className="mb-2 mt-2 text-sm font-semibold">Stock per size</p>
          <div className="flex gap-3">
            {Object.keys(form.sizes).map((name) => (
              <TextField
                key={name}
                label={name}
                size="small"
                value={form.sizes[name]}
                onChange={(e) =>
                  setForm((f) => ({
                    ...f,
                    sizes: { ...f.sizes, [name]: e.target.value.replace(/\D/g, '').slice(0, 6) },
                  }))
                }
                sx={{ width: 90 }}
                slotProps={{ htmlInput: { inputMode: 'numeric', 'data-testid': `product-size-${name}` } }}
              />
            ))}
          </div>
          <ErrorAlert error={error} sx={{ mt: 3 }} />
          <Button
            type="submit"
            variant="contained"
            size="large"
            sx={{ mt: 3 }}
            disabled={!valid || busy}
            data-testid="product-submit"
          >
            {busy ? 'Creating…' : 'Create product'}
          </Button>
        </Paper>
        <div>
          <p className="mb-2 text-sm font-semibold text-slate-600">Preview</p>
          <div className="overflow-hidden rounded-xl border border-slate-200 bg-white">
            <ProductImage
              src={checks.imageUrl ? null : form.imageUrl}
              title={form.title || 'New product'}
              className="aspect-[4/5] w-full object-cover object-top"
            />
            <div className="p-3 text-sm">
              <p className="text-xs font-semibold uppercase text-slate-500">{form.brand || 'Brand'}</p>
              <p className="line-clamp-2">{form.title || 'Product title'}</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
