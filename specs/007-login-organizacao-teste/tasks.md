# Tasks: Login e organização de teste para desenvolvimento (LLT-163)

**Input**: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/dev-tenant-config.md](contracts/dev-tenant-config.md), [quickstart.md](quickstart.md)

**Testes**: obrigatórios (constituição III). Cada fase de código termina com `.\mvnw.cmd clean verify` verde, com o Docker Desktop aberto.

**Ordem**: a US2 (roteiro com a Clerk real) é P1, mas roda por último, porque os itens V3, V4 e V8 dependem do código da US3. As outras histórias são independentes entre si depois da fase 2.

## Phase 1: Setup

- [X] T001 Atualizar o comentário de `lhamalog.dev-tenant.clerk-org-id` em `src/main/resources/application-local.properties` e o de `DEV_TENANT_CLERK_ORG_ID` em `.env.example`: lista separada por vírgula de `org_...`, só no perfil local, com exemplos fictícios (`org_exemploA1,org_exemploB2`), nunca um `org_id` real

## Phase 2: Foundational

- [X] T002 [P] Criar `src/main/java/br/com/lhamalog/core/config/DevTenantOrganizations.java`, função pura sem Spring (research R2): separa por vírgula, aplica `strip`, descarta vazios e repetidos mantendo a ordem, aceita só `^org_[A-Za-z0-9]+$` e devolve os válidos e os descartados
- [X] T003 [P] Teste de unidade `src/test/java/br/com/lhamalog/core/config/DevTenantOrganizationsTests.java`: nulo e vazio → nenhum; um id; dois ids; espaços em volta; repetido; item vazio entre vírgulas; inválidos (`acme`, `org_`, `org_a-b`, `ORG_x`) descartados e devolvidos como descartados

**Checkpoint**: `.\mvnw.cmd clean verify` verde.

## Phase 3: User Story 1 — Entrar com login e organização de teste (P1)

**Goal**: o desenvolvedor confirma pelo log que o Tenant da organização dele existe e entra no Web.

**Independent Test**: com uma organização na variável, subir o Core e ver `dev_tenant.seed.created`; subir de novo e ver `dev_tenant.seed.exists`.

- [X] T004 [US1] Em `src/main/java/br/com/lhamalog/core/tenant/adapter/out/mongo/LocalDevTenantSeeder.java`, trocar os logs pelo padrão da constituição VI (contrato, seção Logs): INFO `event=dev_tenant.seed.created | Development tenant created | clerkOrgId={} tenantId={}`; INFO `event=dev_tenant.seed.exists | Development tenant already exists | clerkOrgId={} status=ACTIVE`; WARN `event=dev_tenant.seed.inactive | Development tenant exists but is not active; access stays denied | clerkOrgId={} status={}` quando o status não for `ACTIVE`. O retorno `boolean` e a regra "nunca altera um Tenant existente" não mudam
- [X] T005 [US1] Em `src/test/java/br/com/lhamalog/core/tenant/adapter/out/mongo/LocalDevTenantSeederTests.java`, com `OutputCaptureExtension`: a primeira subida registra `event=dev_tenant.seed.created` com o `tenantId` gravado; a segunda registra `event=dev_tenant.seed.exists`; o Tenant `SUSPENDED` registra `event=dev_tenant.seed.inactive` em WARN e continua `SUSPENDED`
- [X] T006 [US1] Em `README.md`, seção "Login e organização de teste (enquanto não há fluxo de compra)": passo 4 com a lista de organizações e as linhas de log `dev_tenant.seed.*`; Tenant não ativo (WARN) e como corrigir; um bloco "Se não funcionar" com os sintomas da spec (Web e Core em instâncias diferentes da Clerk → 401; relógio fora da tolerância → 401; Tenant não ativo → 403; organização fora da lista → 403); link para `specs/007-login-organizacao-teste/quickstart.md`. Manter o restante do guia do commit `d7ef2f3`

**Checkpoint**: `.\mvnw.cmd clean verify` verde.

## Phase 4: User Story 3 — Mais de uma organização (P2)

**Goal**: várias organizações provisionadas de uma vez, sem Mongo manual.

**Independent Test**: com `org_a,org_b` na variável, subir e ver os dois Tenants e duas linhas de log.

- [X] T007 [US3] Em `src/main/java/br/com/lhamalog/core/config/LocalDevConfig.java`, o `localDevTenantRunner` passa a usar `DevTenantOrganizations`: chama `seeder.seed` para cada id válido, na ordem; registra WARN `event=dev_tenant.seed.invalid_org_id | Ignored value that is not a Clerk organization id | value={}` para cada descartado; lista vazia não faz nada; nenhum caso impede a subida. Atualizar o Javadoc (uma ou várias organizações)
- [X] T008 [US3] Teste de integração `src/test/java/br/com/lhamalog/core/config/LocalDevConfigTests.java` (`@CoreIntegrationTest`, `OutputCaptureExtension`), executando o runner com `" org_a1 , org_b2,org_a1,,invalido"` (ids únicos por teste): dois Tenants `ACTIVE`, um por organização; nenhum Tenant para `invalido`; um WARN `dev_tenant.seed.invalid_org_id`; uma segunda execução não cria nada novo

