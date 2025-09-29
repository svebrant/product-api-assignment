# Mermaid diagrams for various flows:

The flows are roughly like as follows:

### 1. GET /products endpoints

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

### 2. POST /products/{id}/discount endpoint

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

### 3. Ingestion start → parse → validate → write → status

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

## Component diagram showing the two services and their communication

The general data flows look like this, excluding specifics such as the ingestion related internal Product-service
components.

```mermaid
graph TD
    Admin([Admin User]):::userStyle

    subgraph "Product Service"
        ProductRoutes[ProductRoutes]:::apiStyle
        ProductService[ProductService]:::logicStyle
        ProductRepo[ProductRepository]:::repoStyle
        DiscountService[DiscountService]:::logicStyle
        DiscountClient[DiscountClient]:::clientStyle
    end

    subgraph "Discount Service"
        DiscountRoutes[DiscountRoutes]:::apiStyle
        DiscountLogic[DiscountService]:::logicStyle
        DiscountRepo[DiscountRepository]:::repoStyle
    end

%% Databases with distinct styling
    ProductDB[(MongoDB<br>Product)]:::dbStyle
    DiscountDB[(MongoDB<br> Discount)]:::dbStyle
%% Hard-coded Bearer auth
    BearerToken{{Bearer Token Validation}}:::authStyle
%% Flow for Product Service
    Admin -->|Request with Bearer token| ProductRoutes
    ProductRoutes -->|Token validation| BearerToken
    BearerToken -->|Unauthorized| Admin
    ProductRoutes -->|Authorized requests| ProductService
    ProductService -->|Data operations| ProductRepo
    ProductRepo -->|MongoDB operations<br>Reactive| ProductDB
    ProductService -->|Discount operations| DiscountService
    DiscountService -->|Remote calls| DiscountClient
%% Connection between services
    DiscountClient -->|Request with Bearer token| DiscountRoutes
%% Flow for Discount Service
    DiscountRoutes -->|Token validation| BearerToken
    DiscountRoutes -->|Authorized requests| DiscountLogic
    DiscountLogic -->|Data operations| DiscountRepo
    DiscountRepo -->|MongoDB operations<br>Reactive| DiscountDB
%% Response flows
    DiscountRepo -->|Return data| DiscountLogic
    DiscountLogic -->|Return response| DiscountRoutes
    DiscountRoutes -->|API response| DiscountClient
    DiscountClient -->|Return discounts| DiscountService
    DiscountService -->|Return processed data| ProductService
    ProductRepo -->|Return data| ProductService
    ProductService -->|Return response| ProductRoutes
    ProductRoutes -->|API response| Admin
%% Style definitions
    classDef apiStyle fill: #f9a, stroke: #333, stroke-width: 1px;
    classDef logicStyle fill: #adf, stroke: #333, stroke-width: 1px;
    classDef repoStyle fill: #bfb, stroke: #333, stroke-width: 1px;
    classDef dbStyle fill: #fdd, stroke: #f66, stroke-width: 2px, stroke-dasharray: 5 5;
    classDef clientStyle fill: #ddd, stroke: #333, stroke-width: 1px;
    classDef authStyle fill: #ff9, stroke: #333, stroke-width: 1px;
    classDef userStyle fill: #eee, stroke: #333, stroke-width: 1px;
```