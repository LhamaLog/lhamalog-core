# Contrato: Cargos e permissões (Core)

Este é o contrato do Web, a fonte da verdade do que ele consulta. Todas as rotas exigem `Authorization: Bearer <JWT da Clerk>` com organização ativa. A empresa considerada é sempre a da sessão; `tenantId` ou `organizationId` enviados no corpo são ignorados.

## Respostas comuns

| Situação | Status | Corpo |
|---|---|---|
| Sem JWT | 401 | comportamento atual do filtro |
| Sem empresa ativa, sem Tenant ou Tenant inativo | 403 | `{"error":"forbidden"}`, com revogação das sessões (camada de segurança da LLT-163) |
| Pessoa desativada nesta empresa (LLT-168) | 403 | `{"message":"Seu acesso a esta empresa foi desativado.","code":"user_deactivated"}` |
| Papel sem a permissão | 403 | `{"message":"Somente o Dono ou um Administrador pode fazer esta ação.","code":"not_admin"}` |
| Corpo inválido | 400 | `{"message":"Confira os campos informados.","fieldErrors":{...}}` |
| `version` divergente | 409 | `{"message":"Os dados foram alterados. Recarregue antes de salvar novamente."}` |

O 403 não encerra a sessão: é a negação simples (ADR_109/126). O Web volta para a tela anterior e mantém o usuário logado.

---

## 1. Papel e matriz

### `GET /api/company/access/me`

Qualquer sessão com empresa. Só lê: o papel `OWNER` vem do `member` DONO da LLT-163 com token de administrador. Esta chamada não grava Dono (FR-002, ADR_121).

```json
200
{
  "userId": "user_ana",
  "role": "OWNER",
  "permissions": ["COMPANY_VIEW", "COMPANY_EDIT", "MEMBERS_VIEW", "...", "SUBSCRIPTION_MANAGE"]
}
```

`role` é `OWNER`, `ADMIN` ou `MEMBER`. `permissions` vem em ordem alfabética.

Na matriz, `note` só aparece quando há observação: `PRODUCTS_EDIT` ("MEMBER só nos produtos liberados"), as três de convite e promoção ("Aplicada no Web, pela Clerk.") e `SUBSCRIPTION_MANAGE` e `PENDING_ACCESS_APPROVE` ("Ainda não existe no Core."). A matriz vem na ordem da tabela do `data-model.md`.

### `GET /api/company/access`

Exige `MEMBERS_VIEW`, ou seja, Dono ou Administrador.

```json
200
{
  "ownerUserId": "user_ana",
  "roles": [
    {"key": "OWNER", "label": "Dono"},
    {"key": "ADMIN", "label": "Administrador"},
    {"key": "MEMBER", "label": "Membro"}
  ],
  "matrix": [
    {"permission": "COMPANY_EDIT", "roles": ["OWNER", "ADMIN"]},
    {"permission": "PRODUCTS_EDIT", "roles": ["OWNER", "ADMIN", "MEMBER"], "note": "MEMBER só nos produtos liberados"}
  ]
}
```

`ownerUserId` é o `clerkUserId` do `member` DONO ativo da empresa, ou `null` se não houver (o fluxo de compra ainda não existe; ver `quickstart.md` para marcar o Dono à mão no ambiente local).

---

## 2. Acesso por produto

### `GET /api/company/members/{userId}/products`

Exige `PRODUCT_ACCESS_MANAGE`.

```json
200
{"userId": "user_bruno", "allProducts": false, "productIds": ["p-1", "p-2"]}
```

Para o Dono ou um administrador conhecido, a resposta é `{"userId": "...", "allProducts": true, "productIds": []}`. O papel do alvo vem da coleção `member` (LLT-163) quando há registro ativo; sem ele, do `administrator` da `CompanyMember` (LLT-168).

### `PUT /api/company/members/{userId}/products`

Exige `PRODUCT_ACCESS_MANAGE`.

```json
{"productIds": ["p-1", "p-2"]}
```

| Situação | Status | Corpo |
|---|---|---|
| Ok (substitui) | 200 | o mesmo formato do `GET` |
| Algum produto não é desta empresa | 404 | `{"message":"Produto não encontrado nesta empresa.","code":"product_not_found"}` (nada é gravado) |
| Alvo é o Dono ou um admin conhecido | 409 | `{"message":"Este membro acessa todos os Produtos.","code":"full_access"}` |
| `userId` vazio ou com mais de 200 caracteres | 400 | `{"message":"Informe o usuário."}` |
| Mais de 500 ids, ou `productIds` ausente | 400 | `fieldErrors.productIds` |

A lista vazia é válida e retira todos os acessos. Ids repetidos são deduplicados.

---

## 3. Cargos de notificação

