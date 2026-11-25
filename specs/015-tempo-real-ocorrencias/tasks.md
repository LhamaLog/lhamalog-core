# Tasks: Aviso em tempo real de ocorrências novas

**Input**: artefatos em `specs/015-tempo-real-ocorrencias/`
**Prerequisites**: specs 012, 013 e 014 (branch `LLT-136-ciclo-de-vida`)

**Tests**: obrigatórios (constituição III). Em cada história, escrever os testes primeiro e vê-los falhar.

## Phase 1: Foundational

- [X] T001 [P] Escrever `OccurrenceChangeDetectorTest.java` e criar `OccurrenceEvent` e `OccurrenceChangeDetector` em `occurrence/application/` (research R3): criada → `CREATED`; FECHADA, EM_ANALISE arquivada, ABERTA arquivada ou status desconhecido → ABERTA → `REOPENED`; ABERTA/EM_ANALISE ativas → nada
- [X] T002 [P] Criar a porta `OccurrenceEventPublisher` e acrescentar as propriedades `lhamalog.occurrence.stream.*` (canal, tempo de autenticação 10 s, folga de expiração 30 s, intervalo de produtos 60 s, origens = `clerk.authorized-parties`)

## Phase 2: User Story 2 - Conexão autenticada (P1)

- [X] T003 [US2] Escrever `OccurrenceStreamIntegrationTests.java` (parte US2): sem `auth` em 10 s (com tempo curto no teste) → 4401; primeira mensagem não é `auth` → 4401; token inválido → 4401; token sem empresa ativa e de empresa sem Tenant → 4403; token válido → `ready` com `expiresAt`; renovação da mesma pessoa → novo `ready`; renovação de outra pessoa → 4403; origem não autorizada → aperto de mão recusado
- [X] T004 [US2] Criar `StreamAuthenticator` (token → `RequestContext` pelo `JwtDecoder` e `AccessResolver`, mesma checagem de membro desativado das rotas `/api`; produtos visíveis pelo `ProductService`) e `OccurrenceConnections` (registro thread-safe por empresa, produtos visíveis, `exp`)
- [X] T005 [US2] Criar `OccurrenceStreamHandler` e `OccurrenceStreamConfiguration` (handler em `/ws/occurrences`, origens autorizadas, tarefas de prazo de autenticação, expiração e atualização dos produtos) e liberar o aperto de mão na `SecurityConfig`
- [X] T006 [US2] Escrever `OccurrenceConnectionsTest.java` (registro, remoção, filtro por empresa e produto, expiração) e rodar a US2 verde

## Phase 3: User Story 1 - Aviso de ocorrência nova (P1)

- [X] T007 [US1] Acrescentar a `OccurrenceStreamIntegrationTests`: entrega com uma ocorrência nova e uma reaberta → a conexão de quem vê o produto recebe `CREATED` e `REOPENED`; aumento de contagem de ABERTA → nada; conexão de outra empresa e de membro sem acesso → nada; entrega repetida e entrega recusada → nada; aviso sem título nem assinatura
- [X] T008 [US1] Criar `RedisOccurrenceEventPublisher` (assíncrono, WARN em falha) e `RedisOccurrenceEventListener` (repasse às conexões locais), registrados na `OccurrenceStreamConfiguration`
- [X] T009 [US1] Publicar os avisos no `AnalysisSummaryDeliveryService` depois do `confirm`, pelo `OccurrenceChangeDetector`; acrescentar ao `AnalysisSummaryDeliveryServiceTest` que entrega recusada, repetida e desfeita não publicam
- [X] T010 [US1] Rodar a US1 verde

## Phase 4: Polish

- [X] T011 [P] Documentar no README (seção "Ocorrências") o WebSocket e o contrato
- [X] T012 Rodar `.\mvnw.cmd verify` e registrar o resultado no fim deste arquivo
- [X] T013 Roteiro do quickstart com a tela (spec 014 do web) e registrar
- [ ] T014 Revisão do Felipe

## Resultados (25/11/2026)

### T012 — verify

`.\mvnw.cmd verify` com o Docker Desktop: **BUILD SUCCESS, 539 testes, 0 falhas, 0 erros, 2 ignorados** (desempenho, sob demanda). Da feature: `OccurrenceStreamIntegrationTests` 6 (servidor em porta aleatória, cliente WebSocket real, Redis do Testcontainers e entregas pelas rotas internas), `OccurrenceConnectionsTest` 4, `OccurrenceChangeDetectorTest` 3 e mais 3 casos no `AnalysisSummaryDeliveryServiceTest` (publica só depois do recibo; recusa, repetição e desfazer não publicam; publicador com falha não falha a entrega).

### T013 — roteiro com a tela

Rodado com a spec 014 do web (roteiro T1 a T8 registrado lá): aviso em poucos segundos, filtros respeitados, reconexão depois de o Core cair e sincronização de uma ocorrência gravada com o Core fora. A recusa de origem, os fechamentos 4401/4403 e a renovação do token estão nos testes de integração.
