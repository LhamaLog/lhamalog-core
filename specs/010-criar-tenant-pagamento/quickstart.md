# Quickstart: Criar o Tenant na confirmação de pagamento (Asaas simulado)

Contratos em [contracts/api.md](contracts/api.md); decisões em [research.md](research.md).

## Testes automatizados

Com o Docker Desktop aberto, na raiz do core:

```powershell
.\mvnw.cmd clean verify
```

Esperado: todos os testes verdes, incluindo os desta feature (catálogo, checkout, acesso sem Tenant, webhook, idempotência, falha e retomada, simulador).

## Roteiro manual (perfil local)

Pré-requisitos: o Core no ar (`docker compose up -d --build`) e o `.env` da spec 008 configurado. Não precisa de `ASAAS_WEBHOOK_TOKEN`: no local o token é gerado na subida. Use uma organização **nova**, que **não** esteja em `DEV_TENANT_CLERK_ORG_ID`, e o token da sessão de um administrador dela (no navegador: `await window.Clerk.session.getToken()`). Abaixo, `$t` é esse token.

| # | Passo | Esperado |
|---|---|---|
| M1 | `GET /api/profile` (ou outra rota) com `$t` | 403 `subscription_required`; continua logado no web; o log não mostra revogação |
| M2 | `GET /api/billing/plans` | 200 com os planos |
| M3 | `POST /api/billing/checkout` `{"plan":"STARTER","companyName":"Empresa Teste"}` | 201 com `paymentUrl` |
| M4 | Abrir o `paymentUrl` no navegador | página do simulador com empresa, plano e valor |
| M5 | Clicar em "Simular pagamento" | página de sucesso; log `billing.fake.payment_sent` e `billing.activation.executed` |
| M6 | `GET /api/billing/order` | `status: PAID`, `subscriptionActive: true` |
| M7 | Abrir `/empresa` no web | tela carrega, sem logout |
| M8 | Voltar ao `paymentUrl` e clicar de novo | sucesso, mas nada novo: log `billing.webhook.ignored` com `order_already_paid` |
| M9 | `POST /webhooks/asaas` com corpo válido e token errado | 401; log `billing.webhook.rejected` sem o token |
| M10 | Membro (não admin) de outra organização nova faz o M3 | 403 `not_admin`, continua logado |
| M11 | Com o Core em `SPRING_PROFILES_ACTIVE=prod` (e as variáveis de prod), abrir `/dev/fake-asaas/checkouts/x` | 404 |

No Mongo (`lhamalog_core`): um documento em `tenant` com `originOrderId`, um em `tenant_subscription`, um `member` com `role: DONO` e os eventos em `gateway_event`.

## Resultados executados

18/11/2026, Core no Docker (perfil local) com o PR #14, Web da `Development` em `npm run dev`, instância de desenvolvimento da Clerk, conta admin `+clerk_test` (TOTP) e a organização "Teste C sem Tenant".

| # | Resultado | Observado |
|---|---|---|
| M1 | passou | 403 `subscription_required` com mensagem; o Web mostrou "Contrate um plano para continuar" sem logout; log `access.denied.subscription_required`, nenhuma revogação |
| M2 | passou | 200 com FREE, STARTER, PRO, BUSINESS e ENTERPRISE |
| M3 | passou | 201, pedido `PENDING` e `paymentUrl` do simulador |
| M4 | passou | página com empresa, plano STARTER e R$ 49,90 |
| M5 | passou | `billing.fake.payment_sent` (webhook 200) e `billing.activation.executed` |
| M6 | passou | `PAID` e `subscriptionActive: true` |
| M7 | passou | `/empresa` carrega sem recusa |
| M8 | passou | segundo pagamento → evento `IGNORED order_already_paid`; nada duplicado |
| extra | passou | novo checkout com Tenant ativo → 409 `already_subscribed` |
| M9 | passou | token ausente ou errado → 401 `invalid_token`; log sem o token |
| M10 | teste automático | sem login real da conta membro; `CheckoutIntegrationTests.memberWhoIsNotAdministratorCannotBuyAndStaysSignedIn` |
| M11 | teste automático | sem reiniciar em prod; `BillingProfileTests` |

No Mongo: um Tenant `ACTIVE` com `originOrderId`, uma `tenant_subscription` STARTER, o admin como `DONO` e dois eventos em `gateway_event` (`EXECUTED` e `IGNORED`). Depois, o mesmo fluxo foi repetido só com cliques pela tela `/plano` do Web (spec 007, PR #9 do lhamalog-web).
