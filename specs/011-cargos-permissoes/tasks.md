# Tasks: Cargos e permissões

**Input**: `specs/011-cargos-permissoes/` (spec, plan, research, data-model, contracts/api.md)

**Formato**: `- [ ] Tnnn [P?] [USn] descrição com caminho`. `[P]` = pode rodar em paralelo, porque mexe em arquivos diferentes e não depende de outra task.

**Regra**: o teste de cada história é escrito antes e precisa falhar antes do código (constituição III). Ao fim de cada fase, rode `.\mvnw.cmd verify` e pare para revisão do Pedro.

## Fase 0: preparação

- [X] T001 Confirmar que a branch `LLT-120-cargos-permissoes` saiu da `Development` atualizada e rodar `.\mvnw.cmd verify` como linha de base. Registrar no `quickstart.md` quantos testes rodaram e quantos foram ignorados.
- [X] T002 Rodar `speckit-clarify` e registrar as respostas de C1 a C7 na seção Clarifications da `spec.md`. Se alguma resposta divergir da recomendação, ajustar `data-model.md`, `contracts/api.md` e estas tasks antes de seguir.

## Fase 1: papel de acesso e matriz (US1), MVP

- [X] T003 [P] [US1] Criar `src/test/java/br/com/lhamalog/core/access/application/AccessPolicyTest.java` (unitário, sem Spring). Para cada linha da tabela de `data-model.md`, conferir os papéis que têm a permissão. `require` de quem não tem a permissão lança `ApiStatusException` 403 `not_admin`.
- [X] T004 [P] [US1] Criar `src/test/java/br/com/lhamalog/core/access/AccessHttpTests.java` (`@CoreIntegrationTest` + `TestTenants`, tokens Clerk reais, mesmo arranque do `CompanyMemberHttpTests`). Cobrir os cenários 1 a 8 da US1. O Dono é gravado direto na coleção `member` (`MemberDocument` com `role = DONO`), porque esta entrega não grava Dono (C1, 18/11). Conferir também, com `OutputCaptureExtension`, o WARN `event=access.owner.not_admin` (analyze A8) e que o `member` continua igual depois de `access/me` (analyze A9).
- [X] T005 [US1] Criar `access/application/AccessRole.java` (OWNER "Dono", ADMIN "Administrador", MEMBER "Membro") e `access/application/Permission.java`, com a matriz da tabela de `data-model.md` (cada constante sabe os papéis que a têm e a observação opcional).
- [X] T006 [US1] Criar `access/application/AccessPolicy.java` (`require(AccessRole, Permission)` e `permissionsOf(AccessRole)`, ordenadas). Mensagem: "Somente o Dono ou um Administrador pode fazer esta ação.", código `not_admin`.
- [X] T007 [US1] Criar `access/application/MemberRoleDirectory.java` (porta, só leitura: `memberRoleOf(tenantId, userId)` e `ownerOf(tenantId)`) e `access/infrastructure/MongoMemberRoleDirectory.java`, que lê a coleção `member` (LLT-163: `role`, `removedAt`, `deletedAt`) e, na falta dela, a `CompanyMember` (LLT-168: `administrator`), por `MongoTemplate`, sem mudar o módulo `member`. Mais de um DONO ativo: o de `clerkUserId` menor e WARN `event=access.owner.duplicated` (C1, 18/11).
- [X] T008 [US1] Criar `access/application/AccessService.java` com o construtor recebendo `MemberRoleDirectory`:
  - `resolve(CompanySession)`: OWNER se o token é admin e o `member` da sessão é DONO; ADMIN se o token é admin; MEMBER nos demais. Não grava nada (C1, 18/11);
  - `member` DONO com token de membro vale como MEMBER e gera o log WARN `event=access.owner.not_admin`;
  - `roleOf(tenantId, userId)`: papel de outra pessoa (`member`, depois `CompanyMember`, depois MEMBER);
  - `isOwner(tenantId, userId)` e `ownerOf(tenantId)`.

  Nos logs, `userId` só como hash.
- [X] T009 [US1] Criar `access/api/AccessViews.java` (records `MeView`, `CatalogView`, `RoleView`, `MatrixRow`) e `access/api/AccessController.java`, com `GET /api/company/access/me` e `GET /api/company/access` (exige `MEMBERS_VIEW`), conforme `contracts/api.md`.
- [X] T010 [US1] Rodar `.\mvnw.cmd verify`. T003 e T004 verdes, nenhum teste antigo quebrado. Parar para revisão.

