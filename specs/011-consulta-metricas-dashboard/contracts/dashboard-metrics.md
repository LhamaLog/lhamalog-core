# Contrato: métricas de dashboard (Core ← espelho Web)

**Status**: espelho do contrato do Web em `lhamalog-web/specs/007-dashboards-produto-empresa/contracts/dashboard-metrics.md` (LLT-159). Implementação no Core = spec `011-consulta-metricas-dashboard` / branch `LLT-145-consulta-metricas-dashboard`.

**Princípios**: ADR_141 (Web só fala com o Core); fonte = contagens em logs (OpenObserve via Core, search SQL) + fatos de uso; resposta híbrida (agregados consolidados + intervalo aberto) — job SSID_011 lê **logs** agora; Ocorrências depois da LLT-104 (confirmado Michel 18/11); header `X-Correlation-Id` (Core aceita e devolve o mesmo id). Severidades no payload já normalizadas pelo Core (ADR_170). Campo/série `occurrences` por bucket é **opcional**; ausente nesta entrega. Unidades de volume/cota: **bytes inteiros** na API; conversão para GiB (2^30) só na tela do Web.

## Headers

| Header | Direção | Regra |
|---|---|---|
| `Authorization: Bearer <token>` | Web → Core | Obrigatório |
| `X-Correlation-Id: <uuid>` | Web → Core | Obrigatório nas chamadas de métricas do Web; Core aceita ausente/inválido e gera (filtro existente) |
| `X-Correlation-Id: <uuid>` | Core → Web | Eco do id recebido ou gerado |
| `Content-Type: application/json` | respostas | JSON |

## `GET /api/dashboard/products/{productId}/metrics`

### Parâmetros

| Nome | Onde | Obrigatório | Descrição |
|---|---|---|---|
| `productId` | path | sim | Produto acessível no tenant do token |
| `from` | query | sim | Início do intervalo (ISO-8601 instant) |
| `to` | query | sim | Fim do intervalo (ISO-8601 instant); deve ser > `from` |
| `severities` | query | não | Lista `ERROR,WARNING,INFO`; omitido = todos |

### Resposta 200

```json
{
  "productId": "prd_123",
  "from": "2026-11-11T00:00:00.000Z",
  "to": "2026-11-18T00:00:00.000Z",
  "dadosConsolidadosAte": "2026-11-18T14:00:00.000Z",
  "retentionDays": 30,
  "volumeIngestaoBytes": 10737418240,
  "totals": {
    "logs": 1200,
    "error": 40,
    "warning": 80,
    "info": 1080,
    "traces": 15,
    "volumeUsedBytes": 536870912
  },
  "series": {
    "bucket": "day",
    "points": [
      { "start": "2026-11-11T00:00:00.000Z", "end": "2026-11-12T00:00:00.000Z", "error": 5, "warning": 10 }
    ]
  },
  "serviceRanking": [
    { "serviceName": "checkout", "error": 12 }
  ]
}
```

Notas de unidades no exemplo:

- `volumeIngestaoBytes`: `10737418240` = **10 × 2^30** (10 GiB de cota em bytes).
- `volumeUsedBytes`: `536870912` = **0,5 × 2^30** (512 MiB usados em bytes). Sempre base 2; **nunca** 10^9.
- `traces`: contagem (`acceptedRecordCount` de fatos tipo traces), não bytes.

Demais regras:

- `serviceRanking`: até **5** itens, `error` desc.
- `series.bucket`: `hour` se duração ≤ 48 h; senão `day`.
- `series.points[]`: `error` e `warning` obrigatórios; `occurrences` **omitido** neste MVP.
- `retentionDays` obrigatório; teto do filtro `min(30, retentionDays)`.
- `dadosConsolidadosAte` quando houver consolidação SSID_011.
- Sem selo Crítico/Atenção/Estável.
- Conversão para GiB (÷ 2^30) para exibição: **somente Web**.

## `GET /api/dashboard/company/metrics`

### Parâmetros

| Nome | Onde | Obrigatório | Descrição |
|---|---|---|---|
| `from` / `to` | query | sim | Mesmo significado do endpoint por produto |
| `productIds` | query | não | Lista; omitido/vazio = todos autorizados à pessoa |
| `severities` | query | não | Igual ao por produto |

### Resposta 200

```json
{
  "from": "2026-11-11T00:00:00.000Z",
  "to": "2026-11-18T00:00:00.000Z",
  "dadosConsolidadosAte": "2026-11-18T14:00:00.000Z",
  "retentionDays": 30,
  "totals": {
    "logs": 5000,
    "error": 120,
    "warning": 300,
    "productsMonitored": 3
  },
  "seriesByProduct": [
    {
      "productId": "prd_123",
      "productName": "Checkout",
      "bucket": "day",
      "points": [
        { "start": "2026-11-11T00:00:00.000Z", "end": "2026-11-12T00:00:00.000Z", "error": 5, "warning": 8 }
      ]
    }
  ],
  "products": [
    {
      "productId": "prd_123",
      "productName": "Checkout",
      "logs": 2000,
      "error": 80,
      "warning": 100
    }
  ]
}
```

## Erros

| Status | Quando | Corpo (exemplo) |
|---|---|---|
| 400 | Período inválido, severidade desconhecida, `productIds` malformado / acima do teto | `{"code":"invalid_filter","message":"..."}` |
| 401 | Token inválido / ausente | `{"error":"unauthorized"}` |
| 403 | Segurança (sem Tenant, etc.) | `{"error":"forbidden"}` |
| 404 | Produto inexistente, de outro tenant ou MEMBRO sem AcessoProduto | corpo de erro sem vazamento cross-tenant |
| 5xx | Falha OpenObserve / dependência | recuperável; agregados não corrompidos |

## Autorização

- Escopo = `tenantId` interno do token (Clerk → Tenant ACTIVE).
- DONO/ADMIN (`org:admin`): todos os produtos da empresa.
- MEMBRO (`org:member`): só `AcessoProduto`; sem produtos → 200 vazio no endpoint da empresa.
- Produto inacessível / outra empresa → **404** (alinhado a `ProductService.get`; LLT-158).

## Job e configuração (Core)

- Job SSID_011: `lhamalog.dashboard.aggregation.cron` (padrão `0 0 * * * *` = a cada 1 h).
- OpenObserve: `POST /api/{org}/_search` com SQL de contagem; credenciais fora do código.
- Fora deste contrato de payload: schema Mongo (ver [data-model.md](../data-model.md)), exportação (LLT-224), Ocorrências (LLT-104).
