
# Payment Link System – USD → MXN Cross-Border Payments

This project implements a Payment Link system that allows merchants to charge customers in **USD** via hosted payment links and deliver **MXN** to recipients in Mexico.

It is based on the Kira case study and includes:

- A **hosted checkout** UI (owned by us, not PSP-hosted).
- **Dual PSP orchestration** (Stripe/Adyen mocks) with failover.
- A **fee & FX engine** (fixed, percentage, and FX-embedded fees).
- A **Spring Boot** backend and a **React/TypeScript** frontend.
- **AWS-based staging environment** (EC2 + RDS PostgreSQL + Redis + SSM) provisioned via Terraform.

> ⚠️ All external dependencies (PSPs, FX provider) are **mocked**. The goal is to show architecture & code quality, not real integrations.

---

## Repository structure

_Adjust paths to your actual repo layout if needed:_

```text
.
├─ backend/               # Spring Boot service (Payment Link API, PSP orchestration, fee engine)
├─ frontend/              # Public checkout + merchant portal (React/TypeScript)
├─ infra/                 # Terraform modules and environment definitions (AWS)
└─ docs/                  # Additional documentation
   ├─ ARCHITECTURE.md
   ├─ API.md
   └─ END_TO_END.md
```

---

## Quick start (local)

### Prerequisites

- Java 17+
- Maven or Gradle (depending on the project setup)
- Node.js 18+ and npm or yarn
- Docker (optional but recommended for running PostgreSQL locally)

### 1. Start PostgreSQL (local)

Example using Docker:

```bash
docker run --name payment-link-postgres \
  -e POSTGRES_DB=payment_link \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  -d postgres:16
```

> If you already have a local PostgreSQL instance, just make sure the DB name, user and password match your `application.yml`.

### 2. Run the backend

From the `backend/` directory:

```bash
# Maven
./mvnw spring-boot:run

# or Gradle
./gradlew bootRun
```

By default the API runs on `http://localhost:8080`.

- Database schema is managed via **Liquibase**.
- Swagger UI is exposed at: `http://localhost:8080/swagger-ui.html` (adjust if configured differently).

### 3. Run the frontend

From the `frontend/` directory:

```bash
npm install
npm run dev
```

Frontend typically runs on:

- `http://localhost:5173` (Vite), or
- `http://localhost:3000` (Create React App)

depending on your setup.

You may need to configure an environment variable (e.g. `VITE_API_BASE_URL`) to point the frontend to the backend URL.

---

## End-to-end flow

For a full “customer pays / merchant gets MXN” demo, see:

👉 [`docs/END_TO_END.md`](docs/END_TO_END.md)

That document walks through:

- Creating a merchant & fee config (if not pre-seeded).
- Creating a payment link.
- Opening the public checkout URL.
- Running a successful payment.
- Simulating PSP failures and failover.
- Verifying payments and fees in the portal and DB.

---

## Architecture & infrastructure

High-level design and key tradeoffs (modular monolith, PSP routing, FX snapshotting, AWS infra, Terraform structure) are documented in:

👉 [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)

Key points:

- **Modular monolith** in Spring Boot (payment links, payments, FX, fees, PSP orchestration, webhooks).
- **Relational schema** in PostgreSQL designed for:
  - Fast lookup by payment link slug.
  - Listing payments by merchant and time.
  - Auditable fee & FX breakdown per payment.
- **FX & fee engine** computes the full breakdown and persists snapshots.
- **PSP router** chooses primary/secondary PSP and handles failover.
- **Webhooks** are idempotent and stored in `WEBHOOK_EVENT`.

---

## API reference

Full list of backend endpoints (Payment Link CRUD, public checkout, PSP mocks, webhooks) and example requests/responses:

👉 [`docs/API.md`](docs/API.md)

Swagger/OpenAPI is also available at runtime:

- OpenAPI JSON: `/v3/api-docs`
- Swagger UI: `/swagger-ui.html`

Use Swagger as the source of truth for request/response fields and data types.

