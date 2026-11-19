# Plano: Consulta de métricas para dashboards (produto e empresa)

**Branch**: `LLT-145-consulta-metricas-dashboard` | **Data**: 18/11/2026 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/011-consulta-metricas-dashboard/spec.md`

**Nota**: entrega multi-repositório com `lhamalog-web` spec `007-dashboards-produto-empresa` (contrato LLT-159). Job SSID_011: lê **logs** agora; Ocorrências depois da LLT-104 — **confirmado Michel 18/11 22:02 (A1-C1)**.

## Summary

O Core expõe `GET /api/dashboard/products/{productId}/metrics` e `GET /api/dashboard/company/metrics` conforme o contrato do Web. Contagens de logs vêm do OpenObserve (ADR_168) via `POST /api/{org}/_search` (SQL de contagem); volume usado (`volumeUsedBytes` em bytes) e traces (`acceptedRecordCount` de fatos tipo traces) dos fatos de uso (`UsageFact` / `/internal/usage-facts`); `retentionDays` e cota `volumeIngestaoBytes` (inteiro em bytes; GiB 2^30 só no Web) do plano (ADR_131 / `PlanSubscription`). Um job SSID_011 (ADR_166) agrega logs por tenant, produto, severidade, serviço e hora com cron `lhamalog.dashboard.aggregation.cron` (padrão `0 0 * * * *`). A consulta é híbrida: agregados consolidados + contagem na hora do intervalo aberto, com `dadosConsolidadosAte`. Autorização reutiliza `CompanySession` / `ProductService` / `AcessoProduto` (DONO/ADMIN = todos; MEMBRO = AcessoProduto; produto inacessível → **404**; MEMBRO sem produtos → 200 vazio na empresa). Campo `occurrences` omitido até LLT-104; sem selos de impacto.

## Technical Context

**Language/Version**: Java 25

**Primary Dependencies**: Spring Boot 4.1.1; Spring WebMVC (`RestClient` para OpenObserve, como `ClerkBackendSessionRevocation`); Spring Data MongoDB; Spring Security OAuth2 Resource Server (Clerk, LLT-163); Bean Validation; Spring Scheduling (`@EnableScheduling` já em `LhamalogCoreApplication`). Sem dependência Maven nova.

**Storage**: MongoDB — coleção nova de agregados horários SSID_011 (`DashboardLogHourlyAggregate`); reutiliza `UsageFact`, `PlanSubscription`, `Produto`, `AcessoProduto`. OpenObserve (org `lhamalog`, streams por tenant) só via Core (ADR_141/168).

**Testing**: JUnit 5; unitários sem banco (serviço, normalização ADR_170, merge híbrido, repositórios em memória); integração `@CoreIntegrationTest` + `TestTenants` (MockMvc, tokens Clerk); WireMock ou stub do cliente OpenObserve nos testes de integração HTTP; portão `.\mvnw.cmd test` / `verify`.

**Target Platform**: API HTTP do LhamaLog Core (porta 8080)

**Project Type**: web-service (backend único neste repositório)

**Performance Goals**: consulta híbrida evita varrer OpenObserve em períodos longos; job horário fecha buckets; ranking limitado a 5 serviços.

**Constraints**: constituição 2.1.0; inglês no código / português na documentação; segredos OpenObserve/Clerk só em env (`.env.example` sem valores); `CorrelationId` / `X-Correlation-Id` já existentes — não reimplementar; produto inacessível → 404 (padrão `ProductService.get`); 403 só segurança (sem Tenant etc.); Felipe revisa o que tocar em Clerk/papéis (FR-019); sem UI, exportação, mobile, Ocorrências (LLT-104), nem acesso Web ao OpenObserve.

**Scale/Scope**: módulo novo `br.com.lhamalog.core.dashboard` (api / application / infrastructure); 2 endpoints GET; 1 job agendado; 1 coleção Mongo; espelho do contrato Web; propriedade de cron documentada.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Spec Kit**: spec, plano, research, data-model, contracts, quickstart e tasks nesta pasta antes de qualquer código em `src/`.
- **II. IA e revisão**: agente implementa 100%; decisões em `research.md` / plan / tasks; tasks **sem** etiqueta de autoria; desenvolvedor revisa; Felipe revisa Clerk/papéis (FR-019) — portão nas tasks de autorização.
- **III. Testes**: unitários + integração `@CoreIntegrationTest`; checkpoint `./mvnw test` verde por fase de história; merge só com suite verde.
- **IV. Idioma**: código em inglês (`DashboardMetricsService`, `LogSeverity`, `dadosConsolidadosAte` no JSON do contrato Web permanece como no espelho); docs em português.
- **V. Segredos**: URL/credenciais OpenObserve via propriedades/`OPENOBSERVE_*`; nenhum segredo em artefatos; `.env.example` só nomes/comentários.
- **VI. Logs**: eventos `event=dashboard.*` / `event=dashboard.aggregation.*` com `cid` (MDC na HTTP; job gera correlation id por execução); WARN/ERROR com aviso; sem payload de log do cliente.
- **Stack**: Java 25, Spring Boot 4.1.1, Maven Wrapper, pacote `br.com.lhamalog.core`, MongoDB, Redis existente (não obrigatório nesta feature), OpenObserve.
- **Fluxo**: branch `LLT-145-consulta-metricas-dashboard`; commits `LLT-145: <descricao>`.

Nenhuma violação. Complexity Tracking vazio.

**Reavaliação pós-Phase 1**: design alinhado (módulo dashboard, coleção de agregados, contrato espelho, cron `lhamalog.dashboard.aggregation.cron`, interpretação SSID_011 confirmada por Michel). Sem violação nova.

## Project Structure

### Documentation (this feature)

```text
specs/011-consulta-metricas-dashboard/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── dashboard-metrics.md
├── checklists/
│   └── requirements.md
├── spec.md
└── tasks.md
```

### Source Code (repository root)

```text
src/main/java/br/com/lhamalog/core/dashboard/
├── api/
│   ├── DashboardMetricsController.java
│   └── DashboardMetricsResponses.java
├── application/
│   ├── LogSeverity.java                 # ERROR|WARNING|INFO + ADR_170
│   ├── DashboardMetricsQuery.java
│   ├── DashboardMetricsService.java     # authz via ProductService/session; híbrido
│   ├── DashboardAggregationJob.java     # @Scheduled cron
│   ├── DashboardAggregationProperties.java
│   ├── OpenObserveLogQueryPort.java
│   ├── DashboardAggregateRepository.java
│   └── UsageMetricsPort.java            # volume/traces/retention/cota a partir de usage
└── infrastructure/
    ├── DashboardLogHourlyAggregateDocument.java
    ├── MongoDashboardAggregateRepository.java
    ├── OpenObserveLogQueryAdapter.java  # RestClient
    └── UsageMetricsAdapter.java

src/main/resources/
└── application.properties               # lhamalog.dashboard.aggregation.cron=0 0 * * * *
                                         # + propriedades OpenObserve de leitura (URL/org; auth via env)

src/test/java/br/com/lhamalog/core/dashboard/
├── application/
│   ├── LogSeverityTest.java
│   ├── DashboardMetricsServiceTest.java
│   └── DashboardAggregationJobTest.java
└── api/
    └── DashboardMetricsControllerIntegrationTest.java
```

**Structure Decision**: módulo novo em camadas no estilo `ingestiontoken` / `usage`. Não altera o corpo de `ProductService` além de reutilizar `list`/`get` e `CompanySessionResolver.current()`. `CorrelationIdFilter` permanece como está. Contrato espelho em `contracts/dashboard-metrics.md` (leitura do Web; sem mudar o Web nesta feature).

## Complexity Tracking

> Sem violações da constituição.
