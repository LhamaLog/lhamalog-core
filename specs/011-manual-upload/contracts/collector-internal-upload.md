# Contrato: POST /internal/manual/upload no Collector

Status: fechado no clarify de 20/11/2026 22:45 (America/Sao_Paulo).

## Requisição

```
POST /internal/manual/upload
X-LhamaLog-Service-Token: <LHAMALOG_COLLECTOR_SERVICE_TOKEN>
X-Correlation-Id: <cid da requisição do front>
Content-Type: multipart/form-data

tenantId: <já autorizado pelo Core>
productId: <já autorizado pelo Core>
loteId: <gerado pelo Core ou pelo Collector; o Core ecoa na resposta>
files: <um ou mais arquivos, com o filename original>
```

- Sem service token válido, ou com header `Authorization` presente: 401. Mesmo espírito de `UsageFactsSecurityConfiguration`.
- Token OTLP de produtor não autentica este path.
- O Collector **não** revalida Clerk nem o acesso ao produto. Revalida extensão, tamanho (100 MB arquivo/requisição) e magic bytes (MZ / ELF / Mach-O).

Porta: `server.port` (8080). Não usar as portas OTLP 4317/4318.

## Processamento por arquivo (ordem)

1. Revalidar extensão e tamanho.
2. Recusar binário óbvio (magic bytes).
3. `FileDeduplication.claim(tenantId, productId, FileSignal.LOGS, FileSource.UPLOAD, bytes)`.
4. Rate limit e reserva de cota (mesmas regras OTLP), depois do claim.
5. Gravar no OpenObserve (stream `logs`, um documento com o conteúdo cru, o nome do arquivo, `lhamalog_batch_id` = `batchId` do arquivo).
6. Publicar `log.ingested` em `lhamalog:stream:logs` com `fileSource=upload` e `idempotencyId = UUIDv3("<batchId>:logs:upload:<md5>")`.
7. `commit` do claim com TTL de retenção (ou `release` se a publicação/gravação falhar); `confirm` ou `refund` da reserva, como na spec 010 / LLT-186.

`schemaVersion` permanece 1. Com `lhamalog.file-dedup.enabled=false`, não há claim e o `idempotencyId` é o `batchId`.

## Respostas ao Core

O Core espelha o status ao front. Corpo no mesmo formato do contrato público (`loteId` + `files[]`).

| Status | Quando |
| --- | --- |
| 202 | pelo menos um arquivo enfileirado |
| 400 / 413 | validação / tamanho / binário óbvio no lote inteiro |
| 401 | service token |
| 409 | todos os arquivos duplicata (`ALREADY_EXISTS`) |
| 429 | sem saldo |
| 503 | sem plano, ou dependência crítica fora no caminho de publicação (não no dedup: Redis fora é fail-open só para o claim) |

Duplicata de um arquivo não chama reserva, não grava no OpenObserve e não publica no stream (alinhado a `specs/010-file-hash-dedup/contracts/duplicate-response.md`).

## Correspondência com as partes aceitas

O corpo da resposta MUST ter um item em `files[]` para cada arquivo da parte `files` recebida (as partes aceitas pelo Core), **na mesma ordem em que chegaram**. Cada item MUST usar em `name` o filename original da parte correspondente, sem alteração. O Core associa por essa posição e pelo filename original. Se a quantidade de itens divergir, ou se um item não casar pelo nome, o Core registra um WARN e, na resposta pública, mantém a parte aceita em `files[]` na ordem da requisição, com o filename original, `status=UNKNOWN` e `reason=collector_result_missing` (sem `fileId`/`batchId`). Itens extras do Collector não são acrescentados fora da ordem da requisição pública.

## Cabeçalhos ao Core

Se o Collector responder 429 ou 503 com `Retry-After`, esse header MUST ser enviado; o Core o repassa ao front. Outros headers da resposta interna não são exigidos neste MVP.

O 504 no Core (timeout do repasse) significa resultado desconhecido: o Collector pode ter concluído o enfileiramento depois que o Core desistiu. A B1 da LLT-290 (claim em andamento = 409 hoje) é herdada; não se inventa um 503 local no Core para esse caso.

## MD5 e heap

O MD5 MUST ser calculado em stream a partir do arquivo temporário do multipart, **sem** carregar até 100 MB em um `byte[]`. A assinatura ilustrativa `FileDeduplication.claim(..., bytes)` no passo 3 não autoriza materializar o arquivo inteiro na heap; a implementação no Collector (fora deste worktree) deve expor um overload que leia um `InputStream`/`Path` ou equivalente.