---

## Testing

### Backend tests

- **Unit tests**: fee engine, FX calculations, PSP routing, incentives.
- **Integration tests**:
  - End-to-end payment flow via mocks.
  - Dual PSP failover.
  - Idempotency (same idempotency key, same result).
  - Webhook processing and idempotency.

Run from `backend/`:

```bash
# Maven
./mvnw test

# Gradle
./gradlew test
```

You can document the detailed testing strategy separately in `docs/TESTING.md` if needed.

### Frontend tests (optional)

Depending on your setup:

- Component tests (e.g. React Testing Library).
- E2E tests (e.g. Playwright / Cypress) hitting the local backend.

---

## Deployment

For the case study, a single **staging** environment is provided on AWS.

- **Compute**: EC2 instance running the Spring Boot JAR.
- **Database**: RDS PostgreSQL.
- **Cache**: ElastiCache Redis.
- **Config/Secrets**: SSM Parameter Store.
- **IaC**: Terraform (under `infra/`).

### 1. Infrastructure (Terraform)

Example workflow:

```bash
cd infra/environments/staging
terraform init
terraform apply
```

Terraform will provision:

- VPC, subnets, Internet Gateway, route tables.
- EC2 instance with security groups.
- RDS PostgreSQL (private subnet).
- ElastiCache Redis (private subnet).
- SSM parameters for DB/Redis config.
- Basic CloudWatch logs/metrics.

### 2. Backend deploy (EC2)

Typical steps:

1. Build the JAR:

   ```bash
   cd backend
   ./mvnw clean package
   # or ./gradlew bootJar
   ```

2. Upload the JAR to the EC2 instance (e.g. with `scp` or S3 + script).
3. Configure environment variables on the instance, or use a script that loads from SSM (e.g. `export-staging-env.sh`).
4. Run the app:

   ```bash
   java -jar app.jar
   ```

For production-grade deployments, you might move to:

- Systemd service,
- Elastic Beanstalk,
- or ECS Fargate behind an ALB.

### 3. Frontend deploy (Vercel or similar)

1. Push the frontend to a Git repo.
2. Connect the repo to Vercel.
3. Set the environment variable pointing to the backend:

   - `VITE_API_BASE_URL=https://api.payment-link-staging.your-domain.com`

4. Deploy the project.

Make sure CORS on the backend allows your frontend domain.

---

## Security & compliance (high level)

- **PCI / card data**:
  - Card details are tokenized via **PSP mocks** before hitting the core API.
  - The backend receives only opaque `pspToken`s (no PAN/CVV), mimicking real-world Stripe/Adyen integrations.
  - No card data is stored in the database.

- **Secrets management**:
  - Sensitive configuration (DB URL, user, password, Redis host, PSP secrets) is stored in **AWS SSM Parameter Store** and loaded as environment variables.
  - No secrets are committed to version control.

- **Infrastructure security**:
  - RDS and Redis in private subnets, only reachable from the app’s security group.
  - EC2 accessible via SSH only from trusted IPs.
  - Traffic to the frontend and (future) API domain is served over HTTPS.

- **Data integrity & idempotency**:
  - Idempotency keys for payment processing to prevent double charges.
  - Webhook events persisted with unique `eventId` to guarantee idempotent processing.

Additional hardening (auth for merchant portal, webhook signing, rate limiting) can be added as next steps.

---

## Status and next steps

The MVP implements:

- Hosted checkout UI.
- Payment Link lifecycle (create, fetch, list).
- Fee/FX engine with per-payment FX snapshots.
- Basic PSP routing & failover with mocks.
- Webhook simulation and idempotency.
- Core test suite.
- AWS staging environment via Terraform.

Possible future work:

- Production-grade auth for merchant portal (JWT/OAuth).
- Advanced PSP routing policies and routing UI.
- Richer fee and incentive rules.
- Extended merchant reporting (exports, dashboards).
- Stronger observability stack (metrics, tracing, alerts).
