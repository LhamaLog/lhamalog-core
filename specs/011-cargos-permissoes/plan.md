# Plano: Cargos e permissões

**Branch**: `LLT-120-cargos-permissoes` | **Data**: 17/11/2026 | **Spec**: [spec.md](spec.md)

**Input**: spec de `/specs/011-cargos-permissoes/spec.md` (pré-preenchida; os pontos C1 a C7 são confirmados no clarify).

## Summary

A entrega tem três partes:

1. **Papéis de acesso e matriz:** módulo novo `access`. O papel OWNER, ADMIN ou MEMBER é resolvido pela sessão e pelo `member` DONO da LLT-163, só por leitura (clarify 18/11, C1). Nenhuma coleção nova para o Dono. A matriz de permissões é fixa (`Permission` × `AccessRole`) e fica exposta em `GET /api/company/access/me` e `GET /api/company/access`.
2. **Acesso por produto:** rotas novas sobre a coleção `AcessoProduto`, que já existe.
3. **Cargos de notificação:** módulo novo `notificationrole`, com os cinco cargos padrão criados no primeiro uso, CRUD com exclusão lógica e definitiva, e atribuição de membros.

Também há uma checagem nova na desativação da LLT-168, para proteger o Dono. Nada muda nas rotas existentes além disso.

## Technical Context

- **Language/Version:** Java 25.
- **Primary Dependencies:** Spring Boot 4.1.1, Spring Data MongoDB, Spring Security OAuth2 Resource Server, Bean Validation. Nenhuma dependência nova.
- **Storage:** MongoDB 8.0.34.
  - coleção nova: `NotificationRole`;
  - coleção existente gravada: `AcessoProduto`, sem mudar o formato;
  - coleções existentes só lidas: `member` (LLT-163) e `CompanyMember` (LLT-168).
- **Testing:** JUnit 5, MockMvc e Testcontainers (`TestcontainersConfiguration`, tags fixas). Tokens Clerk reais assinados pelo `TestJwks` (`@CoreIntegrationTest` + `TestTenants`, LLT-163). Sem `TEST_MONGODB_URI`.
- **Target Platform:** API HTTP do Core (8080).
- **Project Type:** web-service.
- **Performance Goals:**
  - `access/me` faz no máximo uma leitura na `member`;
  - as rotas de cargos fazem uma consulta por `tenantId`.
- **Constraints:**
  - sem chave secreta da Clerk e sem chamada à API da Clerk;
  - o Clerk gratuito tem no máximo 20 membros por organização (ADR_123), por isso os limites de 200 e 500 são folgados.
- **Scale/Scope:** 5 user stories, 15 rotas novas e 1 checagem nova numa rota existente.

## Constitution Check

| Princípio | Como esta feature cumpre |
|---|---|
| I. Spec Kit | spec, plan e tasks nesta pasta antes do código. Clarify e analyze no Claude Code |
| II. IA com revisão | o Claude Code explica cada fase ao Pedro e para para revisão. As decisões ficam em `research.md` |
| III. Testes antes do merge | 4 classes HTTP novas e 1 unitária. `CompanyMemberHttpTests` continua verde sem mudança (T029). `.\mvnw.cmd verify` verde |
| IV. Inglês no código | pacotes `access` e `notificationrole`, rotas `/api/company/access`, `/api/company/notification-roles` e `/api/company/members/{userId}/products`. Docs em português |
| V. Segredos | sem `CLERK_SECRET_KEY` e sem token em arquivo ou log |
| VI. Logs | eventos `access.owner.not_admin` e `access.owner.duplicated` (WARN), `product_access.updated`, `notification_role.*`. Sem `userId` em claro |
| Stack | sem mudança de versão nem dependência nova |
| Fluxo | branch `LLT-120-cargos-permissoes` (padrão do time: `LLT-xxx-descricao`, sem `feature/`; a constituição ainda pede `feature/` e precisa de emenda) e commits `LLT-120: ...` |

Não há violação, então a tabela de complexidade não se aplica.

## Project Structure

### Documentação (esta feature)

```text
specs/011-cargos-permissoes/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/api.md
├── checklists/requirements.md
└── tasks.md
```

### Código

