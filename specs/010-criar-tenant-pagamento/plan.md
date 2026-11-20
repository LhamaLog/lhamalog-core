# Implementation Plan: Criar o Tenant na confirmação de pagamento (Asaas simulado)

**Branch**: `LLT-289-criar-o-tenant-na-confirmacao-de-pagamento-asaas-simulado` | **Date**: 2026-11-18 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/010-criar-tenant-pagamento/spec.md`

## Summary

O Tenant passa a nascer na compra (ADR_121). Um administrador de organização sem Tenant vê os planos e pede a contratação; o Core grava um pedido e pede ao gateway uma cobrança, com o id do pedido no `externalReference`. Quando o gateway confirma o pagamento pelo webhook, o Core cria o Tenant ativo, a Assinatura do Tenant e o Membro DONO, de forma idempotente. O gateway fica atrás da porta `PaymentGateway`: nesta feature, um simulador do Asaas só no perfil local, que envia ao endpoint real `/webhooks/asaas` um evento no formato do Asaas. Na segurança, `/api/billing/**` aceita sessões sem Tenant, e as outras rotas respondem 403 `subscription_required` sem revogar a sessão.

## Technical Context

**Language/Version**: Java 25

**Primary Dependencies**: Spring Boot 4.1.1 (webmvc, security, oauth2 resource server, data-mongodb, validation); `RestClient` do Spring para o envio do simulador. Nenhuma dependência nova.

**Storage**: MongoDB (ADR_116). Coleções novas: `subscription_order`, `gateway_event`, `tenant_subscription`. Coleção `tenant` ganha `originOrderId`.

**Testing**: JUnit 5, MockMvc e Testcontainers (`@CoreIntegrationTest`, Mongo com tag fixa), `TestJwks` para tokens; testes de unidade para parser, credencial, ativação e envio do simulador (`MockRestServiceServer`).

**Target Platform**: serviço Linux em contêiner; desenvolvimento no Windows com Docker Desktop.

**Project Type**: web-service (API REST do Core).

**Performance Goals**: a confirmação é processada em uma chamada síncrona (poucas escritas), bem abaixo do tempo-limite do Asaas.

**Constraints**: Mongo sem transação (standalone); idempotência por `_id` e índices únicos; nenhum segredo no repositório; simulador só no perfil `local`; padrão de logs v1.

**Scale/Scope**: uma ativação por empresa; volume de eventos baixo. Fora do escopo: cobrança real, ciclo da assinatura, web.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio | Como a feature cumpre | Status |
|---|---|---|
| I. Spec Kit | spec, plan, tasks e implement nesta ordem, na pasta `specs/010-criar-tenant-pagamento` | OK |
| II. IA com revisão | decisões em research.md (R1–R14); o desenvolvedor revisa antes do merge | OK |
| III. Testes antes do merge | testes de integração e unidade para cada história; `mvnw.cmd clean verify` verde | OK |
| IV. Inglês no código | pacote `br.com.lhamalog.core.billing`, classes e campos em inglês; docs em português | OK |
| V. Segredos | token do webhook só por variável; local gera um aleatório; log sem token | OK |
| VI. Logs | eventos `billing.*` com `event=` (research R13) | OK |
| Stack | Java 25, Spring Boot 4.1.1, Maven Wrapper, MongoDB | OK |
| Fluxo (branch `feature/...`) | branch no formato do Jira, `LLT-289-...`, como as anteriores (LLT-163, LLT-178) | Desvio já praticado pelo time, registrado em Complexity Tracking |

Re-check depois do Phase 1: sem mudanças; o desenho não adiciona dependência nem projeto.

## Project Structure

### Documentation (this feature)

```text
specs/010-criar-tenant-pagamento/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/api.md
├── checklists/requirements.md
└── tasks.md
```

### Source Code (repository root)

```text
src/main/java/br/com/lhamalog/core/
├── billing/                                   # novo módulo (hexagonal leve)
│   ├── domain/
│   │   ├── Plan.java                          # tier, name, price
│   │   ├── SubscriptionOrder.java             # + OrderStatus
│   │   ├── GatewayEvent.java                  # + GatewayEventStatus
│   │   ├── GatewayNotification.java           # evento já lido do gateway
│   │   ├── GatewayCharge.java                 # cobrança criada no gateway
│   │   └── TenantSubscription.java
│   ├── application/
│   │   ├── PlanCatalog.java
│   │   ├── CheckoutService.java               # planos, pedido, pedido mais recente
│   │   ├── SubscriptionActivationService.java # Tenant + Assinatura + DONO + pedido pago
│   │   ├── GatewayEventProcessor.java         # idempotência e resultado do evento
│   │   └── port/out/
│   │       ├── PaymentGateway.java            # createCharge(order) -> GatewayCharge
│   │       ├── SubscriptionOrderRepository.java
│   │       ├── GatewayEventRepository.java
│   │       └── TenantSubscriptionRepository.java
│   └── adapter/
│       ├── in/web/
│       │   ├── BillingController.java         # /api/billing/plans, /checkout, /order
│       │   ├── AsaasWebhookController.java    # POST /webhooks/asaas
│       │   ├── AsaasWebhookParser.java
│       │   └── AsaasWebhookCredential.java
│       └── out/
│           ├── mongo/                         # documentos e repositórios Mongo
│           ├── UnavailablePaymentGateway.java # fora do local
│           └── fake/                          # só @Profile("local")
│               ├── FakeAsaasPaymentGateway.java
│               ├── FakeAsaasCheckoutController.java
│               ├── FakeAsaasEvents.java           # evento no formato do Asaas
│               └── AsaasWebhookSender.java
├── config/
│   ├── BillingConfig.java                     # beans, perfis
│   ├── BillingProperties.java
│   └── SecurityConfig.java                    # + /api/billing/**, /webhooks/asaas, /dev/fake-asaas/**
├── identity/
│   ├── adapter/in/security/
│   │   ├── BillingAccessAuthorizationManager.java   # novo
│   │   ├── SecurityContextCurrentOrganizationSession.java
│   │   └── SessionRevokingAccessDeniedHandler.java  # TENANT_NOT_FOUND sem revogação
│   ├── application/port/CurrentOrganizationSession.java
│   └── domain/OrganizationSession.java
├── member/domain/Member.java                  # + owner(), promotedToOwner()
└── tenant/
    ├── application/port/out/TenantProvisioning.java  # provisionForOrder(...), porta nova
    │                                          # (o TenantRepository fica intacto)
    └── adapter/out/mongo/                     # TenantDocument + originOrderId

src/test/java/br/com/lhamalog/core/billing/     # testes desta feature
```

**Structure Decision**: módulo novo `billing`, no mesmo formato de portas e adaptadores do `tenant` e do `webhook`. Mudanças fora dele ficam na segurança (`identity`, `config`), no `tenant` (criação pela ativação) e no `member` (DONO).

## Fluxo principal

```text
POST /api/billing/checkout ─► CheckoutService ─► cancela PENDING antigos, grava PENDING
                                               └► PaymentGateway.createCharge ─► paymentUrl
navegador ─► /dev/fake-asaas/checkouts/{id} ─► "Simular pagamento"
          ─► AsaasWebhookSender ─HTTP─► POST /webhooks/asaas (asaas-access-token)
AsaasWebhookController ─► credencial ─► parser ─► GatewayEventProcessor
   ─► SubscriptionActivationService: Tenant(upsert) ─► TenantSubscription ─► Membro DONO ─► pedido PAID
```

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Branch `LLT-289-...` em vez de `feature/LLT-289-...` | o time cria os branches pelo botão do Jira (LLT-163, LLT-168, LLT-178 já entraram assim) | renomear só esta branch quebraria o padrão em uso; a emenda da constituição é decisão do time |
