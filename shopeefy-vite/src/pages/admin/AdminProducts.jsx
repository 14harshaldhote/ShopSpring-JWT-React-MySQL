import { useState } from 'react';
import { Link } from 'react-router';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import IconButton from '@mui/material/IconButton';
import Paper from '@mui/material/Paper';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TablePagination from '@mui/material/TablePagination';
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import DeleteOutlined from '@mui/icons-material/DeleteOutlined';
import EditOutlined from '@mui/icons-material/EditOutlined';
import Add from '@mui/icons-material/Add';
import { api } from '../../api/client';
import { errorMessage } from '../../api/errors';
import { ErrorAlert, Loading, PageTitle } from '../../components/Feedback';
import ProductImage from '../../components/ProductImage';
import { useApi } from '../../hooks/useApi';
import { categoryName } from '../../config/catalog';
import { formatPrice } from '../../utils/format';

function EditDialog({ product, onClose, onSaved }) {
  const [form, setForm] = useState(() => ({
    description: product.description ?? '',
    price: String(product.price),
    discountedPrice: String(product.discountedPrice),
    sizes: product.sizes.map((s) => ({ name: s.name, quantity: String(s.quantity) })),
  }));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  const price = Number(form.price);
  const discounted = Number(form.discountedPrice);
  const pricesValid =
    Number.isInteger(price) && price >= 1 && Number.isInteger(discounted) && discounted >= 1 && discounted <= price;
  const sizesValid = form.sizes.every((s) => /^\d{1,6}$/.test(s.quantity));
  const valid = pricesValid && sizesValid && form.description.trim().length > 0;

  const save = async () => {
    setBusy(true);
    setError(null);
    try {
      const { data } = await api.put(`/api/admin/products/${product.id}/update`, {
        description: form.description.trim(),
        price,
        discountedPrice: discounted,
        sizes: form.sizes.map((s) => ({ name: s.name, quantity: Number(s.quantity) })),
      });
      onSaved(data);
    } catch (err) {
      setError(err);
      setBusy(false);
    }
  };

  return (
    <Dialog open onClose={() => !busy && onClose()} fullWidth maxWidth="sm">
      <DialogTitle>Edit product #{product.id}</DialogTitle>
      <DialogContent>
        <p className="mb-4 line-clamp-2 text-sm text-slate-600">{product.title}</p>
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField
            label="Price (₹)"
            value={form.price}
            onChange={(e) => setForm((f) => ({ ...f, price: e.target.value.replace(/\D/g, '') }))}
            error={!pricesValid}
            slotProps={{ htmlInput: { inputMode: 'numeric', 'data-testid': 'edit-price' } }}
          />
          <TextField
            label="Discounted price (₹)"
            value={form.discountedPrice}
            onChange={(e) => setForm((f) => ({ ...f, discountedPrice: e.target.value.replace(/\D/g, '') }))}
            error={!pricesValid}
            helperText={!pricesValid ? 'Must be between 1 and the price.' : ' '}
            slotProps={{ htmlInput: { inputMode: 'numeric', 'data-testid': 'edit-discounted-price' } }}
          />
          <TextField
            label="Description"
            value={form.description}
            onChange={(e) => setForm((f) => ({ ...f, description: e.target.value.slice(0, 2000) }))}
            multiline
            minRows={3}
            sx={{ gridColumn: '1 / -1' }}
            slotProps={{ htmlInput: { maxLength: 2000 } }}
          />
        </div>
        <p className="mb-2 mt-4 text-sm font-semibold">Stock per size</p>
        <div className="flex flex-wrap gap-3">
          {form.sizes.map((size, index) => (
            <TextField
              key={size.name}
              label={size.name}
              size="small"
              value={size.quantity}
              onChange={(e) =>
                setForm((f) => ({
                  ...f,
                  sizes: f.sizes.map((s, i) =>
                    i === index ? { ...s, quantity: e.target.value.replace(/\D/g, '') } : s,
                  ),
                }))
              }
              sx={{ width: 90 }}
              slotProps={{ htmlInput: { inputMode: 'numeric', 'data-testid': `edit-size-${size.name}` } }}
            />
          ))}
        </div>
        <ErrorAlert error={error} sx={{ mt: 2 }} />
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={busy} color="inherit">
          Cancel
        </Button>
        <Button onClick={save} disabled={!valid || busy} variant="contained" data-testid="edit-save">
          {busy ? 'Saving…' : 'Save changes'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

export default function AdminProducts() {
  const products = useApi('/api/admin/products/all');
  const [filter, setFilter] = useState('');
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(25);
  const [editing, setEditing] = useState(null);
  const [deleting, setDeleting] = useState(null);
  const [message, setMessage] = useState(null);
  const [busy, setBusy] = useState(false);

  const query = filter.trim().toLowerCase();
  const rows = (products.data ?? []).filter(
    (p) =>
      !query ||
      p.title.toLowerCase().includes(query) ||
      p.brand.toLowerCase().includes(query) ||
      String(p.id) === query,
  );
  const visible = rows.slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage);

  const remove = async () => {
    setBusy(true);
    try {
      await api.delete(`/api/admin/products/${deleting.id}/delete`);
      products.setData((list) => list.filter((p) => p.id !== deleting.id));
      setMessage({ severity: 'success', text: `Product #${deleting.id} was removed from the catalogue.` });
    } catch (err) {
      setMessage({ severity: 'error', text: errorMessage(err) });
    } finally {
      setBusy(false);
      setDeleting(null);
    }
  };

  return (
    <div data-testid="admin-products">
      <PageTitle
        subtitle={products.data ? `${products.data.length} active products` : null}
        action={
          <Button component={Link} to="/admin/products/new" variant="contained" startIcon={<Add />}>
            Add product
          </Button>
        }
      >
        Products
      </PageTitle>
      {message && (
        <Alert
          severity={message.severity}
          onClose={() => setMessage(null)}
          sx={{ mb: 2 }}
          data-testid="admin-products-message"
        >
          {message.text}
        </Alert>
      )}
      <TextField
        size="small"
        placeholder="Filter by title, brand or id"
        value={filter}
        onChange={(e) => {
          setFilter(e.target.value);
          setPage(0);
        }}
        sx={{ mb: 2, width: { xs: '100%', sm: 320 } }}
        slotProps={{ htmlInput: { maxLength: 100, 'data-testid': 'admin-products-filter' } }}
      />
      <ErrorAlert error={products.error} />
      {products.loading && <Loading />}
      {products.data && (
        <Paper>
          <TableContainer>
            <Table size="small" data-testid="admin-products-table">
              <TableHead>
                <TableRow>
                  <TableCell>Product</TableCell>
                  <TableCell>Category</TableCell>
                  <TableCell align="right">Price</TableCell>
                  <TableCell align="right">Sale price</TableCell>
                  <TableCell>Stock</TableCell>
                  <TableCell align="right">Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {visible.map((product) => (
                  <TableRow key={product.id} hover data-testid="admin-product-row" data-product-id={product.id}>
                    <TableCell>
                      <div className="flex items-center gap-3">
                        <div className="h-12 w-10 shrink-0 overflow-hidden rounded bg-slate-100">
                          <ProductImage
                            src={product.imageUrl}
                            title={product.title}
                            className="h-full w-full object-cover object-top"
                          />
                        </div>
                        <div className="min-w-0">
                          <Link to={`/product/${product.id}`} className="line-clamp-1 text-sm hover:underline">
                            {product.title}
                          </Link>
                          <p className="text-xs text-slate-500">
                            #{product.id} · {product.brand}
                          </p>
                        </div>
                      </div>
                    </TableCell>
                    <TableCell sx={{ textTransform: 'capitalize', whiteSpace: 'nowrap' }}>
                      {product.category?.topLevel} · {categoryName(product.category?.name)}
                    </TableCell>
                    <TableCell align="right">{formatPrice(product.price)}</TableCell>
                    <TableCell align="right">{formatPrice(product.discountedPrice)}</TableCell>
                    <TableCell sx={{ whiteSpace: 'nowrap', fontSize: 12 }}>
                      {product.sizes.map((s) => `${s.name}:${s.quantity}`).join('  ')}
                    </TableCell>
                    <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                      <IconButton
                        size="small"
                        onClick={() => setEditing(product)}
                        aria-label="Edit"
                        data-testid="admin-product-edit"
                      >
                        <EditOutlined fontSize="small" />
                      </IconButton>
                      <IconButton
                        size="small"
                        color="error"
                        onClick={() => setDeleting(product)}
                        aria-label="Delete"
                        data-testid="admin-product-delete"
                      >
                        <DeleteOutlined fontSize="small" />
                      </IconButton>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
          <TablePagination
            component="div"
            count={rows.length}
            page={Math.min(page, Math.max(0, Math.ceil(rows.length / rowsPerPage) - 1))}
            onPageChange={(_, p) => setPage(p)}
            rowsPerPage={rowsPerPage}
            onRowsPerPageChange={(e) => {
              setRowsPerPage(Number(e.target.value));
              setPage(0);
            }}
            rowsPerPageOptions={[10, 25, 50]}
          />
        </Paper>
      )}

      {editing && (
        <EditDialog
          product={editing}
          onClose={() => setEditing(null)}
          onSaved={(updated) => {
            products.setData((list) => list.map((p) => (p.id === updated.id ? updated : p)));
            setEditing(null);
            setMessage({ severity: 'success', text: `Product #${updated.id} was updated.` });
          }}
        />
      )}

      <Dialog open={Boolean(deleting)} onClose={() => !busy && setDeleting(null)}>
        <DialogTitle>Remove product #{deleting?.id}?</DialogTitle>
        <DialogContent>
          <p className="text-sm text-slate-600">It disappears from the shop. Past orders keep their record of it.</p>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleting(null)} disabled={busy} color="inherit">
            Keep it
          </Button>
          <Button
            onClick={remove}
            disabled={busy}
            color="error"
            variant="contained"
            data-testid="admin-product-delete-confirm"
          >
            Remove
          </Button>
        </DialogActions>
      </Dialog>
    </div>
  );
}
