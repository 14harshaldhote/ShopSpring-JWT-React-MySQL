# ShopSpring API (v2)

All endpoints are served from the same origin as the web app. In development the Vite dev server
proxies `/api`, `/auth`, `/oauth2`, `/login/oauth2`, `/.well-known` and `/dev` to the backend on
port 8080, and in Docker nginx does the same. The frontend therefore calls relative URLs
(`/api/...`) and never needs CORS.

* JSON everywhere. Errors use RFC 9457 problem details:
  `{ "type", "title", "status", "detail", "instance", "errors"?: { field: message } }`.
* Authenticated calls send `Authorization: Bearer <accessToken>`.
* The access token lives only in memory in the browser. The refresh token is an `HttpOnly`,
  `Secure`, `SameSite=Strict` cookie named `ss_refresh` scoped to `Path=/auth`. The browser sends it
  automatically; JavaScript cannot read it. Calls to `/auth/*` must use `withCredentials: true`.
* Every state-changing `/auth/*` call must send the header `X-Requested-With: XMLHttpRequest`
  (CSRF defence for the cookie endpoints). The server rejects cookie calls without it.
* Rate-limited responses are `429` with a `Retry-After` header (seconds) and
  `X-RateLimit-Remaining`.

## Auth

| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/auth/providers` | | `{ "oauth2": [ { "id": "google", "name": "Google", "url": "/oauth2/authorization/google" } ] }` (only configured providers) |
| POST | `/auth/register` | `{ firstName, lastName, email, password }` | `202 OtpChallenge`. Always 202, even for an existing email (no account enumeration) |
| POST | `/auth/register/verify` | `{ challengeId, code }` | `200 AuthResponse` + refresh cookie |
| POST | `/auth/login` | `{ email, password }` | `200 OtpChallenge` when the password is right; `401` generic "Invalid email or password" otherwise |
| POST | `/auth/login/verify` | `{ challengeId, code }` | `200 AuthResponse` + refresh cookie |
| POST | `/auth/otp/resend` | `{ challengeId }` | `202 OtpChallenge` (60 s cooldown) |
| POST | `/auth/refresh` | (cookie) | `200 AuthResponse` + rotated cookie; `401` if missing, expired, revoked or reused |
| POST | `/auth/logout` | (cookie) | `204`, cookie cleared, session revoked |
| POST | `/auth/password/forgot` | `{ email }` | `202 OtpChallenge` (always) |
| POST | `/auth/password/reset` | `{ challengeId, code, newPassword }` | `204`; every session of the user is revoked |
| GET | `/oauth2/authorization/{provider}` | | Browser redirect to Google / GitHub / dev IdP |
| GET | `/.well-known/jwks.json` | | Public key set that signs access tokens |

After an OAuth2 login the backend sets the refresh cookie and redirects the browser to
`/oauth2/callback` in the web app (no token in the URL). That page calls `POST /auth/refresh`
to get an access token. On failure it redirects to `/login?error=oauth2`.

```jsonc
// OtpChallenge
{ "challengeId": "9d4c…", "expiresAt": "2026-09-28T18:05:00Z", "resendAfterSeconds": 60,
  "destination": "h****l@gmail.com", "purpose": "LOGIN" }   // purpose: REGISTER | LOGIN | PASSWORD_RESET

// AuthResponse
{ "accessToken": "eyJ…", "tokenType": "Bearer", "expiresIn": 600, "user": User }

// User
{ "id": 7, "email": "a@b.com", "firstName": "Asha", "lastName": "K", "mobile": null,
  "role": "CUSTOMER", "authProvider": "LOCAL", "emailVerified": true, "createdAt": "…" }
