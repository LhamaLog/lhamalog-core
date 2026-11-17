# Tasks: Nome único de produto por empresa

**Input**: Design documents from `/specs/006-nome-unico-produto/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: Incluídos — constituição III e `quickstart.md` exigem cobertura do comportamento novo antes do merge.

**Organization**: Tasks grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

Módulo existente `src/main/java/br/com/lhamalog/core/product/` e testes em `src/test/java/br/com/lhamalog/core/`.

**Restrição de toda a feature**: nada aqui pode apagar ou alterar o que pertence a outras branches ou features LLT. Não alterar `specs/001-empresa-produtos/` nem `specs/002-editar-arquivar-produto/`. Não limpar, apagar nem apontar a aplicação ou os testes para o banco `lhamalog_core`, o volume `lhamalog-core-local_mongo-data` ou o banco de teste `lhamalog_teste`. Não executar `docker compose up` nesta branch.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Delimitar o alcance e preparar um banco de teste próprio; sem projeto novo nem dependência Maven.

- [X] T001 Confirmar que a implementação fica restrita a `src/main/java/br/com/lhamalog/core/product/Product.java`, `src/main/java/br/com/lhamalog/core/product/ProductService.java`, `src/test/java/br/com/lhamalog/core/product/ProductUniqueNameIntegrationTests.java` (novo) e ao teste `validatesProductNameAndAcceptsEmptyOptionalContext` de `src/test/java/br/com/lhamalog/core/company/CompanyProductIntegrationTests.java`, sem alterar `pom.xml`, `src/main/java/br/com/lhamalog/core/shared/ApiExceptionHandler.java` nem criar pacotes
- [X] T002 Na sessão PowerShell usada para os testes, definir `$env:TEST_MONGODB_URI='mongodb://127.0.0.1:27019/lhamalog_nome_unico_produto_test'` conforme `specs/006-nome-unico-produto/quickstart.md`; se o Mongo separado da porta 27019 não estiver no ar, parar e perguntar ao desenvolvedor em vez de usar outro banco

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Campo, índice, preservação do campo nas gravações e ponto único de tradução da exceção, usados por todas as histórias. `src/main` só volta a compilar ao fim de T005.

**CRITICAL**: No user story work can begin until this phase is complete

- [X] T003 Em `src/main/java/br/com/lhamalog/core/product/Product.java`, acrescentar o componente `String normalizedName` logo depois de `name`, anotado com `@JsonIgnore` (`com.fasterxml.jackson.annotation.JsonIgnore`), e o método estático `normalizeName(String name)` retornando `name.trim().toLowerCase(Locale.ROOT)`. Regra do data-model: "derivado de `name` com `trim()` e `toLowerCase(Locale.ROOT)`; não vem do cliente; não aparece nas respostas; recalculado no cadastro e na edição dos dados gerais; preservado na gravação de contexto, no arquivamento e no desarquivamento"
- [X] T004 Em `src/main/java/br/com/lhamalog/core/product/Product.java`, acrescentar o índice `product_tenant_normalized_name` com `def = "{'tenantId': 1, 'normalizedName': 1}"` e `unique = true`, agrupado com o `product_tenant_name` existente em `@CompoundIndexes`; manter `product_tenant_name` igual e não filtrar por `archivedAt`
- [X] T005 Em `src/main/java/br/com/lhamalog/core/product/ProductService.java`, ajustar os quatro `new Product(...)`: `create` e `update` passam `Product.normalizeName(request.name())`; `updateContext` e `withArchivedAt` passam `current.normalizedName()`
- [X] T006 Em `src/main/java/br/com/lhamalog/core/product/ProductService.java`, acrescentar a constante da mensagem `Já existe um produto com este nome nesta empresa. Ele pode estar arquivado.` e o método privado `saveWithUniqueName(Supplier<Product> write)`, que executa `write.get()` e converte `org.springframework.dao.DuplicateKeyException` em `new ResponseStatusException(HttpStatus.CONFLICT, <mensagem>, exception)`, sem inspecionar o texto da exceção
- [X] T007 [P] Em `validatesProductNameAndAcceptsEmptyOptionalContext` de `src/test/java/br/com/lhamalog/core/company/CompanyProductIntegrationTests.java`, trocar o corpo do POST no laço para usar o nome `"Produto " + index`, mantendo a asserção de 400 para nome em branco, os 5 cadastros e a contagem `$.length()` igual a 5; não alterar nenhum outro teste desse arquivo
- [X] T008 [P] Criar `src/test/java/br/com/lhamalog/core/product/ProductUniqueNameIntegrationTests.java` com as mesmas anotações de classe e campos `@Autowired` de `ProductEditArchiveIntegrationTests` (`@SpringBootTest` com `spring.mongodb.uri=${TEST_MONGODB_URI}`, `@EnabledIfEnvironmentVariable(named = "TEST_MONGODB_URI", matches = ".+")`, empresa aleatória por teste), os helpers `session`, `organization`, `json`, `create`, `general`, `edit` e `changeArchive` no mesmo formato, constantes das três mensagens 409 de `specs/006-nome-unico-produto/contracts/api.md`, contagem de documentos por `tenantId` em `Product` e `ProductAccess` via `MongoTemplate`, e um helper que dispara requisições em paralelo com `ExecutorService` e devolve a lista de status HTTP

**Checkpoint**: Foundation ready — `src/main` compila e o índice existe no banco de teste; nomes repetidos já são recusados, ainda com a mensagem genérica do handler global

---

## Phase 3: User Story 1 - Recusar nome repetido no cadastro (Priority: P1) MVP

**Goal**: POST com nome em uso na empresa responde 409 com mensagem própria e não deixa produto nem vínculo de acesso.

**Independent Test**: cadastrar um produto e tentar outro com o mesmo nome em variações de maiúsculas e espaços nas pontas; confirmar a recusa, a mensagem e que nada foi criado; repetir o nome em outra empresa e confirmar 201.

### Tests for User Story 1

> Escrever estes testes primeiro e garantir que falham antes da implementação. Depois da fundação, eles falham na asserção da mensagem própria.

- [X] T009 [US1] Em `src/test/java/br/com/lhamalog/core/product/ProductUniqueNameIntegrationTests.java`, testes de `POST /api/products` (cenários 1 a 6, 14, 15 e 16 do quickstart): nome repetido responde 409 com a mensagem exata e sem documento novo em `Product` ou `ProductAccess`; `checkout`, `CHECKOUT` e `  Checkout  ` recusados quando existe `Checkout`; nome de produto arquivado recusado; mesmo nome em outra empresa responde 201; `  Meu Produto  ` responde 201 com `name` gravado como `Meu Produto` e `normalizedName` gravado como `meu produto`; POSTs paralelos com nome livre têm exatamente um 201, os demais 409 com a mensagem própria, um único `Product` e um único `ProductAccess`; `AÇÃO` colide com `ação`, `Acao` não colide com `Ação` e `Meu App` não colide com `Meu  App`; nome em branco ou com 201 caracteres responde 400 em `name` mesmo com nome ocupado; respostas não contêm `normalizedName`

### Implementation for User Story 1

- [X] T010 [US1] Em `create` de `src/main/java/br/com/lhamalog/core/product/ProductService.java`, envolver `products.insert(...)` com `saveWithUniqueName`, dentro do `try` existente, para o `catch (RuntimeException)` continuar removendo o vínculo de acesso; não mudar a ordem de `accesses.insert`

**Checkpoint**: User Story 1 funcional e testável sem a história de edição

---

## Phase 4: User Story 2 - Recusar nome repetido na edição (Priority: P2)

**Goal**: PUT para nome de outro produto responde 409 com mensagem própria sem alterar nada; manter o próprio nome continua aceito.

**Independent Test**: com dois produtos na empresa, renomear um para o nome do outro em variações de maiúsculas e espaços e confirmar 409 sem alteração; editar a descrição mantendo o nome e confirmar 200.

### Tests for User Story 2

> Escrever estes testes primeiro e garantir que falham antes da implementação. Depois da fundação, eles falham na asserção da mensagem própria.

- [X] T011 [US2] Em `src/test/java/br/com/lhamalog/core/product/ProductUniqueNameIntegrationTests.java`, testes de `PUT /api/products/{id}` (cenários 7 a 11, 15 e 17 do quickstart): renomear para nome de outro produto, ativo ou arquivado, responde 409 com a mensagem exata e mantém campos e `version`; manter o nome ou mudar só maiúsculas e espaços nas pontas responde 200 e grava a nova forma; depois de renomear, POST com o nome antigo responde 201; nome ocupado com usuário sem acesso ou de outra empresa responde 404; PUTs paralelos de produtos diferentes para o mesmo nome livre têm exatamente um 200 e os demais 409 com a mensagem própria; nome em branco com nome ocupado responde 400; produto arquivado com nome ocupado responde 409 com a mensagem de arquivado; `version` desatualizado com nome ocupado responde 409 com a mensagem de versão

### Implementation for User Story 2

- [X] T012 [US2] Em `update` de `src/main/java/br/com/lhamalog/core/product/ProductService.java`, envolver `products.save(...)` com `saveWithUniqueName`, mantendo a ordem `get`, `requireActive`, `requireCurrentVersion`; não envolver `updateContext`, `archive` nem `unarchive`

**Checkpoint**: User Stories 1 e 2 testáveis; contrato de `contracts/api.md` atendido

---

## Phase 5: User Story 3 - Nome reservado durante o arquivamento (Priority: P2)

**Goal**: O nome continua ocupado enquanto o produto estiver arquivado, e o desarquivamento nunca conflita.

**Independent Test**: arquivar um produto, tentar cadastrar outro com o mesmo nome e confirmar 409; desarquivar e confirmar 200.

Esta história não tem código de produção próprio: o índice sem filtro por `archivedAt` (T004) e a preservação do campo (T005) a entregam. Os testes verificam só o status 409, para não depender das mensagens de US1 e US2, e por isso passam logo após a fundação.

### Tests for User Story 3

- [X] T013 [US3] Em `src/test/java/br/com/lhamalog/core/product/ProductUniqueNameIntegrationTests.java`, testes do ciclo de arquivamento (cenário 12 do quickstart): depois de arquivar, POST com o mesmo nome e PUT de outro produto para esse nome respondem 409; desarquivar responde 200 sem conflito de nome; depois de desarquivar e renomear para nome livre, POST com o nome antigo responde 201
- [X] T014 [US3] Em `src/test/java/br/com/lhamalog/core/product/ProductUniqueNameIntegrationTests.java`, teste de preservação (cenário 13 do quickstart e FR-013): depois de `PUT /api/products/{id}/context`, `archive` e `unarchive`, cada operação responde 200, `normalizedName` segue gravado igual em `products.findById` e POST com o mesmo nome responde 409

**Checkpoint**: Três histórias independentes; arquivado continua ocupando o nome

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Aceite da constituição e do quickstart

- [X] T015 Executar `.\mvnw.cmd verify` com o `TEST_MONGODB_URI` de T002, confirmar que os testes de persistência não foram omitidos e registrar o resultado em uma seção "Resultados executados" de `specs/006-nome-unico-produto/quickstart.md`
- [X] T016 Com `git status --short` e `git diff --stat`, confirmar que só mudaram os arquivos de T001 e `specs/006-nome-unico-produto/`, sem alteração em `specs/001-empresa-produtos/` e `specs/002-editar-arquivar-produto/`; confirmar ausência de segredos, de limite por plano e de mudança de ingestão em `src/main/java/br/com/lhamalog/core/product/` e `src/test/java/br/com/lhamalog/core/product/`

---

## Phase 7: Ajustes antes do merge (16/11/2026)

**Purpose**: Pendências da revisão do PR: evidência do verify no merge com a Development, numeração livre para a spec, constituição 2.1.0 (princípio VI) e procedimento para deploy em banco com dados. Mesma restrição de toda a feature: nada de outras branches ou features é apagado ou alterado.

- [X] T017 No commit `31ffea3` (merge da Development), antes de qualquer alteração, executar `.\mvnw.cmd verify` com o `TEST_MONGODB_URI` de T002 e registrar o resultado em "Resultados executados" de `specs/006-nome-unico-produto/quickstart.md`
- [X] T018 Renomear `specs/003-nome-unico-produto/` para `specs/006-nome-unico-produto/` (a 003 já existe na Development como `003-manter-perfil-usuario`, e a 007 está reservada para a LLT-183 na constituição); atualizar as referências a ela nesta pasta, no Javadoc de `src/test/java/br/com/lhamalog/core/product/ProductUniqueNameIntegrationTests.java` e em `.specify/feature.json` (arquivo local, ignorado pelo Git); não alterar pastas de outras features
- [X] T019 Alinhar `specs/006-nome-unico-produto/plan.md` à constituição 2.1.0: Constraints, Constitution Check com o princípio VI e reavaliação registrada
- [X] T020 Em `src/main/java/br/com/lhamalog/core/product/ProductService.java`, `saveWithUniqueName` passa a receber o código do evento, `tenantId` e `productId` e, antes de lançar o 409, registra `log.info("event={} | Product name already in use in the company; write refused with 409 | tenantId={} productId={}", ...)`; `create` passa `product.create.name_in_use` e `update` passa `product.update.name_in_use`; o log não leva o nome digitado nem a exceção (sem stack trace)
- [X] T021 Em `src/test/java/br/com/lhamalog/core/product/ProductUniqueNameIntegrationTests.java`, com `OutputCaptureExtension`, teste `nameInUseRefusalIsLoggedWithEventCodeWithoutNameOrStackTrace` (cenário 19 do quickstart): os dois eventos aparecem em INFO com `tenantId` e, na edição, `productId`, sem o nome e sem stack trace na linha seguinte
- [X] T022 Em `specs/006-nome-unico-produto/quickstart.md`, seção "Antes de deploy em banco com produtos": backup, checagem de duplicatas por empresa, backfill de `normalizedName` (trim e minúsculas, equivalente a `Product.normalizeName`) e conferência antes da subida; registrar em `research.md` e `data-model.md` que hoje só há bancos locais e de teste
- [X] T023 Executar `.\mvnw.cmd verify` com o `TEST_MONGODB_URI` de T002 depois de T020 e T021, registrar em "Resultados executados" e conferir com `git status --short` que só mudaram `specs/006-nome-unico-produto/` (renomeada), `ProductService.java` e `ProductUniqueNameIntegrationTests.java`. Executado em 16/11/2026: classes de produto verdes; o build segue vermelho só pela falha herdada da Development em `CompanyRegistrationHttpTests`, registrada no quickstart

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS all user stories
- **User Stories (Phase 3+)**: All depend on Foundational
- **Polish (Phase 6)**: Depends on US1–US3
- **Ajustes antes do merge (Phase 7)**: T017 antes de qualquer alteração; T020 e T021 antes de T023

### User Story Dependencies

- **User Story 1 (P1)**: After Foundational — no other stories
- **User Story 2 (P2)**: After Foundational — no other stories; usa o `POST` existente só para preparar dados
- **User Story 3 (P2)**: After Foundational — no other stories; verifica só o status 409

### Within Each User Story

- T009 e T011 MUST be written and FAIL before T010 e T012
- T003, T004, T005 e T006 nessa ordem; T007 e T008 podem correr em paralelo a elas
- T007 precisa estar pronta antes de rodar a suíte, porque o índice único quebra a versão antiga do teste

### Parallel Opportunities

- T007 e T008, em arquivos distintos das demais tasks da fundação
- As histórias compartilham `ProductService.java` e `ProductUniqueNameIntegrationTests.java`: executar US1 → US2 → US3

---

## Parallel Example: User Story 1

```text
Não paralelizar T009 e T010: T009 deve falhar antes de T010.
Paralelo possível só na fundação: T007 (CompanyProductIntegrationTests.java) e T008 (ProductUniqueNameIntegrationTests.java).
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 Setup
2. Phase 2 Foundational
3. Phase 3 User Story 1
4. STOP and VALIDATE: POST com nome repetido
5. Seguir para a edição só depois do POST verde

A US1 sozinha não fecha a regra: sem T012, a edição recusa o nome com a mensagem genérica de conflito. O merge exige as três histórias e T015.

### Incremental Delivery

1. Setup + Foundational
2. US1 cadastro → T009, T010
3. US2 edição → T011, T012
4. US3 arquivamento → T013, T014
5. T015 verify, T016 alcance

### Parallel Team Strategy

Um desenvolvedor (arquivos concentrados). Não dividir histórias no mesmo `ProductService`.

---

## Notes

- [P] só com arquivos distintos e sem dependência incompleta
- Identificadores em inglês (`normalizedName`, `normalizeName`, `saveWithUniqueName`); esta lista em português
- Sem commit nesta etapa; commits no formato `LLT-177: <descricao>`
- Branch: `feature/LLT-177-nome-unico-produto`
