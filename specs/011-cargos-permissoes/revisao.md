# Roteiro de revisão: Cargos e permissões (LLT-120)

Este roteiro serve para o Pedro revisar o que a IA implementou e conseguir explicar cada classe (constituição II e checklist de merge). Para cada classe nova ou alterada, ele traz o que ela faz e qual teste prova esse comportamento. Atualizado em 22/11/2026, depois do merge da `Development` com a LLT-289.

## Como ler o código

O fluxo de uma requisição desta feature é sempre o mesmo:

1. O controller (`api`) pega a sessão com o `CompanySessionResolver`, que já existia.
2. O `AccessService.require(session, Permission.X)` descobre o papel da sessão (OWNER, ADMIN ou MEMBER) e confere a permissão na matriz. Sem a permissão, a resposta é 403 `not_admin`.
3. O serviço (`application`) aplica as regras de negócio.
4. O repositório (`infrastructure`) grava ou lê no Mongo.

Antes disso tudo, a camada de segurança da LLT-163 e da LLT-289 já recusou quem não tem token (401), quem não tem organização (403 `forbidden`) e quem ainda não contratou plano (403 `subscription_required`, sem logout).

## Módulo `access`: papel de acesso, matriz e acesso por produto

| Classe | O que faz | Teste que prova |
|---|---|---|
| `access/application/AccessRole` | Enum dos três papéis de acesso, `OWNER`, `ADMIN` e `MEMBER`, com o nome em português (Dono, Administrador, Membro). | `AccessPolicyTest.rolesHaveTheirPortugueseLabels` |
| `access/application/Permission` | Enum com cada ação do sistema e os papéis que podem fazê-la. É a tabela do `data-model.md` escrita em código, fixa (ADR_123). | `AccessPolicyTest.everyPermissionHasExactlyTheRolesOfTheDataModelTable` |
| `access/application/AccessPolicy` | Confere se um papel tem uma permissão e lança 403 `not_admin` se não tiver. Também lista as permissões de um papel em ordem alfabética. | `AccessPolicyTest.requireRefusesWithTheBusinessForbiddenCode` e `permissionsOfARoleComeInAlphabeticalOrder` |
| `access/application/MemberRoleDirectory` | Interface (porta) com as perguntas sobre papéis: qual o papel de uma pessoa na `member`, se ela foi vista como admin na `CompanyMember` e quem são os Donos ativos. Só leitura. | coberta pelos testes do `AccessService` |
| `access/infrastructure/MongoMemberRoleDirectory` | Responde as perguntas da porta lendo a coleção `member` (LLT-163: `role`, `removedAt`, `deletedAt`) e a `CompanyMember` (LLT-168: `administrator`). Na unificação das duas coleções (D1), só ela muda. | `AccessHttpTests.removedOrDeletedOwnerIsNotOwner`, `MemberProductAccessHttpTests` (alvo admin pela `CompanyMember`) |
| `access/application/AccessService` | Decide o papel da sessão: OWNER se o token é admin e a pessoa é `member` DONO; ADMIN se o token é admin; senão MEMBER. Dono com token de membro vale como MEMBER, com WARN. Também dá o papel de outra pessoa (`roleOf`), diz se alguém é Dono (`isOwner`) e quem é o Dono da empresa (`ownerOf`). Nunca grava nada. | `AccessHttpTests` (8 cenários da US1) e `AccessPurchaseOwnerIntegrationTests.buyerIsTheOwnerOfTheCompanyCreatedByThePayment` |
| `access/application/UserHash` | Transforma o `userId` num SHA-256 curto para os logs, porque a constituição VI proíbe dado pessoal em claro. | `AccessHttpTests.ownerDemotedOutsideTheSystemIsMemberAndWarns` (o log não traz o id) |
| `access/application/UserIds` | Valida o `userId` que vem no caminho da URL: de 1 a 200 caracteres. Fora disso, responde 400 "Informe o usuário.". | `MemberProductAccessHttpTests` (`userId` de 201 caracteres) e `NotificationRoleMembersHttpTests` |
| `access/application/ProductAccessService` | Lê e substitui os produtos liberados para um Membro na coleção `AcessoProduto`, que já existia. Confere se todos os produtos são da empresa (senão 404 `product_not_found`, sem gravar nada), recusa Dono e admin (409 `full_access`), inclui o que falta, remove o que sobra e loga só as contagens. | `MemberProductAccessHttpTests` (7 cenários da US2) e `AccessPurchaseOwnerIntegrationTests.buyerHasAccessToAllProducts` |
| `access/api/AccessViews` | Records das respostas JSON: `MeView` (papel e permissões), `CatalogView` (Dono, papéis e matriz), `RoleView` e `MatrixRow`. | `AccessHttpTests.catalogShowsTheOwnerTheRolesAndTheMatrix` |
| `access/api/AccessController` | `GET /api/company/access/me` (qualquer pessoa da empresa) e `GET /api/company/access` (só Dono e Administrador). | `AccessHttpTests` |
| `access/api/MemberProductAccessController` | `GET` e `PUT /api/company/members/{userId}/products`, com a permissão `PRODUCT_ACCESS_MANAGE` e o limite de 500 ids. | `MemberProductAccessHttpTests` |

## Módulo `notificationrole`: cargos de notificação

