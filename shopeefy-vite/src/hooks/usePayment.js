import { useState } from 'react';
import { useSelector } from 'react-redux';
import { useNavigate } from 'react-router';
import { PaymentDismissedError, startPayment } from '../api/payments';
import { selectUser } from '../store/authSlice';
import { fullName } from '../utils/format';

/** Starts payment for an order; the mock gateway leaves the page, Razorpay comes back verified. */
export function usePayment() {
  const navigate = useNavigate();
  const user = useSelector(selectUser);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  const pay = async (orderId) => {
    setBusy(true);
    setError(null);
    try {
      const verified = await startPayment(orderId, user ? { name: fullName(user), email: user.email } : null);
      if (verified) {
        navigate(`/payment/${orderId}`, { replace: true });
      }
      // Mock gateway: the browser is already on its way to the payment page; stay busy.
    } catch (err) {
      if (!(err instanceof PaymentDismissedError)) setError(err);
      setBusy(false);
    }
  };

  return { pay, busy, error };
}
