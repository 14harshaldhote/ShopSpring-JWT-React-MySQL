import { useEffect, useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router';
import Alert from '@mui/material/Alert';
import AlertTitle from '@mui/material/AlertTitle';
import Button from '@mui/material/Button';
import Paper from '@mui/material/Paper';
import CheckCircle from '@mui/icons-material/CheckCircle';
import { api } from '../../api/client';
import { errorMessage, statusOf } from '../../api/errors';
import { verifyPayment } from '../../api/payments';
import { Loading } from '../../components/Feedback';
import { OrderStatusChip } from '../../components/Chips';
import { AddressBlock, CheckoutSteps, OrderItems, OrderTotals, PaymentInfo } from '../../components/OrderParts';
import { usePayment } from '../../hooks/usePayment';

const PAID = ['PLACED', 'CONFIRMED', 'SHIPPED', 'DELIVERED'];

// One verification per payment, even if the effect runs twice (StrictMode) or the page re-renders.
const verifications = new Map();

function verifyOnce(orderId, providerOrderId, paymentId, signature) {
  const key = `${orderId}:${providerOrderId}:${paymentId}`;
  if (!verifications.has(key)) {
    verifications.set(key, verifyPayment({ orderId, providerOrderId, paymentId, signature }));
  }
  return verifications.get(key);
}

function Failure({ error, orderId }) {
  const payment = usePayment();
  const status = statusOf(error);
  const title =
    status === 402
      ? 'Payment failed'
      : status === 409
        ? "This order can't be paid"
        : status === 400
          ? 'Payment not verified'
          : 'Something went wrong';
  return (
    <div
      className="mx-auto max-w-xl space-y-4"
      data-testid="payment-result"
      data-state="error"
      data-http-status={status ?? ''}
    >
      <Alert severity={status === 402 ? 'warning' : 'error'}>
        <AlertTitle>{title}</AlertTitle>
        {errorMessage(error)}
        {status === 400 && ' No money was taken for this order; if you were charged, it will be refunded.'}
      </Alert>
      <ErrorText error={payment.error} />
      <div className="flex flex-wrap gap-3">
        {status === 402 && (
          <Button
            variant="contained"
            onClick={() => payment.pay(orderId)}
            disabled={payment.busy}
            data-testid="payment-retry"
          >
            Try again
          </Button>
        )}
        <Button component={Link} to={`/account/orders/${orderId}`} variant="outlined">
          View order
        </Button>
        <Button component={Link} to="/" color="inherit">
          Continue shopping
        </Button>
      </div>
    </div>
  );
}

function ErrorText({ error }) {
  return error ? <Alert severity="error">{errorMessage(error)}</Alert> : null;
}

function Success({ order }) {
  return (
    <div className="space-y-6" data-testid="payment-result" data-state="success" data-order-status={order.orderStatus}>
      <CheckoutSteps active={3} />
      <div className="flex flex-col items-center gap-2 text-center">
        <CheckCircle color="success" sx={{ fontSize: 56 }} />
        <h1 className="text-2xl font-bold">Payment successful</h1>
        <div className="flex flex-wrap items-center justify-center gap-2 text-slate-600">
          Thank you! Order <span className="font-semibold">#{order.id}</span> is{' '}
          <OrderStatusChip status={order.orderStatus} data-testid="order-status" />
        </div>
      </div>
      <div className="grid gap-6 lg:grid-cols-[1fr_320px]">
        <Paper sx={{ p: 3 }}>
          <h2 className="mb-1 font-semibold">Items</h2>
          <OrderItems items={order.orderItems} />
        </Paper>
        <Paper sx={{ p: 3 }} className="h-fit space-y-4">
          <OrderTotals order={order} />
          <PaymentInfo payment={order.payment} />
          <AddressBlock address={order.shippingAddress} />
          <div className="flex flex-col gap-2">
            <Button
              component={Link}
              to={`/account/orders/${order.id}`}
              variant="contained"
              data-testid="payment-view-order"
            >
              Track this order
            </Button>
            <Button component={Link} to="/" color="inherit">
              Continue shopping
            </Button>
          </div>
        </Paper>
      </div>
    </div>
  );
}

function NotPaid({ order }) {
  const payment = usePayment();
  const cancelled = order.orderStatus === 'CANCELLED';
  return (
    <div
      className="mx-auto max-w-xl space-y-4"
      data-testid="payment-result"
      data-state={cancelled ? 'cancelled' : 'pending'}
    >
      <Alert severity={cancelled ? 'warning' : 'info'}>
        <AlertTitle>{cancelled ? 'Order cancelled' : 'Payment not completed'}</AlertTitle>
        {cancelled
          ? 'This order was cancelled, so it can no longer be paid.'
          : `Order #${order.id} is still waiting for payment.`}
      </Alert>
      <ErrorText error={payment.error} />
      <div className="flex gap-3">
        {!cancelled && (
          <Button
            variant="contained"
            onClick={() => payment.pay(order.id)}
            disabled={payment.busy}
            data-testid="payment-retry"
          >
            Pay now
          </Button>
        )}
        <Button component={Link} to={`/account/orders/${order.id}`} variant="outlined">
          View order
        </Button>
      </div>
    </div>
  );
}

/**
 * Where the payment provider sends the shopper back. The query string is only a claim: the server
 * checks the HMAC signature and asks the provider before the order is marked paid. [OWASP A08:2025]
 */
export default function PaymentResult() {
  const { orderId } = useParams();
  const [params] = useSearchParams();
  const providerOrderId = params.get('providerOrderId');
  const paymentId = params.get('paymentId');
  const signature = params.get('signature');
  const validId = /^\d{1,12}$/.test(orderId ?? '');
  const key = JSON.stringify([orderId, providerOrderId, paymentId, signature]);
  const [result, setResult] = useState({ key: null, order: null, error: null });

  useEffect(() => {
    if (!validId) return undefined;
    let active = true;
    const request =
      providerOrderId && paymentId && signature
        ? verifyOnce(orderId, providerOrderId, paymentId, signature)
        : api.get(`/api/orders/${orderId}`).then((response) => response.data);
    request.then(
      (order) => active && setResult({ key, order, error: null }),
      (error) => active && setResult({ key, order: null, error }),
    );
    return () => {
      active = false;
    };
  }, [key, validId, orderId, providerOrderId, paymentId, signature]);

  if (!validId) return <Alert severity="warning">That payment link isn't valid.</Alert>;
  if (result.key !== key) return <Loading label="Confirming your payment with the server…" />;
  if (result.error) return <Failure error={result.error} orderId={orderId} />;
  if (PAID.includes(result.order.orderStatus)) return <Success order={result.order} />;
  return <NotPaid order={result.order} />;
}