| Classe | O que faz | Teste que prova |
|---|---|---|
| `notificationrole/application/Severity` | Enum das gravidades válidas: `ERROR`, `WARNING` e `INFO` (C3, ADR_170). | `NotificationRoleHttpTests` (lista inválida dá 400) |
| `notificationrole/application/AlertType` | Enum dos tipos de alerta válidos: `OCCURRENCE`, `CRITICAL_STATE` e `SYSTEM_ALERT` (C3, ADR_060). | `NotificationRoleHttpTests` |
| `notificationrole/application/DefaultRole` | Enum dos cinco cargos padrão, com o nome exibido e se é imutável. Dono e Administrador são imutáveis. | `NotificationRoleHttpTests` (seed dos 5) |
| `notificationrole/application/NotificationRole` | Record de domínio do cargo: nome, listas, membros, se é padrão ou imutável, exclusão lógica e versão. | todos os testes de cargos |
| `notificationrole/application/NotificationRoleRepository` | Interface (porta) do repositório de cargos. | idem |
| `notificationrole/application/NotificationRoleService` | Regras dos cargos: cria os padrão no primeiro uso, valida nome (1 a 60, único sem diferenciar maiúsculas) e listas, bloqueia Dono e Administrador (409 `immutable_role` ou `members_from_access_role`), faz a exclusão lógica, inclui e retira membros (até 200) e diz em quais cargos uma pessoa está. Loga os eventos `notification_role.*`. | `NotificationRoleHttpTests` (US3) e `NotificationRoleMembersHttpTests` (US4) |
| `notificationrole/infrastructure/NotificationRoleDocument` | Documento Mongo `NotificationRole`, com `@Version` para o controle de concorrência. | idem |
| `notificationrole/infrastructure/MongoNotificationRoleRepository` | Grava e lê os cargos com `MongoTemplate`. Cria os padrão por upsert (sem duplicar, mesmo com duas chamadas ao mesmo tempo), inclui e retira membro de forma atômica (`$addToSet`/`$pull`) e cria os três índices, dois deles parciais. | `NotificationRoleHttpTests.indexesOfTheDataModelExistWithTheirPartialFilters` e o seed concorrente |
| `notificationrole/api/NotificationRoleViews` | Records das respostas JSON dos cargos. | idem |
| `notificationrole/api/NotificationRoleController` | As rotas de `/api/company/notification-roles` (listar, criar, detalhe, renomear, excluir, trocar listas e membros), todas com `NOTIFICATION_ROLES_MANAGE`. | `NotificationRoleHttpTests` e `NotificationRoleMembersHttpTests` |
| `notificationrole/api/MemberNotificationRoleController` | `GET /api/company/members/{userId}/notification-roles`: os cargos de uma pessoa. | `NotificationRoleMembersHttpTests` |

## Arquivos de outras features alterados (mudança mínima)

| Arquivo | O que mudou | Teste que prova | Avisar |
|---|---|---|---|
| `member/CompanyMemberController` (LLT-168) | Recebe o `AccessService` e, antes de desativar, recusa o Dono com 403 `owner_protected` (C6, FR-020). O resto não mudou. | `AccessHttpTests.ownerCannotBeDeactivatedAndStaysActive`, `AccessPurchaseOwnerIntegrationTests.anotherAdminCannotDeactivateTheBuyer` e `CompanyMemberHttpTests` verde sem mudança | Vinicius, no PR |
| `product/ProductAccessRepository` | Novo método `deleteByTenantIdAndClerkUserIdAndProductIdIn`, para retirar acessos. | `MemberProductAccessHttpTests` | — |
| `product/ProductRepository` | Novo método `countByTenantIdAndIdIn`, para conferir se os produtos são da empresa. | `MemberProductAccessHttpTests` (produto de outra empresa) | — |

## Testes

| Classe | Tipo | Quantos | O que prova |
|---|---|---|---|
| `AccessPolicyTest` | unitário, sem Spring | 5 | a matriz inteira |
| `AccessHttpTests` | integração (Testcontainers + tokens Clerk de teste) | 11 | US1 e US5 |
| `MemberProductAccessHttpTests` | integração | 7 | US2 |
| `NotificationRoleHttpTests` | integração | 10 | US3 |
| `NotificationRoleMembersHttpTests` | integração | 6 | US4 |
| `AccessPurchaseOwnerIntegrationTests` | integração, com a compra simulada da LLT-289 | 4 | o Dono da compra é OWNER, não pode ser desativado e acessa tudo; sem Tenant, as rotas novas dão `subscription_required` sem logout |

## Perguntas que o Pedro deve saber responder

- **Por que o Core não grava o Dono?** Porque, pela ADR_121 e pela spec 003, só o fluxo de compra atribui DONO. Desde a LLT-289, é a confirmação do pagamento que faz isso. Esta feature só lê (C1).
- **Por que o papel vem do token e não só da `member`?** Porque o token é quem manda no poder. Se alguém rebaixar o Dono direto na Clerk, ele passa a valer como Membro, com um WARN no log.
- **Por que duas coleções (`member` e `CompanyMember`)?** Porque o time ainda não unificou a LLT-163 e a LLT-168 (pendência da D1). Só o `MongoMemberRoleDirectory` lê as duas, então a troca futura fica num lugar só.
- **Por que a matriz é fixa no código?** Porque a ADR_123 descartou papéis de acesso customizáveis. Só os cargos de notificação são editáveis, e eles não dão permissão de acesso (USID_052).
- **O que acontece com um Membro sem acesso a um produto?** A lista não mostra o produto, e o detalhe responde 404. É a negação simples da ADR_126, sem logout.
