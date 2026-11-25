# Analyze report: LLT-292 (spec × plan × tasks × ADRs)

**Data**: 2026-11-20 ~22:55 (America/Sao_Paulo)  
**Escopo**: consistência após o clarify fechado (12/12). Sem implementação. Sem commit.

## Método

Confrontar `spec.md`, `clarify-questions.md`, contratos, `plan.md`, `research.md`, `data-model.md`, `tasks.md` com as ADRs 041–056/032/054, a spec 010 do Collector e o código em `origin/Development` `23a69d9` (Core) / pacote `dedup` da LLT-290 (somente leitura).

## Correções aplicadas neste ciclo

1. **OpenObserve antes da fila (buraco do rascunho de specify).** A fila de leitura (specs 002/004) só publica depois da gravação confirmada no OO, e o body é uma referência. O rascunho falava em “publicar na fila” sem citar o OO. Incluído FR-008, research R1, data-model, contrato do Collector e tasks T021–T022/T026.
2. **US4 promovida a P1.** O clarify confirmou vários arquivos e recusa parcial; o rascunho ainda marcava P2.
3. **Teto de 100 MB por requisição e Tomcat.** Além do multipart (hoje 2 MB no Core), o plan/tasks incluem `max-http-form-post-size` / `max-swallow-size` (research R9, T003/T016).
4. **403 vs 404 de `ProductService.get()`.** Documentado na Clarification Q11, research R3 e testes T012: o upload devolve 403 e recusa arquivado; `get()` não é alterado.
5. **Um `batchId`/mensagem por arquivo; `loteId` só no HTTP.** Alinha o `idempotencyId` da 290 (um MD5) com a Q8. Spec, contratos e R1/data-model alinhados.
6. **Worktree do Collector não criado agora.** HEAD da 290 ainda é `5fb3160` com `dedup/` untracked. Tasks T002 bloqueada; research R7. Evita ramificar incompleto ou tocar `lhamalog-collector-llt290`.
7. **B1 herdada** registrada na spec (edge case), research R8 e T032 (caracterização, sem 503 próprio).
8. Linguagem de “recomendado / a confirmar” retirada de spec e contratos; decisões do clarify de 22:45 entram como fechadas.

## Achados restantes (não bloqueiam o plan)

### Média

- **M1 — B1 da LLT-290 ainda aberta.** Claim em andamento = 409 (TTL 10 min). O upload herda isso. Não é pergunta nova; a T041 da 290 precisa fechar para eventualmente mudar o comportamento.
- **M2 — Código da LLT-290 não está na branch.** Implementação Collector bloqueada de fato até o commit (e o Consultor Review da 290, se o Michel mantiver esse gate).
- **M3 — Formato do documento cru no OpenObserve.** Decisão de engenharia (R1), não de negócio. O Finder pós-MVP pode pedir outro formato; `schemaVersion` não muda. Risco aceito e documentado.

### Baixa

- **B1 (deste analyze, não a T041):** rate limiter atual do Collector é pensado no token OTLP. T020/T031 pedem reuso por tenant; se a chave interna for só o token de produtor, o implementador adapta a chave (research R4) sem mudar a regra de negócio da Q5.
- **B2:** `GET /api/products/{id}` continuará 404 no restante da API; só o upload usa 403. Vale uma linha no README (T033).
- **B3:** card Jira LLT-292 desatualizado (5 MB, token OTLP). Fora de escopo alterar o Jira.

## Matriz de cobertura (spec → tasks)

| Requisito / US | Tasks |
| --- | --- |
| FR-001 rota `/api/manual/upload` + Clerk | T009, T011 |
| FR-002 / Q11 produto 403 | T005, T012 |
| FR-003 / Q2–Q4 extensão e 100 MB | T003, T006, T013, T016, T029 |
| FR-004 / FR-012 / Q8 corpo 202 e 409 total | T008, T014, T015, T023, T030 |
| FR-005 / Q12 espera + service token | T007, T014 |
| FR-006 endpoint interno 8080 | T018, T023, T025 |
| FR-007 dedup `upload` + logs | T020, T027, T028 |
| FR-008 OO + fila `log.ingested` | T021, T022, T026 |
| FR-009 cota/rate/assinatura | T020, T031 |
| FR-010 properties, nunca `.env` | T003, T016 |
| FR-011 magic bytes | T019, T029 |
| FR-013 logs | T010, T024 |
| FR-014 worktrees | T001, T002 |
| FR-016 timeout/streaming | T007, T014 |
| US2 não-colisão OTLP | T027 |
| B1 caracterização | T032 |
| Verify | T034 |

Itens pós-MVP (WebSocket, FilaOrdenada, OpenAPI, malware avançado) não têm task de implementação — correto.

## Constituição

Passa: docs completos, testes nas stories, logs padronizados, segredos por env, dois repositórios descritos, sem commit nesta etapa.

## Veredito

Consistente para iniciar a implementação **do Core**. Collector só depois de T002 (commits da LLT-290). Nenhuma pergunta nova para o Michel além da B1 já aberta na 290.
