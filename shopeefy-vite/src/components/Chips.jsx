import Chip from '@mui/material/Chip';
import { humanize } from '../utils/format';

const ORDER_STATUS = {
  PENDING_PAYMENT: { label: 'Awaiting payment', color: 'warning' },
  PLACED: { label: 'Placed', color: 'info' },
  CONFIRMED: { label: 'Confirmed', color: 'primary' },
  SHIPPED: { label: 'Shipped', color: 'secondary' },
  DELIVERED: { label: 'Delivered', color: 'success' },
  CANCELLED: { label: 'Cancelled', color: 'default' },
};

export function OrderStatusChip({ status, ...rest }) {
  const { label, color } = ORDER_STATUS[status] ?? { label: humanize(status), color: 'default' };
  return <Chip size="small" label={label} color={color} data-status={status} {...rest} />;
}

const PAYMENT_STATUS = {
  PENDING: 'default',
  COMPLETED: 'success',
  FAILED: 'error',
  REFUNDED: 'warning',
};

export function PaymentStatusChip({ status }) {
  if (!status) return null;
  return <Chip size="small" variant="outlined" label={humanize(status)} color={PAYMENT_STATUS[status] ?? 'default'} />;
}

const SEVERITY = {
  INFO: { bgcolor: '#e0f2fe', color: '#075985' },
  WARN: { bgcolor: '#fef3c7', color: '#92400e' },
  HIGH: { bgcolor: '#ffedd5', color: '#9a3412', border: '1px solid #fb923c' },
  CRITICAL: { bgcolor: '#dc2626', color: '#ffffff' },
};

export function SeverityChip({ severity }) {
  return (
    <Chip
      size="small"
      label={severity ?? 'UNKNOWN'}
      data-severity={severity}
      sx={{ fontWeight: 700, fontSize: 11, letterSpacing: 0.4, ...(SEVERITY[severity] ?? {}) }}
    />
  );
}

const OUTCOME = { SUCCESS: 'success', FAILURE: 'warning', BLOCKED: 'error' };

export function OutcomeChip({ outcome }) {
  if (!outcome) return null;
  return <Chip size="small" variant="outlined" label={humanize(outcome)} color={OUTCOME[outcome] ?? 'default'} />;
}
