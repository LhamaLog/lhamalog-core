---

description: "Task list for convidar usuário para a empresa"
---

# Tasks: Convidar usuário para a empresa

**Input**: Design documents from `/specs/004-convidar-usuario-empresa/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/organization-invite.md, quickstart.md

**Tests**: A spec pede testes da sessão recém-aceita e da ausência de caminho de convite. Eles travam regra que já existe. Devem passar sem código de produção novo. Não são escritos para falhar à espera de um controlador de convite. Se uma afirmação de papel falhar, a correção fica em `CompanySession`, sem endpoint novo.

**Organization**: Uma fase por história de usuário. O envio, a lista, a revogação, o reenvio e o aceite nas telas de entrada não geram código neste repositório.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Pode rodar em paralelo (arquivos diferentes, sem dependência de tarefa incompleta)
- **[Story]**: História à qual a tarefa pertence (US1, US2, US3)
- Cada tarefa cita o caminho do arquivo

## Path Conventions

- Código em `src/main/java/br/com/lhamalog/core/`
- Testes em `src/test/java/br/com/lhamalog/core/`
- Documentos desta feature em `specs/004-convidar-usuario-empresa/`

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirmar que a entrega não abre stack, SDK, e-mail nem segredo

- [ ] T001 Confirmar que `pom.xml` permanece em Java 25 e Spring Boot 4.1.1, sem dependência nova, sem SDK da Clerk e sem biblioteca de e-mail
- [ ] T002 [P] Confirmar que `src/main/resources/application.properties` mantém `clerk.administrator-roles=${CLERK_ADMIN_ROLES:admin}` e não ganha outra chave. Confirmar que `.env.example`, `.env.prod.example`, `compose.yaml` e `compose.prod.yaml` não ganham `CLERK_SECRET_KEY` nem valor que comece com `sk_`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: A sessão atual continua sendo o único vínculo. Nenhuma história cria outro

**⚠️ CRITICAL**: Nenhuma história começa antes desta fase

- [ ] T003 Confirmar que `src/main/java/br/com/lhamalog/core/shared/CompanySession.java` segue lendo a empresa em `o.id` e o cargo em `o.rol`, com fallback `org_id` e `org_role` somente quando o mapa `o` está ausente, e que remove um prefixo `org:` do cargo. Confirmar que `src/main/java/br/com/lhamalog/core/shared/CompanySessionResolver.java` segue com o padrão `admin`. Não criar classe, documento nem controlador de convite em `src/main/java`

**Checkpoint**: O mapeamento da sessão permanece o atual e não existe recurso de convite

---

## Phase 3: User Story 1 - Enviar convite com cargo e prazo (Priority: P1) 🎯 MVP

**Goal**: O Web cria o convite na Clerk. O Core não envia e-mail e não oferece caminho de convite

**Independent Test**: Com sessão autenticada, os caminhos de convite respondem 404. Sem sessão, a resposta é 401. Não há cliente de e-mail nem chave secreta

### Tests for User Story 1

- [ ] T004 [US1] Criar `src/test/java/br/com/lhamalog/core/company/OrganizationInviteBoundaryTests.java` com `@Import(TestcontainersConfiguration.class)`, `@SpringBootTest` e `@AutoConfigureMockMvc`, as mesmas propriedades `management.otlp.metrics.export.enabled=false` e `management.tracing.export.otlp.enabled=false` de `CompanyRegistrationHttpTests`, sem `TEST_MONGODB_URI`, sem tag `latest` e sem `CLERK_SECRET_KEY`. O teste `invitePathsStayUnmapped` usa JWT sintético com `sub`, `o.id` e `o.rol` `admin` e espera 404 em `POST /api/invitations`, `POST /api/company/invitations`, `POST /api/organization-invitations` e `POST /api/invitations/accept`. O teste `invitePathsRejectAnonymous` espera 401 nesses mesmos caminhos sem JWT. O teste deve passar com o roteamento atual. Se o status autenticado não for 404, não criar rota para ajustá-lo

### Implementation for User Story 1

- [ ] T005 [US1] Não criar controlador, serviço nem cliente HTTP em `src/main/java`. Não alterar `pom.xml`. O e-mail vazio ("Informe o e-mail.") e o e-mail irreconhecível ("Informe um e-mail válido.") ficam no Web, como em `specs/004-convidar-usuario-empresa/contracts/organization-invite.md`. O prazo de 7 dias fica no dashboard da Clerk, documentado nesse contrato, e não vira campo em `src/main/java`

**Checkpoint**: O Core não envia convite. A história de envio se prova pela ausência de caminho

---

## Phase 4: User Story 2 - Aceitar e ficar na empresa com o cargo (Priority: P1)

**Goal**: Depois do aceite nas telas já existentes, a sessão com `member` é membro e a sessão com `admin` é administrador

**Independent Test**: `member` e `org:member` não são administrador e não alteram o cadastro. `admin` e `org:admin` são administrador e alteram o cadastro da própria empresa. Não há rota nova de aceite

### Tests for User Story 2

- [ ] T006 [P] [US2] Estender `src/test/java/br/com/lhamalog/core/config/CompanySessionTests.java`. Com `CompanySessionResolver("admin")`, `o.rol` `admin` e `org:admin` produzem `administrator()` verdadeiro; `o.rol` `member` e `org:member` produzem `administrator()` falso. Sem o mapa `o`, `org_role` `admin` e `org:admin` são administrador e `org_role` `member` e `org:member` não são. A empresa da sessão é o `o.id` ou, na forma antiga, o `org_id`. Não tratar o texto "Administrador" como administrador
- [ ] T007 [US2] Acrescentar `acceptedMemberIsNotAdministrator` e `acceptedAdministratorCanUpdateCompany` em `src/test/java/br/com/lhamalog/core/company/OrganizationInviteBoundaryTests.java`. A forma antiga do JWT traz `org_id` e `org_role` e não traz o mapa `o`. A forma atual traz `o.id` e `o.rol`. Primeiro um administrador com `o.rol` `admin` grava `{"data":{"legalName":"Empresa Convidada","document":""},"context":{"description":"Aceite"},"address":{},"organizationId":"org_outra","version":null}` e o `GET` devolve `id` da organização da sessão e o nome gravado. Na mesma organização, para `o.rol` `member` e `org:member`, e para `org_role` `member` e `org:member`: `GET /api/company` devolve esse nome, `GET /api/user/profile` responde 200 e `PUT /api/company` com corpo `{"role":"admin"}` responde 403 com "Somente o administrador da empresa pode alterar este cadastro." O `GET` seguinte mantém o nome. Para `o.rol` `org:admin` e para `org_role` `admin` e `org:admin`, cada um na própria organização, o mesmo `PUT` com `version` nula responde 200. JWT sem `o.id` e sem `org_id` em `GET /api/company` responde 403 com "Selecione uma empresa para continuar."

### Implementation for User Story 2

- [ ] T008 [US2] Não alterar `src/main/java/br/com/lhamalog/core/company/CompanyController.java` nem `src/main/java/br/com/lhamalog/core/profile/UserProfileController.java` para criar aceite. O vínculo permanece a sessão descrita em `specs/004-convidar-usuario-empresa/data-model.md`. Não gravar cargo, e-mail nem convite em `UserProfile`. Se T006 ou T007 falharem no papel `org:member` ou `org:admin`, corrigir só a leitura já existente em `src/main/java/br/com/lhamalog/core/shared/CompanySession.java`

**Checkpoint**: Membro e administrador recém-aceitos caem nas regras atuais. Não há rota de aceite

---

## Phase 5: User Story 3 - Convite expirado, revogado ou reenviado (Priority: P1)

**Goal**: Lista, revogação e reenvio ficam no Web. O Core não guarda prazo nem pendência

**Independent Test**: Um caminho de lista ou de revogação inexistente responde 404 com sessão de administrador. Nenhum campo de prazo ou de convite aparece no código de produção

### Tests for User Story 3

- [ ] T009 [US3] Acrescentar `pendingListRevokeAndResendStayOutsideCore` em `src/test/java/br/com/lhamalog/core/company/OrganizationInviteBoundaryTests.java`. Com `o.rol` `admin`, `GET /api/invitations` e `POST /api/invitations/revoke` respondem 404. Com `o.rol` `member`, os mesmos caminhos respondem 404, não 200. O teste não define data de expiração nem segredo

### Implementation for User Story 3

- [ ] T010 [US3] Não criar coleção nem campo de convite, prazo, revogação ou reenvio em `src/main/java`. Não alterar `src/main/resources/application.properties` para guardar os 7 dias. A renovação do prazo no reenvio permanece na Clerk, como em `specs/004-convidar-usuario-empresa/contracts/organization-invite.md`

**Checkpoint**: Expiração, revogação e reenvio não têm código neste repositório

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Fechar o isolamento já existente, a ausência de segredo e o portão de teste

- [ ] T011 [P] Confirmar que `operationsStayInsideTheSessionOrganization` em `src/test/java/br/com/lhamalog/core/company/CompanyRegistrationHttpTests.java` continua sendo a prova de que outra empresa citada no pedido não é a empresa da sessão. Não duplicar esse teste
- [ ] T012 [P] Confirmar que `src/main/java/br/com/lhamalog/core/product/ProductService.java` e `src/main/java/br/com/lhamalog/core/profile/UserProfileService.java` não passam a ler convite. LLT-168, LLT-169, LLT-170, LLT-171, LLT-172, LLT-173 e LLT-174 permanecem fora
- [ ] T013 Executar o `verify` descrito em `specs/004-convidar-usuario-empresa/quickstart.md` (`./mvnw verify` ou `.\mvnw.cmd verify`) e confirmar `CompanySessionTests` e `OrganizationInviteBoundaryTests` verdes, com Mongo `mongo:8.0.34` e sem segredo no comando

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: começa imediata
- **Foundational (Phase 2)**: depende do Setup e bloqueia as histórias
- **User Stories (Phase 3+)**: dependem da fase Foundational
- **Polish (Phase 6)**: depende das três histórias

### User Story Dependencies

- **User Story 1 (P1)**: começa depois da fase Foundational. Não depende das outras histórias
- **User Story 2 (P1)**: começa depois da fase Foundational. O teste HTTP usa a classe criada em T004, então T007 espera T004. T006, em outro arquivo, não espera T004
- **User Story 3 (P1)**: T009 espera a classe criada em T004 e não depende do comportamento de papel de T007

### Within Each User Story

- O teste da história vem antes da tarefa que só confirma a ausência de código novo
- Não há modelo, serviço nem endpoint novos
- A história termina antes da próxima só por ordem de leitura. US2 e US3 podem avançar em arquivos diferentes depois de T004

### Parallel Opportunities

- T001 e T002 podem rodar juntos
- T006 pode rodar junto de T004, porque os arquivos são diferentes
- T011 e T012 podem rodar juntos
- T007 e T009 editam o mesmo arquivo de teste e não rodam juntos

---

## Parallel Example: User Story 2

```bash
# T006 não espera o teste HTTP de T004:
Task: "Estender CompanySessionTests com admin, org:admin, member e org:member"

