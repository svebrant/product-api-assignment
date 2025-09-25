# 🧩 Country-Based Product API — Technical Assessment

You will build a small but complete system consisting of **two cooperating services**:

1. **Product Service** – Manages products, calculates final prices, and exposes a trigger to ingest data.
2. **Discount Service** – Manages discounts.

---

## 📦 Problem Description

Implement two services that together provide a country-based product API with discounts and ingestion workflow.

### Service A — Product API
- Manages a catalog of **pre-seeded products** (in-memory or database).
- Applies **country-specific tax rules** to product prices.
- Fetches **active discounts** from the Discount Service.
- Calculates and returns the **final price** (after tax and discounts).
- Delegates **applying a discount** to the Discount Service.
- Provides an admin endpoint to **ingest products and discounts from files**.

### Service B — Discount Service
- Stores applied discounts per product.
- Avoids duplication: the same `discountId` can only be applied once per product.
- Provides endpoints for applying discounts and fetching active discounts.

---

## 🔹 Data Model

### Product
- `id`: Unique identifier
- `name`: Product name
- `basePrice`: Price before tax or discount (decimal/number)
- `country`: Country the product belongs to (`Sweden`, `Germany`, `France`)

### Discount
- `productId`: Target product id
- `discountId`: Unique discount identifier (idempotency key)
- `percent`: Discount percentage (0–100, exclusive of 0)

### Country VAT Rules (hardcoded)

| Country  | VAT  |
|----------|------|
| Sweden   | 25%  |
| Germany  | 19%  |
| France   | 20%  |

---

## 📡 API Endpoints

### Service A — Product API

#### `GET /products?country={country}`
Returns all products for the given country, including **final price**.

**Example Response**
```json
[
  {
    "id": "1",
    "name": "T-shirt",
    "basePrice": 100.0,
    "country": "Sweden",
    "taxedPrice": 112.5
  }
]
```

#### `POST /products/{id}/discount`
Applies a discount to a product by delegating to the Discount Service (idempotent per `discountId`).

**Request**
```json
{
  "discountId": "summer2025",
  "percent": 10.0
}
```

**Response**
```json
{
  "productId": "1",
  "discountId": "summer2025",
  "percent": 10.0,
  "applied": true,
  "alreadyApplied": false
}
```

#### **`POST /admin/ingest`**
Triggers ingestion of predefined files (see **Data Ingestion** below).  
Takes optional query parameters to control concurrency:

- `workers` (int, default e.g. 4): number of sub-workers to spawn
- `chunkSize` (int, default e.g. 100): number of records per chunk/batch
- `mode` (`products|discounts|all`, default `all`): which data to ingest
- `failFast` (`true|false`, default `false`): whether to stop on first error
- `retries` (int, default e.g. 2): max retry attempts per failing record
- `dryRun` (`true|false`, default `false`): parse/validate only (no writes)

**Response (example)**
```json
{
  "startedAt": "2025-09-23T10:00:00Z",
  "mode": "all",
  "workers": 4,
  "chunkSize": 100,
  "dryRun": false,
  "ingestionId": "ing-20250923-100000-abc123",
  "status": "started"
}
```

#### **`GET /admin/ingest/{ingestionId}/status`**
Returns ingestion progress.

**Response (example)**
```json
{
  "ingestionId": "ing-20250923-100000-abc123",
  "status": "running",
  "filesDiscovered": 3,
  "filesProcessed": 2,
  "products": { "parsed": 1200, "ingested": 1188, "failed": 12 },
  "discounts": { "parsed": 500, "ingested": 498, "failed": 2, "deduplicated": 7 },
  "errorsSample": [
    { "file": "discounts.ndjson", "line": 342, "reason": "duplicate discountId for productId=42" }
  ],
  "startedAt": "2025-09-23T10:00:00Z",
  "updatedAt": "2025-09-23T10:03:12Z"
}
```

> You can choose to keep ingestion state in memory or persist it.

---

### Service B — Discount Service

#### `PUT /discounts/apply`
Applies a discount.

**Request**
```json
{ "productId": "1", "discountId": "summer2025", "percent": 10.0 }
```

**Response**
```json
{ "applied": true, "alreadyApplied": false }
```

#### `GET /discounts/{productId}`
Returns all active discounts for the product.

---

## 🔑 Authentication

Both services use **Bearer authentication**.  
Every request must include:

```
Authorization: Bearer secret-dev-token-please-change
```

- Product API validates client requests.
- Product API calls Discount Service with the same token.
- Discount Service validates all incoming requests.

You may configure the token through an environment variable.

---

## 🛠 Tech Stack

- **Language:** Kotlin
- **Framework:** Ktor
- **Concurrency:** Coroutines (required)
- **Database:** In-memory (minimum). A NoSQL database (e.g., MongoDB) is optional.
- **Build:** Gradle (preferred)
- **Containerization:** Docker + Docker Compose

---

## ⚙️ Data Ingestion 

### Data Files
Place files under a dedicated directory (e.g., `./data`):

- `products.ndjson`
- `discounts.ndjson`

**NDJSON product line example**
```json
{ "id": "101", "name": "Socks", "basePrice": 5.0, "country": "Germany" }
```

**NDJSON discount line example**
```json
{ "productId": "101", "discountId": "black-friday-2025", "percent": 15.0 }
```

### Observability & Status
- Track progress and expose via `GET /admin/ingest/{id}/status`.
- Consider basic metrics (counts, failed lines, deduplications, duration).
- Log a concise summary when ingestion completes.

---

## 🔬 Validation & Error Handling

- Validate payloads and file rows (schema, ranges, enums).
- Respond with appropriate status codes and clear error messages.
- In ingestion, collect and surface a **sample** of failures in the status response.

---

## 🧪 Requirements (Summary)

- Two Ktor services (`Product Service`, `Discount Service`).
- Country tax rules are hardcoded in Product Service.
- Discounts are idempotent per `(productId, discountId)`.
- **Authentication:** static Bearer token on both services.
- **Documentation:** `README.md` + **`ARCHITECTURE.md` with Mermaid diagrams**.

---

## 🚀 Running the Services
A `docker-compose.yml` can start both services together.

---

## 📝 Candidate Deliverables

1. **Code** for both services, runnable locally or via Docker Compose.
2. **README.md** with run instructions and a brief overview of ingestion controls.
3. **ARCHITECTURE.md** including:
    - A short description of your design and trade-offs.
    - At least one **Mermaid sequence diagram** for:
        - `GET /products`
        - `POST /products/{id}/discount`
        - **Ingestion start → parse → validate → write → status**
    - A **Mermaid component diagram** showing the two services and their communication (and ingestion components/queues if used).

**Example sequence diagram (simplified)**

```mermaid
sequenceDiagram
    participant Client
    participant ProductAPI
    participant DiscountService

    Client->>ProductAPI: GET /products?country=Sweden
    loop For each product
        ProductAPI->>DiscountService: GET /discounts/{productId}
        DiscountService-->>ProductAPI: Active discounts
    end
    ProductAPI-->>Client: Products with taxed+discounted prices
```

---

## 📬 Submission

When you are done:

1. Push your solution to a **public GitHub repository**.
2. Ensure we can build and run your services locally (with Docker Compose or instructions).
3. Include:
    - Your **code**.
    - Your **README.md** with run instructions.
    - Your **ARCHITECTURE.md** with Mermaid diagrams.
4. Share the repository link with us.

---

✨ **Good luck, and have fun!**  
We look forward to your solution.