// role: CUSTOMER | ADMIN, authProvider: LOCAL | GOOGLE | GITHUB | DEVIDP
```

Password rules: 10 to 128 characters, not in the common-password list, not containing the email
name. OTP: 6 digits, valid for 5 minutes, 5 wrong attempts invalidate it.

## Account (authenticated)

| Method | Path | Response |
|---|---|---|
| GET | `/api/users/me` (alias `/api/users/profile`) | `User` |
| GET | `/api/users/me/sessions` | `[ { id, createdAt, lastUsedAt, expiresAt, userAgent, ipAddress, current } ]` |
| DELETE | `/api/users/me/sessions/{id}` | `204` |
| POST | `/api/users/me/sessions/revoke-others` | `204` (sign out every other device) |
| POST | `/api/users/me/password` `{ currentPassword, newPassword }` | `204`; every other session is signed out and the owner is emailed |
| GET | `/api/users/me/security-events` | `[ { type, ipAddress, userAgent, detail, createdAt } ]` last 20 |
| GET | `/api/users/me/addresses` | `[ Address ]` |

## Catalogue (public)

| Method | Path | Response |
|---|---|---|
| GET | `/api/products?category=&color=&size=&minPrice=&maxPrice=&minDiscount=&sort=&stock=&pageNumber=0&pageSize=10` | `Page<Product>` |
| GET | `/api/products/id/{id}` | `Product` |
| GET | `/api/products/search?q=` | `[ Product ]` (max 20) |
| GET | `/api/reviews/product/{productId}` | `[ Review ]` |
| GET | `/api/ratings/product/{productId}` | `RatingSummary` |

`color` and `size` accept comma-separated values. `sort`: `price_low`, `price_high`, `newest`
(default). `stock`: `in_stock`, `out_of_stock` or empty. `category` is the third-level
category name such as `women_dress` or `mens_kurta`.

```jsonc
// Product
{ "id": 1, "title": "…", "description": "…", "brand": "…", "color": "yellow",
  "imageUrl": "https://…", "price": 1999, "discountedPrice": 699, "discountPercent": 65,
  "quantity": 100, "sizes": [ { "name": "S", "quantity": 20 } ],
  "category": { "id": 3, "name": "women_dress", "parent": "Clothing", "topLevel": "Women" },
  "averageRating": 4.3, "ratingCount": 12, "reviewCount": 4, "createdAt": "…" }

// Page<T> (Spring Data page, trimmed)
{ "content": [ T ], "totalElements": 120, "totalPages": 12, "number": 0, "size": 10 }

// Review
{ "id": 1, "review": "Great fit", "author": "Asha K.", "createdAt": "…" }   // author = first name + last initial

// RatingSummary
{ "average": 4.3, "count": 12, "distribution": { "1": 0, "2": 1, "3": 1, "4": 4, "5": 6 } }
```

## Reviews and ratings (authenticated, verified buyers only)

| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/api/reviews/create` | `{ productId, review }` (1 to 1000 chars) | `201 Review`; `403` if the user has not bought the product |
| POST | `/api/ratings/create` | `{ productId, rating }` (1 to 5) | `201 RatingSummary`; one rating per user per product (updates it) |

## Cart (authenticated)

| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/api/cart/` | | `Cart` |
| PUT | `/api/cart/add` | `{ productId, size, quantity }` (quantity 1 to 10) | `Cart` |
| PUT | `/api/cart_items/{id}` | `{ quantity }` | `Cart` |
| DELETE | `/api/cart_items/{id}` | | `Cart` |

```jsonc
// Cart
{ "id": 1, "cartItems": [ CartItem ], "totalItem": 3, "totalPrice": 5997,
  "totalDiscountedPrice": 2097, "discount": 3900 }
// CartItem
{ "id": 5, "product": { "id", "title", "brand", "imageUrl", "color" }, "size": "M",
  "quantity": 1, "price": 1999, "discountedPrice": 699 }   // line totals
