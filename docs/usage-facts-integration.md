# Integração de fatos de uso

O Collector chama `POST /internal/usage-facts` e pode consultar `GET /internal/usage-facts`
e `GET /internal/usage-facts/current-cycle`. Todas as rotas exigem exclusivamente
`X-LhamaLog-Service-Token`; o valor vem de `LHAMALOG_CORE_USAGE_TOKEN` e não deve ser
registrado, versionado ou enviado por `Authorization`.

## Persistência

Há duas coleções distintas:

- `UsageFactIdempotency`: chave `usageFactId`, TTL de sete dias em `receivedAt`. Uma repetição
  idêntica recebe `200` sem nova agregação; corpo diferente com o mesmo identificador recebe `409`.
  Só o código de duplicate key `11000` é tratado como idempotência; outros erros de escrita
  propagam como falha `5xx`. Se o documento de idempotência expirar entre o conflito e a leitura,
  o Core tenta reinserir em vez de devolver `409` indevido.
- `UsageFact`: armazenamento para consulta e ciclo, sem TTL. `acceptedAt` é persistido como
  `Instant` (o DTO HTTP continua `OffsetDateTime` UTC). Um mapper separa o documento Mongo do
  contrato HTTP.

Índices são garantidos no startup por `UsageMongoIndexInitializer` (`IndexOperations`):
TTL de idempotência, consulta `tenantId+productId+acceptedAt` e único
`plan_subscription_tenant_product`.

`GET /internal/usage-facts` usa a janela `[from,to)` e paginação `limit`/`cursor` (padrão 50,
máximo 100). A validação `from < to` fica somente no serviço.

## Ciclo vigente

`GET /internal/usage-facts/current-cycle` devolve resumo separado de `logs` e `traces` (contagem,
bytes e GB decimal). O ciclo usa UTC e `firstPaymentDate` obrigatório: semanal para planos
semanais e mensal para planos mensais ou semestrais.

Escolha técnica reversível, para o Michel confirmar: antes de `firstPaymentDate` não há ciclo
ativo (`active: false`), sem devolver um ciclo futuro. Isto não decide a fonte externa de
`firstPaymentDate` (permanece pendente).

## Validação

Em 2026-11-10, a revisão do PR #4 foi aplicada no worktree local, sem commit.

## Pendências

O detalhamento normativo destas pendências está na spec do Collector
(`lhamalog-collector/specs/005-tenant-product-volume`):

- Ensaio E2E T046 com Collector e Core juntos (em execução nesta entrega).
- Durabilidade condicional do Redis (AOF `everysec`, RPO de ~1 s após HTTP 200; `appendfsync always` não decidido; Lua sem rollback/`AMBIGUOUS`; Redis Cluster não suportado).
- Fonte externa de `firstPaymentDate` indefinida.
- Política operacional de rotação de `LHAMALOG_CORE_USAGE_TOKEN` / `LHAMALOG_COLLECTOR_CORE_TOKEN` (há runbook técnico no Collector; a política em si permanece pendente).
- Confirmação do Michel sobre o comportamento de ciclo antes de `firstPaymentDate`.
- Os 4 testes `CompanyProductIntegrationTests` deste repositório foram pulados por falta de `TEST_MONGODB_URI` até o verify desta entrega.
