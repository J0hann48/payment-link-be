
# Payment Link API – Reference

This document describes the main HTTP endpoints exposed by the Payment Link backend and how they are used by:

- The **merchant portal** (create/manage payment links, see payments).
- The **public checkout** (customers paying via a payment link).
- The **PSP mocks** (tokenization and simulated charges).
- **Webhooks** (PSP → platform notifications).

> **Note:** Paths and payloads reflect the intended design. If your implementation differs slightly, keep this file in sync with the code and Swagger/OpenAPI.

---

## 1. Conventions

- Base URL (local): `http://localhost:8080`
- All APIs are JSON over HTTP.
- Errors are returned with a JSON body containing at least:
  - `code` – machine-readable error code.
  - `message` – human-readable message.
  - Optionally `details` – extra info (field errors, etc.).

Examples:

```json
{
  "code": "PAYMENT_LINK_NOT_FOUND",
  "message": "Payment link not found"
}
```

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Validation failed",
  "details": [
    { "field": "amount", "message": "must be greater than 0" }
  ]
}
```

---

## 2. Public checkout API

These endpoints are called by the **public checkout** UI opened by the customer from a payment link URL.

### 2.1 Get payment link by slug (with fee & FX preview)

**GET** `/api/public/payment-links/{slug}`

Returns the information needed to render the checkout page and preview fees.

**Path parameters**

- `slug` – public identifier of the payment link (part of the URL).

**Response 200 – example**

```json
{
  "slug": "abc123",
  "publicId": "pl_123",
  "merchantName": "Demo Merchant",
  "recipientName": "Juan Perez",
  "amount": 100.00,
  "currency": "USD",
  "status": "ACTIVE",
  "expiresAt": "2025-12-31T23:59:59Z",
  "description": "Invoice #123",
  "feeBreakdown": {
    "fixedFeeUsd": 2.00,
    "variableFeeUsd": 1.50,
    "fxRate": 17.20,
    "fxMarkupBps": 50,
    "totalFeeUsd": 3.50,
    "payoutMxn": 1660.40
  }
}
```

**Response codes**

- `200` – link is active and can be paid.
- `404` – link not found.
- `410` (optional) – link expired.

---

### 2.2 Process payment for a link

**POST** `/api/public/payment-links/{slug}/payments`

Processes a payment using a PSP token previously obtained via a PSP mock.

**Path parameters**

- `slug` – payment link slug.

**Request body**

```json
{
  "pspToken": "tok_stripe_123",
  "pspHint": "STRIPE",
  "idempotencyKey": "checkout-session-xyz-123"
}
```

- `pspToken` – opaque token returned by a PSP mock tokenization endpoint.
- `pspHint` – optional hint (`"STRIPE"` or `"ADYEN"`) indicating the preferred PSP.
- `idempotencyKey` – unique key for this logical payment attempt (idempotency per link).

**Response 200 – success**

```json
{
  "paymentId": "pay_123",
  "status": "SUCCEEDED",
  "psp": "STRIPE",
  "amountUsd": 100.00,
  "amountMxn": 1720.00,
  "feeBreakdown": {
    "fixedFeeUsd": 2.00,
    "variableFeeUsd": 1.50,
    "totalFeeUsd": 3.50,
    "fxRate": 17.20
  }
}
```

**Response 4xx/5xx – failure**

```json
{
  "paymentId": "pay_123",
  "status": "FAILED",
  "psp": "ADYEN",
  "errorCode": "CARD_DECLINED",
  "message": "The card was declined"
}
```

**Behavior**

- Validates link status (must be `ACTIVE`, not expired).
- Computes FX and fees (and persists snapshots).
- Routes to primary PSP based on routing rules and `pspHint`.
- If primary PSP fails with a retriable error, may attempt secondary PSP.
- Enforces idempotency using `idempotencyKey` + `payment_link_id`.

---

## 3. Merchant / admin APIs

These endpoints are used by the **merchant portal** or internal tools.

### 3.1 Create payment link

**POST** `/api/payment-links`

Creates a new payment link for a merchant and recipient.

**Request body**

```json
{
  "merchantId": 1,
  "recipientId": 10,
  "amount": 100.00,
  "currency": "USD",
  "description": "Invoice #123",
  "expiresAt": "2025-12-31T23:59:59Z"
}
```

**Response 201**

```json
{
  "id": 42,
  "publicId": "pl_123",
  "slug": "abc123",
  "checkoutUrl": "https://checkout.example.com/pay/abc123",
  "status": "ACTIVE",
  "amount": 100.00,
  "currency": "USD",
  "description": "Invoice #123",
  "expiresAt": "2025-12-31T23:59:59Z",
  "createdAt": "2025-01-10T10:00:00Z"
}
```

**Errors**

- `404` – `MERCHANT_NOT_FOUND`, `RECIPIENT_NOT_FOUND`.
- `400` – validation errors (missing/invalid fields).

---

### 3.2 Get payment link by ID

**GET** `/api/payment-links/{id}`

Returns details of a payment link by its internal ID.

---

### 3.3 List payment links by merchant

**GET** `/api/payment-links?merchantId={merchantId}`

Lists payment links for a given merchant, ordered by creation date (desc).

**Query parameters**

- `merchantId` (**required**) – merchant internal ID.
- Optional pagination params can be added later (`page`, `size`).

**Response 200 – example**

```json
[
  {
    "id": 42,
    "publicId": "pl_123",
    "slug": "abc123",
    "status": "EXPIRED",
    "amount": 100.00,
    "currency": "USD",
    "createdAt": "2025-01-10T10:00:00Z",
    "expiresAt": "2025-01-12T10:00:00Z",
    "checkoutUrl": "https://checkout.example.com/pay/abc123",
    "preferredPsp": "STRIPE"
  },
  {
    "id": 43,
    "publicId": "pl_456",
    "slug": "def456",
    "status": "ACTIVE",
    "amount": 250.00,
    "currency": "USD",
    "createdAt": "2025-01-15T09:00:00Z",
    "expiresAt": null,
    "checkoutUrl": "https://checkout.example.com/pay/def456",
    "preferredPsp": "ADYEN"
  }
]
```

---

### 3.4 List payments for a payment link

**GET** `/api/payment-links/{id}/payments`

Returns all payment attempts associated with a single payment link.

(Shape of the response can mirror your `PaymentView`.)

---

## 4. PSP mock APIs

These endpoints simulate PSP SDKs and servers. They are used mainly for:

- Tokenizing card data from the frontend.
- Simulating charge creation from the backend.
- Forcing specific success/failure scenarios to test PSP routing and failover.

### 4.1 Tokenization (frontend → PSP mock)

#### 4.1.1 Stripe tokenization

**POST** `/api/psp/stripe/tokenize`

**Request body**

```json
{
  "cardNumber": "4242424242424242",
  "expMonth": 12,
  "expYear": 2030,
  "cvc": "123"
}
```

**Response 200 – example**

```json
{
  "token": "tok_stripe_123",
  "brand": "VISA",
  "last4": "4242"
}
```

#### 4.1.2 Adyen tokenization

**POST** `/api/psp/adyen/tokenize`

Same semantics, different token prefix (`tok_adyen_...`).

---

### 4.2 Mock charge endpoints (backend → PSP mock)

These are called internally by the PSP clients; they might be implemented as:

- `POST /internal/psp/stripe/charges`
- `POST /internal/psp/adyen/charges`

**Example request**

```json
{
  "token": "tok_stripe_123",
  "amount": 100.00,
  "currency": "USD",
  "idempotencyKey": "checkout-session-xyz-123",
  "metadata": {
    "paymentId": "pay_123"
  }
}
```

**Example response**

```json
{
  "chargeId": "ch_123",
  "status": "succeeded",
  "errorCode": null,
  "errorMessage": null
}
```

> These endpoints are usually not exposed publicly; they are part of the internal adapter layer for mocks.

---

### 4.3 Special test tokens

To demonstrate failover and error handling, the mocks can define **special tokens** that always trigger certain behaviors:

- `tok_force_stripe_fail` – Stripe mock always returns a **temporary error**.
- `tok_force_adyen_fail` – Adyen mock always returns a **temporary error**.
- `tok_force_decline` – Always returns a card declined error.

Document the actual tokens you support in the mocks so QA/reviewers can easily reproduce scenarios.

---

## 5. Webhook endpoints

These endpoints receive asynchronous notifications from the PSP mocks.

### 5.1 Stripe webhook

**POST** `/api/webhooks/stripe`

**Example body**

```json
{
  "eventId": "evt_123",
  "type": "payment.succeeded",
  "data": {
    "chargeId": "ch_123",
    "status": "succeeded",
    "amount": 100.00,
    "currency": "USD",
    "metadata": {
      "paymentId": "pay_123"
    }
  }
}
```

### 5.2 Adyen webhook

**POST** `/api/webhooks/adyen`

**Example body**

```json
{
  "eventId": "ady_evt_123",
  "type": "AUTHORISATION",
  "data": {
    "pspReference": "psp_123",
    "success": true,
    "amount": 100.00,
    "currency": "USD",
    "metadata": {
      "paymentId": "pay_123"
    }
  }
}
```

**Common behavior for all webhooks**

- Store the raw event in `WEBHOOK_EVENT` with:
  - `psp_event_id` (from `eventId`),
  - PSP type (Stripe/Adyen),
  - payload JSON.
- Ensure idempotency using a **unique constraint** on `psp_event_id`:
  - If the same event arrives again, it is ignored or treated as a no-op.
- Look up the `PAYMENT` by some identifier (`paymentId` in metadata).
- Update the `PAYMENT` status to match the event.

---

## 6. Error codes catalog (suggested)

Below is a list of suggested `code` values for errors. Keep this in sync with your global exception handler.

- `VALIDATION_ERROR`
- `MERCHANT_NOT_FOUND`
- `RECIPIENT_NOT_FOUND`
- `PSP_NOT_CONFIGURED`
- `PAYMENT_LINK_NOT_FOUND`
- `PAYMENT_LINK_EXPIRED`
- `PAYMENT_ALREADY_SUCCEEDED`
- `PSP_ERROR`
- `PSP_TEMPORARY_ERROR`
- `CARD_DECLINED`
- `IDEMPOTENT_REPLAY`

Example error response:

```json
{
  "code": "PAYMENT_LINK_EXPIRED",
  "message": "This payment link is no longer valid"
}
```

---

## 7. Swagger / OpenAPI

The service exposes an OpenAPI specification at runtime (if enabled):

- JSON: `GET /v3/api-docs`
- UI: `GET /swagger-ui.html`

OpenAPI should be treated as the **source of truth** for:

- Exact field names and types.
- Required vs optional fields.
- Possible HTTP status codes per endpoint.

This `API.md` file is a human-friendly overview and complements (but does not replace) the automatically generated API documentation.
