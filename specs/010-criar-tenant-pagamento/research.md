# Research: Criar o Tenant na confirmação de pagamento (Asaas simulado)

**Feature**: [spec.md](spec.md) | **Plano**: [plan.md](plan.md) | **Data**: 2026-11-18

Cada item segue o formato Decisão / Por quê / Alternativas.

## R1. Formato do webhook do Asaas

- **Decisão**: o endpoint `POST /webhooks/asaas` aceita o formato documentado do Asaas: corpo JSON com `id` (id único do evento, `evt_...`), `event` (tipo, por exemplo `PAYMENT_RECEIVED`), `dateCreated` e o objeto `payment` com `id` (`pay_...`), `customer`, `subscription` (opcional), `value`, `status`, `billingType` e `externalReference`. A autenticação é o header `asaas-access-token`, com o token configurado no webhook do Asaas. Campos desconhecidos são ignorados.
- **Por quê**: é o contrato público do Asaas (docs.asaas.com, "Receba eventos do Asaas no seu endpoint de Webhook" e "Eventos para cobranças", consultadas em 18/11/2026). O simulador envia exatamente esse formato, então o adaptador real não muda o recebimento (FR-012).
- **Alternativas**: um formato próprio "simplificado" para o mock. Rejeitado: o objetivo é que o lado que recebe já seja o definitivo.

## R2. Quais eventos ativam

- **Decisão**: `PAYMENT_CONFIRMED` e `PAYMENT_RECEIVED` ativam. O primeiro que chegar ativa; o segundo encontra o pedido já pago e é registrado como ignorado (`order_already_paid`). Qualquer outro tipo é registrado como ignorado (`event_not_handled`).
- **Por quê**: no cartão o Asaas manda `PAYMENT_CONFIRMED` e, depois da liquidação, `PAYMENT_RECEIVED`; no Pix e no boleto vem só `PAYMENT_RECEIVED`. Esperar o `RECEIVED` no cartão atrasaria o acesso em dias.
- **Alternativas**: só `PAYMENT_RECEIVED`. Rejeitado pelo atraso no cartão. Reembolso e chargeback (que exigiriam suspender) estão fora do escopo.

## R3. Ligação entre a cobrança e o pedido

- **Decisão**: o id do pedido (`SubscriptionOrder.orderId`, UUID) vai no `externalReference` da cobrança. A confirmação é ligada ao pedido só por esse campo. O `payment.id` é gravado no pedido como `gatewayChargeId`.
- **Por quê**: o `externalReference` é o campo do Asaas para o id do sistema de origem e volta em todos os eventos da cobrança. Não depende de buscar a cobrança no Asaas.
- **Alternativas**: ligar pelo `customer` do Asaas. Rejeitado: um cliente pode ter várias cobranças.

## R4. Idempotência e falha (EventoGateway, ADR_125/133)

- **Decisão**: coleção `gateway_event` com `_id` = `id` do evento do Asaas. Ao receber: se o evento já existe com resultado final (`EXECUTED` ou `IGNORED`), responde 200 sem processar; se existe com `FAILED`, processa de novo; se não existe, grava `RECEIVED` e processa. No fim grava o resultado (`EXECUTED`, `IGNORED` com motivo, `FAILED` com o nome do erro) e o número de tentativas. Falha responde 500, para o Asaas reenviar. Pedido desconhecido responde 200 com o evento `FAILED` (`unknown_order`), para não pausar a fila do Asaas com algo que o reenvio não resolve.
- **Por quê**: o Asaas entrega "ao menos uma vez" e recomenda usar o `id` como chave de idempotência. A fila do Asaas pausa depois de 15 falhas seguidas, então só se devolve erro quando o reenvio pode resolver. O registro guarda o que a rota da ADR_133 vai precisar (status, erro, tentativas).
- **Alternativas**: processar em segundo plano depois de responder 200 (sugestão do Asaas). Rejeitado nesta etapa: a ativação são poucas escritas no Mongo, como no webhook da Clerk, e o processamento síncrono deixa o reenvio do Asaas como mecanismo de retentativa sem fila própria. TTL como o da Clerk: rejeitado, o EventoGateway é histórico de cobrança (ADR_125) e fica.

## R5. Ativação sem transação

