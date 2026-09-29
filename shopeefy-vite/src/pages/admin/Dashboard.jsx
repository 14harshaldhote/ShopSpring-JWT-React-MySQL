import { Link } from 'react-router';
import Button from '@mui/material/Button';
import Paper from '@mui/material/Paper';
import { OrderStatusChip } from '../../components/Chips';
import { ErrorAlert, Loading, PageTitle } from '../../components/Feedback';
import ProductImage from '../../components/ProductImage';
import { useApi } from '../../hooks/useApi';
import { formatDate, formatPrice } from '../../utils/format';

const STATUSES = ['PENDING_PAYMENT', 'PLACED', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'CANCELLED'];

function Stat({ label, value, testId }) {
  return (
    <Paper sx={{ p: 2.5 }} data-testid={testId}>
      <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">{label}</p>
      <p className="mt-1 text-2xl font-bold text-slate-900">{value}</p>
    </Paper>
  );
}

export default function Dashboard() {
  const stats = useApi('/api/admin/stats');
  const recent = useApi('/api/admin/products/recent');
  const data = stats.data;
  const byStatus = data?.ordersByStatus ?? {};
  const maxCount = Math.max(1, ...STATUSES.map((s) => byStatus[s] ?? 0));

  return (
    <div className="space-y-6" data-testid="admin-dashboard">
      <PageTitle subtitle="Live numbers from the shop database.">Dashboard</PageTitle>
      <ErrorAlert error={stats.error} />
      {stats.loading && <Loading />}
      {data && (
        <>
          <div className="grid grid-cols-2 gap-4 xl:grid-cols-4">
            <Stat label="Revenue (paid orders)" value={formatPrice(data.revenue)} testId="stat-revenue" />
            <Stat label="Orders" value={data.orders} testId="stat-orders" />
            <Stat label="Products" value={data.products} testId="stat-products" />
            <Stat label="Users" value={data.users} testId="stat-users" />
          </div>
          <Paper sx={{ p: 3 }}>
            <div className="mb-4 flex items-center justify-between">
              <h2 className="font-semibold">Orders by status</h2>
              <Button component={Link} to="/admin/orders" size="small">
                Manage orders
              </Button>
            </div>
            <div className="space-y-3" data-testid="orders-by-status">
              {STATUSES.map((status) => {
                const count = byStatus[status] ?? 0;
                return (
                  <div key={status} className="grid grid-cols-[150px_1fr_40px] items-center gap-3">
                    <OrderStatusChip status={status} sx={{ justifySelf: 'start' }} />
                    <div className="h-2.5 overflow-hidden rounded-full bg-slate-100">
                      <div
                        className="h-full rounded-full bg-indigo-500"
                        style={{ width: `${(count / maxCount) * 100}%` }}
                      />
                    </div>
                    <span className="text-right text-sm font-semibold">{count}</span>
                  </div>
                );
              })}
            </div>
          </Paper>
        </>
      )}
      <Paper sx={{ p: 3 }}>
        <div className="mb-3 flex items-center justify-between">
          <h2 className="font-semibold">Recently added products</h2>
          <Button component={Link} to="/admin/products" size="small">
            All products
          </Button>
        </div>
        <ErrorAlert error={recent.error} />
        <ul className="divide-y divide-slate-100">
          {recent.data?.map((product) => (
            <li key={product.id} className="flex items-center gap-3 py-2">
              <div className="h-12 w-10 shrink-0 overflow-hidden rounded bg-slate-100">
                <ProductImage
                  src={product.imageUrl}
                  title={product.title}
                  className="h-full w-full object-cover object-top"
                />
              </div>
              <Link to={`/product/${product.id}`} className="line-clamp-1 flex-1 text-sm hover:underline">
                {product.title}
              </Link>
              <span className="text-sm text-slate-500">{formatDate(product.createdAt)}</span>
              <span className="w-20 text-right text-sm font-semibold">{formatPrice(product.discountedPrice)}</span>
            </li>
          ))}
        </ul>
      </Paper>
    </div>
  );
}
