
# Payment Link System – Architecture & Infrastructure

This document describes the architecture and infrastructure of the Payment Link System and how it maps to the original case study requirements.

---

## 1. Context and goals

The system enables:

- Merchants to **create payment links** in USD.
- Payers to **complete card payments** via a hosted checkout page.
- Recipients in Mexico to **receive MXN** with a clear fee & FX breakdown.
- The platform to **orchestrate between two PSPs** (Stripe, Adyen – mocked) with failover.
- Accurate **fee & FX calculations**, including incentives.

---

## 2. High-level architecture

### 2.1 Components overview

**Frontend**

- **Public checkout page**
  - Opened via a URL of the form `https://<checkout-domain>/checkout/{slug}`.
  - Fetches payment link data and fee/FX breakdown from the backend.
  - Collects card details in the browser and calls a **PSP mock tokenization endpoint**, then sends only a PSP token to the core API.
- **Merchant portal**
  - Allows merchants to:
    - Create new payment links.
    - List existing payment links.
    - Inspect payments and their statuses.

**Backend (Spring Boot)**

- **Payment Link API**
  - CRUD for payment links (merchant/admin scope).
  - Public read endpoints for the checkout page.
- **Payment Orchestrator / PSP Router**
  - Validates the payment link and merchant configuration.
  - Calculates fees and FX.
  - Chooses the PSP (primary/secondary) and executes the payment.
  - Handles **failover** from primary to secondary PSP based on error types.
  - Enforces **idempotency** for payments.
- **Fee Engine**
  - Calculates:
    - Fixed fees (flat per transaction),
    - Percentage fees,
    - FX markup.
  - Persists fee breakdown per payment.
- **FX Service**
  - Calls a **mock FX provider** to obtain the USD→MXN rate.
  - Applies markups and stores a **snapshot** of the rate used per payment.
- **PSP Adapters (Mocks)**
  - `StripePspClientMock`, `AdyenPspClientMock`, etc.
  - Implement a common `PspClient` interface for operations like `charge`.
  - Can be configured to simulate success/failure scenarios via special tokens.
- **Webhook Handler**
  - Receives callbacks from PSP mocks.
  - Stores raw payloads in `WEBHOOK_EVENT`.
  - Updates `PAYMENT` status idempotently using a unique event ID.

**Supporting services**

- **PostgreSQL**
  - Main relational database.
- **Redis**
  - Used for caching FX rates or other transient data.
- **AWS SSM Parameter Store**
  - Stores secrets and configuration for the staging environment.
- **CloudWatch / logging**
  - Captures logs from the backend in staging.

The backend is implemented as a **modular monolith**: one Spring Boot app with clear internal module boundaries instead of multiple microservices.

---

## 3. Backend modules

Modules are logical (packages and services) within the Spring Boot application:

- `paymentlink`
  - Entities, repositories, services for `PAYMENT_LINK`, `PAYMENT`, `MERCHANT`, `RECIPIENT`.
- `fees`
  - Entities and services for `MERCHANT_FEE_CONFIG`, `PAYMENT_FEE`, incentives.
- `fx`
  - FX provider client (mock), caching, and `FX_RATE_SNAPSHOT`.
- `psp`
  - PSP entities (`PSP`, `PSP_ROUTING_RULE`).
  - PSP client implementations (Stripe/Adyen mocks).
  - Orchestrator that routes and fails over between PSPs.
- `webhooks`
  - Controllers and services to handle PSP webhooks, store `WEBHOOK_EVENT`, and update payments.

Each module exposes a service layer and uses repositories to interact with the database. Controllers depend only on the service interfaces, not directly on persistence.

---

## 4. Data model

### 4.1 Core entities

**MERCHANT**

- Represents the business using the platform.
- Key fields:
  - `id`, `name`, `created_at`
  - Additional configuration (optional): default PSP, status, etc.

**RECIPIENT**

- Represents the ultimate beneficiary in MXN.
- Key fields:
  - `id`, `merchant_id`, `name`, `created_at`
  - Bank details or account info (modelled at the level needed for the case study).

