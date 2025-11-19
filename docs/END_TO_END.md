
# End-to-End Flow – Payment Link System

This guide explains how to run the full flow:

> Merchant creates a payment link → Customer opens checkout → Payment is processed via PSP mock → Webhooks update status → Merchant sees final MXN payout.

---

## 1. Prerequisites

- Backend running on `http://localhost:8080`.
- Frontend running on something like `http://localhost:5173` (Vite) or `http://localhost:3000` (CRA).
- PostgreSQL with the Liquibase schema applied.
- Optional: Redis (if used locally for FX caching).

If you haven't set up the environment yet, follow the root [`README.md`](../README.md) first.

---

## 2. Seed initial data

You need at least:

- One `MERCHANT`
- One `RECIPIENT` linked to that merchant
- PSP configuration (`PSP`, `PSP_ROUTING_RULE`)
- A basic `MERCHANT_FEE_CONFIG`

Depending on the project, this may be done via:

1. **Liquibase changelog with seed data**.
2. A **`data.sql`** file.
3. Manual SQL inserts (for local testing).

Example SQL (simplified):

```sql
INSERT INTO merchant (id, name, created_at)
VALUES (1, 'Demo Merchant', now());

INSERT INTO recipient (id, merchant_id, name, created_at)
VALUES (10, 1, 'Juan Perez', now());

INSERT INTO psp (id, code, name, active)
VALUES (1, 'STRIPE', 'Stripe Mock', true),
       (2, 'ADYEN',  'Adyen Mock',  true);

INSERT INTO psp_routing_rule (id, merchant_id, currency, primary_psp_id, secondary_psp_id)
VALUES (1, 1, 'USD', 1, 2);

INSERT INTO merchant_fee_config (id, merchant_id, fixed_fee_usd, variable_fee_percent, fx_markup_bps)
VALUES (1, 1, 2.00, 1.5, 50);
```

Adapt the actual columns and table names to your schema if they differ.

---

## 3. Create a payment link (merchant portal or API)

### Option A – via merchant portal UI

1. Open the merchant portal: `http://localhost:5173` (or your frontend URL).
2. Go to **Create Payment Link**.
3. Fill:
   - Merchant: `Demo Merchant`
   - Recipient: `Juan Perez`
   - Amount: `100.00`
   - Currency: `USD`
   - Description: `Invoice #123`
   - Expiry: choose a future date/time.
4. Submit the form.

The UI should show the generated **slug** and **checkout URL** (e.g. `http://localhost:5173/pay/abc123` or `https://checkout.local/pay/abc123` depending on your routing).

### Option B – via API (curl)

```bash
curl -X POST http://localhost:8080/api/payment-links   -H "Content-Type: application/json"   -d '{
    "merchantId": 1,
    "recipientId": 10,
    "amount": 100.00,
    "currency": "USD",
    "description": "Invoice #123",
    "expiresAt": "2025-12-31T23:59:59Z"
  }'
```

The response will contain the `slug` and `checkoutUrl`.

---

## 4. Open the public checkout

1. In a browser (incognito window recommended), open the **checkout URL**:
   - Example: `http://localhost:5173/pay/abc123`
   - Or if the frontend just uses the slug, something like `http://localhost:5173/checkout/abc123`.
2. The page should display:
   - Merchant name.
   - Amount and currency.
   - Fee and FX breakdown (USD → MXN).
   - A card form (number, expiry, CVC) plus a **Pay** button.

Under the hood the frontend calls:

- `GET /api/public/payment-links/{slug}` to render the link and fee preview.

---

## 5. Run a successful payment

1. Enter a **test card** (e.g. `4242 4242 4242 4242`) and any valid expiry/CVC.
2. Click **Pay**.

The frontend will:

1. Call the **mock PSP tokenization endpoint**:
   - `POST /api/psp/stripe/tokenize` (or `/api/psp/adyen/tokenize` depending on UI).
2. Receive a token (e.g. `tok_stripe_123`).
3. Call:
   - `POST /api/public/payment-links/{slug}/payments`
   - With `{ pspToken, pspHint, idempotencyKey }`.

The backend orchestrator will:

- Validate the payment link (status, expiry, currency).
- Fetch FX rate and fees.
- Route to primary PSP (Stripe or Adyen mock).
- Store `PAYMENT`, `PAYMENT_FEE`, and `FX_RATE_SNAPSHOT`.
- Return a final status (e.g. `SUCCEEDED`).

The UI should show a **success state** and optionally the MXN amount delivered.

---

## 6. Verify the result

You can verify the payment in three ways:

1. **Merchant portal**:
   - Go to the **Payments** or **Payment Links** section.
   - Open the details of your payment link.
   - You should see the new `PAYMENT` with status `SUCCEEDED` (and PSP used).

2. **Database**:
   - Query `PAYMENT`, `PAYMENT_FEE`, `FX_RATE_SNAPSHOT` tables.
   - Check that the stored amounts and rates match what was shown in the checkout.

3. **Logs**:
   - Check backend logs for PSP calls, routing decisions, FX calls, and webhook processing (if webhooks are simulated asynchronously).

---

