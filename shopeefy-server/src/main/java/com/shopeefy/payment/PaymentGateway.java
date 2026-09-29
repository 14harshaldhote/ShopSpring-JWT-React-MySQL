package com.shopeefy.payment;

/** A payment provider: Razorpay in production, an in-process mock for the local demo. */
public interface PaymentGateway {

    String name();

    /** Public key id the browser checkout needs (never the secret). */
    String keyId();

    ProviderOrder createOrder(long orderId, long amountPaise, String currency);

    /** Checks the signature the provider's checkout returned to the browser. */
    boolean verifyCheckoutSignature(String providerOrderId, String paymentId, String signature);

    /** Reads the payment from the provider itself (server to server). Null if it doesn't exist. */
    ProviderPayment fetchPayment(String paymentId);

    void capture(String paymentId, long amountPaise, String currency);

    void refund(String paymentId, long amountPaise);

    boolean verifyWebhookSignature(byte[] body, String signature);

    record ProviderOrder(String id, String checkoutUrl) {
    }

    enum Status { CAPTURED, AUTHORIZED, FAILED, OTHER }

    record ProviderPayment(String id, String orderId, long amount, String currency, Status status) {
    }
}
