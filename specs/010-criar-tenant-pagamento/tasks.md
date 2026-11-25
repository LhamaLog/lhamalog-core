# Tasks: Criar o Tenant na confirmação de pagamento (Asaas simulado)

**Input**: Design documents from `/specs/010-criar-tenant-pagamento/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: Incluídos — a constituição (III) exige cobertura do comportamento novo antes do merge.

**Organization**: Tasks grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)

Base: `src/main/java/br/com/lhamalog/core/` (abreviado `main/`) e `src/test/java/br/com/lhamalog/core/` (abreviado `test/`).

**Restrição de toda a feature**: não alterar a `PlanSubscription` nem o módulo `usage` (só reusar `PlanTier` e `SubscriptionStatus`); não alterar o seed local da spec 008; nenhum segredo em código, teste ou properties versionado.

---

## Phase 1: Setup

- [X] T001 Em `src/main/resources/application.properties`, adicionar `lhamalog.billing.return-url`, `lhamalog.billing.plans.<tier>.price` (valores provisórios do research R10) e o comentário de cada um; em `application-local.properties`, `lhamalog.billing.asaas.webhook-token=${ASAAS_WEBHOOK_TOKEN:}`, `lhamalog.billing.fake.checkout-base-url` e `lhamalog.billing.fake.webhook-url`; em `application-prod.properties`, `lhamalog.billing.asaas.webhook-token=${ASAAS_WEBHOOK_TOKEN}` na lista de obrigatórias
- [X] T002 [P] Em `compose.yaml`, `compose.prod.yaml` e `.env.example`, repassar `ASAAS_WEBHOOK_TOKEN` (opcional no local, obrigatório no prod) e `BILLING_RETURN_URL`, sem valores reais

---

## Phase 2: Foundational

**Purpose**: domínio, portas, persistência e configuração usados por todas as histórias.

- [X] T003 [P] Criar `main/billing/domain/Plan.java` (record `tier`, `name`, `price`) e `main/billing/application/PlanCatalog.java` (lista ordenada por tier, `find(String)` sem diferenciar maiúsculas)
- [X] T004 [P] Criar `main/billing/domain/SubscriptionOrder.java` com `OrderStatus` (`PENDING`, `PAID`, `CANCELED`), `pending(...)`, `withCharge(...)`, `paid(Instant)`
- [X] T005 [P] Criar `main/billing/domain/GatewayEvent.java` com `GatewayEventStatus` (`RECEIVED`, `EXECUTED`, `IGNORED`, `FAILED`), `main/billing/domain/GatewayNotification.java` (eventId, gateway, type, orderId, chargeId, value; `confirmsPayment()`) e `main/billing/domain/GatewayCharge.java`
- [X] T006 [P] Criar `main/billing/domain/TenantSubscription.java` (tenantId, planTier, status, orderId, startedAt)
- [X] T007 Criar as portas em `main/billing/application/port/out/`: `PaymentGateway` (+ `GatewayCharge`), `SubscriptionOrderRepository`, `GatewayEventRepository`, `TenantSubscriptionRepository`
- [X] T008 Criar os adaptadores Mongo em `main/billing/adapter/out/mongo/` (documentos `subscription_order`, `gateway_event`, `tenant_subscription`; índices `clerkOrgId`+`createdAt` e `gatewayChargeId` criados no `afterPropertiesSet`, como no `MongoTenantRepository`)
- [X] T009 Em `main/tenant/adapter/out/mongo/TenantDocument.java`, campo `originOrderId` (construtor de 4 argumentos mantido); porta nova `main/tenant/application/port/out/TenantProvisioning.java` com `provisionForOrder(clerkOrgId, name, orderId)` devolvendo `ProvisionedTenant(tenantId, originOrderId, created)`, para não mudar o `TenantRepository`; implementar em `MongoTenantRepository.java` com `findAndModify` + upsert + `setOnInsert` (e nova leitura em `DuplicateKeyException`)
- [X] T010 [P] Em `main/member/domain/Member.java`, `owner(memberId, tenantId, clerkUserId)` e `promotedToOwner()`
- [X] T011 Criar `main/config/BillingProperties.java` (`@ConfigurationProperties("lhamalog.billing")`, `toString` sem o token) e `main/billing/adapter/in/web/AsaasWebhookCredential.java` (token configurado ou aleatório no local; prod exige ≥ 32 caracteres; `matches` em tempo constante)
- [X] T012 Criar `main/config/BillingConfig.java` com os beans de `PlanCatalog`, serviços, credencial e `UnavailablePaymentGateway` (`main/billing/adapter/out/UnavailablePaymentGateway.java`, 503 `payment_unavailable`) quando não houver outro `PaymentGateway`
- [X] T013 [P] Testes de unidade em `test/billing/BillingUnitTests.java`: catálogo; credencial (local aleatório, prod vazio falha, prod curto falha, comparação); `Member.owner` e `promotedToOwner`
- [X] T014 Teste de integração `test/tenant/TenantProvisioningIntegrationTests.java`: cria com `originOrderId`; segunda chamada devolve o mesmo Tenant com `created=false`; Tenant do seed devolve `originOrderId` nulo

**Checkpoint**: domínio e persistência prontos; `mvnw.cmd clean verify` verde.

---

## Phase 3: User Story 2 - Organização sem Tenant é levada à contratação (P1)

**Goal**: ninguém é deslogado por falta de Tenant; `/api/billing/**` responde para quem ainda não tem Tenant.

**Independent Test**: sessão sem Tenant recebe 403 `subscription_required` sem revogação nas rotas comuns e 200 em `/api/billing/plans`.

- [X] T015 [US2] Testes em `test/billing/BillingAccessIntegrationTests.java`: sem Tenant, rota comum → 403 `subscription_required`, sem revogação (`RecordingSessionRevocation`); `GET /api/billing/plans` → 200; sem organização, papel desconhecido e Tenant suspenso → 403 básico e revogação; sem token → 401 em `/api/billing/plans`. Em `ClerkTokenAuthenticationTests.java`, o teste da organização sem Tenant passa a esperar `subscription_required` sem revogação, e o de falha da revogação usa o Tenant suspenso
- [X] T016 [P] [US2] Criar `main/identity/domain/OrganizationSession.java`, `main/identity/application/port/CurrentOrganizationSession.java` e `main/identity/adapter/in/security/SecurityContextCurrentOrganizationSession.java` (aceita `Granted` e `Denied(TENANT_NOT_FOUND)`)
- [X] T017 [P] [US2] Criar `main/identity/adapter/in/security/BillingAccessAuthorizationManager.java`
- [X] T018 [US2] Em `main/identity/adapter/in/security/SessionRevokingAccessDeniedHandler.java`, tratar `TENANT_NOT_FOUND`: 403 `{"message","code":"subscription_required"}`, sem revogar, log `event=access.denied.subscription_required`; os logs antigos do arquivo passam ao padrão (`event=access.denied.revoked`, `event=access.revocation.failed`)
- [X] T019 [US2] Em `main/config/SecurityConfig.java`, `/api/billing/**` com o novo manager, `POST /webhooks/asaas` e `/dev/fake-asaas/**` públicos; registrar o bean `CurrentOrganizationSession`
- [X] T020 [US2] Criar `main/billing/adapter/in/web/BillingController.java` com `GET /api/billing/plans`

**Checkpoint**: US2 testável sozinha.

---

## Phase 4: User Story 1 - Contratar um plano e entrar depois da confirmação (P1)

**Goal**: pedido → cobrança no simulador → confirmação → Tenant, Assinatura e DONO.

**Independent Test**: organização nova contrata pelo simulador e acessa uma rota comum.

- [X] T021 [US1] Testes em `test/billing/CheckoutIntegrationTests.java`: 201 com `paymentUrl` do simulador; `invalid_plan`; nome inválido (400 `fieldErrors`); `not_admin`; `already_subscribed`; pedido novo cancela o anterior; `GET /api/billing/order` (pendente, `no_order`)
- [X] T022 [US1] Testes em `test/billing/AsaasWebhookIntegrationTests.java`: `PAYMENT_RECEIVED` válido cria Tenant ativo com `originOrderId`, `tenant_subscription`, Membro DONO e pedido `PAID`; depois disso a rota comum responde 200 e `/api/billing/order` mostra `subscriptionActive=true`; `PAYMENT_CONFIRMED` também ativa; plano FREE segue o mesmo caminho
- [X] T023 [US1] Criar `main/billing/application/CheckoutService.java` (validação, cancelamento dos pendentes, cobrança, pedido mais recente) e completar `BillingController.java` com `POST /api/billing/checkout` e `GET /api/billing/order`
- [X] T024 [US1] Criar `main/billing/adapter/in/web/AsaasWebhookParser.java` (lê o formato do research R1, ignora campos desconhecidos, `InvalidWebhookPayloadException` sem `id`/`event`)
- [X] T025 [US1] Criar `main/billing/application/SubscriptionActivationService.java` (passos do research R5, logs do R13)
- [X] T026 [US1] Criar `main/billing/application/GatewayEventProcessor.java` (registro e resultado do evento, research R4) e `main/billing/adapter/in/web/AsaasWebhookController.java` (`POST /webhooks/asaas`, respostas do contrato)
- [X] T027 [US1] Criar o simulador em `main/billing/adapter/out/fake/`: `FakeAsaasPaymentGateway.java`, `FakeAsaasCheckoutController.java` (página GET e POST `/pay`, HTML escapado) e `AsaasWebhookSender.java` (`RestClient`, header `asaas-access-token`), com `@Profile("local")` em `BillingConfig`
- [X] T028 [P] [US1] Testes: parser e sender (`MockRestServiceServer`: URL, header e corpo; webhook inalcançável) em `test/billing/BillingUnitTests.java`; `test/billing/FakeAsaasCheckoutIntegrationTests.java` (página mostra plano e valor; nome escapado; 404 de cobrança inexistente; POST `/pay` entrega ao sender um evento que, aplicado ao endpoint, ativa; recusa do webhook aparece na página). O sender dos testes é o `test/support/RecordingAsaasWebhookSender.java`, registrado no `ClerkTestConfiguration`

**Checkpoint**: MVP — uma organização nova entra pelo simulador.

---

## Phase 5: User Story 3 - Confirmações repetidas, falsas ou com falha (P2)

**Goal**: no máximo um Tenant por organização e nada criado sem confirmação autêntica.

**Independent Test**: reenvio, token errado, pedido inexistente e falha simulada.

- [X] T029 [US3] Testes em `test/billing/AsaasWebhookIdempotencyIntegrationTests.java`: mesmo evento duas vezes → um Tenant, uma Assinatura, um DONO, `attempts=1`; token ausente/errado → 401 e nada criado; corpo inválido → 400; pedido desconhecido → 200 e evento `FAILED unknown_order`; `PAYMENT_CREATED` → `IGNORED event_not_handled`; `CONFIRMED` + `RECEIVED` do mesmo pedido → segundo `IGNORED order_already_paid`; dois pedidos da mesma organização pagos → um Tenant e o segundo `IGNORED tenant_already_active`; organização do seed → `tenant_already_active`; valor diferente do preço → ativa com o plano do pedido
- [X] T030 [US3] Teste de unidade `test/billing/SubscriptionActivationServiceTest.java` com repositórios em memória: falha depois de criar o Tenant → evento `FAILED`, resposta 500; reenvio retoma e termina com um Tenant, uma Assinatura e um DONO; membro já existente é promovido a DONO
- [X] T031 [US3] Ajustar `GatewayEventProcessor` e `SubscriptionActivationService` ao que os testes T029/T030 exigirem (evento em andamento → 409, `attempts`, motivos)

---

## Phase 6: User Story 4 - O simulador só existe no ambiente local (P3)

**Goal**: fora do `local`, sem simulador e com token obrigatório.

**Independent Test**: contexto com perfil `prod`.

- [X] T032 [US4] Teste em `test/billing/BillingProfileTests.java` (contexto de unidade com `ApplicationContextRunner`, sem subir a aplicação): perfil `prod` não registra os beans do simulador e usa `UnavailablePaymentGateway`; sem `ASAAS_WEBHOOK_TOKEN` a configuração falha
- [X] T033 [US4] Teste HTTP no contexto comum: `/dev/fake-asaas/checkouts/x` sem cobrança → 404 (em `FakeAsaasCheckoutIntegrationTests`; o 404 do prod vem da ausência do controller, coberto no T032)

---

## Phase 7: Polish & Cross-Cutting

- [X] T034 [P] README do core: seção "Contratar um plano (Asaas simulado)" com o roteiro curto e as variáveis novas; nota de que o seed `DEV_TENANT_CLERK_ORG_ID` continua valendo
- [X] T035 [P] Conferir todos os logs novos contra o princípio VI (`event=`, sem token, nome da empresa ou e-mail)
- [X] T036 Rodar `.\mvnw.cmd clean verify` com o Docker Desktop aberto e registrar o total de testes — 2026-11-18: BUILD SUCCESS, 360 testes, 0 falhas, 0 pulados
- [X] T037 Roteiro manual M1–M11 do `quickstart.md` com a Clerk real — 2026-11-18: M1–M9 passaram com a conta admin `+clerk_test` e a organização "Teste C sem Tenant"; M10 e M11 cobertos só pelos testes automáticos (`CheckoutIntegrationTests` e `BillingProfileTests`). Resultado em `quickstart.md`, seção "Resultados executados"

---

## Dependencies & Execution Order

- Phase 1 → Phase 2 → US2 (Phase 3) → US1 (Phase 4) → US3 (Phase 5) → US4 (Phase 6) → Polish.
- US2 vem antes da US1 porque o checkout depende do acesso sem Tenant.
- US3 e US4 dependem do endpoint e do simulador da US1.

## Parallel Opportunities

- Phase 2: T003, T004, T005, T006 e T010 em paralelo; T013 depois deles.
- US2: T016 e T017 em paralelo.
- US1: T028 em paralelo com T025/T026 depois do T024.

## Implementation Strategy

1. MVP = Phases 1–4: uma organização nova entra pelo simulador sem editar o `.env`.
2. Phase 5 fecha as garantias de idempotência e segurança do webhook.
3. Phase 6 e Polish antes do PR.