**PAYMENT_LINK**

- A link that a merchant sends to a customer.
- Key fields:
  - `id`
  - `public_id` – opaque identifier for external use.
  - `slug` – short string used in the URL; **unique** and indexed.
  - `merchant_id`, `recipient_id`
  - `amount`, `currency` (typically `USD`)
  - `status` (`ACTIVE`, `EXPIRED`, etc.)
  - `expires_at`, `created_at`, `updated_at`
  - Optional `description`

**PAYMENT**

- A payment attempt made against a `PAYMENT_LINK`.
- Key fields:
  - `id`
  - `payment_link_id`
  - `merchant_id`, `recipient_id`
  - `amount_usd`, `amount_mxn`
  - `status` (`PENDING`, `SUCCEEDED`, `FAILED`, etc.)
  - `psp_code` (e.g. `STRIPE`, `ADYEN`)
  - `psp_payment_id` / `psp_charge_id`
  - `idempotency_key`
  - `created_at`, `updated_at`

**MERCHANT_FEE_CONFIG**

- Configuration of fees per merchant.
- Key fields:
  - `id`, `merchant_id`
  - `fixed_fee_usd`
  - `variable_fee_percent`
  - `fx_markup_bps` (basis points to mark up FX rate)
  - Validity period / status if needed.

**PAYMENT_FEE**

- Concrete fee breakdown for a specific `PAYMENT`.
- Key fields:
  - `id`, `payment_id`
  - `fixed_fee_usd`
  - `variable_fee_usd`
  - `total_fee_usd`
  - `fx_rate_used`
  - `fx_markup_bps`

**FX_RATE_SNAPSHOT**

- Snapshot of the FX rate used for a given payment.
- Key fields:
  - `id`, `payment_id`
  - `base_currency`, `quote_currency` (e.g. `USD` → `MXN`)
  - `rate`
  - `provider` (mock code)
  - `created_at`

**INCENTIVE_RULE / PAYMENT_INCENTIVE**

- Optional advanced incentives:
  - Example: first N transactions discounted or zero fee.
- Represent rule definitions and concrete incentive applications.

**PSP**

- Represents a PSP like Stripe or Adyen.
- Key fields:
  - `id`, `code`, `name`
  - `active` flag.

**PSP_ROUTING_RULE**

- Defines routing preferences per merchant, card, or currency.
- Key fields:
  - `id`, `merchant_id`
  - `currency`
  - `primary_psp_id`, `secondary_psp_id`
  - Additional conditions if required.

**WEBHOOK_EVENT**

- Stores raw webhook information.
- Key fields:
  - `id`
  - `psp_event_id` – unique identifier from PSP (or generated by mock).
  - `psp_code`
  - `payload` (JSON)
  - `received_at`
  - `processed` flag / timestamps.

---

### 4.2 Indexing strategy

To support the expected queries:

- `PAYMENT_LINK.slug` → **unique index**  
  - Fast lookup from the public checkout.
- `PAYMENT.merchant_id, PAYMENT.created_at` → index  
  - Efficient listing of payments by merchant and date.
- `PAYMENT.payment_link_id` → index  
  - Show all payments related to one link.
- `WEBHOOK_EVENT.psp_event_id` → **unique index**  
  - Ensures webhook idempotency.
- Standard indexes on foreign keys (merchant, recipient, PSP, etc.).

---

## 5. Payment flow

This section ties together the modules and data model.

### 5.1 Creating a payment link (merchant portal)

1. Merchant uses the portal (frontend) to send a `POST /api/payment-links` request with:
   - `merchantId`, `recipientId`, `amount`, `currency`, `description`, `expiresAt`.
2. Backend validates:
   - Merchant and recipient exist.
   - Amount and currency are valid.
3. Backend creates a `PAYMENT_LINK`:
   - Generates `public_id` and `slug`.
   - Derives `checkoutUrl` using `payment-link.public-base-url`.