```text
src/main/java/br/com/lhamalog/core/
├── access/
│   ├── api/
│   │   ├── AccessController.java                 # GET /api/company/access, /access/me
│   │   ├── MemberProductAccessController.java    # GET/PUT /api/company/members/{userId}/products
│   │   └── AccessViews.java                      # records de resposta
│   ├── application/
│   │   ├── AccessRole.java                       # OWNER, ADMIN, MEMBER (+ label)
│   │   ├── Permission.java                       # enum com a matriz (data-model.md)
│   │   ├── AccessPolicy.java                     # require(session, permission) -> 403 not_admin
│   │   ├── AccessService.java                    # resolve(session), roleOf(alvo), isOwner, ownerOf
│   │   ├── MemberRoleDirectory.java              # porta: papel do alvo e Dono (member LLT-163 + CompanyMember LLT-168)
│   │   └── ProductAccessService.java             # replace/list sobre ProductAccessRepository
│   └── infrastructure/
│       └── MongoMemberRoleDirectory.java         # só leitura em member e CompanyMember (MongoTemplate)
├── notificationrole/
│   ├── api/
│   │   ├── NotificationRoleController.java       # /api/company/notification-roles/**
│   │   ├── MemberNotificationRoleController.java # GET /api/company/members/{userId}/notification-roles
│   │   └── NotificationRoleViews.java
│   ├── application/
│   │   ├── Severity.java                         # ERROR, WARNING, INFO
│   │   ├── AlertType.java                        # OCCURRENCE, CRITICAL_STATE, SYSTEM_ALERT
│   │   ├── DefaultRole.java                      # OWNER, ADMIN, MANAGER, SUPERVISOR, MEMBER (+ nome, immutable)
│   │   ├── NotificationRole.java                 # record de domínio
│   │   ├── NotificationRoleRepository.java       # porta
│   │   └── NotificationRoleService.java          # seed, CRUD, membros, validações
│   └── infrastructure/
│       ├── NotificationRoleDocument.java         # @Document("NotificationRole")
│       ├── MongoNotificationRoleRepository.java  # usa MongoTemplate para $addToSet/$pull e upsert dos padrão
│       └── NotificationRoleIndexInitializer.java # índices parciais (se @CompoundIndex não aceitar partialFilter)
├── member/CompanyMemberController.java           # +1 checagem: owner_protected (FR-020)
└── product/ProductAccessRepository.java          # +deleteByTenantIdAndClerkUserIdAndProductIdIn

src/test/java/br/com/lhamalog/core/
├── access/AccessHttpTests.java                   # US1 + US5
├── access/MemberProductAccessHttpTests.java      # US2
├── access/application/AccessPolicyTest.java      # matriz (unitário, sem Spring)
├── notificationrole/NotificationRoleHttpTests.java        # US3
└── notificationrole/NotificationRoleMembersHttpTests.java # US4
```

**Structure Decision:** dois módulos novos no padrão do `usage` (api, application, infrastructure). O `AcessoProduto` continua no pacote `product`, e o `access` o usa pelo repositório. Só uma linha muda no `member`.

## Fases

1. **Fundação (US1):** `AccessRole`, `Permission`, `AccessPolicy`, `MemberRoleDirectory` e `AccessService`, mais as rotas `access` e os testes. Esta é a entrega mínima (MVP).
2. **Acesso por produto (US2):** `ProductAccessService`, controller e testes.
3. **Catálogo de cargos (US3):** enums, seed, CRUD, índices e testes.
4. **Atribuição (US4):** rotas de membros e consulta por pessoa, com testes.
5. **Proteção do Dono (US5):** checagem na LLT-168 e teste. O `CompanyMemberHttpTests` não muda: nenhum teste antigo grava `member` DONO.
6. **Fechamento:** quickstart, logs (VI), `verify` e revisão.

## Riscos

| Risco | Mitigação |
|---|---|
| Sem fluxo de compra, nenhuma empresa real tem Dono | o quickstart mostra como marcar o `member` DONO no banco local. Decisão C1 (18/11) aguarda o Pedro |
| Testes da LLT-168 desativam alguém que é Dono | nenhum teste antigo grava `member` DONO; só os testes novos gravam. Conferir no `verify` |
| A unificação de `member` e `CompanyMember` (D1) muda as fontes | só o `MongoMemberRoleDirectory` lê as duas coleções |
| `@CompoundIndex` sem `partialFilter` | `NotificationRoleIndexInitializer` com `IndexOperations` |
