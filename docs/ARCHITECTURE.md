# Payment Link Service – Arquitectura & Convenciones

Este documento describe la arquitectura, estructura de paquetes y convenciones que **cualquier desarrollador o asistente de IA (Codex, etc.)** debe seguir al trabajar en este proyecto.

## 1. Visión general

Servicio backend para un **Payment Link System** (moneda origen → destino) que permite:

- Crear enlaces de pago (payment links)
- Mostrar vista previa de tarifas (fee preview)
- Procesar pagos a través de PSPs (Stripe/Adyen mock)
- Manejar webhooks de PSP
- Calcular diferentes tipos de comisiones (fees) y aplicar reglas de incentivos

Stack principal:

- **Java 17**
- **Spring Boot 3**
- **Maven**
- **PostgreSQL**
- **Flyway** (migraciones)
- **Spring Data JPA**
- **springdoc-openapi** (Swagger UI)
- **JUnit 5 + Mockito**

Proyecto como **monolito modular**, organizado por capas y features.

---

## 2. Estructura de paquetes (capas)

Paquete raíz:

```text
com.kira.payment.paymentlinkbe
 ├─ api            // Capa de borde HTTP: Controllers + DTOs (REST)
 ├─ application    // Casos de uso / orquestación
 ├─ domain         // Modelo de dominio + lógica de negocio
 ├─ infrastructure // Adaptadores a infraestructura (DB, PSP, FX, etc.)
 └─ config         // Configuración de Spring, Swagger, etc.
```

### 2.1. `api` (controllers + DTOs)

```text
com.kira.payment.paymentlinkbe.api
 ├─ paymentlink   // Controllers + DTOs para payment links
 ├─ payment       // Controllers + DTOs para pagos
 ├─ psp           // Controllers para webhooks de PSP
 └─ error         // Manejo global de errores (handlers, error response)
```

Reglas:

- **No lógica de negocio** aquí.
- Solo mapeo HTTP ↔ DTOs ↔ comandos de aplicación.
- Usar `@RestController`, `@RequestMapping`, `@Valid`, etc.

### 2.2. `application` (casos de uso)

```text
com.kira.payment.paymentlinkbe.application
 ├─ paymentlink   // Casos de uso: crear link, obtener link con fees
 ├─ payment       // Casos de uso: procesar pago, actualizar estado
 ├─ psp           // Orquestación de PSP, failover
 ├─ fee           // Servicios de cálculo de tarifas (usa dominio)
 └─ common        // Servicios compartidos (si se necesitan)
```

Reglas:

- Orquesta flujos: coordina repos, fee engine, PSPs, etc.
- Contiene **servicios de aplicación**, típicamente anotados con `@Service`.
- No depende de detalles concretos de infra (idealmente va contra interfaces).

### 2.3. `domain` (modelo de negocio)

```text
com.kira.payment.paymentlinkbe.domain
 ├─ paymentlink   // PaymentLink, reglas de expiración, estados
 ├─ payment       // Payment, PaymentStatus, invariantes
 ├─ fee           // FeeEngine, FeeConfig (modelo de dominio), FeeBreakdown
 ├─ psp           // Interfaces y modelos de PSP (PspClient, PspChargeResult, etc.)
 └─ shared        // Value Objects (Money, Currency, ids), enums comunes
```

Reglas:

- **Lógica de negocio pura**.
- Idealmente sin anotaciones de Spring/JPA (o las mínimas si es inevitable).
- Aquí viven las reglas del fee engine, validaciones de negocio, etc.

### 2.4. `infrastructure` (adaptadores)

```text
com.kira.payment.paymentlinkbe.infrastructure
 ├─ persistence
 │   ├─ paymentlink   // Entidades JPA + repos de payment links
 │   ├─ payment       // Entidades JPA + repos de pagos
 │   ├─ fee           // Entidades JPA relacionadas a fees/configs
 │   ├─ psp           // Entidades JPA para PSP, routing rules
 │   └─ fx            // Entidades JPA para snapshots FX (si aplican)
 ├─ psp
 │   ├─ stripe        // Mock Stripe (clients, simulación)
 │   └─ adyen         // Mock Adyen
 ├─ fx                // Implementación mock de proveedor FX
 └─ config            // Config de infra específica (Jackson, converters, etc.)
```

Reglas:

- Aquí van:
  - entidades JPA (`@Entity`),
  - repositorios (`extends JpaRepository`),
  - adaptadores concretos (PSP mocks, FX provider mock, etc.).
