# Data model: Upload manual (LLT-292)

## ManualUploadLote

| Campo | Tipo | Notas |
| --- | --- | --- |
| loteId | UUID | Agrupa a requisição HTTP; não é o `batchId` da fila |
| tenantId | String | Da sessão Clerk, no Core; repassado ao Collector |
| productId | String | Do card; autorizado no Core |
| correlationId | String | `X-Correlation-Id` |

## ManualUploadFile

| Campo | Tipo | Notas |
| --- | --- | --- |
| name | String | Nome original (ADR_044) |
| fileId | UUID | Atribuído no Collector aos aceitos |
| batchId | UUID | `openobserve.batchId` e parcela do `idempotencyId`; um por arquivo aceito |
| status | enum | `ACCEPTED`, `DUPLICATE`, `INVALID_EXTENSION`, `TOO_LARGE`, `OBVIOUS_BINARY` |
| reason | String? | Ex.: `ALREADY_EXISTS` |
| md5 | String | Hex minúsculo; só internamente / prefixo nos logs |

## FileIdentity (existente, LLT-290)

Chave Redis: `lhamalog:dedup:file:{tenantId}:{productId}:logs:upload:{md5}`.

`idempotencyId = UUIDv3("<batchId>:logs:upload:<md5>")`.

## Documento no OpenObserve (MVP)

Stream `logs`, um documento por arquivo aceito:

- `lhamalog_batch_id` = `batchId`
- `lhamalog_record_id` = `fileId` (um registro)
- `tenant_id`, `product_id`, `file_name`, `file_source=upload`, `raw` (conteúdo texto), `bytes`, `received_at`

Sem mudança de `schemaVersion` da mensagem da fila.

## Configuração (sem segredos)

- `lhamalog.manual-upload.allowed-extensions` = `txt,json,csv,log`
- `lhamalog.manual-upload.collector-url` (só Core)
- `lhamalog.manual-upload.timeout` (só Core)
- `spring.servlet.multipart.max-file-size` / `max-request-size` = 100MB
- Env (não versionado): `LHAMALOG_CORE_COLLECTOR_TOKEN`, `LHAMALOG_COLLECTOR_SERVICE_TOKEN`
