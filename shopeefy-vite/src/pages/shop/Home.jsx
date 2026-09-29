import { Link } from 'react-router';
import Button from '@mui/material/Button';
import ArrowForward from '@mui/icons-material/ArrowForward';
import VerifiedUserOutlined from '@mui/icons-material/VerifiedUserOutlined';
import LockOutlined from '@mui/icons-material/LockOutlined';
import LocalShippingOutlined from '@mui/icons-material/LocalShippingOutlined';
import ProductCard from '../../components/ProductCard';
import { ErrorAlert } from '../../components/Feedback';
import { useApi } from '../../hooks/useApi';
import { CATEGORIES } from '../../config/catalog';

function Section({ category }) {
  const { data, error, loading } = useApi('/api/products', { category: category.id, pageSize: 10, sort: 'newest' });
  const products = data?.content ?? [];
  if (!loading && !error && products.length === 0) return null;
  return (
    <section className="space-y-3" data-testid={`home-section-${category.id}`}>
      <div className="flex items-end justify-between">
        <div>
          <p className="text-xs font-semibold uppercase tracking-wider text-slate-500">{category.department}</p>
          <h2 className="text-xl font-bold text-slate-900">{category.name}</h2>
        </div>
        <Button component={Link} to={`/products/${category.id}`} endIcon={<ArrowForward />} size="small">
          View all{data ? ` (${data.totalElements})` : ''}
        </Button>
      </div>
      <ErrorAlert error={error} />
      <div className="no-scrollbar -mx-4 flex gap-4 overflow-x-auto px-4 pb-2 sm:mx-0 sm:px-0">
        {loading
          ? Array.from({ length: 5 }, (_, i) => (
              <div key={i} className="aspect-[4/5] w-48 shrink-0 animate-pulse rounded-xl bg-slate-200 sm:w-56" />
            ))
          : products.map((product) => <ProductCard key={product.id} product={product} compact />)}
      </div>
    </section>
  );
}

const PROMISES = [
  { icon: LockOutlined, title: 'Two-step sign-in', text: 'Password plus a one-time email code on every login.' },
  { icon: VerifiedUserOutlined, title: 'Verified payments', text: 'Every payment is signature-checked by the server.' },
  { icon: LocalShippingOutlined, title: 'Tracked orders', text: 'Follow each order from placed to delivered.' },
];

export default function Home() {
  return (
    <div className="space-y-10">
      <section className="overflow-hidden rounded-2xl bg-linear-to-br from-indigo-600 via-indigo-500 to-teal-500 px-6 py-12 text-white sm:px-12">
        <p className="text-sm font-semibold uppercase tracking-widest text-indigo-100">New season</p>
        <h1 className="mt-2 max-w-xl text-3xl font-bold sm:text-4xl">Ethnic and everyday wear, delivered safely.</h1>
        <p className="mt-3 max-w-lg text-indigo-100">
          Kurtas, dresses, lengha cholis and denim for women and men, at up to 70% off.
        </p>
        <div className="mt-6 flex flex-wrap gap-3">
          <Button
            component={Link}
            to="/products/women_dress"
            variant="contained"
            color="inherit"
            sx={{ color: 'primary.main' }}
          >
            Shop women
          </Button>
          <Button component={Link} to="/products/mens_kurta" variant="outlined" color="inherit">
            Shop men
          </Button>
        </div>
      </section>

      <section className="grid gap-4 sm:grid-cols-3">
        {PROMISES.map(({ icon: Icon, title, text }) => (
          <div key={title} className="flex gap-3 rounded-xl border border-slate-200 bg-white p-4">
            <Icon className="text-indigo-600" />
            <div>
              <p className="font-semibold">{title}</p>
              <p className="text-sm text-slate-500">{text}</p>
            </div>
          </div>
        ))}
      </section>

      {CATEGORIES.map((category) => (
        <Section key={category.id} category={category} />
      ))}
    </div>
  );
}