- No meter lógica de negocio compleja aquí.

## 3. Entidades principales (persistencia)

Ubicación recomendada: `com.kira.payment.paymentlinkbe.infrastructure.persistence.<feature>`.

Entidades JPA actuales (a ubicar por feature):

- **Payment Link / Pagos**
  - `PaymentLink`
  - `Payment`
  - `PaymentFee`
  - `PaymentIncentive`
  - `WebhookEvent`

- **Merchant / Fees / Incentivos**
  - `Merchant`
  - `MerchantFeeConfig`
  - `IncentiveRule`

- **PSP / Routing**
  - `Psp`
  - `PspRoutingRule`

- **FX**
  - `FxRateSnapshot`

- **Otros**
  - `Recipient`

Enums (pueden vivir en `domain.*` si son de negocio o en `infrastructure.persistence.*` si son puramente de persistencia):

- `DiscountType`
- `PaymentFeeType`
- `PaymentLinkStatus`
- `PaymentStatus`

> **Regla:** si un enum expresa una regla de negocio (por ejemplo estados de Payment o PaymentLink), debe estar en `domain.*` y luego se mapea desde/hacia la entidad JPA.

---

## 4. Alcance del MVP (casos de uso backend)

### 4.1. Payment Link

1. **Crear enlace de pago**
   - Input: merchantId, recipientId, amount, currency, description, expiresAt (opcional)
   - Output: `PaymentLinkResponse` con `publicId`, `slug`, `checkoutUrl`, estado inicial.

2. **Obtener enlace de pago + fee preview**
   - Input: `slug` o `publicId`
   - Output: datos del link + `feeBreakdown` calculado por el Fee Engine.

### 4.2. Pagos

3. **Procesar pago**
   - Input: `publicId` o `slug` del link + `pspToken`.
   - Flujo:
     - Valida estado y expiración del PaymentLink.
     - Llama al `PspOrchestrator` (PSP primario → secundario en caso de fallo).
     - Crea `Payment` + `PaymentFee` + `PaymentIncentive` según aplique.
     - Actualiza estado del PaymentLink si es exitoso.

4. **Webhooks PSP (mock)**
   - Input: eventos de Stripe/Adyen mock.
   - Actualiza estado de `Payment` y `PaymentLink`.

---

## 5. Endpoints REST (MVP)

Base path sugerido: `/api`.

### 5.1. Payment Links

- `POST /api/payment-links`
  - Crea un nuevo PaymentLink.
- `GET /api/payment-links/{slug}`
  - Devuelve el PaymentLink + fee preview.

### 5.2. Pagos

- `POST /api/payment-links/{slug}/pay`
  - Procesa el pago usando el orquestador PSP y el `pspToken`.

### 5.3. Webhooks PSP

- `POST /api/psp/webhook/stripe`
- `POST /api/psp/webhook/adyen`

---

## 6. Guía para asistentes de IA (Codex, etc.)

Siempre que generes código para este proyecto:

1. **Respeta la estructura de paquetes:**
   - Controllers/DTOs → `com.kira.payment.paymentlinkbe.api.<feature>`
   - Casos de uso → `com.kira.payment.paymentlinkbe.application.<feature>`
   - Dominio → `com.kira.payment.paymentlinkbe.domain.<feature>`
   - Entidades JPA/Repos → `com.kira.payment.paymentlinkbe.infrastructure.persistence.<feature>`
   - PSP mocks → `com.kira.payment.paymentlinkbe.infrastructure.psp.<pspName>`
   - FX mocks → `com.kira.payment.paymentlinkbe.infrastructure.fx`

2. **Incluye SIEMPRE el `package` correcto al inicio de cada archivo nuevo.**

3. **No pongas lógica de negocio en controllers**.
   - Usa servicios de aplicación en `application.*`.

4. **No crees nuevas estructuras de paquetes arbitrarias**.
   - Usa solo las mencionadas aquí, o extiéndelas de forma consistente (por ejemplo, `api.fee` si en el futuro hay endpoints solo de fees).

5. **Usa nombres consistentes**:
   - `PaymentLinkApplicationService` para casos de uso.
   - `PaymentLinkRepository` para repositorio JPA.
   - `CreatePaymentLinkRequest`, `PaymentLinkResponse` para DTOs.

6. Si necesitas dudas sobre el modelo, **revisa este archivo antes de inventar entidades o paquetes nuevos**.

---
