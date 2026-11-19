# Modelo de dados — Consulta de métricas (dashboard)

## DashboardLogHourlyAggregate (nova — SSID_011 / ADR_166)

Coleção MongoDB dos agregados pré-calculados a partir de **logs** do OpenObserve (não Ocorrências neste MVP; Ocorrências depois da LLT-104 — confirmado Michel 18/11).

| Campo | Tipo | Regras |
| --- | --- | --- |
| `_id` | string | Estável: hash ou chave composta serializada (`tenantId`+`productId`+`severity`+`serviceName`+`hourStart`) para upsert idempotente |
| `tenantId` | string | Id interno do Tenant (nunca o org id da Clerk) |
| `productId` | string | Produto do tenant |
| `severity` | `ERROR` \| `WARNING` \| `INFO` | Já normalizada (ADR_170) |
| `serviceName` | string | Nome do serviço (atributo do log); vazio/`unknown` se ausente |
| `hourStart` | Instant | Início da hora UTC truncada (bucket fechado) |
| `count` | long | Contagem de logs no bucket (≥ 0) |
| `consolidatedAt` | Instant | Momento em que o job fechou/gravou este bucket |
| `schemaVersion` | int | Versão do documento (iniciar em 1) |

**Índices**:

- Único composto: `tenantId` + `productId` + `severity` + `serviceName` + `hourStart` (garante upsert do job).
- Consulta: `tenantId` + `productId` + `hourStart` (intervalo da API).
- Cursor do job / watermark por tenant: opcional coleção auxiliar ou documento `DashboardAggregationWatermark` (`tenantId`, `dadosConsolidadosAte`).

### Watermark (recomendado)

| Campo | Tipo | Regras |
| --- | --- | --- |
| `_id` / `tenantId` | string | Um documento por tenant |
| `dadosConsolidadosAte` | Instant | Fim do último bucket horário fechado com sucesso |
| `updatedAt` | Instant | Última execução bem-sucedida do job para o tenant |

## Entidades de resposta (não persistidas)

### ProductDashboardMetrics

- `productId`, `from`, `to`, `retentionDays`, `volumeIngestaoBytes` (**inteiro em bytes**; cotas em múltiplos de 2^30 quando derivadas de GiB de plano)
- `dadosConsolidadosAte` (opcional)
- `totals`: `logs`, `error`, `warning`, `info`, `traces` (soma `acceptedRecordCount` de fatos traces), `volumeUsedBytes` (inteiro em bytes)
- `series`: `bucket` (`hour`|`day`), `points[]` com `start`, `end`, `error`, `warning` — **sem** `occurrences` neste MVP
- `serviceRanking[]`: até 5 × `{ serviceName, error }` ordenado por `error` desc

Unidades: a API trafega **bytes**; conversão para GiB (÷ 2^30) só na tela do Web — nunca misturar base 10^9.

### CompanyDashboardMetrics

- `from`, `to`, `retentionDays`, `dadosConsolidadosAte` (opcional)
- `totals`: `logs`, `error`, `warning`, `productsMonitored`
- `seriesByProduct[]`: `productId`, `productName`, `bucket`, `points[]` (mesmas regras; sem `occurrences`)
- `products[]`: `productId`, `productName`, `logs`, `error`, `warning`

## Entidades reutilizadas (somente leitura nesta feature)

### UsageFact (`UsageFact`)

Fonte de `volumeUsedBytes` (soma `acceptedBytes` com `domain=LOGS` no intervalo) e `traces` (soma **`acceptedRecordCount`** dos fatos com domínio/tipo **traces** no intervalo). Já exposto em `/internal/usage-facts`.

### PlanSubscription (`PlanSubscription`)

- `allowanceGigabytes` → `volumeIngestaoBytes` via **× 2^30** (GiB binário → bytes); sem fator 10^9
- `planTier` / `TenantRetentionResolver` → `retentionDays`
- Dashboard igual em todos os planos; só o teto `min(30, retentionDays)` muda

### Produto / AcessoProduto

Autorização e nomes (`productName`). MEMBRO: ids via `AcessoProduto`; DONO/ADMIN: todos do tenant. Produto arquivado: segue regras atuais de listagem/`get` (sem exceção nova). Produto inacessível / outra empresa → **404** (padrão `ProductService.get`).

### Severidade (`LogSeverity`)

Enum de domínio ERROR | WARNING | INFO após mapeamento ADR_170.

### Stream e campos OpenObserve (leitura — LLT-185)

| Conceito | Valor real (padrão) | Notas |
| --- | --- | --- |
| Prefixo | `logs` (`OPENOBSERVE_LOGS_STREAM`) | Mesmo env do Collector |
| Nome do stream | `OpenObserveStreamNames.resolve(prefix, tenantId)` | Ex.: `llt187-dash-tenant` → `logs_llt187_dash_tenant_94439d83`; `acme` → `logs_acme` |
| Tenant | `tenantid` | Filtro SQL além do stream |
| Produto | `productid` | Filtro SQL |
| Severidade numérica | `severitynumber` | OTel 1–24 → ERROR/WARNING/INFO |
| Severidade texto | `severitytext` | Fallback se número 0/ausente |
| Serviço | `servicename` | Ausente → `unknown` no agregado |
| Tempo | `_timestamp` | Microssegundos; `_search` usa `start_time`/`end_time` em µs |

## Regras de validação (consulta)

- `from` / `to` obrigatórios, ISO-8601 instant, `to` > `from`
- Duração ≤ `min(30, retentionDays)` dias → senão 400
- `severities` opcional; só ERROR/WARNING/INFO
- `productIds` (empresa): só ids autorizados; id inacessível/outro tenant → 400 ou 404, nunca mistura
- Bucket: ≤ 48 h → `hour`; senão `day`

## Transições do agregado / watermark

```text
(hora aberta no OpenObserve)
        --job SSID_011-->  DashboardLogHourlyAggregate (upsert) + avança dadosConsolidadosAte
(consulta)
        --híbrido-->  soma(agregados hourStart < dadosConsolidadosAte)
                      + contagem OpenObserve [dadosConsolidadosAte, to)
```

Falha no OpenObserve durante o job: não avança watermark; agregados já gravados permanecem. Falha na consulta do intervalo aberto: 5xx, agregados intactos.