## 7. Test PSP failover

To validate dual-PSP behavior, use **special test tokens** (adapt to the tokens you actually implemented).

Example scenarios:

### 7.1 Force Stripe to fail → expect failover to Adyen

- Use a card or token that leads the Stripe mock to fail, for example:
  - Token like `tok_force_stripe_fail`, or
  - A special card number your mock maps to a failure.
- Expected behavior:
  - Stripe mock returns a retriable error (e.g. `PSP_TEMPORARY_ERROR`).
  - The orchestrator retries on Adyen as secondary.
  - Final payment status is `SUCCEEDED`, with `psp = ADYEN`.
  - Logs show:
    - Failed attempt on Stripe.
    - Successful attempt on Adyen.

### 7.2 Force both PSPs to fail

- Use another special token (e.g. `tok_force_all_fail`) that fails on both mocks.
- Expected behavior:
  - Final payment status is `FAILED`.
  - Error returned to the frontend (e.g. `PSP_ERROR`).
  - No duplicate charges in DB, a single `PAYMENT` row.

---

## 8. Test idempotency

1. Trigger a payment with a specific `idempotencyKey`, e.g. `order-123`:

   ```bash
   curl -X POST http://localhost:8080/api/public/payment-links/{slug}/payments      -H "Content-Type: application/json"      -H "Idempotency-Key: order-123"      -d '{
       "pspToken": "tok_stripe_123",
       "pspHint": "STRIPE",
       "idempotencyKey": "order-123"
     }'
   ```

   (Whether you pass the key in a header or body depends on your actual implementation.)

2. Repeat **the exact same request** (same `idempotencyKey` and `slug`).

Expected result:

- Backend returns the **same** `PAYMENT` result.
- Only one row in `PAYMENT` for that link + idempotency key.
- No second PSP charge (verify logs if needed).

---

## 9. Test webhooks

Depending on how your mocks work, webhooks may:

- Be triggered automatically by the PSP mock after a charge.
- Require manual triggering for the case study.

### 9.1 Manual webhook example (Stripe)

```bash
curl -X POST http://localhost:8080/api/webhooks/stripe   -H "Content-Type: application/json"   -d '{
    "eventId": "evt_manual_123",
    "type": "payment.succeeded",
    "data": {
      "chargeId": "ch_manual_123",
      "status": "succeeded",
      "amount": 100.00,
      "currency": "USD",
      "metadata": {
        "paymentId": "pay_123"
      }
    }
  }'
```

Expected behavior:

- A new row in `WEBHOOK_EVENT`.
- `PAYMENT` status updated (e.g. from `PENDING` to `SUCCEEDED`).
- Re-sending the same `eventId` should be a **no-op** thanks to a unique index on `psp_event_id`.

---

## 10. Staging environment flow (AWS)

For a cloud-based demo with EC2 + RDS + Redis:

1. **Deploy infra with Terraform**:
   - Go to `infra/environments/staging/`.
   - Run `terraform init` and `terraform apply`.
2. **Build backend JAR**:
   - From `backend/`: `./mvnw clean package` or `./gradlew bootJar`.
3. **Upload & run JAR on EC2**:
   - Copy the JAR to EC2 (e.g. with `scp`).
   - Configure environment variables or use SSM parameters for:
     - `SPRING_DATASOURCE_URL`
     - `SPRING_DATASOURCE_USERNAME`
     - `SPRING_DATASOURCE_PASSWORD`
     - Redis host/port, etc.
   - Run the app with `java -jar app.jar`.
4. **Configure frontend** (e.g. Vercel):
   - Set `VITE_API_BASE_URL` or equivalent to the public API URL:
     - e.g. `https://api.payment-link-staging.your-domain.com`
   - Deploy frontend.

End-to-end steps are the same as local, but using:

- Public API endpoint (`https://api...`).
- Public checkout URL (`https://checkout.../pay/{slug}`).

Make sure:

- CORS is configured to allow your frontend domain.
- Security groups / ALB rules allow HTTP/HTTPS from the internet to the API.

---

## 11. Troubleshooting checklist

- **404 on `GET /api/public/payment-links/{slug}`**
  - Payment link does not exist, is not `ACTIVE`, or is expired.
  - Check DB: `PAYMENT_LINK` row, `status`, `expires_at`.

- **400 on `POST /payments`**
  - Missing `pspToken` or `idempotencyKey`.
  - Invalid or expired link.
  - Validation error body should include a `code` and message.

- **502/500 from API**
  - PSP mock failing unexpectedly or network issue.
  - Check backend logs.
  - Verify environment variables (DB URL, Redis, etc.).

- **DB schema mismatch**
  - Verify that all Liquibase changesets have been applied.
  - Compare actual DB schema with `docs/ARCHITECTURE.md`.

If something still looks off, the best debugging steps are:

1. Check backend logs (routing decisions, PSP calls, FX/fee logs).
2. Inspect DB rows in `PAYMENT_LINK`, `PAYMENT`, `PAYMENT_FEE`, `FX_RATE_SNAPSHOT`.
3. Confirm that the frontend is calling the expected endpoints (check browser dev tools).
