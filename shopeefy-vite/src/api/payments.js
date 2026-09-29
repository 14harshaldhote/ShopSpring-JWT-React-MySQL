import { api } from './client';

const RAZORPAY_CHECKOUT = 'https://checkout.razorpay.com/v1/checkout.js';

export class PaymentDismissedError extends Error {
  constructor() {
    super('Payment window closed before the payment finished.');
    this.name = 'PaymentDismissedError';
  }
}

let razorpayScript = null;

/**
 * Loads Razorpay Checkout from its fixed origin (the only third-party script the CSP allows) as a
 * normal external script, never inline.                                     [OWASP A08:2025]
 */
function loadRazorpay() {
  if (window.Razorpay) return Promise.resolve(window.Razorpay);
  razorpayScript ??= new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.src = RAZORPAY_CHECKOUT;
    script.async = true;
    script.onload = () => resolve(window.Razorpay);
    script.onerror = () => {
      razorpayScript = null;
      script.remove();
      reject(new Error("Couldn't load the Razorpay checkout. Check your connection and try again."));
    };
    document.head.appendChild(script);
  });
  return razorpayScript;
}

/**
 * [OWASP A01:2025] Only follow a checkout URL that stays on this site's mock gateway, so a
 * tampered response can't turn the Pay button into an open redirect.
 */
function sameOriginCheckoutUrl(checkoutUrl) {
  const url = new URL(checkoutUrl, window.location.origin);
  if (url.origin !== window.location.origin || !url.pathname.startsWith('/dev/mock-gateway/')) {
    throw new Error('Unexpected checkout address. Please try again.');
  }
  return url.href;
}

/** Posts a checkout result for the server to verify. The browser never decides that an order is paid. */
export async function verifyPayment({ orderId, providerOrderId, paymentId, signature }) {
  const { data } = await api.post('/api/payments/verify', {
    orderId: Number(orderId),
    providerOrderId,
    paymentId,
    signature,
  });
  return data;
}

/**
 * Starts payment for an order. The mock gateway is a full-page redirect (it comes back to
 * /payment/:orderId); Razorpay opens its checkout window and resolves with the verified order.
 */
export async function startPayment(orderId, customer) {
  const { data: session } = await api.post(`/api/payments/${orderId}`);

  if (session.provider === 'mock') {
    window.location.assign(sameOriginCheckoutUrl(session.checkoutUrl));
    return null;
  }

  if (session.provider !== 'razorpay') {
    throw new Error('This payment method is not supported.');
  }

  const Razorpay = await loadRazorpay();
  const result = await new Promise((resolve, reject) => {
    const checkout = new Razorpay({
      key: session.keyId,
      order_id: session.providerOrderId,
      amount: session.amount,
      currency: session.currency,
      name: 'ShopSpring',
      description: `Order #${orderId}`,
      prefill: customer ? { name: customer.name, email: customer.email } : undefined,
      handler: (response) =>
        resolve({
          providerOrderId: response.razorpay_order_id,
          paymentId: response.razorpay_payment_id,
          signature: response.razorpay_signature,
        }),
      modal: { ondismiss: () => reject(new PaymentDismissedError()) },
    });
    checkout.open();
  });
  return verifyPayment({ orderId, ...result });
}
