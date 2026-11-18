# Data Model: Webhooks da Clerk (LLT-164)

## Membro (`member`)

| Campo | Tipo | Regra |
|---|---|---|
| `_id` (memberId) | string (UUID) | gerado pelo Core (referência de pessoa, ADR_159) |
| `tenantId` | string | Tenant da organização; **índice único** `member_tenant_user` em (`tenantId`, `clerkUserId`) |
| `clerkUserId` | string | `user_...`; índice `member_clerk_user` |
| `clerkMembershipId` | string | `orgmem_...` (pode ser nulo em Membro criado por remoção antecipada) |
| `role` | string | `DONO`, `ADMIN` ou `MEMBRO` |
| `name` | string | nome de exibição (primeiro + último nome) |
| `email` | string | e-mail principal |
| `membershipVersion` | long (ms) | versão do último evento de vínculo aplicado |
| `userVersion` | long (ms) | versão do último `user.*` aplicado (0 = nenhum) |
| `removedAt` | date | vínculo removido da organização (pode ser reativado por vínculo mais novo) |
| `deletedAt` | date | usuário apagado na Clerk (definitivo) |

## Webhook processado (`clerk_webhook_event`)

| Campo | Tipo | Regra |
|---|---|---|
| `_id` | string | `svix-id` |
| `type` | string | tipo do evento |
| `tenantId` | string ou nulo | tenant do vínculo; nulo para eventos de usuário e desconhecidos |
| `processedAt` | date | **índice TTL** `clerk_webhook_event_ttl`, 7 dias |

## Usuário apagado (`deleted_clerk_user`)

| Campo | Tipo | Regra |
|---|---|---|
| `_id` | string | `clerkUserId` |
| `tenantId` | nulo | o usuário não pertence a um tenant só (FR-011) |
| `deletedAt` | date | horário do `user.deleted` |

## Regras de aplicação

| Evento | Versão | Efeito |
|---|---|---|
| `organizationMembership.created/updated` | `data.updated_at` | cria ou atualiza; reativa se removido; papel por ADR_123, DONO mantido |
| `organizationMembership.deleted` | envelope `timestamp` | marca `removedAt`; cria Membro já removido se ainda não existe |
| `user.created/updated` | `data.updated_at` | atualiza nome/e-mail de todos os Membros do usuário |
| `user.deleted` | envelope `timestamp` | registra em `deleted_clerk_user` e marca `deletedAt` em todos os Membros |

Membro com `deletedAt` e usuário em `deleted_clerk_user` nunca são criados nem alterados por eventos posteriores. Versão menor ou igual à aplicada é ignorada.
