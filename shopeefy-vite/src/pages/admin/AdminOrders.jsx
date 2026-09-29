import { useState } from 'react';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import ButtonGroup from '@mui/material/ButtonGroup';
import IconButton from '@mui/material/IconButton';
import Paper from '@mui/material/Paper';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Tooltip from '@mui/material/Tooltip';
import DeleteOutlined from '@mui/icons-material/DeleteOutlined';
import Refresh from '@mui/icons-material/Refresh';
import { api } from '../../api/client';
import { errorMessage } from '../../api/errors';
import { OrderStatusChip, PaymentStatusChip } from '../../components/Chips';
import { ErrorAlert, Loading, PageTitle } from '../../components/Feedback';
import { useApi } from '../../hooks/useApi';
import { formatDateTime, formatPrice, fullName } from '../../utils/format';

// The server's state machine decides what is allowed; the highlighted button is just the usual next step.
const ACTIONS = [
  { key: 'confirmed', label: 'Confirm', from: 'PLACED' },
  { key: 'ship', label: 'Ship', from: 'CONFIRMED' },
  { key: 'deliver', label: 'Deliver', from: 'SHIPPED' },
  { key: 'cancel', label: 'Cancel', from: null },
];

export default function AdminOrders() {
  const orders = useApi('/api/admin/orders/');
  const [busy, setBusy] = useState(null);
  const [message, setMessage] = useState(null);

  const act = async (order, action) => {
    setBusy(`${order.id}:${action}`);
    setMessage(null);
    try {
      if (action === 'delete') {
        await api.delete(`/api/admin/orders/${order.id}/delete`);
        orders.setData((list) => list.filter((o) => o.id !== order.id));
        setMessage({ severity: 'success', text: `Order #${order.id} was deleted.` });
      } else {
        const { data } = await api.put(`/api/admin/orders/${order.id}/${action}`);
        orders.setData((list) => list.map((o) => (o.id === data.id ? data : o)));
        setMessage({ severity: 'success', text: `Order #${order.id} is now ${data.orderStatus}.` });
      }
    } catch (err) {
      setMessage({ severity: 'error', text: `Order #${order.id}: ${errorMessage(err)}` });
    } finally {
      setBusy(null);
    }
  };

  return (
    <div data-testid="admin-orders">
      <PageTitle
        subtitle="Status changes follow the order state machine; invalid moves are refused by the server (409)."
        action={
          <Button startIcon={<Refresh />} onClick={orders.reload}>
            Refresh
          </Button>
        }
      >
        Orders
      </PageTitle>
      {message && (
        <Alert
          severity={message.severity}
          onClose={() => setMessage(null)}
          sx={{ mb: 2 }}
          data-testid="admin-orders-message"
        >
          {message.text}
        </Alert>
      )}
      <ErrorAlert error={orders.error} />
      {orders.loading && <Loading />}
      {orders.data && (
        <TableContainer component={Paper}>
          <Table size="small" data-testid="admin-orders-table">
            <TableHead>
              <TableRow>
                <TableCell>Order</TableCell>
                <TableCell>Customer</TableCell>
                <TableCell>Placed</TableCell>
                <TableCell align="right">Total</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Payment</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {orders.data.length === 0 && (
                <TableRow>
                  <TableCell colSpan={7} align="center" sx={{ py: 4, color: 'text.secondary' }}>
                    No orders yet.
                  </TableCell>
                </TableRow>
              )}
              {orders.data.map((order) => (
                <TableRow
                  key={order.id}
                  hover
                  data-testid="admin-order-row"
                  data-order-id={order.id}
                  data-status={order.orderStatus}
                >
                  <TableCell sx={{ fontWeight: 600 }}>#{order.id}</TableCell>
                  <TableCell>
                    <div className="text-sm">{fullName(order.user)}</div>
                    <div className="text-xs text-slate-500">{order.user?.email}</div>
                  </TableCell>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>{formatDateTime(order.orderDate)}</TableCell>
                  <TableCell align="right">{formatPrice(order.totalDiscountedPrice)}</TableCell>
                  <TableCell>
                    <OrderStatusChip status={order.orderStatus} />
                  </TableCell>
                  <TableCell>
                    <PaymentStatusChip status={order.payment?.status} />
                  </TableCell>
                  <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                    <ButtonGroup size="small" variant="outlined" disabled={busy !== null}>
                      {ACTIONS.map((a) => (
                        <Button
                          key={a.key}
                          variant={a.from === order.orderStatus ? 'contained' : 'outlined'}
                          color={a.key === 'cancel' ? 'error' : 'primary'}
                          onClick={() => act(order, a.key)}
                          data-testid={`admin-order-${a.key}`}
                        >
                          {a.label}
                        </Button>
                      ))}
                    </ButtonGroup>
                    <Tooltip title="Delete (cancelled orders only)">
                      <span>
                        <IconButton
                          size="small"
                          color="error"
                          disabled={busy !== null}
                          onClick={() => act(order, 'delete')}
                          data-testid="admin-order-delete"
                          aria-label={`Delete order ${order.id}`}
                        >
                          <DeleteOutlined fontSize="small" />
                        </IconButton>
                      </span>
                    </Tooltip>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </div>
  );
}
