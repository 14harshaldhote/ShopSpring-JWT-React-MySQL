import { Link, useParams } from 'react-router';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Paper from '@mui/material/Paper';
import LockOutlined from '@mui/icons-material/LockOutlined';
import { ErrorAlert, Loading, PageTitle } from '../../components/Feedback';
import { OrderStatusChip } from '../../components/Chips';
import { AddressBlock, CheckoutSteps, OrderItems, OrderTotals } from '../../components/OrderParts';
import { useApi } from '../../hooks/useApi';
import { usePayment } from '../../hooks/usePayment';
import { formatPrice } from '../../utils/format';

/** Checkout step 2: the order exists (stock reserved) and waits for payment. */
export default function OrderPayment() {
  const { orderId } = useParams();
  const id = /^\d{1,12}$/.test(orderId ?? '') ? orderId : null;
  const { data: order, error, loading } = useApi(id ? `/api/orders/${id}` : null);
  const payment = usePayment();

  if (!id) return <Alert severity="warning">That order link isn't valid.</Alert>;
  if (loading) return <Loading />;
  if (error) return <ErrorAlert error={error} />;
  if (!order) return null;

  const awaitingPayment = order.orderStatus === 'PENDING_PAYMENT';

  return (
    <div>
      <PageTitle subtitle={`Order #${order.id}`}>Review and pay</PageTitle>
      <CheckoutSteps active={awaitingPayment ? 1 : 3} />
      <div className="grid gap-6 lg:grid-cols-[1fr_340px]">
        <div className="space-y-4">
          <Paper sx={{ p: 3 }}>
            <h2 className="mb-3 font-semibold">Deliver to</h2>
            <AddressBlock address={order.shippingAddress} />
          </Paper>
          <Paper sx={{ p: 3 }}>
            <h2 className="mb-1 font-semibold">Items</h2>
            <OrderItems items={order.orderItems} />
          </Paper>
        </div>
        <Paper sx={{ p: 3 }} className="h-fit space-y-4" data-testid="order-summary">
          <div className="flex items-center justify-between">
            <h2 className="font-semibold">Order #{order.id}</h2>
            <OrderStatusChip status={order.orderStatus} data-testid="order-status" />
          </div>
          <OrderTotals order={order} />
          <ErrorAlert error={payment.error} data-testid="pay-error" />
          {awaitingPayment ? (
            <>
              <Button
                variant="contained"
                size="large"
                fullWidth
                startIcon={<LockOutlined />}
                disabled={payment.busy}
                onClick={() => payment.pay(order.id)}
                data-testid="pay-button"
              >
                {payment.busy ? 'Opening secure payment…' : `Pay ${formatPrice(order.totalDiscountedPrice)}`}
              </Button>
              <p className="text-xs text-slate-500">
                The amount is fixed by the server. Unpaid orders are cancelled after 30 minutes and the stock is
                released.
              </p>
            </>
          ) : (
            <Button component={Link} to={`/account/orders/${order.id}`} variant="outlined" fullWidth>
              View order
            </Button>
          )}
        </Paper>
      </div>
    </div>
  );
}
