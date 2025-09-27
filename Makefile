.PHONY: all build fmt lint build-product format-product lint-product start stop stop-reset start-db stop-db stop-reset-db help

DOCKER_COMPOSE := $(shell command -v docker-compose > /dev/null 2>&1 && echo docker-compose || echo "docker compose")

help:
	@echo "Usage: make [target]"
	@echo ""
	@echo "Targets:"
	@echo "  all            Build application and the Docker images"
	@echo "  build          Build the applications"
	@echo "  image          Build the docker images"
	@echo "  fmt            Format the application code"
	@echo "  lint           Lint the application code"
	@echo "  start          Start the Docker containers"
	@echo "  stop           Stop the Docker containers"
	@echo "  stop-reset     Stop the Docker containers and reset storage"
	@echo "  start-db       Start the Docker containers (db only)"
	@echo "  stop-db        Stop the Docker containers (db only)"
	@echo "  stop-reset-db  Stop the Docker containers and reset storage (db only)"

all: build image

image: image-product

build: fmt build-product

fmt: format-product

lint: lint-product

build-product:
	 cd product && ./gradlew build

format-product:
	 cd product && ./gradlew ktlintFormat

lint-product:
	 cd product && ./gradlew ktlintCheck

image-product:
	 docker build -f docker/Dockerfile.product -t svebrant-product:latest .

start:
	 $(DOCKER_COMPOSE) -f docker-compose.local.yml -f docker-compose.services.yml up

start-db:
	 $(DOCKER_COMPOSE) -f docker-compose.local.yml up

stop:
	 $(DOCKER_COMPOSE) -f docker-compose.local.yml  -f docker-compose.services.yml down

stop-db:
	 $(DOCKER_COMPOSE) -f docker-compose.local.yml down

stop-reset:
	 $(DOCKER_COMPOSE) -f docker-compose.local.yml  -f docker-compose.services.yml down -v

stop-reset-db:
	 $(DOCKER_COMPOSE) -f docker-compose.local.yml down -v
