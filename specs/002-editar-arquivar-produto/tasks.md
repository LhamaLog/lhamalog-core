# Tasks: Editar e arquivar Produto

**Input**: Design documents from `/specs/002-editar-arquivar-produto/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: Incluídos — constituição III e `quickstart.md` exigem cobertura do comportamento novo antes do merge.

**Organization**: Tasks grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

Módulo existente `src/main/java/br/com/lhamalog/core/product/` e testes em `src/test/java/br/com/lhamalog/core/`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirmar o módulo da 001; sem projeto novo nem dependência Maven.

- [x] T001 Confirmar implementação só em `src/main/java/br/com/lhamalog/core/product/` sem alterar `pom.xml` nem criar pacotes novos

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Campo `archivedAt` no documento para todas as histórias. `status` permanece `CONFIGURING`.

**CRITICAL**: No user story work can begin until this phase is complete

- [x] T002 Acrescentar `archivedAt` (`Instant` ou nulo) ao record em `src/main/java/br/com/lhamalog/core/product/Product.java` sem tornar `status` gravável e sem mudar o significado de `CONFIGURING`
- [x] T003 Gravar `archivedAt` nulo no cadastro (`status` continua `CONFIGURING`) em `src/main/java/br/com/lhamalog/core/product/ProductService.java`
- [x] T004 Ajustar qualquer `new Product(` restante para a nova assinatura em `src/main/java/br/com/lhamalog/core/product/` e `src/test/java/br/com/lhamalog/core/`

**Checkpoint**: Foundation ready — cadastro/listagem/contexto da 001 compilam com `archivedAt` nulo

---

## Phase 3: User Story 1 - Editar dados gerais do produto (Priority: P1) MVP

**Goal**: PUT dos dados gerais com validação, acesso da 001 e concorrência por `version`.

**Independent Test**: criar produto, PUT com `version` atual, GET confirma persistência; PUT com `version` velho e PUT sem acesso recusados sem alteração.

### Tests for User Story 1

> Escrever estes testes primeiro e garantir que falham antes da implementação.

- [x] T005 [US1] Testes de PUT `/api/products/{id}` (persistência, nome vazio/máx. 200, descrição máx. 10000, 409 de `version`, 404 sem acesso e outro tenant) em `src/test/java/br/com/lhamalog/core/product/ProductEditArchiveIntegrationTests.java`

### Implementation for User Story 1

- [x] T006 [US1] Implementar `update` em `src/main/java/br/com/lhamalog/core/product/ProductService.java`: `name` obrigatório, trim, não vazio, máx. 200; `description` máx. 10000; `category` e `productVersion` máx. 200; `version` obrigatório e igual ao persistido senão 409; não alterar `context`, `status`, `tenantId`, `createdAt` nem `archivedAt`; recarregar persiste; isolamento `findByIdAndTenantId` e acesso como em `get`
- [x] T007 [US1] Expor `PUT /api/products/{id}` e record `UpdateProduct` (`name` `@NotBlank` `@Size(max = 200)`, `description` `@Size(max = 10000)`, `category` e `productVersion` `@Size(max = 200)`, `version` `@NotNull`) em `src/main/java/br/com/lhamalog/core/product/ProductController.java`

**Checkpoint**: User Story 1 funcional e testável sem arquivo

---

## Phase 4: User Story 2 - Arquivar produto (Priority: P2)

**Goal**: Admin arquiva via `archivedAt`; some da listagem padrão; edição recusada; membro comum 403.

**Independent Test**: como admin, arquivar; sumir de GET lista; GET por id 200; PUT geral e PUT contexto 409; membro com acesso POST archive 403.

### Tests for User Story 2

> Escrever estes testes primeiro e garantir que falham antes da implementação.

- [x] T008 [US2] Testes de arquivo (admin 200, some da lista padrão, GET id 200, PUT geral/contexto 409, `status` inalterado, membro 403, outro tenant 404, segundo archive idempotente) em `src/test/java/br/com/lhamalog/core/product/ProductEditArchiveIntegrationTests.java`

### Implementation for User Story 2

- [x] T009 [US2] Adicionar consultas `findByTenantIdAndArchivedAtIsNullOrderByNameAscIdAsc` e `findByTenantIdAndIdInAndArchivedAtIsNullOrderByNameAscIdAsc` em `src/main/java/br/com/lhamalog/core/product/ProductRepository.java`
- [x] T010 [US2] Em `src/main/java/br/com/lhamalog/core/product/ProductService.java`: `list` só `archivedAt == null`; `archive` exige `session.administrator()` (403 se o produto é visível e não é admin, 404 se sem acesso/outro tenant); transição real exige `version` atual e grava `archivedAt` agora; já arquivado devolve o documento sem gravar; `update` e `updateContext` recusam `archivedAt != null` com 409
- [x] T011 [US2] Expor `POST /api/products/{id}/archive` com corpo `{ "version": 0 }` (`version` `@NotNull`) em `src/main/java/br/com/lhamalog/core/product/ProductController.java`

**Checkpoint**: User Stories 1 e 2 testáveis; lista padrão sem arquivados

---

## Phase 5: User Story 3 - Desarquivar e filtrar arquivados (Priority: P2)

**Goal**: Filtro `archived=true` e desarquive por admin; produto volta à lista e à edição.

**Independent Test**: listar com `archived=true`; desarquivar como admin; voltar à lista padrão; PUT geral 200; membro POST unarchive 403.

### Tests for User Story 3

> Escrever estes testes primeiro e garantir que falham antes da implementação.

- [x] T012 [US3] Testes de `GET /api/products?archived=true`, unarchive (admin 200, membro 403, outro tenant 404, já ativo idempotente, edição após desarquive) em `src/test/java/br/com/lhamalog/core/product/ProductEditArchiveIntegrationTests.java`

### Implementation for User Story 3

- [x] T013 [US3] Adicionar consultas `findByTenantIdAndArchivedAtIsNotNullOrderByNameAscIdAsc` e `findByTenantIdAndIdInAndArchivedAtIsNotNullOrderByNameAscIdAsc` em `src/main/java/br/com/lhamalog/core/product/ProductRepository.java`
- [x] T014 [US3] Em `src/main/java/br/com/lhamalog/core/product/ProductService.java`: `list(..., boolean archived)` escolhe nulo vs não nulo; `unarchive` com as mesmas regras de papel/404/409; transição real zera `archivedAt`; já ativo sem gravação
- [x] T015 [US3] Expor `POST /api/products/{id}/unarchive` e query `archived` (padrão `false`) em `GET /api/products` em `src/main/java/br/com/lhamalog/core/product/ProductController.java`

**Checkpoint**: Três histórias independentes no contrato de `contracts/api.md`

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Aceite da constituição e do quickstart

- [x] T016 Executar `.\mvnw.cmd verify` com `TEST_MONGODB_URI` isolado conforme `specs/002-editar-arquivar-produto/quickstart.md` e registrar o resultado nesse arquivo
- [x] T017 Confirmar ausência de DELETE, de mudança de `status` e de ingestão, e ausência de segredos em `src/main/java/br/com/lhamalog/core/product/` e `src/test/java/br/com/lhamalog/core/product/`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS all user stories
- **User Stories (Phase 3+)**: All depend on Foundational; US2 usa o PUT da US1 para 409 de arquivado; US3 usa o arquivo da US2
- **Polish (Final Phase)**: Depends on US1–US3

### User Story Dependencies

- **User Story 1 (P1)**: After Foundational — no other stories
- **User Story 2 (P2)**: After US1 (rejeição de edição de arquivado no PUT geral); PUT contexto da 001 já existe
- **User Story 3 (P2)**: After US2 (precisa de produto arquivado e lista filtrada)

### Within Each User Story

- Tests MUST be written and FAIL before implementation
- Repository before service before controller
- Story complete before the next priority

### Parallel Opportunities

- Poucas: `Product.java` / `ProductService.java` / `ProductController.java` / o mesmo arquivo de teste são compartilhados
- T009 e os testes T008 não são paralelos com T006/T007 (mesmo service)
- Trabalho em paralelo só após fundação se pessoas distintas editarem arquivos diferentes — neste módulo, executar US1 → US2 → US3

---

## Parallel Example: User Story 1

```text
Não paralelizar T006 e T007: ProductService.java e ProductController.java encadeiam a assinatura de update.
T005 deve falhar antes de T006/T007.
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 Setup
2. Phase 2 Foundational
3. Phase 3 User Story 1
4. STOP and VALIDATE: PUT de dados gerais
5. Seguir para arquivo só depois do PUT verde

### Incremental Delivery

1. Setup + Foundational
2. US1 edição → testes T005
3. US2 arquivo + lista padrão
4. US3 filtro e desarquive
5. T016 verify

### Parallel Team Strategy

Um desenvolvedor (arquivos concentrados). Não dividir histórias no mesmo `ProductService`.

---

## Notes

- [P] só com arquivos distintos e sem dependência incompleta — nesta feature quase não há
- Identificadores em inglês (`archivedAt`, `archive`, `unarchive`, `UpdateProduct`); esta lista em português
- Sem commit nesta etapa
- Branch de merge da constituição: `feature/LLT-123-editar-arquivar-produto`
