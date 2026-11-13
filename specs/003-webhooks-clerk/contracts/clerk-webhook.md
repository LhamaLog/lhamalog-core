# Contrato: `POST /webhooks/clerk` (LLT-164)

## Requisição (enviada pela Clerk via Svix)

| Header | Conteúdo |
|---|---|
| `svix-id` | id único da mensagem (igual nos reenvios) |
| `svix-timestamp` | segundos Unix do envio |
| `svix-signature` | `v1,<base64>` (uma ou mais, separadas por espaço) |

Corpo (JSON, verificado byte a byte):

```json
{ "type": "organizationMembership.created", "object": "event", "timestamp": 1762470000000, "instance_id": "ins_exemplo",
  "data": { "id": "orgmem_exemplo", "role": "org:admin", "updated_at": 1762470000000,
            "organization": { "id": "org_exemplo" },
            "public_user_data": { "user_id": "user_exemplo", "first_name": "Ana", "last_name": "Silva", "identifier": "ana@exemplo.com" } } }
```

Campos lidos por tipo:

| Tipo | Campos |
|---|---|
| `user.created`, `user.updated` | `data.id`, `data.first_name`, `data.last_name`, `data.primary_email_address_id`, `data.email_addresses[].id/email_address`, `data.updated_at` |
| `user.deleted` | `data.id`, envelope `timestamp` |
| `organizationMembership.created/updated` | `data.id`, `data.role`, `data.organization.id`, `data.public_user_data.user_id/first_name/last_name/identifier`, `data.updated_at` |
| `organizationMembership.deleted` | `data.organization.id`, `data.public_user_data.user_id`, `data.role`, envelope `timestamp` |

## Respostas

| Status | Corpo | Quando |
|---|---|---|
| 200 | `{"status":"ok"}` | processado, duplicado, ignorado ou tipo desconhecido |
| 400 | `{"error":"invalid_webhook"}` | assinatura, headers ou timestamp inválidos; segredo não configurado; JSON inválido |
| 500 | `{"error":"processing_failed"}` | falha ao gravar (o Svix reenvia) |
