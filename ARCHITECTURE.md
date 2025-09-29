# Mermaid diagrams for various flows:

The flows are roughly like as follows:

PS = Product Service

DS = Discount Service

# 1. GET /products endpoints

```mermaid
sequenceDiagram
    participant [PS] ProductRoutes
    participant [PS] ProductService
    participant [PS] ProductRepository
    participant [PS] DiscountService
    participant [PS] DiscountClient
    participant [DS] DiscountRoutes
    participant [DS] DiscountService
    participant [DS] DiscountRepository
    [PS] ProductRoutes ->> [PS] ProductRoutes: id = null - HTTP 400
    [PS] ProductRoutes ->> [PS] ProductService: /products/{id} -> getByProductId(id)
    [PS] ProductService ->> [PS] ProductRepository: findByProductId(id)
    [PS] ProductRepository -->> [PS] ProductService: Product?
    [PS] ProductService -->> [PS] ProductRoutes: null - HTTP 404
    [PS] ProductService ->> [PS] DiscountClient: getDiscountsForProduct(productId)
    [PS] DiscountClient ->> [DS] DiscountRoutes: getDiscounts(productId)
    [DS] DiscountRoutes ->> [DS] DiscountService: getByProductId(productId)
    [DS] DiscountService ->> [DS] DiscountRepository: findByProductId(productId)
    [DS] DiscountRepository -->> [DS] DiscountService: List<DiscountDto>
    [DS] DiscountService -->> [DS] DiscountRoutes: List<DiscountResponse>
    [DS] DiscountRoutes -->> [PS] DiscountClient: List<DiscountResponse>
    [PS] DiscountClient -->> [PS] ProductService: List<DiscountResponse>
    [PS] ProductService -->> [PS] ProductRoutes: ProductResponse - HTTP 200
```

# 2. POST /products/{id}/discount endpoint

```mermaid
sequenceDiagram
    participant [PS] ProductRoutes
    participant [PS] ProductService
    participant [PS] ProductRepository
    participant [PS] DiscountService
    participant [PS] DiscountClient
    participant [DS] DiscountRoutes
    participant [DS] DiscountService
    participant [DS] DiscountRepository
    [PS] ProductRoutes ->> [PS] ProductRoutes: id = null - HTTP 400
    [PS] ProductRoutes ->> [PS] ProductRoutes: discountRequest.validate()
    [PS] ProductRoutes ->> [PS] ProductService: applyDiscount(id, discountRequest)
    [PS] ProductService ->> [PS] ProductRepository: findByProductId(id) -> ProductDto?
    [PS] ProductRepository -->> [PS] ProductService: ProductDto? = null
    [PS] ProductService -->> [PS] ProductRoutes: HTTP 400
    [PS] ProductService ->> [PS] DiscountService: create - DiscountApiRequest
    [PS] DiscountService ->> [PS] DiscountClient: validate - createRequest
    [PS] DiscountClient ->> [DS] DiscountRoutes: /discounts/apply
    [DS] DiscountRoutes ->> [DS] DiscountService: validate - applyDiscount
    [DS] DiscountService ->> [DS] DiscountRepository: save (idempotent on productId + discountId)
    [DS] DiscountRepository -->> [DS] DiscountService: DiscountDto?
    [DS] DiscountService -->> [DS] DiscountRoutes: DiscountApplicationResponse
    [DS] DiscountRoutes -->> [PS] DiscountClient: DiscountApplicationResponse
    [PS] DiscountClient -->> [PS] DiscountService: DiscountApplicationResponse
    [PS] DiscountService -->> [PS] ProductService: DiscountApplicationResponse
    [PS] ProductService -->> [PS] ProductRoutes: ProductWithDiscountResponse - HTTP 200
```

3. Ingestion start → parse → validate → write → status

I am interpreting this one as the ingestion flow start until the ingestion gets status STARTED and ingestions starts
with IngestMode = ALL