```

Prices are always computed on the server from the product table. Any price sent by a client
is ignored.

## Orders (authenticated, owner only)

| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/api/orders/` | `Address` without id (or `{ addressId }` to reuse one) | `201 Order` created from the cart, status `PENDING_PAYMENT` |
| GET | `/api/orders/user` | | `[ Order ]` newest first |
| GET | `/api/orders/{id}` | | `Order`; `404` if it belongs to someone else |
| POST | `/api/orders/{id}/cancel` | | `Order` (`CANCELLED`, stock returned, refunded if paid); `409` once the shop has confirmed it |

```jsonc
// Address
{ "id": 3, "firstName", "lastName", "streetAddress", "city", "state", "zipCode", "mobile" }
// Order
{ "id": 12, "orderStatus": "PLACED", "orderDate": "…", "deliveryDate": null,
  "orderItems": [ { "id", "product": { "id", "title", "brand", "imageUrl", "color" }, "size",
                    "quantity", "price", "discountedPrice" } ],
  "shippingAddress": Address, "totalPrice": 5997, "totalDiscountedPrice": 2097,
  "discount": 3900, "totalItem": 3,
  "payment": { "status": "COMPLETED", "provider": "mock", "paymentId": "pay_…" } }
// orderStatus: PENDING_PAYMENT | PLACED | CONFIRMED | SHIPPED | DELIVERED | CANCELLED
// payment.status: PENDING | COMPLETED | FAILED | REFUNDED
```

## Payments (authenticated, owner only)

| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/api/payments/{orderId}` | | `PaymentSession` |
| POST | `/api/payments/verify` | `{ orderId, providerOrderId, paymentId, signature }` | `Order` (now `PLACED`); `400` if the signature, order or amount does not match |
| POST | `/api/payments/webhook` | Razorpay webhook (public, HMAC signed) | `200` |

```jsonc
// PaymentSession
{ "provider": "razorpay", "keyId": "rzp_test_…", "providerOrderId": "order_…",
  "amount": 209700, "currency": "INR", "checkoutUrl": null }
// provider "mock" (local demo): checkoutUrl points at /dev/mock-gateway/checkout?...; the web app
// navigates there, and the mock gateway redirects back to
// /payment/{orderId}?providerOrderId=…&paymentId=…&signature=…  which the web app posts to /verify.
// provider "razorpay": open Razorpay Checkout with keyId/order_id; its handler receives
// razorpay_order_id, razorpay_payment_id, razorpay_signature, which are posted to /verify.
```

## Admin (role ADMIN)

| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/api/admin/orders/` | | `[ Order ]` (with `user: { id, email, firstName, lastName }`) |
| PUT | `/api/admin/orders/{id}/confirmed` \| `/ship` \| `/deliver` \| `/cancel` | | `Order`; `409` for an invalid status change |
| DELETE | `/api/admin/orders/{id}/delete` | | `204` |
| GET | `/api/admin/products/all` | | `[ Product ]` |
| GET | `/api/admin/products/recent` | | `[ Product ]` (10 newest) |
| POST | `/api/admin/products/` | `[ CreateProductRequest ]` | `201 [ Product ]` |
| PUT | `/api/admin/products/{id}/update` | `{ quantity?, description?, price?, discountedPrice? }` | `Product` |
| DELETE | `/api/admin/products/{id}/delete` | | `204` |
| GET | `/api/admin/users` | | `[ User ]` |
| GET | `/api/admin/security-events?page=0&size=50` | | `Page<{ id, type, email, ipAddress, userAgent, detail, createdAt }>` |
| GET | `/api/admin/security-events/verify` | | `{ valid, eventsChecked, firstBrokenEventId }`: recomputes the audit hash chain |
| GET | `/api/admin/stats` | | `{ users, products, orders, revenue, ordersByStatus: { PLACED: 3, … } }` |

```jsonc
// CreateProductRequest
{ "title", "description", "brand", "color", "imageUrl", "price", "discountedPrice",
  "discountPercent", "quantity", "size": [ { "name": "S", "quantity": 20 } ],
  "topLevelCategory": "Women", "secondLevelCategory": "Clothing", "thirdLevelCategory": "women_dress" }
```
