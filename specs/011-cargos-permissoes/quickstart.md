# Quickstart: Cargos e permissões

Este guia serve para verificar a feature. Não traz a implementação.

## Pré-requisitos

- JDK 25 e Docker Desktop aberto. Os testes usam Testcontainers com `mongo:8.0.34`, `redis:8.2.10` e `grafana/otel-lgtm:0.37.0`.
- Sem `CLERK_SECRET_KEY` e sem valor que comece com `sk_`.
- PowerShell, não o CMD.

## Rodar os testes

```powershell
cd C:\Users\Pedro\Desktop\LhamaLog\lhamalog-core
.\mvnw.cmd verify
```

Desde a LLT-163, todos os testes de integração sobem um Mongo temporário pelo Testcontainers (`@CoreIntegrationTest`). Nenhum usa `TEST_MONGODB_URI`, e nenhum toca os bancos compartilhados `lhamalog_core` ou `lhamalog_teste`.

Para conferências manuais no banco, use só o seu container e o banco desta feature: `mongo-teste`, porta 27019, banco `lhamalog_llt120`.
| Classe | Prova |
|---|---|
| `AccessPolicyTest` | a matriz inteira (data-model.md) |
| `AccessHttpTests` | US1 (papel pela sessão e pelo `member` DONO, Dono rebaixado, Dono removido) e US5 (Dono protegido) |
| `MemberProductAccessHttpTests` | US2 |
| `NotificationRoleHttpTests` | US3 |
| `NotificationRoleMembersHttpTests` | US4 |
| `CompanyMemberHttpTests` (LLT-168) | continua verde sem mudança |

Linha de base antes do código (T001): 18/11/2026, de 06:51 a 06:54 (-03:00), branch `LLT-120-cargos-permissoes` em `de4cd79` (igual à `origin/Development`), Docker Desktop 29.6.2. `.\mvnw.cmd -B clean verify`: BUILD SUCCESS, com **308 testes, 0 falhas, 0 erros e 0 ignorados**. O ERROR `ClerkWebhookController.receive ... DataIntegrityViolationException` no log é de um cenário de falha proposital da `ClerkWebhookTests`, que passou.
Resultado final (T034): 18/11/2026, de 07:35 a 07:38 (-03:00), branch `LLT-120-cargos-permissoes` no commit `8bb3f3b` mais os ajustes de documentação da Fase 6. `.\mvnw.cmd -B clean verify`: BUILD SUCCESS, com **347 testes, 0 falhas, 0 erros e 0 ignorados** (308 da linha de base + 39 novos: `AccessHttpTests` 11, `AccessPolicyTest` 5, `MemberProductAccessHttpTests` 7, `NotificationRoleHttpTests` 10, `NotificationRoleMembersHttpTests` 6). `CompanyMemberHttpTests` (LLT-168) passou sem mudança.

## Rotas desta feature

Todas exigem sessão com empresa. Detalhes em `contracts/api.md`.

| Método e rota | Quem pode | Para quê |
|---|---|---|
| `GET /api/company/access/me` | qualquer pessoa da empresa | papel e permissões de quem chamou |
| `GET /api/company/access` | Dono e Administrador | Dono da empresa, os três papéis e a matriz |
| `GET` e `PUT /api/company/members/{userId}/products` | Dono e Administrador | produtos liberados para um Membro |
| `GET` e `POST /api/company/notification-roles` | Dono e Administrador | listar (cria os padrão) e criar cargo |
| `GET`, `PATCH` e `DELETE /api/company/notification-roles/{roleId}` | Dono e Administrador | detalhe, renomear e excluir |
| `PUT /api/company/notification-roles/{roleId}/severities` e `/alert-types` | Dono e Administrador | trocar gravidades e tipos |
| `PUT /api/company/notification-roles/{roleId}/members` | Dono e Administrador | trocar a lista de pessoas |
| `POST` e `DELETE /api/company/notification-roles/{roleId}/members/{userId}` | Dono e Administrador | incluir e retirar uma pessoa |
| `GET /api/company/members/{userId}/notification-roles` | Dono e Administrador | cargos de uma pessoa |
| `POST /api/company/members/{userId}/deactivation` (LLT-168, alterada) | Dono e Administrador | agora recusa o Dono com 403 `owner_protected` |
## Conferir à mão (Core local + token do navegador)

1. Suba o Core local (README do Core) e entre no Web com um admin da empresa de teste.
2. No console do navegador, pegue o token com `await window.Clerk.session.getToken()`. Não salve o token em lugar nenhum.
3. No PowerShell:
   ```powershell
   $h = @{ Authorization = "Bearer <cole o token aqui>" }
   Invoke-RestMethod http://localhost:8080/api/company/access/me -Headers $h
   Invoke-RestMethod http://localhost:8080/api/company/access -Headers $h
   Invoke-RestMethod http://localhost:8080/api/company/notification-roles -Headers $h
   ```
4. Sem Dono marcado, o `access/me` de um admin traz `"role": "ADMIN"` e o `access` traz `"ownerUserId": null`. Depois de marcar o Dono (abaixo), o `access/me` dele traz `"role": "OWNER"`.

## Marcar o Dono à mão (enquanto não existe o fluxo de compra)

O Dono é o `member` da LLT-163 com papel `DONO` (C1). Esta feature não grava Dono. Para testar no ambiente local, rode no `mongosh` **só no banco que o seu Core local usa** (nunca em `lhamalog_core` compartilhado nem em `lhamalog_teste`):

```js
// 1. descubra o tenantId interno da empresa de teste
db.tenant.findOne({ clerkOrgId: "<org_id da Clerk>" }, { _id: 1 })
// 2. marque o Dono (o member precisa existir; ele nasce pelo webhook da Clerk)
db.member.updateOne({ tenantId: "<tenantId>", clerkUserId: "<user_id>" }, { $set: { role: "DONO" } })
```

Se o `member` não existir (webhook não configurado no local), crie um com `tenantId`, `clerkUserId`, `role: "DONO"`, `membershipVersion: 0` e `userVersion: 0`. Em produção, isso exige combinar antes com o time.
## Fora desta verificação

- entrega e aviso de alertas (módulo Notificação);
- ACESSO_PENDENTE;
- fluxo de compra;
- transferência do Dono;
- telas (verificadas pela spec do Web).