**Checkpoint**: `.\mvnw.cmd clean verify` verde.

## Phase 5: User Story 4 — Nunca fora do desenvolvimento (P3)

**Goal**: prova automatizada de que o perfil `prod` não provisiona.

**Independent Test**: o teste de perfil passa.

- [X] T009 [P] [US4] Teste `src/test/java/br/com/lhamalog/core/config/LocalDevConfigProfileTests.java` com `ApplicationContextRunner` (research R4), registrando `LocalDevConfig` e um `MongoTemplate` de mentira, com `lhamalog.dev-tenant.clerk-org-id=org_exemplo1`: com o perfil `local`, os beans `LocalDevTenantSeeder` e `localDevTenantRunner` existem; com o perfil `prod`, nenhum dos dois existe

**Checkpoint**: `.\mvnw.cmd clean verify` verde.

## Phase 6: User Story 2 — Validar com a Clerk real antes do merge (P1)

**Goal**: contrato de recusas pronto para o Web (FR-010) e evidência manual, no PR #6, de que o login e a integração funcionam com a Clerk real.

**Independent Test**: os testes de T010 a T012 passam; tabela V1 a V10 comentada no PR #6.

- [X] T010 [US2] Em `src/main/java/br/com/lhamalog/core/company/CompanyController.java` (`requireAdministrator`) e `src/main/java/br/com/lhamalog/core/product/ProductService.java` (`requireAdministrator`), trocar `ResponseStatusException(HttpStatus.FORBIDDEN, ...)` por `ApiStatusException(HttpStatus.FORBIDDEN, <texto atual>, "not_admin")` (research R8), sem mudar o texto
- [X] T011 [US2] Nos testes `src/test/java/br/com/lhamalog/core/company/CompanyProductIntegrationTests.java` (membro alterando o cadastro) e `src/test/java/br/com/lhamalog/core/product/ProductEditArchiveIntegrationTests.java` (membro arquivando), conferir `$.code` igual a `not_admin`, `$.message` com o texto atual e nenhuma sessão revogada
- [X] T012 [P] [US2] Teste `src/test/java/br/com/lhamalog/core/shared/BusinessForbiddenHasCodeTests.java`: varre `src/main/java` e falha se encontrar `ResponseStatusException(HttpStatus.FORBIDDEN`

**Checkpoint**: `.\mvnw.cmd clean verify` verde antes do roteiro.

- [X] T013 [US2] (manual, Felipe) Preparar o ambiente pela Parte A de `specs/007-login-organizacao-teste/quickstart.md`: login, organizações A, B e C, conta membro `+clerk_test`, `CLERK_SECRET_KEY` e `DEV_TENANT_CLERK_ORG_ID` só no `.env` local
- [X] T014 [US2] (manual, Felipe) Executar os itens V1 a V10 da Parte B do quickstart, inclusive o webhook pelo túnel (V9, quickstart da spec 003), com o Web da `Development` mais o PR #7
- [ ] T015 [US2] (manual, Felipe) Pedir a uma pessoa do time que ainda não preparou o ambiente para seguir só o guia do README, cronometrando do cadastro até a Empresa (SC-001, até 15 minutos), e incluir o tempo na tabela. Comentar no PR #6 a tabela `Item | Data | Resultado | Observação`, sem token, chave, `org_id` real ou e-mail. Item que falhar vira task nova neste arquivo antes do merge

## Phase 7: Polish

- [X] T016 Rodar `.\mvnw.cmd clean verify` completo e registrar o total de testes na descrição do PR #6 (seção Testes)
- [X] T017 Conferir os arquivos alterados contra a constituição V: nenhum `org_` real, `sk_test`, `whsec_`, senha ou e-mail; exemplos só fictícios
- [X] T018 Em `specs/002-validacao-token-clerk/research.md`, citar na R18 que o provisionamento local passou a aceitar várias organizações e que todo 403 de negócio tem `code` (spec 007)

## Dependencies

```text
T001 ─┐
T002 ─┼─> T007 ─> T008 ─┐
T003 ─┘                 │
T004 ─> T005 ─> T006 ───┤
T009 ───────────────────┼─> T013 ─> T014 ─> T015 ─> T016 ─> T017 ─> T018
T010 ─> T011, T012 ─────┘
```

- US1 (T004 a T006) depende só da fase 1.
- US3 (T007, T008) depende de T002.
- US4 (T009) é independente.
- US2: T010 a T012 não dependem das outras histórias; o roteiro (T013 a T015) depende de US1, US3, US4 e de T010 a T012.

## Parallel Execution

- Fase 2: T002 e T003 juntos (arquivos diferentes, o teste guia a função).
- Depois da fase 2: US1 (T004 a T006), US3 (T007, T008), US4 (T009) e a parte de código da US2 (T010 a T012) em paralelo.

## Implementation Strategy

- **MVP**: fases 1 a 3 (US1). Com elas, o guia e os logs já bastam para qualquer pessoa entrar com uma organização.
- **Incremento**: US3, US4 e T010 a T012. Depois, o roteiro (T013 a T015) fecha o PR #6.
- Cada fase termina com o verify verde e uma pausa para a revisão do Felipe (constituição II).
