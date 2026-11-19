# Tasks: Consulta de métricas para dashboards (produto e empresa)

**Input**: Design documents from `/specs/011-consulta-metricas-dashboard/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: Incluídos (constituição III + Independent Test das US). Sem etiquetas de autoria — o agente implementa 100%. Estimativas: S (~1h), M (~2–4h), L (~1 dia).

**Restrições**: não alterar constituição nem outras specs; sem dependência Maven nova; segredos só em env; `occurrences` ausente; job SSID_011 lê logs agora (Ocorrências pós-LLT-104 — confirmado Michel 18/11); API em bytes (GiB 2^30 só no Web); produto inacessível → 404; Felipe revisa tasks de Clerk/papéis antes de fechar a US4.

## Format: `[ID] [P?] [Story] Description`

---

## Phase 1: Setup

**Purpose**: Estrutura do módulo dashboard sem lógica de negócio.

- [x] T001 Criar esqueleto de pacotes `src/main/java/br/com/lhamalog/core/dashboard/{api,application,infrastructure}/` e `src/test/java/br/com/lhamalog/core/dashboard/{api,application}/` conforme `plan.md` (S)
- [x] T002 [P] Confirmar que `pom.xml` não ganha dependência nova e que `CorrelationIdFilter` / `LhamalogCoreApplication` (`@EnableScheduling`) permanecem reutilizados sem reimplementação (S)

**Checkpoint**: árvore de pacotes criada; `.\mvnw.cmd -B test` ainda verde na suíte existente.

---

## Phase 2: Foundational (bloqueia as user stories)

**Purpose**: Domínio compartilhado, portas, persistência de agregados, config do job, adaptadores de leitura.

- [x] T003 [P] Implementar `LogSeverity` (ERROR|WARNING|INFO) e mapeamento ADR_170 em `src/main/java/br/com/lhamalog/core/dashboard/application/LogSeverity.java` (S)
- [x] T004 [P] Testes unitários de normalização ADR_170 em `src/test/java/br/com/lhamalog/core/dashboard/application/LogSeverityTest.java` (S)
- [x] T005 [P] Documentos Mongo `DashboardLogHourlyAggregateDocument` (campos `_id`, `tenantId`, `productId`, `severity`, `serviceName`, `hourStart`, `count`, `consolidatedAt`, `schemaVersion=1`; índices único composto e consulta) e watermark `dadosConsolidadosAte` em `src/main/java/br/com/lhamalog/core/dashboard/infrastructure/` conforme `data-model.md` (M)
- [x] T006 [P] Portas `OpenObserveLogQueryPort`, `DashboardAggregateRepository`, `UsageMetricsPort` em `src/main/java/br/com/lhamalog/core/dashboard/application/` (S)
- [x] T007 Propriedade `lhamalog.dashboard.aggregation.cron` com padrão `0 0 * * * *` em `src/main/resources/application.properties` + record `DashboardAggregationProperties` em `src/main/java/br/com/lhamalog/core/dashboard/application/DashboardAggregationProperties.java`; documentar nome/override (sem segredo) em `.env.example` e README no padrão do repo (M)
- [x] T008 [P] `MongoDashboardAggregateRepository` em `src/main/java/br/com/lhamalog/core/dashboard/infrastructure/MongoDashboardAggregateRepository.java` (upsert por chave composta; leitura por intervalo) (M)
- [x] T009 [P] `UsageMetricsAdapter` em `src/main/java/br/com/lhamalog/core/dashboard/infrastructure/UsageMetricsAdapter.java` lendo `UsageFact` (`volumeUsedBytes` = soma `acceptedBytes` domain LOGS; `traces` = soma `acceptedRecordCount` domain/tipo traces) e `PlanSubscription` / `TenantRetentionResolver` (`retentionDays`, `volumeIngestaoBytes` = `allowanceGigabytes` × 2^30 em bytes; sem 10^9) (M)
- [x] T010 Stub ou `OpenObserveLogQueryAdapter` (`RestClient`) em `src/main/java/br/com/lhamalog/core/dashboard/infrastructure/OpenObserveLogQueryAdapter.java` — `POST /api/{org}/_search` com SQL de contagem por severidade/serviço/intervalo no stream do tenant (org `lhamalog`); URL/auth só via env/`OPENOBSERVE_*` (L)
- [x] T011 Helpers de consulta híbrida (merge agregados + intervalo aberto; `dadosConsolidadosAte`; bucket `hour` se duração ≤ 48h senão `day`; ranking top 5 por ERROR; **omitir** `occurrences`) em `src/main/java/br/com/lhamalog/core/dashboard/application/` (M)

**Checkpoint**: foundation pronta; `.\mvnw.cmd -B test` verde (inclui `LogSeverityTest`).

---

## Phase 3: User Story 1 — Métricas por produto (P1) 🎯 MVP

**Goal**: `GET /api/dashboard/products/{productId}/metrics` conforme contrato.

**Independent Test**: tenant + produto acessível + dados → 200 com totais, série, ranking, `retentionDays`, `volumeIngestaoBytes`; eco `X-Correlation-Id`.

- [x] T012 [P] [US1] Testes unitários de validação de intervalo/`severities`/merge híbrido em `src/test/java/br/com/lhamalog/core/dashboard/application/DashboardMetricsServiceTest.java` (cenários: zeros, filtro severidade, período > `min(30, retentionDays)` → invalid_filter) (M)
- [x] T013 [US1] `DashboardMetricsService` (consulta por produto: authz via `ProductService.get` / `CompanySession`; híbrido; usage; sem selos) em `src/main/java/br/com/lhamalog/core/dashboard/application/DashboardMetricsService.java` (L)
- [x] T014 [P] [US1] DTOs de resposta alinhados a `contracts/dashboard-metrics.md` em `src/main/java/br/com/lhamalog/core/dashboard/api/DashboardMetricsResponses.java` (S)
- [x] T015 [US1] `DashboardMetricsController` — `GET /api/dashboard/products/{productId}/metrics` com `CompanySessionResolver.current()`, query `from`/`to`/`severities`, erros 400/404 (produto inacessível → 404), em `src/main/java/br/com/lhamalog/core/dashboard/api/DashboardMetricsController.java` (M)
- [x] T016 [US1] Teste de integração `@CoreIntegrationTest` + `TestTenants` + stub OpenObserve em `src/test/java/br/com/lhamalog/core/dashboard/api/DashboardMetricsControllerIntegrationTest.java` (produto 200, 400, 404, correlation id, ausência de `occurrences`) (L)
- [x] T017 [US1] Logs INFO/WARN/ERROR com `event=dashboard.product_metrics.*` (constituição VI); falha OpenObserve → 5xx sem corromper agregados (S)

**Checkpoint**: US1 independente; `.\mvnw.cmd -B test` verde.

---

## Phase 4: User Story 2 — Métricas da empresa (P1)

**Goal**: `GET /api/dashboard/company/metrics` multiproduto autorizado.

**Independent Test**: dois produtos → consolidado só autorizados; MEMBRO sem Acesso → 200 vazio.

- [x] T018 [P] [US2] Estender `DashboardMetricsServiceTest` com consolidação, `productIds`, MEMBRO sem produtos → totais/listas vazios (M)
- [x] T019 [US2] Estender `DashboardMetricsService` para métricas da empresa (`ProductService.list`, filtro `productIds`, rejeição de ids inacessíveis/outro tenant) em `src/main/java/br/com/lhamalog/core/dashboard/application/DashboardMetricsService.java` (M)
- [x] T020 [US2] Endpoint `GET /api/dashboard/company/metrics` em `DashboardMetricsController.java` + DTOs `seriesByProduct` / `products` / `productsMonitored` (M)
- [x] T021 [US2] Estender `DashboardMetricsControllerIntegrationTest` com cenários empresa (DONO consolidado, MEMBRO filtrado, 200 vazio, `productIds` inválido) (M)

**Checkpoint**: US1+US2; `.\mvnw.cmd -B test` verde.

---

## Phase 5: User Story 3 — Job SSID_011 e resposta híbrida (P1)

**Goal**: Job periódico lendo **logs** via OpenObserve `_search` SQL (não Ocorrências neste MVP; Ocorrências pós-LLT-104 — confirmado Michel); agregados + watermark; consulta híbrida com `dadosConsolidadosAte`.

**Independent Test**: job sobre intervalo conhecido → agregados corretos; consulta mescla + `dadosConsolidadosAte`; cron padrão `0 0 * * * *` / override.

- [x] T022 [P] [US3] Testes unitários do job (upsert, watermark, falha sem avançar consolidação, correlation id por execução; SQL/contagens por severidade·serviço·hora) em `src/test/java/br/com/lhamalog/core/dashboard/application/DashboardAggregationJobTest.java` (M)
- [x] T023 [US3] `DashboardAggregationJob` com `@Scheduled(cron = "${lhamalog.dashboard.aggregation.cron:0 0 * * * *}")` em `src/main/java/br/com/lhamalog/core/dashboard/application/DashboardAggregationJob.java` — lê logs via porta OpenObserve (`POST /api/{org}/_search` + SQL de contagem); grava agregados; avança `dadosConsolidadosAte`; gera `cid` por execução (L)
- [x] T024 [US3] Integrar merge híbrido nos endpoints (já em T011/T013): garantir `dadosConsolidadosAte` na resposta quando houver watermark; intervalo aberto só no OpenObserve (M)
- [x] T025 [US3] Teste de propriedade/cron: valor padrão a cada 1h; override de `lhamalog.dashboard.aggregation.cron` refletido no bean/`@Scheduled` (teste de binding ou smoke) em `src/test/java/br/com/lhamalog/core/dashboard/application/` (S)
- [x] T026 [US3] Logs `event=dashboard.aggregation.*`; confirmar que falha transitória não corrompe agregados já gravados (S)

**Checkpoint**: job + híbrido; `.\mvnw.cmd -B test` verde.

---

## Phase 6: User Story 4 — Autorização por papel e isolamento (P1)

**Goal**: Matriz Clerk/papéis; isolamento LLT-158; teto por plano; portão Felipe.

**Independent Test**: DONO, ADMIN, MEMBRO com/sem AcessoProduto, outro tenant, token inválido nos dois GET.

- [x] T027 [P] [US4] Matriz de testes de autorização/isolamento em `DashboardMetricsControllerIntegrationTest` (401 sem token; MEMBRO sem AcessoProduto → **404**; produto outra empresa / inexistente → **404**; DONO=ADMIN mesma visão; 403 só segurança sem Tenant; planos só alteram `retentionDays`/teto) (L)
- [x] T028 [US4] Garantir alinhamento a `ProductService.get`: produto inacessível → **404** (não 403 de negócio); 403 permanece só para camada de segurança; sem cache de permissão além do token (M)
- [ ] T029 [US4] **Portão Felipe**: revisão explícita do que toca Clerk/papéis/`CompanySession`/`AcessoProduto`/matriz T027–T028; registrar aceite no PR ou checklist antes de fechar a entrega (FR-019) (S) — **PENDENTE** (não bloqueia implementação do agente; aguarda revisão humana do Felipe)

**Checkpoint**: US4 + aceite Felipe; `.\mvnw.cmd -B test` verde.

---

## Phase 7: Polish

- [x] T030 [P] Alinhar `quickstart.md` “Resultados executados” após `.\mvnw.cmd -B clean verify` (S)
- [x] T031 [P] Revisar contrato espelho vs Web (`contracts/dashboard-metrics.md`): campos obrigatórios, sem selos, sem `occurrences` (S)
- [x] T032 Confirmar `.env.example` + README documentam `lhamalog.dashboard.aggregation.cron` e variáveis OpenObserve de leitura sem valores secretos (S)
- [x] T033 Rodar `.\mvnw.cmd -B clean verify` e garantir suíte verde antes do merge (M)

**Checkpoint final**: verify verde; Spec Kit completo; revisão Felipe (T029) feita.

---

## Dependencies

- Setup → Foundational → US1 → US2 → US3 → US4 → Polish
- US2 depende do serviço/controller da US1
- US3 depende das portas/agregados da Foundational e dos endpoints US1/US2 para validar híbrido
- US4 depende dos dois GET; T029 bloqueia o “feito” da autorização
- Dentro de cada US: testes que falham → implementação → integração → checkpoint `.\mvnw.cmd -B test`

## Parallel opportunities

- T003/T004, T005, T006, T008, T009, T010 em paralelo após T001
- T012 e T014 em paralelo antes de T013/T015
- T018 em paralelo ao início de T019
- T022 e preparação de T025 em paralelo
- T030/T031/T032 em paralelo no polish

## MVP sugerido

Phases 1–3 (Setup + Foundational + US1). Em seguida US2, US3, US4+Felipe, polish.

## Notes

- Commits: `LLT-145: <descricao>`; branch `LLT-145-consulta-metricas-dashboard`
- SSID_011 confirmado (Michel 18/11): lê logs agora; Ocorrências após LLT-104
- OpenObserve: search SQL `POST /api/{org}/_search`; traces = `acceptedRecordCount` de fatos traces
- API em bytes (×/÷ 2^30); produto inacessível → 404
- Não inventar `occurrences` nem selos de impacto
