# Contratos: Criar o Tenant na confirmação de pagamento

Erros de negócio usam o corpo do `ApiError`: `{"message": "...", "code": "..."}`. 401 e o 403 de segurança continuam com os corpos básicos da LLT-163.

## Acesso

| Rotas | Quem passa |
|---|---|
| `/api/billing/**` | token válido, organização ativa e papel conhecido, com ou sem Tenant (Tenant suspenso: 403 de segurança) |
| demais `/api/**` | como hoje; sem Tenant → **403 `subscription_required`, sem revogar a sessão** |
| `POST /webhooks/asaas` | público; autenticado pelo header `asaas-access-token` |
| `/dev/fake-asaas/**` | público, **só no perfil local**; fora dele, 404 |

```json
403 {"message":"Sua empresa ainda não tem um plano ativo. Contrate um plano para continuar.","code":"subscription_required"}
```

## GET /api/billing/plans

200:
```json
[{"plan":"FREE","name":"Free","price":0.00,"currency":"BRL"},
 {"plan":"STARTER","name":"Starter","price":49.90,"currency":"BRL"}]
```

## POST /api/billing/checkout

Corpo: `{"plan":"STARTER","companyName":"Empresa Exemplo"}`

| Status | Quando | Corpo |
|---|---|---|
| 201 | pedido criado | `{"orderId":"…","plan":"STARTER","status":"PENDING","paymentUrl":"…"}` |
| 400 | plano fora do catálogo | `code: invalid_plan` |
| 400 | nome da empresa inválido | `fieldErrors.companyName` |
| 403 | não é administrador | `code: not_admin` |
| 409 | organização já tem Tenant | `code: already_subscribed` |
| 503 | sem gateway disponível (fora do local, nesta etapa) | `code: payment_unavailable` |

Efeito: cancela o pedido pendente anterior da organização.

## GET /api/billing/order

Pedido mais recente da organização ativa.

| Status | Corpo |
|---|---|
| 200 | `{"orderId":"…","plan":"STARTER","status":"PENDING"|"PAID"|"CANCELED","paymentUrl":"…","subscriptionActive":false}` |
| 404 | `code: no_order` |

`subscriptionActive` é `true` quando a organização já tem Tenant ativo (o web para de esperar).

## POST /webhooks/asaas

Header `asaas-access-token`. Corpo no formato do Asaas (research R1):

```json
{"id":"evt_…","event":"PAYMENT_RECEIVED","dateCreated":"2026-11-18 10:00:00",
 "payment":{"object":"payment","id":"pay_…","customer":"cus_…","value":49.9,
            "status":"RECEIVED","billingType":"PIX","externalReference":"<orderId>"}}
```

| Status | Quando |
|---|---|
| 200 `{"received":true}` | processado, repetido, ignorado ou pedido desconhecido (registrado `FAILED`, sem reenvio útil) |
| 400 `{"error":"invalid_webhook"}` | corpo ilegível ou sem `id`/`event` |
| 401 `{"error":"invalid_token"}` | token ausente ou errado |
| 409 `{"error":"in_progress"}` | o mesmo evento está sendo processado agora |
| 500 `{"error":"processing_failed"}` | falha na ativação (registrado `FAILED`; o Asaas reenvia) |

## Simulador (perfil local)

- `GET /dev/fake-asaas/checkouts/{chargeId}` → página HTML com empresa, plano, valor e o botão "Simular pagamento"; 404 se a cobrança não existir.
- `POST /dev/fake-asaas/checkouts/{chargeId}/pay` → envia um `PAYMENT_RECEIVED` ao `lhamalog.billing.fake.webhook-url` com o token e mostra o resultado, com link para `lhamalog.billing.return-url`.

## Configuração

| Propriedade | Variável | Padrão |
|---|---|---|
| `lhamalog.billing.asaas.webhook-token` | `ASAAS_WEBHOOK_TOKEN` | local: aleatório na subida; prod: obrigatória, ≥ 32 caracteres |
| `lhamalog.billing.return-url` | `BILLING_RETURN_URL` | `http://localhost:5173/empresa` |
| `lhamalog.billing.fake.checkout-base-url` | `BILLING_FAKE_CHECKOUT_BASE_URL` | `http://localhost:8080` |
| `lhamalog.billing.fake.webhook-url` | `BILLING_FAKE_WEBHOOK_URL` | `http://localhost:${server.port}/webhooks/asaas` |
| `lhamalog.billing.plans.<tier>.price` | — | valores provisórios (research R10) |
