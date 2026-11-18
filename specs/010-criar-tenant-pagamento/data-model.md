# Data Model: Criar o Tenant na confirmação de pagamento

Decisões em [research.md](research.md). Coleções no MongoDB do Core; nomes de campos em inglês (princípio IV).

## Plan (configuração, sem coleção)

| Campo | Tipo | Regra |
|---|---|---|
| tier | `PlanTier` | FREE, STARTER, PRO, BUSINESS, ENTERPRISE |
| name | texto | nome exibido, derivado do tier |
| price | decimal (BRL, 2 casas) | `lhamalog.billing.plans.<tier>.price`; ≥ 0 |

Só os tiers com preço configurado aparecem no catálogo.

## SubscriptionOrder (`subscription_order`)

| Campo | Tipo | Regra |
|---|---|---|
| _id `orderId` | UUID texto | vai no `externalReference` da cobrança |
| clerkOrgId | texto | organização ativa no pedido |
| clerkUserId | texto | quem pediu; vira DONO |
| companyName | texto | 2 a 120 caracteres, sem espaços nas pontas; vira o nome do Tenant |
| planTier | `PlanTier` | do catálogo |
| price | decimal | preço do plano no momento do pedido |
| status | `PENDING` · `PAID` · `CANCELED` | ver transições |
| gatewayChargeId | texto | id da cobrança no gateway (`pay_...`) |
| paymentUrl | texto | endereço da página de pagamento |
| createdAt, paidAt, canceledAt | instante | |

Índices: `clerkOrgId` + `createdAt` (desc), `gatewayChargeId`.

Transições: `PENDING → CANCELED` (pedido novo da mesma organização); `PENDING → PAID` e `CANCELED → PAID` (confirmação). `PAID` é final.

## GatewayEvent (`gateway_event`, ADR_125)

| Campo | Tipo | Regra |
|---|---|---|
| _id `eventId` | texto | `id` do evento do Asaas |
| gateway | texto | `ASAAS` |
| type | texto | `event` do Asaas |
| orderId | texto, opcional | `payment.externalReference` |
| chargeId | texto, opcional | `payment.id` |
| status | `RECEIVED` · `EXECUTED` · `IGNORED` · `FAILED` | |
| reason | texto, opcional | `order_already_paid`, `tenant_already_active`, `event_not_handled`, `unknown_order` ou o nome do erro |
| tenantId | texto, opcional | Tenant afetado |
| attempts | inteiro | +1 a cada processamento |
| receivedAt, processedAt | instante | |

Transições: `RECEIVED → EXECUTED | IGNORED | FAILED`; `FAILED → EXECUTED | IGNORED | FAILED` (reenvio). `EXECUTED` e `IGNORED` são finais. Sem TTL.

## Tenant (`tenant`, existente)

Campo novo: `originOrderId` (texto, opcional), gravado só na criação pela ativação, pela porta nova `TenantProvisioning`. O seed local continua sem ele. O domínio `Tenant` e a porta `TenantRepository` não mudam.

## TenantSubscription (`tenant_subscription`, ADR_122)

| Campo | Tipo | Regra |
|---|---|---|
| _id `tenantId` | texto | um por Tenant |
| planTier | `PlanTier` | do pedido |
| status | `SubscriptionStatus` | `ACTIVE` nesta etapa |
| orderId | texto | pedido que a criou |
| startedAt | instante | data da ativação |

## GatewayNotification (sem coleção)

Evento autêntico já lido do corpo do gateway: `eventId`, `gateway`, `type`, `orderId` (`externalReference`), `chargeId`, `value`. `confirmsPayment()` é verdadeiro para `PAYMENT_CONFIRMED` e `PAYMENT_RECEIVED`.

## Member (`member`, existente)

Novos métodos de domínio: `Member.owner(memberId, tenantId, clerkUserId)` (papel DONO, `membershipVersion = 0`, sem nome e e-mail) e `promotedToOwner()`. Coleção e índices sem mudança.

## Sessão sem Tenant (sem coleção)

`OrganizationSession(clerkUserId, clerkOrgId, role, tenantId opcional)`, lida pela porta `CurrentOrganizationSession` nas rotas de billing.