# T007 espera a classe de T004 e não roda junto de T009:
Task: "Acrescentar membro e administrador em OrganizationInviteBoundaryTests"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Completar Phase 1: Setup
2. Completar Phase 2: Foundational
3. Completar Phase 3: User Story 1
4. **STOP and VALIDATE**: os caminhos de convite respondem 404 e 401, sem segredo e sem e-mail no Core

### Incremental Delivery

1. Setup + Foundational prontos
2. User Story 1 prova que o Core não envia convite
3. User Story 2 prova o membro e o administrador recém-aceitos
4. User Story 3 prova que lista, revogação e reenvio não entram no Core
5. O `verify` fecha as três

### Parallel Team Strategy

1. Setup e Foundational juntos
2. Depois de T004:
   - Uma pessoa segue o papel em `CompanySessionTests`
   - Outra segue os caminhos HTTP, um teste por vez no mesmo arquivo
3. Ninguém cria endpoint de convite

---

## Notes

- [P] marca arquivos diferentes, sem dependência aberta
- [US1], [US2] e [US3] ligam a tarefa à história da spec
- O Web e a Clerk implementam envio, e-mail, 7 dias, lista, revogação, reenvio e aceite. As tarefas deste arquivo não criam esse código
- Os testes devem passar no código atual. Criar um controlador para "fazer o teste passar" viola a spec
- Commit ao fim de cada história, não no meio de um teste pela metade
- Evitar endpoint, coleção, SDK e segredo
