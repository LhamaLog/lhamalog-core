---

description: "Task list for listar, editar e desativar usuários"
---

# Tasks: Listar, editar e desativar usuários

**Input**: Design documents from `/specs/005-listar-editar-usuarios/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/company-members.md, quickstart.md

**Tests**: A spec (FR-017) pede testes de lista, intervalo de 5 minutos, marca em toda requisição, desativação, reativação, último administrador, ausência de troca de cargo no Core, membro recusado e ausência de segredo. Os testes HTTP sobem o Mongo de `TestcontainersConfiguration` (`mongo:8.0.34`, nunca `latest`).

**Organization**: Uma fase por história. A tela `/equipe` e `organization.updateMember` não geram código neste repositório. O contrato é a fonte da verdade dessa parte.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Pode rodar em paralelo (arquivos diferentes, sem dependência de tarefa incompleta)
- **[Story]**: História à qual a tarefa pertence (US1, US2, US3, US4)
- Cada tarefa cita o caminho do arquivo

## Path Conventions

- Código em `src/main/java/br/com/lhamalog/core/`
- Testes em `src/test/java/br/com/lhamalog/core/`
- Documentos desta feature em `specs/005-listar-editar-usuarios/`

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirmar que a entrega não abre stack, SDK nem segredo

- [ ] T001 Confirmar que `pom.xml` permanece em Java 25 e Spring Boot 4.1.1, sem dependência nova e sem SDK da Clerk
- [ ] T002 [P] Confirmar que `src/main/resources/application.properties`, `.env.example`, `.env.prod.example`, `compose.yaml` e `compose.prod.yaml` não ganham `CLERK_SECRET_KEY`, valor que comece com `sk_`, nem intervalo configurável. O intervalo de 5 minutos fica no código, como em `specs/005-listar-editar-usuarios/research.md`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Os documentos e a regra de observação existem antes das histórias

**⚠️ CRITICAL**: Nenhuma história começa antes desta fase

- [ ] T003 Criar `src/main/java/br/com/lhamalog/core/member/CompanyMember.java` como documento Mongo. O id é `tenantId + "/" + userId`. Campos: `tenantId`, `userId`, `administrator` (booleano), `status` (`active` ou `deactivated`), `lastAccessAt` (`Instant`, nulo se ainda não houve requisição aceita) e `version` otimista. Não gravar nome, e-mail, cargo cru nem token. Um documento por empresa e `userId`, como em `specs/005-listar-editar-usuarios/data-model.md`
- [ ] T004 [P] Criar `src/main/java/br/com/lhamalog/core/member/CompanyAdminCount.java` com id igual ao `tenantId` e campo `activeAdminCount` inteiro. Um documento por empresa
- [ ] T005 Criar `src/main/java/br/com/lhamalog/core/member/CompanyMemberRepository.java` e `src/main/java/br/com/lhamalog/core/member/CompanyAdminCountRepository.java` como `MongoRepository`. A lista da empresa é `findByTenantIdOrderByUserIdAsc`. Esta tarefa espera T003 e T004
- [ ] T006 Criar `src/main/java/br/com/lhamalog/core/member/MemberObservation.java` com o intervalo de produção de 5 minutos. A regra recebe o registro atual, o cargo administrador do token, o instante de agora e o intervalo: grava quando não há registro, quando `lastAccessAt` é nulo, quando a idade é maior ou igual a 5 minutos, ou quando `administrator` difere do token. Com idade menor que 5 minutos e o mesmo cargo, não grava. Requisição recusada não chama esta regra

**Checkpoint**: Documentos e a folga de 5 minutos existem. Ainda não há caminho HTTP

---

## Phase 3: User Story 1 - Ver quem está na empresa (Priority: P1) 🎯 MVP

**Goal**: O administrador lê status e último acesso da própria empresa. Membro não lê. Requisição aceita observa, com a folga de 5 minutos

**Independent Test**: `GET /api/company/members` do administrador devolve só a empresa da sessão, ordenada por `userId`, sem nome, e-mail ou cargo. Membro recebe 403. Duas requisições aceitas dentro do intervalo deixam `lastAccessAt` igual

### Tests for User Story 1

- [ ] T007 [US1] Criar `src/test/java/br/com/lhamalog/core/member/CompanyMemberHttpTests.java` com `@Import(TestcontainersConfiguration.class)`, `@SpringBootTest` e `@AutoConfigureMockMvc`, as propriedades `management.otlp.metrics.export.enabled=false` e `management.tracing.export.otlp.enabled=false` de `src/test/java/br/com/lhamalog/core/company/CompanyRegistrationHttpTests.java`, sem `TEST_MONGODB_URI`, sem tag `latest` e sem `CLERK_SECRET_KEY`. JWT sintético com `sub`, `o.id` e `o.rol`. Cobrir: administrador recebe 200 em `GET /api/company/members` com `members` ordenados por `userId` e sem campos `name`, `email` ou `role`; membro recebe 403 `code` `not_admin` e "Somente o administrador da empresa pode gerenciar os usuários.", e o JSON não traz outro `code`; sem JWT, 401, sem `code`; sem `o.id` e sem `org_id`, 403 "Selecione uma empresa para continuar." e a propriedade `code` ausente; `userId` observado na empresa B não aparece na empresa A; duas requisições aceitas do mesmo usuário, com o mesmo cargo, dentro do intervalo injetado, mantêm `lastAccessAt`; depois do intervalo, `lastAccessAt` muda. O teste controla o relógio. Não chamar a Clerk

### Implementation for User Story 1

- [ ] T008 [US1] Implementar `src/main/java/br/com/lhamalog/core/member/CompanyMemberService.java` com lista por `tenantId` e observação da requisição aceita usando `MemberObservation`, um `Clock` injetado (produção: `Clock.systemUTC()`) e o intervalo injetado (produção: 5 minutos). A lista não cria documento para quem o Core ainda não viu. Não devolver nome, e-mail nem cargo. `OptimisticLockingFailureException` e `DuplicateKeyException` nesta observação não falham a requisição: vale o instante de uma das chamadas
- [ ] T009 [US1] Implementar `GET /api/company/members` em `src/main/java/br/com/lhamalog/core/member/CompanyMemberController.java`. Só administrador, pela `CompanySession` já existente. Membro responde 403 com `code` `not_admin`. Em `src/main/java/br/com/lhamalog/core/shared/ApiExceptionHandler.java`, `ApiError` ganha `code` opcional e o Jackson omite a propriedade quando ela é nula, para o JSON dos erros já publicados continuar só com `message` e `fieldErrors`. Não colocar `code` em `ResponseStatusException` nem no 403 do cadastro da empresa. Corpo com `userId` ou empresa é ignorado. Resposta no formato de `specs/005-listar-editar-usuarios/contracts/company-members.md`
- [ ] T010 [US1] Criar `src/main/java/br/com/lhamalog/core/member/MemberAccessInterceptor.java` e `src/main/java/br/com/lhamalog/core/member/MemberAccessConfig.java` para `/api/**`. Com JWT e empresa, observa a requisição aceita antes do controlador. Sem empresa, não observa e deixa a recusa já existente. Sem JWT, não age: o 401 continua no filtro atual. Usar `CompanySessionResolver` para não reinterpretar `o.rol`, `org_role` nem `CLERK_ADMIN_ROLES`

**Checkpoint**: A lista e o último acesso funcionam. Desativar ainda não existe

---

## Phase 4: User Story 2 - Desativar, reativar e cortar o acesso (Priority: P1)

**Goal**: A marca recusa a próxima requisição nesta empresa, mesmo com token válido, e a reativação devolve o acesso. A pessoa não sai da organização

**Independent Test**: Desativar quem não é o último administrador ativo conhecido responde 200 `deactivated`. A requisição seguinte dessa pessoa em company, produtos e perfil é 403. `DELETE` devolve `active` e a próxima requisição passa. A outra empresa não muda

### Tests for User Story 2

- [ ] T011 [US2] Estender `src/test/java/br/com/lhamalog/core/member/CompanyMemberHttpTests.java`. `POST /api/company/members/{userId}/deactivation` sem corpo, com alvo já observado como `member`, responde 200 com `status` `deactivated` e o `lastAccessAt` anterior. A requisição seguinte desse `userId` na mesma empresa em `GET /api/company`, `GET /api/products` e `GET /api/user/profile` responde 403 `code` `user_deactivated` e "Seu acesso a esta empresa foi desativado." Outro usuário da mesma empresa continua 200. Repetir o `POST` responde 200 `deactivated` e não reativa. `DELETE /api/company/members/{userId}/deactivation` responde 200 `active` e a requisição seguinte volta a ser aceita. Repetir o `DELETE` responde 200 `active`. Desativar na empresa A não faz a mesma pessoa receber 403 na empresa B. `userId` com mais de 200 caracteres responde 400 "Informe o usuário." e não grava. `POST` e `DELETE` de membro respondem 403 `code` `not_admin`. O 400 "Informe o usuário." não traz `code`. A requisição recusada não muda `lastAccessAt`

### Implementation for User Story 2

- [ ] T012 [US2] Em `src/main/java/br/com/lhamalog/core/member/CompanyMemberService.java`, gravar `deactivated` sem alterar `lastAccessAt` nem `administrator`. Sem registro, criar `administrator` falso, `status` `deactivated` e `lastAccessAt` nulo. Repetir não muda quem já está `deactivated`. `DELETE` volta `active` e repetir sobre `active` não desativa. `userId` vazio ou acima de 200 caracteres lança 400 "Informe o usuário."
- [ ] T013 [US2] Em `src/main/java/br/com/lhamalog/core/member/CompanyMemberController.java`, acrescentar `POST` e `DELETE` de `/api/company/members/{userId}/deactivation`, só administrador. Membro recebe `code` `not_admin`. Ignorar corpo. Resposta no formato do contrato
- [ ] T014 [US2] Em `src/main/java/br/com/lhamalog/core/member/MemberAccessInterceptor.java`, se o documento da empresa e do `sub` está `deactivated`, responder 403 com `code` `user_deactivated` e "Seu acesso a esta empresa foi desativado.", pelo `ApiError` de `src/main/java/br/com/lhamalog/core/shared/ApiExceptionHandler.java`, sem observar. Não usar `ResponseStatusException` para este caso, porque ela não carrega `code`. A conferência ocorre em toda requisição de `/api/**` com empresa, também dentro dos 5 minutos. Não remover membro e não chamar a Clerk

**Checkpoint**: A marca corta e a reativação devolve. A trava do último administrador ainda não está fechada

---

## Phase 5: User Story 3 - Preservar o último administrador (Priority: P1)

**Goal**: O Core recusa desativar o último administrador ativo que ele conhece, inclusive sob duas chamadas simultâneas

**Independent Test**: Com um único administrador ativo conhecido, o `POST` contra ele responde 403 e ele segue `active`. Com dois, desativar um deixa o outro `active`. Duas desativações simultâneas desses dois não deixam a contagem em zero

### Tests for User Story 3

- [ ] T015 [US3] Estender `src/test/java/br/com/lhamalog/core/member/CompanyMemberHttpTests.java`. Depois que dois usuários tiveram requisição aceita com `o.rol` `admin` na mesma empresa, desativar um responde 200 e o outro permanece `active` no `GET`. O `POST` contra o único administrador ativo conhecido responde 403 `code` `last_admin` e "Não é possível desativar o último administrador." e o `GET` o mantém `active`. Esse administrador pode desativar a si mesmo quando o outro continua ativo. Dois `POST` simultâneos, um para cada administrador ativo, terminam com exatamente um `active` e um `deactivated`. `org:admin` conta como administrador; `member` e `org:member` não contam. Não usar chave secreta

### Implementation for User Story 3

- [ ] T016 [US3] Em `src/main/java/br/com/lhamalog/core/member/CompanyMemberService.java`, manter `CompanyAdminCount` como em `specs/005-listar-editar-usuarios/data-model.md`. Se o documento não existe, criá-lo com a contagem dos `CompanyMember` já administrador e `active`, sem sobrescrever valor existente. A contagem só muda depois que a escrita condicional do membro realmente altera o par administrador ativo. Inserção que perde a chave única não conta de novo. Desativar administrador ativo só grava a marca se um `findAndModify` casar `activeAdminCount > 1` e descer 1. Sem casamento, responder 403 com `code` `last_admin` e "Não é possível desativar o último administrador." e não mudar o membro. Se a contagem desceu e o membro não mudou, subir 1 de volta. Desativar quem não é administrador ativo conhecido não mexe na contagem. Não abrir transação entre documentos. A contagem do Web antes de rebaixar ou desativar permanece só em `specs/005-listar-editar-usuarios/contracts/company-members.md` e não vira código neste repositório

**Checkpoint**: A empresa não fica sem administrador ativo conhecido pelo Core

---

## Phase 6: User Story 4 - Editar o cargo (Priority: P2)

**Goal**: O Core não troca cargo. A observação acompanha o token na próxima requisição aceita, mesmo dentro dos 5 minutos

**Independent Test**: `POST /api/company/members/{userId}/role` responde 404. Uma requisição aceita com cargo diferente, dentro do intervalo, grava `lastAccessAt` novo e passa a contar o cargo novo na trava de administrador

### Tests for User Story 4

- [ ] T017 [US4] Estender `src/test/java/br/com/lhamalog/core/member/CompanyMemberHttpTests.java`. Com sessão autenticada, `POST /api/company/members/{userId}/role` responde 404. Uma pessoa observada como `member` faz uma requisição aceita com `o.rol` `admin` dentro do intervalo: `lastAccessAt` muda e ela passa a contar na trava do último administrador. O inverso, token `member` dentro do intervalo, deixa de contar. `GET /api/company/members` continua sem campo de cargo. O teste não adiciona SDK nem `CLERK_SECRET_KEY`

### Implementation for User Story 4

- [ ] T018 [US4] Não criar caminho de cargo em `src/main/java/br/com/lhamalog/core/member/CompanyMemberController.java`. Se T017 falhar na atualização dentro do intervalo, corrigir só `src/main/java/br/com/lhamalog/core/member/MemberObservation.java` e o uso dela em `src/main/java/br/com/lhamalog/core/member/CompanyMemberService.java`: cargo diferente grava mesmo com idade menor que 5 minutos, e a contagem de `CompanyAdminCount` acompanha essa passagem. A troca visível continua no Web, por `organization.updateMember`, descrita em `specs/005-listar-editar-usuarios/contracts/company-members.md`

**Checkpoint**: O Core não oferece edição de cargo e não fica cego a um token novo

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Segredo, fronteiras e o guia batem com o código

- [ ] T019 [P] Confirmar que `pom.xml`, `src/main/resources/application.properties`, `.env.example`, `.env.prod.example`, `compose.yaml`, `compose.prod.yaml` e `src/test/java/br/com/lhamalog/core/member/CompanyMemberHttpTests.java` não contêm `CLERK_SECRET_KEY` nem valor que comece com `sk_`
- [ ] T020 [P] Confirmar que `src/main/java/br/com/lhamalog/core/profile/UserProfile.java` não ganha e-mail, cargo nem status, e que `src/main/java/br/com/lhamalog/core/company/CompanyController.java` não ganha caminho de convite nem de cargo. A marca entra nesses caminhos só pelo interceptor
- [ ] T021 Conferir `specs/005-listar-editar-usuarios/quickstart.md` contra `src/test/java/br/com/lhamalog/core/member/CompanyMemberHttpTests.java`: os dez itens do guia têm uma afirmação correspondente, incluindo 404 do cargo, isolamento entre empresas e o relógio injetado

---

## Dependencies & Execution Order

### Phase Dependencies

- Setup (Phase 1) antes de Foundational
- Foundational (Phase 2) antes de US1, US2, US3 e US4
- US1 antes de US2: a lista e a observação já existem quando a marca chega
- US2 antes de US3: desativar existe, e a fase 5 fecha a contagem
- US3 antes de US4: a trava já conta administrador quando o token muda
- Polish por último

### User Story Dependencies

- **US1**: só depende de Foundational
- **US2**: depende de US1 (interceptor e lista)
- **US3**: depende de US2 (o `POST` já grava a marca)
- **US4**: depende de US3 (a contagem reage ao cargo novo)

### Parallel Opportunities

- T002 em paralelo com T001
- T004 em paralelo com T003. T005 só depois dos dois tipos. T006 em paralelo com T005
- T019 e T020 em paralelo no polish
- Histórias não são paralelas entre si: cada uma edita `CompanyMemberService` ou o teste HTTP

---

## Parallel Example: Foundational

```text
T003 CompanyMember.java
T004 CompanyAdminCount.java em paralelo
T005 repositories depois dos dois tipos
T006 MemberObservation.java em paralelo com T005
```

---

## Implementation Strategy

### MVP First (User Story 1)

1. Phase 1 e Phase 2
2. Phase 3
3. Parar e provar a lista, o isolamento e a folga de 5 minutos

### Incremental Delivery

1. US1: lista e último acesso
2. US2: marca, corte na próxima requisição e reativação
3. US3: último administrador, com a condição atômica
4. US4: cargo continua no Web; o Core só acompanha o token
5. Polish: segredo e fronteiras

### Parallel Team Strategy

Não dividir as histórias entre pessoas ao mesmo tempo: o serviço e o teste HTTP são os mesmos arquivos. A parte do Web (`/equipe`, contagem antes de rebaixar, `organization.updateMember`) está no contrato e não entra nestas tarefas.

## Notes

- Não implementar nesta lista além do que as tarefas citam. A execução que gerou este arquivo para antes do código.
- Mensagens citadas aqui são literais do contrato.
- `CLERK_ADMIN_ROLES` não muda. O padrão continua `admin`.
