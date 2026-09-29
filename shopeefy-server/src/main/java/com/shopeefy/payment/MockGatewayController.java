package com.shopeefy.payment;

import java.net.URI;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

import com.shopeefy.config.AppProperties;

/**
 * The mock provider's hosted checkout page (local demo only). It plays the part of Razorpay's
 * payment window: shows the amount the server fixed, and on "Pay" redirects back to the shop
 * with a payment id and an HMAC signature, just like the real provider.
 */
@RestController
@RequestMapping("/dev/mock-gateway")
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "mock", matchIfMissing = true)
public class MockGatewayController {

    private final MockPaymentGateway gateway;
    private final String frontendUrl;

    public MockGatewayController(MockPaymentGateway gateway, AppProperties props) {
        this.gateway = gateway;
        this.frontendUrl = props.frontendUrl();
    }

    @GetMapping("/checkout")
    ResponseEntity<String> checkout(@RequestParam String providerOrderId) {
        var order = gateway.findOrder(providerOrderId);
        if (order == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.TEXT_PLAIN).body("Unknown payment.");
        }
        String id = HtmlUtils.htmlEscape(providerOrderId);
        String amount = "%,.2f".formatted(order.amountPaise() / 100.0);
        String html = """
                <!doctype html><html lang="en"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1"><title>MockPay checkout</title>
                <style>
                  body{font-family:system-ui,sans-serif;background:#f1f5f9;display:grid;place-items:center;min-height:100vh;margin:0}
                  .card{background:#fff;border-radius:12px;box-shadow:0 8px 30px #0002;padding:28px 32px;width:340px}
                  .tag{display:inline-block;background:#fef3c7;color:#92400e;font-size:12px;padding:2px 8px;border-radius:99px}
                  h1{font-size:20px;margin:12px 0 4px} .amt{font-size:32px;font-weight:700;margin:12px 0}
                  .muted{color:#64748b;font-size:13px} button{width:100%%;padding:12px;border:0;border-radius:8px;font-size:16px;margin-top:10px;cursor:pointer}
                  .pay{background:#4f46e5;color:#fff} .decline{background:#e2e8f0}
                </style></head><body><div class="card">
                <span class="tag">TEST MODE</span><h1>MockPay</h1>
                <div class="muted">ShopSpring order #%d &middot; %s</div>
                <div class="amt">&#8377;%s</div>
                <form method="post" action="/dev/mock-gateway/pay">
                  <input type="hidden" name="providerOrderId" value="%s">
                  <button class="pay" name="outcome" value="success" id="pay">Pay &#8377;%s</button>
                  <button class="decline" name="outcome" value="failure" id="decline">Decline payment</button>
                </form>
                <p class="muted">The amount was fixed by the server when this payment was created.</p>
                </div></body></html>
                """.formatted(order.orderId(), id, amount, id, amount);
        // same-origin, not the API's usual no-referrer: under no-referrer the browser sends the form
        // with "Origin: null", which CORS rightly refuses.
        return ResponseEntity.ok()
                .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; frame-ancestors 'none'")
                .header("Referrer-Policy", "same-origin")
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }

    @PostMapping(path = "/pay", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    ResponseEntity<Void> pay(@RequestParam String providerOrderId, @RequestParam String outcome) {
        if (gateway.findOrder(providerOrderId) == null) {
            return ResponseEntity.notFound().build();
        }
        var done = gateway.complete(providerOrderId, "success".equals(outcome));
        URI back = UriComponentsBuilder.fromUriString(frontendUrl).path("/payment/{orderId}")
                .queryParam("providerOrderId", providerOrderId)
                .queryParam("paymentId", done.paymentId())
                .queryParam("signature", done.signature())
                .buildAndExpand(done.orderId()).encode().toUri();
        return ResponseEntity.status(HttpStatus.SEE_OTHER).header(HttpHeaders.LOCATION, back.toString()).build();
    }
}