```mermaid
sequenceDiagram
    participant [PS] AdminRoutes
    participant [PS] IngestionScheduler
    participant [PS] IngestService
    participant [PS] IngestRepository
    participant [PS] ProductService
    participant [PS] ProductRepository
    participant [PS] DiscountService
    participant [PS] DiscountClient
    participant [DS] DiscountRoutes
    participant [DS] DiscountService
    participant [DS] DiscountRepository
    [PS] AdminRoutes ->> [PS] AdminRoutes: POST /admin/ingest
    [PS] AdminRoutes --> [PS] AdminRoutes: ingestRequest.validate()
    [PS] AdminRoutes ->> [PS] IngestService: createIngestJob(ingestRequest) - PENDING
    [PS] IngestionScheduler ->> [PS] IngestionScheduler: poll for PENDING jobs
    [PS] IngestionScheduler ->> [PS] IngestService: processIngestion(ingestionId)
    [PS] IngestService ->> [PS] IngestRepository: updateInitialProgress(ingestionId)
    [PS] IngestService ->> [PS] IngestRepository: updateStatus(STARTED)
    [PS] IngestService ->> [PS] IngestService: processWithWorkers - spawn workers
    [PS] IngestService ->> [PS] IngestService: launchFileReader - writes contents to channel
    [PS] IngestService ->> [PS] IngestService: processProduct - product workers processes from channel
    [PS] IngestService ->> [PS] ProductService: create - ProductRequest
    [PS] ProductService ->> [PS] ProductRepository: save - deduplicate, save metrics, etc
    [PS] IngestService ->> [PS] IngestService: processDiscount - product workers processes from channel
    [PS] IngestService ->> [PS] DiscountService: createBatch - List<DiscountApiRequest>
    [PS] DiscountService ->> [PS] DiscountClient: createRequest - DiscountApiRequest
    [PS] DiscountClient ->> [DS] DiscountRoutes: POST /discounts/apply/batch
    [DS] DiscountService ->> [DS] DiscountRepository: save
    [DS] DiscountRepository --> [DS] DiscountService: BatchDiscountApplicationResponse
    [DS] DiscountService --> [DS] DiscountRoutes: BatchDiscountApplicationResponse
    [DS] DiscountRoutes --> [PS] DiscountClient: BatchDiscountApplicationResponse (200)
    [PS] DiscountClient --> [PS] DiscountService: BatchDiscountApplicationResponse
    [PS] DiscountService --> [PS] IngestService: BatchDiscountApplicationResponse
    [PS] IngestService --> [PS] IngestService: update progress
    [PS] IngestService ->> [PS] IngestRepository: update progress
    [PS] AdminRoutes ->> [PS] IngestService: GET /admin/ingest/{$PARAM_INGESTION_ID}/status
    [PS] IngestService ->> [PS] IngestService: getIngestionStatus(ingestionId)
    [PS] IngestService ->> [PS] IngestRepository: findByIngestionId(ingestionId)
    [PS] IngestRepository --> [PS] IngestService: IngestionStatusResponse
    [PS] IngestService --> [PS] AdminRoutes: IngestionStatusResponse - HTTP 200

```

# Component diagram showing the two services and their communication

```mermaid
graph TD
    subgraph "Users"
        User[Admin]
    end

    subgraph "Product-Service"
        ProductRoutes[Product REST API<br>BEARER Auth Protected]
        ProductService[Product Service layer]
        DiscountClient[REST client towards Discount-Service]
        ProductRepository[Product Repository]
    end

    subgraph "Discount-Service"
        DiscountRoutes[Discount REST API<br>BEARER Auth Protected]
        DiscountService[Discount Business Logic]
        DiscountRepository[Discount Repository]
    end

    subgraph "Databases"
        ProductDB[(MongoDB<br>Product Database)]
        DiscountDB[(MongoDB<br>Discount Database)]
    end

%% Product Service
    User -->|1 . User calls REST endpoints<br>Bearer token protected| ProductRoutes
    ProductRoutes -->|1 . 5 . Unauthorized calls get blocked| User
    ProductRoutes -->|2 . Authorized calls gets forwarded to service layer| ProductService
    ProductService -->|3 . Business logic <br> storage in repository| ProductRepository
    ProductRepository -->|4 . Repository stores in MongoDB <br> Reactive driver| ProductDB
    ProductService -->|5 . Forwards calls to DiscountService| DiscountClient
    DiscountClient -->|6 . Forwards calls to DiscountService <br> Authorization header| DiscountRoutes
%% Discount Service
    User -->|1 . User calls REST endpoints<br>Bearer token protected| DiscountRoutes
    DiscountRoutes -->|1 . 5 . Unauthorized calls get blocked| User
    DiscountRoutes -->|2 . Authorized calls gets forwarded to service layer| DiscountService
    DiscountService -->|3 . Business logic <br> storage in repository| DiscountRepository
    DiscountRepository -->|4 . Repository stores in MongoDB <br> Reactive driver| DiscountDB


```