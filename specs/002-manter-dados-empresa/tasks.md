---

description: "Task list for manter dados da empresa"
---

# Tasks: Manter dados da empresa

**Input**: Design documents from `/specs/002-manter-dados-empresa/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/company-registration.md, quickstart.md

**Tests**: A spec pede testes HTTP de administrador, membro (403) e isolamento. Os testes desta lista são obrigatórios e devem falhar antes da implementação correspondente.

**Organization**: Uma fase por história de usuário, para implementar e testar cada uma em separado.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Pode rodar em paralelo (arquivos diferentes, sem dependência de tarefa incompleta)
- **[Story]**: História à qual a tarefa pertence (US1, US2, US3, US4)
- Cada tarefa cita o caminho do arquivo

## Path Conventions

- Código em `src/main/java/br/com/lhamalog/core/`
- Testes em `src/test/java/br/com/lhamalog/core/`
- Documentos desta feature em `specs/002-manter-dados-empresa/`

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirmar que a entrega reusa o Core e não abre stack nem configuração nova

- [ ] T001 Confirmar que `pom.xml` permanece em Java 25 e Spring Boot 4.1.1, sem dependência nova para esta feature
- [ ] T002 [P] Confirmar que `src/main/resources/application.properties` mantém `clerk.administrator-roles=${CLERK_ADMIN_ROLES:admin}` como única origem dos papéis de administrador

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Peças compartilhadas pelas histórias de gravação, recusa e exclusão

**⚠️ CRITICAL**: Nenhuma história começa antes desta fase

- [ ] T003 Adicionar `requireAdministrator` em `src/main/java/br/com/lhamalog/core/company/CompanyController.java`: quando `CompanySession.administrator()` for falso, lançar 403 com a mensagem "Somente o administrador da empresa pode alterar este cadastro.", diferente de "Selecione uma empresa para continuar." Ainda não chamar o método nos endpoints
- [ ] T004 [P] Adicionar `deleteByTenantId(String tenantId)` em `src/main/java/br/com/lhamalog/core/company/CompanyContextRevisionRepository.java`, removendo só revisões cujo `tenantId` seja exatamente o informado

**Checkpoint**: O helper de administrador e a remoção de revisões por organização existem

---

## Phase 3: User Story 1 - Administrador cadastra e edita a empresa (Priority: P1) 🎯 MVP

**Goal**: O administrador grava e altera o cadastro da própria organização, com os valores preservados e o conflito de versão já existente

**Independent Test**: Com papel `admin`, um `PUT` com `version: null` e o `GET` seguinte devolvem os dados gravados; um segundo `PUT` com a versão vigente muda um campo e mantém outro; um `PUT` com a versão antiga responde 409 e a consulta fica no segundo salvamento

### Tests for User Story 1 ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [ ] T005 [US1] Criar `src/test/java/br/com/lhamalog/core/company/CompanyRegistrationHttpTests.java` com `@Import(TestcontainersConfiguration.class)`, `@SpringBootTest` e `@AutoConfigureMockMvc`, sem `TEST_MONGODB_URI` e sem tag `latest`. O teste `adminRegistersAndUpdatesOwnCompany` usa JWT sintético `o.id` e `o.rol=admin` e falha até T006. Cobrir: primeira gravação com `version: null`; campo omitido na alteração permanece; versão divergente responde 409 e o documento fica como estava. Incluir troca e remoção de logotipo pelo administrador

### Implementation for User Story 1

- [ ] T006 [US1] Chamar `requireAdministrator` no início de `PUT /api/company`, `POST /api/company/logo` e `DELETE /api/company/logo` em `src/main/java/br/com/lhamalog/core/company/CompanyController.java`, antes de ler corpo, arquivo ou versão. Em `POST /api/company/logo`, aceitar arquivo ausente no parâmetro para a checagem ocorrer primeiro; arquivo ausente de administrador continua erro de pedido inválido. Manter em `src/main/java/br/com/lhamalog/core/company/CompanyService.java` as regras: cadastro incompleto continua aceito; campo preenchido continua validado; campos omitidos numa alteração permanecem
- [ ] T007 [US1] Atualizar `src/test/java/br/com/lhamalog/core/company/CompanyProductIntegrationTests.java` para que as gravações de empresa usem papel `admin`. O `PUT` de membro deixa de ser o caso de validação 400. Acrescentar um `PUT` de administrador com corpo inválido que ainda recebe 400

**Checkpoint**: O administrador cadastra, altera e recebe 409 na versão antiga. A história se prova sem a exclusão

---

## Phase 4: User Story 2 - Membro consulta e não altera (Priority: P1)

**Goal**: O membro da organização lê o cadastro e recebe 403 ao tentar gravar ou mexer no logotipo, sem perder a sessão e sem mudar os dados

**Independent Test**: Com o cadastro já gravado por um administrador, o membro consulta empresa, logotipo e revisões com sucesso. `PUT`, `POST` do logotipo e `DELETE` do logotipo respondem 403, inclusive com corpo inválido ou arquivo ausente, e a consulta seguinte permanece igual

### Tests for User Story 2 ⚠️

- [ ] T008 [US2] Acrescentar em `src/test/java/br/com/lhamalog/core/company/CompanyRegistrationHttpTests.java` os testes `memberReadsAndCannotChange`, `nonAdminSlugCannotChange` e `sessionRulesStayDistinct`. Membro com organização recebe 200 na leitura. `PUT`, `POST /api/company/logo` e `DELETE /api/company/logo` respondem 403 com "Somente o administrador da empresa pode alterar este cadastro." e os dados não mudam. Slug `owner` ou `Administrador`, fora de `CLERK_ADMIN_ROLES` padrão `admin`, segue o membro. Pedido sem JWT responde 401. JWT sem organização responde 403 com "Selecione uma empresa para continuar." Os testes falham até T009

### Implementation for User Story 2

- [ ] T009 [US2] Ajustar a ordem em `src/main/java/br/com/lhamalog/core/company/CompanyController.java` para o membro receber 403 antes da validação de JSON, de arquivo e de versão. A mensagem do membro permanece "Somente o administrador da empresa pode alterar este cadastro." Não alterar `src/main/java/br/com/lhamalog/core/shared/CompanySession.java`: sessão sem organização continua "Selecione uma empresa para continuar."

**Checkpoint**: Leitura do membro e recusa das gravações existentes funcionam sem o endpoint novo de exclusão

---

## Phase 5: User Story 3 - Administrador exclui o cadastro (Priority: P1)

**Goal**: O administrador remove só o cadastro da organização ativa. Produtos, vínculos e a organização no serviço de identidade permanecem. Sem cadastro, a exclusão não tem efeito

**Independent Test**: Administrador grava cadastro, logotipo, uma mudança de contexto e um produto. `DELETE /api/company` responde 200 com `version` nula e campos vazios. Logotipo responde 404. Revisões ficam vazias. O produto continua. Outro `DELETE` responde 200 vazio. Novo `PUT` com `version: null` grava só os dados novos. `PUT` com a versão anterior responde 409. Membro recebe 403 no `DELETE` e o cadastro não muda

### Tests for User Story 3 ⚠️

- [ ] T010 [US3] Acrescentar `adminDeletesRegistrationAndMemberCannot` em `src/test/java/br/com/lhamalog/core/company/CompanyRegistrationHttpTests.java`, falhando até T012. Afirmar: exclusão com cadastro responde 200 no mesmo formato do GET sem cadastro e `version` nula; segundo DELETE sem documento termina sem efeito e também responde 200; logotipo seguinte responde 404; revisões da organização ficam vazias; `Produto` e `AcessoProduto` continuam com o `tenantId`; novo `PUT` com `version: null` não restaura os valores removidos; `PUT` com a versão anterior responde 409; membro recebe 403 e o documento fica como estava

### Implementation for User Story 3

- [ ] T011 [US3] Implementar `deleteRegistration(String tenantId)` em `src/main/java/br/com/lhamalog/core/company/CompanyService.java`. Sem documento `Tenant`, devolver a empresa vazia dessa organização, com `version` nula, sem apagar outra organização. Com documento, remover `Tenant`, `TenantLogo` e as revisões daquele `tenantId` via `deleteByTenantId`. Não chamar repositório de `Produto` nem de `AcessoProduto` e não chamar a Clerk
- [ ] T012 [US3] Adicionar `DELETE /api/company` em `src/main/java/br/com/lhamalog/core/company/CompanyController.java`, sem corpo e sem versão, chamando `requireAdministrator` e em seguida `deleteRegistration` só com o id da sessão

**Checkpoint**: A exclusão esvazia o cadastro da organização e preserva o produto

---

## Phase 6: User Story 4 - Isolamento entre empresas (Priority: P1)

**Goal**: Consulta, alteração e exclusão de uma organização não revelam nem modificam o cadastro de outra

**Independent Test**: A e B têm cadastros diferentes. A sessão de A não mostra nem altera B, mesmo quando o corpo cita o id de B. O membro de A que tenta gravar recebe 403 e as duas empresas ficam como estavam

### Tests for User Story 4 ⚠️

- [ ] T013 [US4] Acrescentar `operationsStayInsideTheSessionOrganization` em `src/test/java/br/com/lhamalog/core/company/CompanyRegistrationHttpTests.java`, falhando até T014. Duas organizações com cadastros diferentes: consulta, `PUT` e `DELETE` da sessão A deixam B intacto e oculto. Corpo com `organizationId` de B ainda grava só em A, porque id de organização enviado no corpo continua ignorado. Membro de A recebe 403 e nenhum dos dois cadastros muda

### Implementation for User Story 4

- [ ] T014 [US4] Garantir em `src/main/java/br/com/lhamalog/core/company/CompanyController.java` e `src/main/java/br/com/lhamalog/core/company/CompanyService.java` que gravação e exclusão usam somente o id da sessão. A exclusão não recebe id no caminho nem no corpo. Não acrescentar parâmetro de organização no `DELETE`

**Checkpoint**: As quatro histórias passam na classe HTTP nova

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Fechar a regressão dos caminhos fora da entrega e o portão de teste

- [ ] T015 [P] Acrescentar em `src/test/java/br/com/lhamalog/core/company/CompanyRegistrationHttpTests.java` que `GET /api/company/postal-code/{cep}`, `POST /api/company/actions/export-pdf` e `POST /api/company/actions/sync` continuam 501 para administrador e para membro
- [ ] T016 [P] Confirmar em `src/main/java/br/com/lhamalog/core/company/CompanyService.java` que a exclusão não cria coleção, métrica nem evento de auditoria
- [ ] T017 Executar `.\mvnw.cmd verify` conforme `specs/002-manter-dados-empresa/quickstart.md` e registrar o resultado nesse arquivo

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: começa imediatamente
- **Foundational (Phase 2)**: depende da Phase 1 e bloqueia todas as histórias
- **User Stories (Phase 3+)**: dependem da Phase 2
- **Polish (Phase 7)**: depende das quatro histórias

### User Story Dependencies

- **User Story 1 (P1)**: começa após a Phase 2. Não depende das outras histórias
- **User Story 2 (P1)**: começa após a Phase 2. Usa o gate ligado na US1; o teste independente é a leitura e o 403 das gravações já existentes
- **User Story 3 (P1)**: começa após a Phase 2. Usa `requireAdministrator` e `deleteByTenantId`. O teste independente é a exclusão do cadastro com o produto preservado
- **User Story 4 (P1)**: começa depois da US1 e da US3, porque o teste cobre consulta, gravação e exclusão entre duas organizações

### Within Each User Story

- O teste HTTP falha antes da implementação
- O serviço fica pronto antes do endpoint que o chama
- A história fecha no checkpoint antes da próxima

### Parallel Opportunities

- T001 e T002 podem rodar juntos
- T003 e T004 podem rodar juntos
- T015 e T016 podem rodar juntos
- Os testes das histórias editam o mesmo arquivo `CompanyRegistrationHttpTests.java`, então não são paralelos entre si

---

## Parallel Example: Foundational

```bash
Task: "Adicionar requireAdministrator em src/main/java/br/com/lhamalog/core/company/CompanyController.java"
Task: "Adicionar deleteByTenantId em src/main/java/br/com/lhamalog/core/company/CompanyContextRevisionRepository.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Completar Phase 1 e Phase 2
2. Completar Phase 3 (US1)
3. Parar e validar o cadastro e a alteração do administrador
4. Seguir para a recusa do membro só depois desse checkpoint

### Incremental Delivery

1. Setup e fundação
2. US1: administrador grava e altera
3. US2: membro lê e recebe 403
4. US3: administrador exclui o cadastro e o produto fica
5. US4: duas organizações permanecem isoladas
6. Phase 7: 501 inalterado e `.\mvnw.cmd verify`

### Parallel Team Strategy

1. Uma pessoa confirma T001 e T002
2. Em seguida, uma pessoa faz T003 e outra faz T004
3. As histórias seguem em ordem: US1, US2, US3 e US4, porque compartilham `CompanyController.java` e o arquivo de teste

---

## Notes

- [P] só em arquivos diferentes e sem dependência em aberto
- Não criar módulo, coleção, propriedade nem chamada à Clerk
- Não gravar segredo em teste, código ou documento
- Identificadores novos em inglês; texto de mensagem e desta lista em português
