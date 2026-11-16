# Wrapper do Maven: no Windows (cmd/PowerShell) usa mvnw.cmd; no Linux/macOS usa ./mvnw
ifeq ($(OS),Windows_NT)
MVNW := mvnw.cmd
else
MVNW := ./mvnw
endif

# Compose v2 na raiz. Nao usa down -v nem prune.
COMPOSE := docker compose
COMPOSE_OBS := docker compose -f compose.yaml -f compose.observability.yaml

.PHONY: run run-local build test verify clean up down logs status infra-up infra-down infra-ps infra-logs infra-config obs-up obs-down config

run:
	$(MVNW) spring-boot:run

# Roda com o perfil local (MongoDB e Redis do compose; OpenObserve e opcional)
run-local:
	$(MVNW) spring-boot:run -Dspring-boot.run.profiles=local

build:
	$(MVNW) clean package

test:
	$(MVNW) test

verify:
	$(MVNW) clean verify

clean:
	$(MVNW) clean

# API + Mongo + Redis. Equivale a: docker compose up -d --build
up:
	$(COMPOSE) up -d --build

# Remove containers e a rede do projeto. Os volumes nomeados permanecem.
down:
	$(COMPOSE) down

logs:
	$(COMPOSE) logs -f

status:
	$(COMPOSE) ps

config:
	$(COMPOSE) config -q

# So Mongo e Redis, sem construir a API (Java no host).
infra-up:
	$(COMPOSE) up -d mongo redis

# Para Mongo e Redis. Nao remove a API e nao apaga volumes.
infra-down:
	$(COMPOSE) stop mongo redis

infra-ps: status

infra-logs:
	$(COMPOSE) logs -f mongo redis

infra-config: config

# OpenObserve opcional, mesmo projeto/volume de antes.
obs-up:
	$(COMPOSE_OBS) up -d --build

obs-down:
	docker compose -f compose.observability.yaml stop openobserve
