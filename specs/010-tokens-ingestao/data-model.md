# Modelo de dados

## product_tokens (contrato do Collector)

Os campos marcados com **C** são lidos pelo Collector (`MongoProductTokenStore`) e não podem mudar de nome ou tipo.

| Campo | Tipo | C | Regras |
| --- | --- | --- | --- |
| _id | string (UUID) | C | identificador do token |
| productId | string | C | produto dono do token |
| tokenHash | string | C | SHA-256 hex minúsculo dos bytes UTF-8 do token; índice único |
| status | `ACTIVE` \| `REVOKED` | C | `ACTIVE` sem `revokedAt`; `REVOKED` com `revokedAt` |
| createdAt | data | C | momento da geração |
| revokedAt | data ou ausente | C | preenchido na revogação |
| expiresAt | data ou ausente | C | preenchido na rotação (fim da transição) |
| tenantId | string | | empresa do produto: id interno do Tenant (LLT-163), não o id da organização na Clerk |
| name | string | | 1 a 100 caracteres, sem espaços nas pontas |
| createdBy | string | | usuário da Clerk que gerou |
| revokedBy | string ou ausente | | usuário que revogou |
| replacedBy | string ou ausente | | id do token novo, na rotação |
| version | long | | controle de concorrência |

Índices: `product_token_hash` (único, `tokenHash`) e `product_token_tenant_product` (`tenantId`, `productId`, `createdAt`).

Validade (igual ao Collector): `status = ACTIVE` e `revokedAt` ausente e (`expiresAt` ausente ou futuro).

Situação mostrada na API:

| Situação | Condição |
| --- | --- |
| `ACTIVE` | válido, sem `expiresAt` |
| `EXPIRING` | válido, com `expiresAt` futuro (em transição) |
| `EXPIRED` | `ACTIVE` com `expiresAt` no passado |
| `REVOKED` | `status = REVOKED` |

## ProductTokenAudit (nova)

| Campo | Tipo | Regras |
| --- | --- | --- |
| _id | string (UUID) | |
| tenantId | string | |
| productId | string | |
| tokenId | string | token afetado |
| action | `CREATED` \| `REVOKED` \| `ROTATED` | |
| actorUserId | string | usuário da Clerk |
| at | data | momento da operação |
| relatedTokenId | string ou ausente | na rotação: o token novo |
| transitionEndsAt | data ou ausente | na rotação: fim da transição |

Índice `token_audit_tenant_product` (`tenantId`, `productId`, `at`). Nunca contém valor nem hash do token.

## Transições do token

```text
(novo)  --gerar-->      ACTIVE
ACTIVE  --revogar-->    REVOKED (revokedAt, revokedBy)
ACTIVE  --rotacionar--> EXPIRING (expiresAt = agora + transição, replacedBy) + token novo ACTIVE
EXPIRING --tempo-->     EXPIRED (sem gravação; calculado)
EXPIRING/EXPIRED --revogar--> REVOKED
REVOKED --revogar-->    REVOKED (nada muda, sem auditoria)
```

Datas gravadas em milissegundos.
