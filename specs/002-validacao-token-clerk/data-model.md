# Data Model: Validação do session token da Clerk (LLT-163)

## Tenant (coleção `tenant`, MongoDB)

Empresa cliente, espelho da Organization da Clerk (ADR_121/122). Nesta feature é só lido. A criação fica com o fluxo de compra (feature futura).

| Campo | Tipo | Regra |
|---|---|---|
| `_id` (tenantId) | string (UUID) | identidade do tenant; é a chave de isolamento de todo o Core |
| `clerkOrgId` | string | id da Organization na Clerk (`org_...`); **índice único** `tenant_clerk_org_id` |
| `name` | string | nome de exibição |
| `status` | string | `ACTIVE` ou `SUSPENDED`; só `ACTIVE` dá acesso |

Exemplo (fictício):

```json
{ "_id": "7f1c2a4e-0000-4000-8000-000000000001", "clerkOrgId": "org_exemplo", "name": "Empresa Exemplo", "status": "ACTIVE" }
```

## RequestContext (não persistido)

| Campo | Origem |
|---|---|
| `clerkUserId` | claim `sub` |
| `sessionId` | claim `sid` |
| `clerkOrgId` | claim `o.id` |
| `role` | claim `o.rol`, normalizada para `OrganizationRole` (`ADMIN` \| `MEMBER`) |
| `tenantId` | `Tenant._id` resolvido por `clerkOrgId` |

## ClerkSessionClaims (não persistido)

Claims já validadas pelo decoder, extraídas do JWT para tipos simples: `subject`, `sessionId`, `organizationId` (opcional), `organizationRole` (opcional, texto bruto).

## AccessDecision

- `Granted(RequestContext)`
- `Denied(reason)` com `reason` ∈ `NO_ACTIVE_ORGANIZATION`, `UNKNOWN_ROLE`, `TENANT_NOT_FOUND`, `TENANT_INACTIVE`. O motivo vai só para log (DEBUG/INFO), nunca para a resposta.