## Fase 2: acesso por produto (US2)

- [X] T011 [P] [US2] Criar `src/test/java/br/com/lhamalog/core/access/MemberProductAccessHttpTests.java`, cobrindo os cenários 1 a 7 da US2:
  - liberar A e B, listar como membro, retirar B e ver o 404 no detalhe;
  - produto de outra empresa responde 404 `product_not_found`, sem gravar nada;
  - alvo admin conhecido (feito `touch` com cargo admin, ou `member` ADMIN) ou Dono (`member` DONO) responde 409 `full_access`;
  - membro chamando responde 403 `not_admin`;
  - idempotência;
  - produto arquivado;
  - mais de 500 ids responde 400;
  - `userId` com 201 caracteres responde 400 "Informe o usuário.".
- [X] T012 [US2] Em `product/ProductAccessRepository.java`, acrescentar `deleteByTenantIdAndClerkUserIdAndProductIdIn(String, String, Collection<String>)`. Em `product/ProductRepository.java`, acrescentar `countByTenantIdAndIdIn` ou `findByTenantIdAndIdIn`, para validar a lista.
- [X] T013 [US2] Criar `access/application/ProductAccessService.java`:
  - `list(tenantId, userId)` devolve `allProducts`/`productIds`, usando `AccessService.roleOf` (OWNER ou ADMIN = acesso total);
  - `replace(tenantId, userId, productIds)`: deduplica, valida que todos são da empresa (404 `product_not_found`, sem gravar), recusa alvo com acesso total (409 `full_access`), insere o que falta ignorando `DuplicateKeyException`, remove o que sobra e loga `event=product_access.updated | added=n removed=m`.
- [X] T014 [US2] Criar `access/api/MemberProductAccessController.java` com `GET` e `PUT /api/company/members/{userId}/products` (exige `PRODUCT_ACCESS_MANAGE`; `userId` de 1 a 200; `productIds` obrigatório, até 500).
- [X] T015 [US2] Rodar `.\mvnw.cmd verify`, com atenção aos testes de produto que já existem (`ProductEditArchiveIntegrationTests`, `ProductUniqueNameIntegrationTests`, `CompanyProductIntegrationTests`). Parar para revisão.

## Fase 3: catálogo de cargos de notificação (US3)

- [X] T016 [P] [US3] Criar `src/test/java/br/com/lhamalog/core/notificationrole/NotificationRoleHttpTests.java`, cobrindo os cenários 1 a 9 da US3:
  - seed dos 5 cargos, com dois `GET` simultâneos sem duplicar;
  - criar personalizado;
  - nome repetido responde 409 (inclusive com maiúsculas e espaços), e o nome de um cargo excluído pode ser reutilizado;
  - Dono e Administrador respondem 409 `immutable_role`;
  - exclusão responde 204 e depois 404;
  - cargo de outra empresa responde 404;
  - listas inválidas respondem 400;
  - `version` velha responde 409;
  - membro responde 403.
- [X] T017 [P] [US3] Criar os enums `notificationrole/application/Severity.java`, `AlertType.java` e `DefaultRole.java` (OWNER "Dono", imutável; ADMIN "Administrador", imutável; MANAGER "Gestor"; SUPERVISOR "Supervisor"; MEMBER "Membro").
- [X] T018 [US3] Criar `notificationrole/application/NotificationRole.java` (record), `NotificationRoleRepository.java` (porta), `infrastructure/NotificationRoleDocument.java` (`@Document("NotificationRole")`, `@Version`) e `infrastructure/MongoNotificationRoleRepository.java`:
  - `ensureDefaults(tenantId)` faz um upsert por `defaultKey` com `setOnInsert`;
  - `findActive`, `findById`, `save`, `addMember` (`$addToSet`) e `removeMember` (`$pull`).
- [X] T019 [US3] Criar os índices da tabela de `data-model.md`. Feito com `Index.partial(PartialIndexFilter)` no `afterPropertiesSet` do `MongoNotificationRoleRepository` (padrão do `MongoMemberRepository`), sem initializer separado. O teste `indexesOfTheDataModelExistWithTheirPartialFilters` confere os três.
- [X] T020 [US3] Criar `notificationrole/application/NotificationRoleService.java`:
  - seed na entrada de toda operação;
  - validação de nome (1 a 60 caracteres, normalizado com `Locale.ROOT`) e das listas (personalizado de 1 a 3; padrão editável de 0 a 3 na criação e de 1 a 3 no PUT);
  - 409 `role_name_in_use` traduzido da `DuplicateKeyException` do índice de nome;
  - 409 `immutable_role`;
  - 404 `role_not_found` para cargo excluído ou de outra empresa;
  - exclusão lógica;
  - logs `event=notification_role.created|updated|deleted`.
