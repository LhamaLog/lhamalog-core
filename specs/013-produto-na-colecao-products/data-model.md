# Data Model: Produto em `products`

**Data**: 27/11/2026 | **Spec**: [spec.md](spec.md)

## Coleção `products` (antes `Produto`)

Mesmos campos das features 001, 002, 006 e 009, mais `productId`:

| Campo | Tipo | Observação |
|---|---|---|
| `_id` | texto (UUID) | id do produto, gerado pelo Core |
| `productId` | texto | **novo**, igual ao `_id`. Contrato com o Coletor. Escrito a cada gravação; não existe no record `Product` |
| `tenantId` | texto | id interno do tenant. Contrato com o Coletor |
| `name`, `normalizedName`, `description`, `category`, `productVersion`, `status`, `createdAt`, `archivedAt`, `context`, `version` | — | sem mudança |

Índices:

| Nome | Definição | Observação |
|---|---|---|
| `product_tenant_name` | `{tenantId, name, _id}` | sem mudança |
| `product_tenant_normalized_name` | `{tenantId, normalizedName}`, único | sem mudança |
| `product_collector_product_id` | `{productId}`, único, parcial (`productId` existe) | **novo** |

## Leitura do Coletor (não muda)

1. `product_tokens` por `tokenHash` (SHA-256 hex minúsculo da chave).
2. `products` por `productId` do token, para pegar o `tenantId`.
