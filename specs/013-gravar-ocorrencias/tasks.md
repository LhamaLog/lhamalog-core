# Tasks: Gravar ocorrências a partir do resumo de análise do Finder (Módulo Ocorrência)

**Input**: artefatos em `specs/013-gravar-ocorrencias/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/internal-api.md; pacote `occurrence` da spec 012 (branch `LLT-135-lista-e-detalhe`)

**Tests**: obrigatórios (constituição III). Em cada história, escrever os testes primeiro e vê-los falhar.

**Organization**: por história da spec. US1 a US4 são P1; US4 (segurança) entra na fundação porque toda rota depende dela; US5 (homologação) é P2 e depende do Finder.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: pode rodar em paralelo (arquivos diferentes, sem dependência)
- **[Story]**: história da spec (US1 a US5)

## Phase 1: Setup

- [ ] T001 Acrescentar as propriedades do data-model ("Configuração") em `src/main/resources/application.properties` (`lhamalog.occurrence.internal-auth.*` vindas de `OCCURRENCE_INTERNAL_JWT_*`, `lhamalog.occurrence.delivery.*`) e repassar `OCCURRENCE_INTERNAL_JWT_SECRETS`, `_ISSUER`, `_AUDIENCE` e `_SUBJECTS` no serviço `api` do `compose.yaml` e no `.env.example` (sem valor)
- [ ] T002 [P] Criar `OccurrenceModuleProperties` (`@ConfigurationProperties("lhamalog.occurrence")`) em `occurrence/application/` com `internalAuth` (secrets, issuer, audience, subjects) e `delivery` (lease, receiptRetention, maxFindings, maxSamples)

---

## Phase 2: Foundational (bloqueia as histórias)

- [ ] T003 [P] Criar `InternalTokens` em `src/test/java/br/com/lhamalog/core/occurrence/InternalTokens.java`: gera JWT HS256 com segredo fictício de teste, `iss`, `aud`, `sub`, `exp`, `nbf` e permite variar cada um (expirado, `alg` errado, outro segredo, sem `sub`)
- [ ] T004 Escrever `InternalAuthIntegrationTests.java` (US4): 401 sem token, malformado, assinatura de outro segredo, `alg: none`, expirado, `nbf` futuro, `iss` e `aud` errados, token da Clerk (`TestJwks`); 403 para `sub` fora da lista; 200 com o segundo segredo da lista (rotação); JWT interno recusado em `/api/occurrences`; corpo `{"codigo"}` e `WWW-Authenticate: Bearer` no 401; sem segredo configurado → 401 em tudo
- [ ] T005 Criar `InternalServiceSecurityConfiguration` em `config/`: `SecurityFilterChain` com `@Order` antes da chain da Clerk e `securityMatcher("/internal/v1/**")`, stateless, CSRF desligado, `oauth2ResourceServer().jwt()` com um `JwtDecoder` HS256 que tenta cada segredo da lista, validadores de `exp`/`nbf` (30 s), `iss`, `aud` e `sub` presente, autorização por `sub` na lista, `AuthenticationEntryPoint` e `AccessDeniedHandler` com `{"codigo":"NAO_AUTENTICADO"}`/`{"codigo":"ACESSO_NEGADO"}`; log `event=occurrence.auth.configured` (quantidade e tamanho dos segredos, nunca o valor) ou `event=occurrence.auth.not_configured` (WARN) na subida
- [ ] T006 [P] Criar `InternalOccurrenceErrors` em `occurrence/api/`: exceção `InternalApiException(status, codigo)` e `@RestControllerAdvice(assignableTypes = InternalOccurrenceController.class)` que responde `{"codigo"}`; JSON ilegível ou tipo JSON errado → 400 `REQUISICAO_INVALIDA`; falha inesperada → 500 `ERRO_INTERNO` com ERROR sem o corpo
- [ ] T007 [P] Criar `InternalOccurrenceRequests` em `occurrence/api/` com os corpos `ConsultaAchado`, `ResumoAnalise`, `Contadores`, `IdentificacaoRegras` e `AchadoAnalise` do contrato (nomes JSON em português, campos `String`/`Long`/`Boolean`/`List`/`Map`, `@JsonIgnoreProperties(ignoreUnknown = false)`)
- [ ] T008 [P] Criar `InternalOccurrenceResponses` em `occurrence/api/` com `ResultadoConsulta(estado)`, `EstadoOcorrencia` e `Confirmacao` (com `execId` omitido quando ausente)
- [ ] T009 Criar o controller `InternalOccurrenceController` em `occurrence/api/` com as duas rotas `POST` do contrato, ainda delegando a services vazios, e a conferência de produto do tenant (`ProductRepository.findByIdAndTenantId`, ausente → 403 `ACESSO_NEGADO`, research R6)
- [ ] T010 Rodar `InternalAuthIntegrationTests` e deixar a US4 verde

**Checkpoint**: rotas internas protegidas e com formato de erro do contrato.

---

## Phase 3: User Story 1 - Consultar o estado de um achado (P1) 🎯 MVP

**Goal**: `POST /internal/v1/ocorrencias/consultas` (FR-001, FR-002).

**Independent Test**: spec US1; quickstart M4, M5 e M8.

### Tests

- [ ] T011 [US1] Escrever `OccurrenceLookupIntegrationTests.java` com a `OccurrenceFixtures` da spec 012: chave inexistente → `{"estado": null}`; existente → contagem, datas com `Z`, status e revisão; documento sem `revision` → `sem-revisao`; mesma assinatura com outro tipo é outra chave; produto de outro tenant → 403; campo a mais, tipo `FATAL`, campo vazio → 422; JSON ilegível → 400; documento sem `count` → 500 `OCORRENCIA_INVALIDA` com ERROR sem a assinatura

### Implementation

- [ ] T012 [US1] Criar a porta `OccurrenceWriteRepository` em `occurrence/application/` com `List<StoredOccurrence> findByKeys(tenantId, productId, Collection<OccurrenceKey>)` (documento bruto + revisão) e `MongoOccurrenceWriteRepository` em `occurrence/infrastructure/` com a leitura em lote por `signature $in`
- [ ] T013 [US1] Criar `OccurrenceLookupService` em `occurrence/application/`: valida o corpo (tipo, identificadores), lê a chave, monta o `EstadoOcorrencia` ou `null`, erro `OCORRENCIA_INVALIDA` para duplicidade ou documento sem contagem/datas/status; log `event=occurrence.lookup.served` em DEBUG e ERROR `event=occurrence.lookup.invalid` sem assinatura
- [ ] T014 [US1] Ligar a rota de consulta no `InternalOccurrenceController` e rodar a US1 verde

**Checkpoint**: o Finder consegue consultar.

---

## Phase 4: User Story 2 - Entregar o resumo e receber o recibo (P1)

**Goal**: `POST /internal/v1/resumos-analise` com gravação, recibo e reenvio (FR-003 a FR-007, FR-011 a FR-013).

**Independent Test**: spec US2; quickstart M6, M7, M9, M11 e M12.

### Tests

- [ ] T015 [P] [US2] Escrever `AnalysisSummaryValidatorTest.java`: cada regra do research R5 (horário sem `Z`, contagem 0, `numeroLinha` 0, `execId` fora do padrão, tipo desconhecido, primeira > última, contadores incoerentes, `versaoUnica` diferente, chave repetida no resumo, `revisaoConsultada` vazia, mais de 1.000 achados) e um resumo válido completo
- [ ] T016 [P] [US2] Escrever `OccurrenceMergeTest.java`: criação (ABERTA, histórico de criação, UUID), FECHADA → ABERTA com histórico, ABERTA e EM_ANALISE mantidos, status desconhecido → ABERTA, campos opcionais `null` mantêm o anterior, `camposExtraido` substitui, `linhas` anexadas com limite de 20, `linhas: []` mantém, contagem e datas sempre do achado, `rulesVersion` do resumo, revisão nova
- [ ] T017 [US2] Escrever `AnalysisSummaryDeliveryIntegrationTests.java` (parte US2): primeira entrega 201 com recibo e documentos gravados no formato da spec 012 (lidos de volta por `GET /api/occurrences` da LLT-135, com `http.route` em `extractedFields`); reenvio igual e reenvio com corpo recalculado → 200 com o mesmo `reciboId` e coleção igual; `execId` diferente → nova chave; resumo sem achados → 201 sem mudar ocorrências; reabertura FECHADA → ABERTA com histórico; 422 sem gravar nada; recibo `CONFIRMED` com `expiresAt` em 30 dias e sem diário

### Implementation

- [ ] T018 [P] [US2] Criar `AnalysisSummary` (records de domínio: `DeliveryKey`, `Finding`, `OccurrenceKey`) e `AnalysisSummaryValidator` em `occurrence/application/` (research R5)
- [ ] T019 [P] [US2] Criar `OccurrenceMerge` em `occurrence/application/` (função pura, research R4), devolvendo o documento depois como `org.bson.Document` no formato do data-model da spec 012
- [ ] T020 [US2] Criar `DeliveryReceiptDocument` e a porta `DeliveryReceiptRepository` com `MongoDeliveryReceiptRepository` em `occurrence/infrastructure/`: índices `delivery_receipt_key` (único) e `delivery_receipt_expiry` (TTL), `find(key)`, `reserve(key, attemptId, reciboId, leaseUntil)` (duplicata → vazio), `saveJournal(attemptId, entries)`, `confirm(attemptId, confirmedAt, expiresAt, findingCount)`, `release(attemptId)`
- [ ] T021 [US2] Completar `MongoOccurrenceWriteRepository` com `replaceIfRevision(id, expectedRevision, after)`, `insertNew(after)` (duplicata da chave → conflito) e os inversos `restoreIfRevision(id, newRevision, before)` e `deleteIfRevision(id, newRevision)`, gravando com `Document` para aceitar chaves com ponto
- [ ] T022 [US2] Criar `AnalysisSummaryDeliveryService` com o caminho feliz do research R2 (passos 1, 2 com `CONFIRMED`, 3, 4 sem divergência, 5, 6 e 8) e logs `event=occurrence.delivery.accepted` / `.repeated` com `tenantId`, `productId`, `batchId`, `execId` e quantidade de achados
- [ ] T023 [US2] Ligar a rota de entrega no `InternalOccurrenceController` (201/200 e corpo `Confirmacao`) e rodar a US2 verde

**Checkpoint**: lotes são gravados e o reenvio não duplica.

---

## Phase 5: User Story 3 - Recusar entregas desatualizadas ou concorrentes sem efeito parcial (P1)

**Goal**: FR-008 a FR-010, SC-002 e SC-003.

**Independent Test**: spec US3; quickstart M10.

### Tests

- [ ] T024 [US3] Acrescentar a `AnalysisSummaryDeliveryIntegrationTests`: revisão desatualizada num achado de três → 409 `REVISAO_DESATUALIZADA` e nenhuma das três ocorrências muda; `null` para ocorrência existente e revisão para inexistente → 409; depois do 409, consulta e reenvio na mesma chave → 201; duas entregas da mesma chave em paralelo → uma 201 e outra 409 `ENTREGA_EM_ANDAMENTO` (ou 200 se a primeira já confirmou), coleção com um único efeito; dois lotes que consultaram a mesma revisão → um 201 e outro 409, e depois da nova consulta a contagem final soma os dois
- [ ] T025 [US3] Acrescentar a `AnalysisSummaryDeliveryIntegrationTests`: falha simulada no meio da aplicação (repositório de teste que lança na segunda gravação) → nenhuma ocorrência fica alterada e o recibo é removido; corrida entre conferir e gravar (outra escrita muda a revisão de um achado depois da conferência) → desfaz os já gravados e 409; recibo `PENDING` vencido com diário de uma entrega interrompida → o próximo envio da chave desfaz e aplica uma vez (contagem igual à de uma única aplicação); `PENDING` vigente → 409 `ENTREGA_EM_ANDAMENTO`

### Implementation

- [ ] T026 [US3] Completar `AnalysisSummaryDeliveryService` com os ramos de recusa e recuperação do research R2: `PENDING` vigente → 409; `PENDING` vencido → desfazer pelo diário e recomeçar; divergência de revisão → liberar a reserva e 409; falha de condição na aplicação → desfazer, liberar e 409; reserva perdida na confirmação → 500 `ERRO_INTERNO`; logs `event=occurrence.delivery.stale_revision`, `.in_progress`, `.rolled_back`, `.recovered` (WARN) e `.count_decreased` (WARN)
- [ ] T027 [US3] Rodar a US3 verde, repetindo os testes de concorrência 20 vezes para conferir que não são instáveis

**Checkpoint**: tudo ou nada garantido sem transação.

---

## Phase 6: User Story 5 - Homologar com o cliente do Finder (P2)

**Goal**: os cinco cenários do contrato com o cliente da spec 009 do finder (SC-006).

**Independent Test**: quickstart "Homologação com o Finder".

- [ ] T028 [P] [US5] Criar `scripts/InternalJwt.java` (Java de arquivo único, sem dependências): lê `OCCURRENCE_INTERNAL_JWT_SECRET`, aceita `--sub`, `--days`, `--iss` e `--aud` com os padrões do data-model, imprime só o token; documentar a rotação no README
- [ ] T029 [US5] Rodar o roteiro M1 a M13 do quickstart contra o Core local e registrar o resultado no fim deste arquivo
- [ ] T030 [US5] Combinar com o João a homologação dos cinco cenários (Finder apontado para o Core local com proxy TLS) e registrar o resultado no fim deste arquivo

---

## Phase 7: Polish

- [ ] T031 [P] Documentar no `README.md` a seção "Módulo Ocorrência": rotas internas, variáveis `OCCURRENCE_INTERNAL_JWT_*`, geração e rotação do token, retenção dos recibos
- [ ] T032 [P] Atualizar `specs/012-consultar-ocorrencias/contracts/ocorrencia-documento.md` dizendo que a gravação foi implementada pela spec 013 e que nomes com ponto em `extractedFields` são gravados como vêm
- [ ] T033 Medir o SC-004 (consulta e entrega de 100 achados) com um teste sob demanda, como o `OccurrencePerformanceTests` da spec 012, e registrar os tempos no fim deste arquivo
- [ ] T034 Rodar `.\mvnw.cmd verify` com o Docker ligado e registrar o resultado no fim deste arquivo
- [ ] T035 Revisão do Felipe: conferir cada arquivo contra a spec e o research (constituição II), em especial o desfazer do research R2

---

## Dependencies & Execution Order

- Setup (T001–T002) → Foundational (T003–T010) → US1 (T011–T014) → US2 (T015–T023) → US3 (T024–T027) → US5 (T028–T030) → Polish.
- US3 estende o service e os testes da US2; vem depois.
- US1 e US2 compartilham `MongoOccurrenceWriteRepository` (T012 cria, T021 completa).
- A homologação (T030) depende do Finder e do João; o PR pode ir para revisão antes dela, com a T030 registrada como pendente.

### Parallel Opportunities

- T003, T006, T007 e T008 em paralelo; T015, T016, T018 e T019 em paralelo; T028, T031 e T032 em paralelo com a US3.

## Implementation Strategy

1. MVP: Setup + Foundational + US1 → o Finder já consegue consultar (estado sempre `null` até haver gravação).
2. US2 grava e responde recibos; US3 fecha o tudo ou nada e a concorrência.
3. US5 e Polish: script de token, roteiro, homologação e revisão.
