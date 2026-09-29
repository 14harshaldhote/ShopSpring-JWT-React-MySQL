import { useState } from 'react';
import { Link, useParams } from 'react-router';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Paper from '@mui/material/Paper';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import ArrowBack from '@mui/icons-material/ArrowBack';
import { api } from '../../api/client';
import { OrderStatusChip } from '../../components/Chips';
import { ErrorAlert, Loading } from '../../components/Feedback';
import { AddressBlock, OrderItems, OrderTimeline, OrderTotals, PaymentInfo } from '../../components/OrderParts';
import { useApi } from '../../hooks/useApi';
import { usePayment } from '../../hooks/usePayment';
import { formatDateTime, formatPrice } from '../../utils/format';

const CANCELLABLE = ['PENDING_PAYMENT', 'PLACED'];
const BOUGHT = ['PLACED', 'CONFIRMED', 'SHIPPED', 'DELIVERED'];

export default function OrderDetails() {
  const { orderId } = useParams();
  const id = /^\d{1,12}$/.test(orderId ?? '') ? orderId : null;
  const { data: order, error, loading, setData } = useApi(id ? `/api/orders/${id}` : null);
  const payment = usePayment();
  const [confirming, setConfirming] = useState(false);
  const [cancelBusy, setCancelBusy] = useState(false);
  const [cancelError, setCancelError] = useState(null);
  const [cancelled, setCancelled] = useState(false);

  if (!id) return <Alert severity="warning">That order link isn't valid.</Alert>;
  if (loading) return <Loading />;
  if (error) return <ErrorAlert error={error} />;
  if (!order) return null;

  const cancel = async () => {
    setCancelBusy(true);
    setCancelError(null);
    try {
      const { data } = await api.post(`/api/orders/${order.id}/cancel`);
      setData(data);
      setCancelled(true);
      setConfirming(false);
    } catch (err) {
      setCancelError(err);
    } finally {
      setCancelBusy(false);
    }
  };

  const canCancel = CANCELLABLE.includes(order.orderStatus);
  const awaitingPayment = order.orderStatus === 'PENDING_PAYMENT';

  return (
    <div className="space-y-5" data-testid="order-details" data-order-id={order.id} data-status={order.orderStatus}>
      <Button component={Link} to="/account/orders" startIcon={<ArrowBack />} color="inherit" size="small">
        All orders
      </Button>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-bold">Order #{order.id}</h1>
            <OrderStatusChip status={order.orderStatus} data-testid="order-status" />
          </div>
          <p className="text-sm text-slate-500">
            Ordered {formatDateTime(order.orderDate)}
            {order.deliveryDate && <> · Delivered {formatDateTime(order.deliveryDate)}</>}
          </p>
        </div>
        <div className="flex gap-2">
          {awaitingPayment && (
            <Button
              variant="contained"
              onClick={() => payment.pay(order.id)}
              disabled={payment.busy}
              data-testid="order-pay"
            >
              {payment.busy ? 'Opening payment…' : `Pay ${formatPrice(order.totalDiscountedPrice)}`}
            </Button>
          )}
          {canCancel && (
            <Button color="error" variant="outlined" onClick={() => setConfirming(true)} data-testid="order-cancel">
              Cancel order
            </Button>
          )}
        </div>
      </div>

      <ErrorAlert error={payment.error} />
      {cancelled && (
        <Alert severity="success">
          The order was cancelled{order.payment?.status === 'REFUNDED' ? ' and your payment refunded' : ''}.
        </Alert>
      )}

      <Paper sx={{ p: 3 }}>
        <OrderTimeline order={order} />
      </Paper>

      <div className="grid gap-5 lg:grid-cols-[1fr_320px]">
        <Paper sx={{ p: 3 }}>
          <h2 className="mb-1 font-semibold">Items</h2>
          <OrderItems items={order.orderItems} showReviewLink={BOUGHT.includes(order.orderStatus)} />
        </Paper>
        <div className="space-y-5">
          <Paper sx={{ p: 3 }} className="space-y-4">
            <OrderTotals order={order} />
            <PaymentInfo payment={order.payment} />
          </Paper>
          <Paper sx={{ p: 3 }}>
            <h2 className="mb-2 font-semibold">Shipping address</h2>
            <AddressBlock address={order.shippingAddress} />
          </Paper>
        </div>
      </div>

      <Dialog open={confirming} onClose={() => !cancelBusy && setConfirming(false)}>
        <DialogTitle>Cancel order #{order.id}?</DialogTitle>
        <DialogContent>
          <p className="text-sm text-slate-600">
            The items go back into stock
            {order.payment?.status === 'COMPLETED' ? ' and the payment is refunded to you.' : '.'}
          </p>
          <ErrorAlert error={cancelError} sx={{ mt: 2 }} />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirming(false)} disabled={cancelBusy} color="inherit">
            Keep order
          </Button>
          <Button
            onClick={cancel}
            disabled={cancelBusy}
            color="error"
            variant="contained"
            data-testid="order-cancel-confirm"
          >
            {cancelBusy ? 'Cancelling…' : 'Cancel order'}
          </Button>
        </DialogActions>
      </Dialog>
    </div>
  );
}