4. Response returns the newly created link including `slug` and `checkoutUrl`, which the merchant can copy and share.

### 5.2 Public checkout

1. Customer opens `https://<checkout-domain>/checkout/{slug}`.
2. Frontend calls:
   - `GET /api/public/payment-links/{slug}`.
3. Backend:
   - Finds `PAYMENT_LINK` by `slug`.
   - Verifies the link is `ACTIVE` and not expired.
   - Uses **Fee Engine** and **FX Service** to compute a `FeeBreakdown`:
     - `fixedFeeUsd`, `variableFeeUsd`, `totalFeeUsd`.
     - `fxRate`, `payoutMxn`.
4. The response populates the checkout UI with:
   - Merchant, recipient, amount.
   - Fee and FX breakdown.

### 5.3 Tokenization & payment processing

**Tokenization:**

1. Customer enters card details in the checkout.
2. Frontend calls **PSP mock tokenization** endpoint:
   - e.g. `POST /api/psp/stripe/tokenize`.
3. PSP mock returns:
   - `token`, `brand`, `last4`.

**Payment request:**

4. Frontend calls:
   - `POST /api/public/payment-links/{slug}/payments`  
   - With `{ pspToken, pspHint, idempotencyKey }`.
5. Backend:
   - Validates link and merchant again.
   - Evaluates fee/FX and persists `PAYMENT`, `PAYMENT_FEE`, `FX_RATE_SNAPSHOT`.
   - Uses **PSP Router** to pick primary/secondary PSP.
   - Calls `PspClient.charge(...)` with token and idempotency key.
6. On success:
   - Updates `PAYMENT` status to `SUCCEEDED`.
   - Returns final state to frontend, including PSP used and fee/FX breakdown.
7. On failure:
   - If error is temporary and primary PSP has a configured fallback:
     - Tries secondary PSP.
   - Otherwise marks `PAYMENT` as `FAILED` with appropriate error code.

### 5.4 Webhooks

Depending on configuration, PSP mocks can:

- Immediately send a webhook to:
  - `POST /api/webhooks/stripe`
  - `POST /api/webhooks/adyen`
- Or require manual calling for demo.

Webhook handler:

1. Receives event with `eventId`, event type and nested data.
2. Checks if an entry with the same `psp_event_id` already exists in `WEBHOOK_EVENT`.
   - If yes → ignore (idempotent).
3. Stores the raw event payload.
4. Resolves the corresponding `PAYMENT` (e.g. from metadata).
5. Updates `PAYMENT` status according to the event (e.g. `SUCCEEDED`, `FAILED`).

---

## 6. PSP routing & failover

### 6.1 Routing logic

Routing is driven by `PSP_ROUTING_RULE` and possibly a hint from the frontend:

- For a given `merchantId` and `currency`:
  - Determine **primary PSP** (e.g. Stripe).
  - Determine **secondary PSP** (e.g. Adyen).

The orchestrator:

1. Optionally considers `pspHint` from the request (e.g. to force Stripe in a test).
2. Selects the main PSP client.
3. On retriable errors from primary, tries secondary if configured.

### 6.2 Failover scenarios

Some tokens are used to simulate failures:

- `tok_force_stripe_fail` – Stripe fails with a retriable error.
- `tok_force_adyen_fail` – Adyen fails with a retriable error.
- `tok_force_all_fail` or `tok_force_decline` – both PSPs fail.

The orchestrator:

- Records which PSP was attempted and the outcome.
- If secondary succeeds, the payment is `SUCCEEDED` with `psp = secondary`.
- If both fail, the payment is `FAILED` with `PSP_ERROR` or more specific code.

---

## 7. Idempotency

Idempotency is crucial to avoid double charges.

The system uses:

- A per-payment **idempotency key**, typically a combination like:
  - `payment_link_id` + client-provided `idempotencyKey`.
- A unique constraint or lookup table to guarantee:
  - The same logical request returns the same `PAYMENT` result.
  - Retries do not create multiple `PAYMENT` rows or double-charge PSP.

