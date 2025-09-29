# Mermaid diagrams for various flows:

The flows are roughly like as follows:

PS = Product Service

DS = Discount Service

# 1. GET /products endpoints

```mermaid
sequenceDiagram
    box Product Service
        participant PR as [PS] ProductRoutes
        participant PS as [PS] ProductService
        participant PREP as [PS] ProductRepository
        participant DS as [PS] DiscountService
        participant DC as [PS] DiscountClient
    end
    box Discount Service
        participant DR as [DS] DiscountRoutes
        participant DSS as [DS] DiscountService
        participant DREP as [DS] DiscountRepository
    end

    PR ->> PR: id = null - HTTP 400
    PR ->> PS: /products/{id} -> getByProductId(id)
    PS ->> PREP: findByProductId(id)
    PREP -->> PS: Product?
    PS -->> PR: null - HTTP 404
    PS ->> DC: getDiscountsForProduct(productId)
    DC ->> DR: getDiscounts(productId)
    DR ->> DSS: getByProductId(productId)
    DSS ->> DREP: findByProductId(productId)
    DREP -->> DSS: List<DiscountDto>
    DSS -->> DR: List<DiscountResponse>
    DR -->> DC: List<DiscountResponse>
    DC -->> PS: List<DiscountResponse>
    PS -->> PR: ProductResponse - HTTP 200
```

# 2. POST /products/{id}/discount endpoint

```mermaid
sequenceDiagram
    box Product Service
        participant PR as [PS] ProductRoutes
        participant PS as [PS] ProductService
        participant PREP as [PS] ProductRepository
        participant DS as [PS] DiscountService
        participant DC as [PS] DiscountClient
    end
    box Discount Service
        participant DR as [DS] DiscountRoutes
        participant DSS as [DS] DiscountService
        participant DREP as [DS] DiscountRepository
    end

    PR ->> PR: id = null - HTTP 400
    PR ->> PR: discountRequest.validate()
    PR ->> PS: applyDiscount(id, discountRequest)
    PS ->> PREP: findByProductId(id) -> ProductDto?
    PREP -->> PS: ProductDto? = null
    PS -->> PR: HTTP 400
    PS ->> DS: create - DiscountApiRequest
    DS ->> DC: validate - createRequest
    DC ->> DR: /discounts/apply
    DR ->> DSS: validate - applyDiscount
    DSS ->> DREP: save (idempotent on productId + discountId)
    DREP -->> DSS: DiscountDto?
    DSS -->> DR: DiscountApplicationResponse
    DR -->> DC: DiscountApplicationResponse
    DC -->> DS: DiscountApplicationResponse
    DS -->> PS: DiscountApplicationResponse
    PS -->> PR: ProductWithDiscountResponse - HTTP 200
```

3. Ingestion start → parse → validate → write → status

I am interpreting this one as the ingestion flow start until the ingestion gets status STARTED and ingestions starts
with IngestMode = ALL

```mermaid
sequenceDiagram
    box Product Service
        participant AR as [PS] AdminRoutes
        participant IS as [PS] IngestionScheduler
        participant InS as [PS] IngestService
        participant IR as [PS] IngestRepository
        participant PS as [PS] ProductService
        participant PR as [PS] ProductRepository
        participant DS as [PS] DiscountService
        participant DC as [PS] DiscountClient
    end
    box Discount Service
        participant DR as [DS] DiscountRoutes
        participant DSS as [DS] DiscountService
        participant DREP as [DS] DiscountRepository
    end

    AR ->> AR: POST /admin/ingest
    AR --> AR: ingestRequest.validate()
    AR ->> InS: createIngestJob(ingestRequest) - PENDING
    IS ->> IS: poll for PENDING jobs
    IS ->> InS: processIngestion(ingestionId)
    InS ->> IR: updateInitialProgress(ingestionId)
    InS ->> IR: updateStatus(STARTED)
    InS ->> InS: processWithWorkers - spawn workers
    InS ->> InS: launchFileReader - writes contents to channel
    InS ->> InS: processProduct - product workers processes from channel
    InS ->> PS: create - ProductRequest
    PS ->> PR: save - deduplicate, save metrics, etc
    InS ->> InS: processDiscount - product workers processes from channel
    InS ->> DS: createBatch - List<DiscountApiRequest>
    DS ->> DC: createRequest - DiscountApiRequest
    DC ->> DR: POST /discounts/apply/batch
    DSS ->> DREP: save
    DREP --> DSS: BatchDiscountApplicationResponse
    DSS --> DR: BatchDiscountApplicationResponse
    DR --> DC: BatchDiscountApplicationResponse (200)
    DC --> DS: BatchDiscountApplicationResponse
    DS --> InS: BatchDiscountApplicationResponse
    InS --> InS: update progress
    InS ->> IR: update progress
    AR ->> InS: GET /admin/ingest/{$PARAM_INGESTION_ID}/status
    InS ->> InS: getIngestionStatus(ingestionId)
    InS ->> IR: findByIngestionId(ingestionId)
    IR --> InS: IngestionStatusResponse
    InS --> AR: IngestionStatusResponse - HTTP 200
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