Base: `/api/company/notification-roles`. Equivale à `/papeis` da ADR_139 (C2). Todas as rotas exigem `NOTIFICATION_ROLES_MANAGE`.

Formato do cargo:

```json
{
  "id": "6c1e...",
  "name": "Gestor",
  "defaultKey": "MANAGER",
  "isDefault": true,
  "immutable": false,
  "severities": ["ERROR", "WARNING"],
  "alertTypes": ["OCCURRENCE"],
  "memberUserIds": ["user_bruno"],
  "membersSource": "EXPLICIT",
  "version": 3
}
```

`membersSource` é `ACCESS_ROLE` em Dono e Administrador. Nesses dois, `memberUserIds` vem vazio e o Web monta a lista pelo papel.

| Método | Caminho | Corpo | Sucesso | Recusas |
|---|---|---|---|---|
| GET | `/` | — | 200 `{"roles":[...]}`, com os padrão na ordem Dono, Administrador, Gestor, Supervisor e Membro, depois os personalizados por nome. Cria os padrão na primeira vez | — |
| POST | `/` | `{"name","severities":[..],"alertTypes":[..]}` | 201 cargo | 400 campos; 409 `role_name_in_use` |
| GET | `/{roleId}` | — | 200 cargo | 404 `role_not_found` |
| PATCH | `/{roleId}` | `{"name","version"}` | 200 cargo | 404; 409 `immutable_role`; 409 `role_name_in_use`; 409 versão |
| DELETE | `/{roleId}` | — | 204 | 404; 409 `immutable_role` |
| PUT | `/{roleId}/severities` | `{"severities":[..],"version"}` | 200 cargo | 400; 404; 409 `immutable_role`; 409 versão |
| PUT | `/{roleId}/alert-types` | `{"alertTypes":[..],"version"}` | 200 cargo | 400; 404; 409 `immutable_role`; 409 versão |
| PUT | `/{roleId}/members` | `{"memberUserIds":[..],"version"}` | 200 cargo (ids repetidos são deduplicados) | 400 `fieldErrors.memberUserIds` (mais de 200, ou id vazio ou com mais de 200 caracteres); 404; 409 `members_from_access_role`; 409 versão |
| POST | `/{roleId}/members/{userId}` | — | 200 cargo (idempotente) | 400 "Informe o usuário."; 404; 409 `members_from_access_role`; 409 `role_members_limit` (cargo já com 200) |
| DELETE | `/{roleId}/members/{userId}` | — | 204 (idempotente) | 400; 404; 409 `members_from_access_role` |

Mensagens:

| Código | Mensagem |
|---|---|
| `role_not_found` | "Cargo não encontrado." |
| (campo `name`) | "Informe o nome do cargo, com até 60 caracteres." |
| (campos `severities` / `alertTypes`) | "Escolha pelo menos um valor válido." |
| `role_name_in_use` | "Já existe um cargo com este nome nesta empresa." |
| `immutable_role` | "Este cargo é padrão e não pode ser alterado." |
| `members_from_access_role` | "Os membros deste cargo vêm do papel de acesso." |
| `role_members_limit` | "O cargo já tem 200 membros." |

Valores válidos (C3):

- `severities`: `ERROR`, `WARNING`, `INFO`;
- `alertTypes`: `OCCURRENCE`, `CRITICAL_STATE`, `SYSTEM_ALERT`.

### `GET /api/company/members/{userId}/notification-roles`

Exige `NOTIFICATION_ROLES_MANAGE`.

```json
200
{"userId": "user_bruno", "roles": [{"id": "...", "name": "Gestor"}]}
```

Inclui Dono ou Administrador quando o alvo tem esse papel de acesso.

---

## 4. Mudança em rota existente (LLT-168)

### `POST /api/company/members/{userId}/deactivation`

Quando o alvo é o Dono, a rota responde 403 `{"message":"Não é possível desativar o Dono da empresa.","code":"owner_protected"}`. Essa checagem acontece antes da regra de último administrador. As outras respostas não mudam.

## Para o módulo Notificação (leitura)

O módulo Notificação lê estes dados:

- **Cargos:** coleção `NotificationRole`, filtrando `tenantId` e `deleted: false`.
- **Dono e Administrador:** recebem todas as gravidades e todos os tipos de todos os produtos (ADR_137).
- **Demais cargos:** um membro recebe o alerta quando está em `memberUserIds` de um cargo que tem a gravidade e o tipo do alerta, e tem `AcessoProduto` ao produto do alerta (ADR_136).
- **Pessoas desativadas:** pular quem tem `CompanyMember.status = deactivated`.
- **Fallback:** gravidade ou tipo sem nenhum cargo ativo vai para o Dono e os Administradores (ADR_138).
