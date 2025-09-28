# product-api-assignment

# Dependencies
* Make - used to use the Makefile to build and run the project easily

On OSX install by (assuming you have homebrew installed)

```bash
brew install make
```

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

# When running locally

The product service will be available at [localhost:8050](http://localhost:8050)
The discount service will be available at [localhost:8060](http://localhost:8060)


