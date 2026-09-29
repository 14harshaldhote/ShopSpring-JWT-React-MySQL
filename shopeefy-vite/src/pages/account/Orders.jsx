import { Link } from 'react-router';
import Button from '@mui/material/Button';
import ChevronRight from '@mui/icons-material/ChevronRight';
import ProductImage from '../../components/ProductImage';
import { OrderStatusChip } from '../../components/Chips';
import { EmptyState, ErrorAlert, Loading, PageTitle } from '../../components/Feedback';
import { useApi } from '../../hooks/useApi';
import { formatDateTime, formatPrice } from '../../utils/format';

export default function Orders() {
  const { data: orders, error, loading } = useApi('/api/orders/user');

  return (
    <div>
      <PageTitle subtitle="Newest first">My orders</PageTitle>
      <ErrorAlert error={error} />
      {loading && <Loading />}
      {orders?.length === 0 && (
        <EmptyState
          title="No orders yet"
          action={
            <Button component={Link} to="/products" variant="contained">
              Start shopping
            </Button>
          }
        />
      )}
      <ul className="space-y-3" data-testid="orders-list">
        {orders?.map((order) => (
          <li key={order.id}>
            <Link
              to={`/account/orders/${order.id}`}
              className="flex items-center gap-4 rounded-xl border border-slate-200 bg-white p-4 transition hover:border-indigo-300 hover:shadow-sm"
              data-testid="order-row"
              data-order-id={order.id}
              data-status={order.orderStatus}
            >
              <div className="flex -space-x-3">
                {order.orderItems.slice(0, 3).map((item) => (
                  <div
                    key={item.id}
                    className="h-16 w-12 overflow-hidden rounded-md border-2 border-white bg-slate-100"
                  >
                    <ProductImage
                      src={item.product.imageUrl}
                      title={item.product.title}
                      className="h-full w-full object-cover object-top"
                    />
                  </div>
                ))}
              </div>
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="font-semibold">Order #{order.id}</span>
                  <OrderStatusChip status={order.orderStatus} />
                </div>
                <p className="line-clamp-1 text-sm text-slate-600">
                  {order.orderItems.map((i) => i.product.title).join(', ')}
                </p>
                <p className="text-xs text-slate-500">{formatDateTime(order.orderDate)}</p>
              </div>
              <div className="text-right">
                <p className="font-semibold">{formatPrice(order.totalDiscountedPrice)}</p>
                <p className="text-xs text-slate-500">
                  {order.totalItem} item{order.totalItem === 1 ? '' : 's'}
                </p>
              </div>
              <ChevronRight className="text-slate-400" />
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}