- [X] T021 [US3] Criar `notificationrole/api/NotificationRoleViews.java` e `NotificationRoleController.java` com `GET/POST /`, `GET/PATCH/DELETE /{roleId}`, `PUT /{roleId}/severities` e `PUT /{roleId}/alert-types` (exige `NOTIFICATION_ROLES_MANAGE`).
- [X] T022 [US3] Rodar `.\mvnw.cmd verify`. Parar para revisão.

## Fase 4: atribuir cargo (US4)

- [X] T023 [P] [US4] Criar `src/test/java/br/com/lhamalog/core/notificationrole/NotificationRoleMembersHttpTests.java`, cobrindo os cenários 1 a 7 da US4:
  - `POST` e `DELETE` idempotentes;
  - `PUT` com `version`;
  - Dono e Administrador respondem 409 `members_from_access_role`;
  - consulta por pessoa traz 2 cargos;
  - cargo excluído some da consulta;
  - `userId` inválido responde 400.
- [X] T024 [US4] No `NotificationRoleService`:
  - `addMember` (update condicional: só inclui se o cargo tem menos de 200; cheio → 409 `role_members_limit`, analyze A5), `removeMember` e `replaceMembers` (até 200, `version`, log `event=notification_role.members.updated`);
  - `rolesOf(tenantId, userId)`: inclui Dono ou Administrador pelo papel de acesso (`AccessService.roleOf`).
- [X] T025 [US4] Acrescentar ao `NotificationRoleController` as rotas `PUT /{roleId}/members`, `POST /{roleId}/members/{userId}` e `DELETE /{roleId}/members/{userId}`. Criar `notificationrole/api/MemberNotificationRoleController.java` com `GET /api/company/members/{userId}/notification-roles`.
- [X] T026 [US4] Rodar `.\mvnw.cmd verify`. Parar para revisão.

## Fase 5: proteger o Dono (US5)

- [X] T027 [US5] Em `AccessHttpTests`, acrescentar o cenário: o admin A tem `member` DONO (gravado no teste); o admin B tenta desativar A pela rota da LLT-168 e recebe 403 `owner_protected`; A continua `active` no `GET /api/company/members`. Rodar e ver falhar.
- [X] T028 [US5] Em `member/CompanyMemberController.java` (LLT-168), injetar o `AccessService` e, em `deactivate`, recusar o Dono com `ApiStatusException(FORBIDDEN, "Não é possível desativar o Dono da empresa.", "owner_protected")`, antes de chamar o serviço. **Avisar o Vinicius no PR** (C6).
- [X] T029 [US5] Rodar `.\mvnw.cmd verify`, inclusive `CompanyMemberHttpTests`, que tem que continuar verde sem mudança.

## Fase 6: fechamento

- [X] T030 [P] Atualizar o `quickstart.md` com os comandos de teste, a correção manual do Dono e a lista de rotas.
- [X] T031 [P] Conferir os logs novos contra a constituição VI: formato `event=`, sem `userId` em claro, sem stack trace em condição esperada.
- [X] T032 Conferir que não há segredo (`sk_`, `CLERK_SECRET_KEY`, JWT) em nenhum arquivo novo.
- [X] T033 Rodar `speckit-analyze` e resolver as inconsistências entre spec, plan, tasks e código.
- [X] T034 Rodar `.\mvnw.cmd verify` final (com Docker aberto) e registrar o resumo no `quickstart.md`.
- [ ] T035 Checklist de merge da constituição: Spec Kit completo, revisão do Pedro (ele precisa conseguir explicar cada classe), verde local, branch e commits no padrão, nenhum segredo. PR só com a confirmação do Pedro. **Em 18/11: verde local, branch e commits no padrão do time e nenhum segredo conferidos; faltam a revisão do Pedro, a confirmação das decisões do clarify (C1) e a emenda da constituição sobre o nome da branch (analyze A1).**

## Dependências

- A Fase 1 bloqueia todas as outras, porque o `AccessService` e o `AccessPolicy` são usados em tudo.
- As Fases 2 e 3 são independentes entre si.
- A Fase 4 depende da 3. A Fase 5 depende da 1.
- Dentro de cada fase, as tasks `[P]` de teste podem ser escritas juntas.