- **Decisão**: a ativação é uma sequência de escritas idempotentes, nesta ordem: (1) Tenant por `findAndModify` com upsert em `clerkOrgId` e `setOnInsert` (`_id`, `name`, `status=ACTIVE`, `originOrderId`), pela porta nova `TenantProvisioning` (o `TenantRepository`, usado por todas as requisições, não muda); (2) Assinatura com `_id` = `tenantId` e `setOnInsert`; (3) Membro DONO (insere, ou promove a DONO um membro já existente); (4) pedido para `PAID`. Se o Tenant já existia com outro `originOrderId` (ou sem, como o do seed), nada é criado: o evento fica `IGNORED` (`tenant_already_active`) e o pedido vai para `PAID`, para tratamento manual. Se o Tenant já existia com o mesmo `originOrderId`, a ativação continua dos passos 2 a 4 (retomada depois de falha).
- **Por quê**: o Mongo do compose local é standalone (sem replica set), então não há transação. O índice único de `clerkOrgId` garante um Tenant por organização mesmo com confirmações concorrentes; o `originOrderId` distingue "eu criei e caí no meio" de "já existia".
- **Alternativas**: transação multi-documento. Rejeitada: exigiria replica set no compose e nos testes. Criar o Tenant por último: rejeitado, a Assinatura e o Membro precisam do `tenantId`.
- **Consequência aceita**: entre o passo 1 e o 3 existe uma janela curta com o Tenant ativo e sem DONO. Ela só fica aberta se a ativação falhar, e o reenvio a fecha.

## R6. Assinatura do Tenant e `PlanSubscription`

- **Decisão**: nova coleção `tenant_subscription` (classe `TenantSubscription`), um documento por Tenant (`_id` = `tenantId`): `planTier`, `status` (reusa `SubscriptionStatus` do módulo de uso, só `ACTIVE` nesta etapa), `orderId`, `startedAt`. A `PlanSubscription` por produto do módulo de uso (LLT-183/185) não é tocada.
- **Por quê**: a ADR_122 define a Assinatura por Tenant. A `PlanSubscription` mede franquia e retenção por produto e é criada por outro fluxo do Michel; mexer nela agora mistura escopos. Reusar `PlanTier` e `SubscriptionStatus` deixa as duas falando a mesma língua.
- **Alternativas**: gravar uma `PlanSubscription` por produto na ativação. Rejeitado: na ativação a empresa ainda não tem produtos. **Pendência para o Michel**: como a franquia por produto passa a derivar da Assinatura do Tenant.

## R7. Acesso de quem ainda não tem Tenant

- **Decisão**: `/api/billing/**` passa a aceitar a sessão cujo acesso foi `Granted` ou `Denied(TENANT_NOT_FOUND)` (novo `BillingAccessAuthorizationManager`). Nas outras rotas, o `SessionRevokingAccessDeniedHandler` trata `TENANT_NOT_FOUND` à parte: responde 403 com `{"message": "...", "code": "subscription_required"}` e **não** revoga as sessões. Os demais motivos (`NO_ACTIVE_ORGANIZATION`, `UNKNOWN_ROLE`, `TENANT_INACTIVE`) continuam com o 403 básico e a revogação. Os controllers de billing leem a sessão por uma porta nova, `CurrentOrganizationSession`, que devolve usuário, organização, papel e o `tenantId` opcional.
- **Por quê**: o web (spec 006) já trata 403 com `code` como recusa de negócio e mantém a pessoa logada, então ninguém mais é expulso por falta de Tenant (SC-004). O corpo usa os mesmos campos do `ApiError` (`message`, `code`).
- **Alternativas**: liberar todas as rotas e checar o Tenant em cada controller. Rejeitado: espalha a regra de segurança. Manter o 403 básico e só liberar o billing: rejeitado, o web continuaria deslogando nas outras rotas.

## R8. Credencial do webhook

- **Decisão**: propriedade `lhamalog.billing.asaas.webhook-token` (`ASAAS_WEBHOOK_TOKEN`). No perfil `local`, se vazia, o Core gera um token aleatório na subida e o simulador usa o mesmo (nada para configurar, nada no repositório). No perfil `prod`, é obrigatória: vazia ou com menos de 32 caracteres impede a subida. A comparação é em tempo constante (`MessageDigest.isEqual`), e o log nunca mostra o token.
- **Por quê**: princípios V (segredos) e FR-014. O Asaas exige token de 32 a 255 caracteres.
- **Alternativas**: token fixo de teste em `application-local.properties`. Rejeitado pelo princípio V.

## R9. O simulador