On the PSP side:

- PSP mocks also receive an idempotency key and are expected to behave idempotently.

On the webhook side:

- `WEBHOOK_EVENT.psp_event_id` is unique to avoid processing the same event twice.

---

## 8. Infrastructure (staging environment)

The staging environment runs on **AWS**, provisioned via Terraform (in a separate `payment-link-infra` repo or an `infra/` folder).

### 8.1 Components

- **VPC**
  - Public subnet for EC2.
  - Private subnets for RDS and Redis.
- **EC2**
  - Hosts the Spring Boot JAR.
  - Security group allowing:
    - HTTP/HTTPS from the internet (for demo).
    - SSH only from trusted IP(s).
- **RDS PostgreSQL**
  - Deployed in private subnets.
  - Not publicly accessible.
- **ElastiCache Redis**
  - Deployed in private subnets.
- **SSM Parameter Store**
  - Holds configuration and secrets:
    - `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
    - `REDIS_HOST`
    - `PAYMENT_LINK_PUBLIC_BASE_URL`
    - `SPRING_PROFILES_ACTIVE=aws`
- **CloudWatch**
  - Collects logs from the EC2 instance.
  - Can be extended with alarms.

### 8.2 Deployment flow (staging)

1. Build the backend JAR (`mvn package` or `gradle bootJar`).
2. Upload the JAR to an S3 bucket (e.g. `payment-link-staging-artifacts`).
3. Connect to the EC2 instance and download the JAR.
4. Load environment variables/SSM parameters via a script (`export-staging-env.sh`).
5. Run the application:

   ```bash
   java -jar app.jar
   ```

The frontend (e.g. Vercel) is configured to point to the EC2 public endpoint for the API.

---

## 9. Security & compliance (architecture level)

This architecture includes several security measures aligned with the case study:

- **PCI / card data handling**
  - Card data is never stored in the core system.
  - The backend receives only PSP tokens, not raw PAN/CVV.
  - The behavior mimics using real PSP SDKs (Stripe/Adyen) where the browser handles card details and only tokens flow through the API.

- **Secrets management**
  - Sensitive values (DB credentials, Redis host, etc.) are stored in **SSM Parameter Store**.
  - Application reads them as environment variables at startup.
  - Secrets are not committed to source control.

- **Network isolation**
  - DB and Redis in private subnets, accessible only from the app’s security group.
  - The app instance is the only public entry point.

- **Transport & data encryption**
  - Staging frontends use HTTPS (e.g. Vercel).
  - For a more production-like setup, TLS termination would sit at an ALB in front of EC2 and RDS encryption at rest would be enabled via AWS settings.

- **Data integrity**
  - Idempotency keys for payment processing.
  - Webhook events stored with unique `psp_event_id`.

Further hardening (beyond this case study) would include:

- Authentication & authorization for the merchant portal and APIs.
- HMAC signature verification for webhooks.
- Rate limiting and WAF.
- More advanced observability (metrics, tracing, dashboards).

---

## 10. Evolution and tradeoffs

### 10.1 Why a modular monolith?

- **Pros**
  - Faster development.
  - Simpler deployment (single artifact).
  - Easier to reason about for a case study / single dev.

- **Cons**
  - All modules share the same resources and failure domain.
  - Scaling is coarse-grained (scale the entire app).

The internal modularization is done with an eye toward **future extraction** of services:

- `FX` could become its own microservice.
- `PSP Orchestrator` could be separated if multi-team or high throughput is needed.
- `Merchant configuration` and `Reporting` could also be split.

### 10.2 Future extensions

- Add real PSP integrations while preserving the same interfaces.
- Introduce a dedicated FX provider client if moving beyond mocks.
- Add a reporting module for merchants (exports, dashboards, reconciliation views).
- Add full observability (metrics, tracing, logging correlation).

---

This document explains how the current implementation satisfies the product and technical requirements of the Payment Link case study, while leaving clear paths for future evolution toward a more production-grade architecture.
