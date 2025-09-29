# product-api-assignment

# Dependencies

* Docker - used to run the services in containers
* Make - used to use the Makefile to build and run the project easily

On OSX install by (assuming you have homebrew installed)

# Starting the project using docker-compose and Makefile

```bash
brew install make
```

If you do not have make installed, you can view the raw commands within the Makefile.

# How to run the project

Build code and docker files with

```bash
make all
```

Run the services with

```bash
make start
```

Stop the services with

```bash
make stop 
```

Reset the services (including db storage) with

```bash
make stop-reset 
```

You can also start only the databases with

```bash
make start-db
```

and stop them with

```bash
make stop-db
```

If you prefer to run the services locally without docker

# When running locally

The product service will be available at [localhost:8050](http://localhost:8050)
The discount service will be available at [localhost:8060](http://localhost:8060)

# Implementation details

The project is implemented in Kotlin using Ktor as the web framework as specified.
I have added Koin for dependency injection. For storage we are using MongoDB with a reactive driver.

# Ingestion flow

The ingestion flow is as follows:
Once an ingestion is started, a new ingestion record is created in the database with status `PENDING`."
A background worker continuously checks for new ingestions with status `PENDING` and processes them one by one.
When an ingestion is picked up by the worker, its status is updated to `PROCESSING`.
The worker then reads the corresponding .ndjson file based on the ingestion type (products or discounts) being
processed.

* The ingestion files are found in the resource package of the product-service repository.

For product ingestion we simply read the contents of the file into a channel and then have a pool of workers that read
from the channel and process each line.
If a line is valid, we upsert the product into the database.

For discount ingestion we read the contents of the file into a channel and then have a pool of workers.
What is different here is that discounts are passed to the discount service over REST, which adds significant overhead.
To mitigate this after having a working ingestion flow, the ingestion pipeline was changed to ingest in batches.
This right now has led to metrics not being fully working as expected, and don't intend on spending more time on it.

Once the ingestion is complete, the ingestion status is updated to `COMPLETED`.

During the ingestion processing we continuously log status updates. You can of course also query the API for the status
as well.

Ingestions can be controlled as per the assignment specification, e.g.

```json
{
  "workers": 6,
  "chunkSize": 15000,
  "mode": "ALL",
  "failFast": false,
  "retries": 2,
  "dryRun": false
}
```

I have also added basic pageable APIs for both products and discounts for easier verification of the ingested data.

(Requires BEARER token as per assignment spec)

[localhost:8050/products?limit=100&offset=0&sortOrder=ASC](localhost:8050/products?limit=100&offset=0&sortOrder=ASC)

[localhost:8060/discounts?limit=100&offset=0&sortOrder=ASC](localhost:8060/discounts?limit=100&offset=0&sortOrder=ASC)
