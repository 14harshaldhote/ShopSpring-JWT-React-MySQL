import { Link } from 'react-router';
import Step from '@mui/material/Step';
import StepLabel from '@mui/material/StepLabel';
import Stepper from '@mui/material/Stepper';
import Alert from '@mui/material/Alert';
import ProductImage from './ProductImage';
import { PaymentStatusChip } from './Chips';
import { formatPrice, fullName } from '../utils/format';

export function OrderItems({ items, showReviewLink = false }) {
  return (
    <ul className="divide-y divide-slate-100" data-testid="order-items">
      {items.map((item) => (
        <li key={item.id} className="flex gap-3 py-3" data-testid="order-item">
          <Link to={`/product/${item.product.id}`} className="w-16 shrink-0 overflow-hidden rounded-md bg-slate-100">
            <ProductImage
              src={item.product.imageUrl}
              title={item.product.title}
              className="aspect-[4/5] w-full object-cover object-top"
            />
          </Link>
          <div className="min-w-0 flex-1 text-sm">
            <Link to={`/product/${item.product.id}`} className="line-clamp-1 font-medium hover:underline">
              {item.product.title}
            </Link>
            <p className="text-slate-500">
              {item.product.brand} · Size {item.size} · Qty {item.quantity}
            </p>
            {showReviewLink && (
              <Link to={`/product/${item.product.id}#reviews`} className="text-xs font-semibold text-indigo-600">
                Rate & review
              </Link>
            )}
          </div>
          <div className="text-right text-sm">
            <p className="font-semibold">{formatPrice(item.discountedPrice)}</p>
            {item.discountedPrice < item.price && (
              <p className="text-xs text-slate-400 line-through">{formatPrice(item.price)}</p>
            )}
          </div>
        </li>
      ))}
    </ul>
  );
}

export function AddressBlock({ address }) {
  if (!address) return null;
  return (
    <address className="text-sm not-italic leading-relaxed text-slate-700" data-testid="order-address">
      <span className="font-semibold text-slate-900">{fullName(address)}</span>
      <br />
      {address.streetAddress}
      <br />
      {address.city}, {address.state} {address.zipCode}
      <br />
      {address.mobile}
    </address>
  );
}

export function OrderTotals({ order }) {
  return (
    <dl className="space-y-1.5 text-sm">
      <div className="flex justify-between">
        <dt className="text-slate-600">Price ({order.totalItem} items)</dt>
        <dd>{formatPrice(order.totalPrice)}</dd>
      </div>
      <div className="flex justify-between">
        <dt className="text-slate-600">Discount</dt>
        <dd className="text-emerald-600">−{formatPrice(order.discount)}</dd>
      </div>
      <div className="flex justify-between border-t border-slate-200 pt-2 text-base font-bold">
        <dt>Total</dt>
        <dd data-testid="order-total">{formatPrice(order.totalDiscountedPrice)}</dd>
      </div>
    </dl>
  );
}

export function PaymentInfo({ payment }) {
  if (!payment) return null;
  return (
    <div className="space-y-1 text-sm" data-testid="order-payment">
      <div className="flex items-center gap-2">
        <span className="text-slate-600">Payment</span>
        <PaymentStatusChip status={payment.status} />
      </div>
      {payment.provider && (
        <p className="text-slate-600">
          Provider: <span className="font-medium">{payment.provider}</span>
        </p>
      )}
      {payment.paymentId && (
        <p className="break-all text-slate-600">
          Payment id: <span className="font-mono text-xs">{payment.paymentId}</span>
        </p>
      )}
    </div>
  );
}

const TIMELINE = [
  ['PENDING_PAYMENT', 'Ordered'],
  ['PLACED', 'Paid'],
  ['CONFIRMED', 'Confirmed'],
  ['SHIPPED', 'Shipped'],
  ['DELIVERED', 'Delivered'],
];

export function OrderTimeline({ order }) {
  if (order.orderStatus === 'CANCELLED') {
    return (
      <Alert severity="warning" data-testid="order-timeline" data-status="CANCELLED">
        This order was cancelled.
        {order.payment?.status === 'REFUNDED' && ' The payment has been refunded.'}
        {order.payment?.status !== 'REFUNDED' && ' Nothing was charged.'}
      </Alert>
    );
  }
  const active = TIMELINE.findIndex(([status]) => status === order.orderStatus);
  return (
    <Stepper activeStep={active + 1} alternativeLabel data-testid="order-timeline" data-status={order.orderStatus}>
      {TIMELINE.map(([status, label]) => (
        <Step key={status}>
          <StepLabel>{label}</StepLabel>
        </Step>
      ))}
    </Stepper>
  );
}

const CHECKOUT_STEPS = ['Address', 'Review & pay', 'Done'];

export function CheckoutSteps({ active }) {
  return (
    <Stepper activeStep={active} sx={{ mb: 4, maxWidth: 640 }}>
      {CHECKOUT_STEPS.map((label) => (
        <Step key={label}>
          <StepLabel>{label}</StepLabel>
        </Step>
      ))}
    </Stepper>
  );
}