- **Decisão**: só no perfil `local` (`@Profile("local")`):
  - `FakeAsaasPaymentGateway` (implementa a porta `PaymentGateway`): gera `pay_fake_<uuid>` e devolve a URL `{checkout-base-url}/dev/fake-asaas/checkouts/{chargeId}`.
  - `FakeAsaasCheckoutController`: `GET` mostra uma página HTML simples (empresa, plano, valor e o botão "Simular pagamento"); `POST .../pay` monta um evento `PAYMENT_RECEIVED` no formato do R1 (`id` = `evt_fake_<uuid>`, `billingType` = `PIX`) e o envia ao `webhook-url` com o token, pelo `AsaasWebhookSender` (HTTP, `RestClient`). Depois mostra o resultado e um link para o `return-url` do web.
  - Fora do `local`, a porta é implementada por `UnavailablePaymentGateway`, que responde 503 `payment_unavailable` até o adaptador real existir; as rotas `/dev/fake-asaas/**` não têm controller e respondem 404.
  - Configuração: `lhamalog.billing.fake.checkout-base-url` (padrão `http://localhost:8080`, o endereço que o navegador usa), `lhamalog.billing.fake.webhook-url` (padrão `http://localhost:${server.port}/webhooks/asaas`, chamada do próprio processo) e `lhamalog.billing.return-url` (padrão `http://localhost:5173/empresa`).
- **Por quê**: a página do checkout do Asaas é pública, identificada por um id difícil de adivinhar; o navegador chega a ela sem o token da Clerk. Mandar o evento pelo HTTP, do servidor, exercita o endpoint real com a credencial sem expor o token ao navegador.
- **Alternativas**: chamar o processador direto, sem HTTP. Rejeitado: pularia a verificação do token e o parser. Usar o sandbox do Asaas: fica para a feature do adaptador real; exige conta, chave e túnel por desenvolvedor.

## R10. Catálogo de planos e preços

- **Decisão**: `lhamalog.billing.plans.<tier>.price` em `application.properties`, um por `PlanTier`, com valores provisórios (FREE 0,00; STARTER 49,90; PRO 149,90; BUSINESS 399,90; ENTERPRISE 999,90), marcados como ilustrativos. O nome exibido vem do próprio tier. A moeda é BRL.
- **Por quê**: os preços não estão fechados (LLT-229/230). Configuração permite trocar sem código.
- **Alternativas**: coleção de planos no Mongo com tela de gestão. Rejeitado: fora do escopo.

## R11. Membro DONO na ativação

- **Decisão**: `Member.owner(memberId, tenantId, clerkUserId)` cria o DONO com `membershipVersion = 0`, sem nome nem e-mail (o token não os traz). Se o membro já existir (um evento da Clerk chegou entre a criação do Tenant e este passo), `promotedToOwner()` muda só o papel, pela escrita condicional do `MemberRepository.update`. Os eventos seguintes da Clerk completam nome, e-mail e o id do vínculo, e o `syncedTo` já preserva o DONO.
- **Por quê**: a spec 003 registra que o vínculo criado antes do Tenant não é reprocessado e que o fluxo de compra cria o DONO diretamente.
- **Alternativas**: buscar o perfil na API da Clerk. Rejeitado: chamada externa na ativação e mais um ponto de falha.

## R12. Pedidos

- **Decisão**: coleção `subscription_order`. Um pedido novo cancela os pendentes da mesma organização (`updateMulti` de `PENDING` para `CANCELED`) antes de gravar o novo. A consulta "pedido mais recente" ordena por `createdAt`. Índices: `clerkOrgId + createdAt` e `gatewayChargeId`.
- **Por quê**: FR-004 e FR-005. A janela entre cancelar e inserir pode deixar zero pendentes por um instante, o que não causa dano.
- **Alternativas**: índice único parcial "um `PENDING` por organização". Rejeitado: dois pedidos simultâneos do mesmo administrador são improváveis e o último ganha de qualquer jeito.

## R13. Logs (princípio VI)

- **Decisão**: eventos de negócio com `event=`: `billing.checkout.created`, `billing.webhook.rejected` (WARN, sem o token), `billing.webhook.duplicate`, `billing.webhook.ignored`, `billing.activation.executed`, `billing.activation.already_active` (WARN), `billing.activation.unknown_order` (WARN), `billing.activation.value_mismatch` (WARN), `billing.activation.failed` (ERROR), `billing.fake.payment_sent`. Campos: `eventId`, `orderId`, `clerkOrgId`, `tenantId`, `plan`; nunca token, nome da empresa ou e-mail.
- **Por quê**: padrão de logs da constituição.

## R14. O que fica para depois

- Adaptador real do Asaas (`AsaasPaymentGateway`: `POST /v3/customers`, `POST /v3/subscriptions` ou checkout, sandbox, túnel).
- Plano FREE no Asaas real (não há cobrança de valor zero).
- Rota interna de reprocessamento (ADR_133), com JWT interno (ADR_028).
- Inadimplência, renovação, troca, cancelamento, reembolso e suspensão por chargeback.
- Ligação entre `TenantSubscription` e `PlanSubscription` (Michel).
- Spec do web: tela de planos, desvio pelo `subscription_required` e espera pela confirmação.
