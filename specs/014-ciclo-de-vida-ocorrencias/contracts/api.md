# Contrato HTTP: ciclo de vida da ocorrência

Complementa `specs/012-consultar-ocorrencias/contracts/api.md`. Rotas sob `/api/**`, com o token da Clerk e Tenant, como as da spec 012.

## GET /api/occurrences (mudança)

Parâmetro novo `archived`: `false` (padrão) lista só as ativas; `true`, só as arquivadas. Outro valor → 400 `fieldErrors.archived` "Use true ou false.". Cada item ganha `"archived": false|true`.

## GET /api/occurrences/{id} (mudança)

Ganha `"revision": "…"`, `"archived": true|false`, `"archivedAt": "…" | null` e `"archivedBy": {"type": "USER", "id": "user_…"} | null`. O detalhe de uma arquivada continua 200.

## PUT /api/occurrences/{id}/status

```json
{ "status": "EM_ANALISE", "revision": "a7d53230-…" }
```

| Situação | Status | Corpo |
|---|---|---|
| Mudou (ou já estava nesse status) | 200 | detalhe atualizado |
| `status` fora de ABERTA, EM_ANALISE, FECHADA | 400 | `fieldErrors.status` "Status desconhecido. Use ABERTA, EM_ANALISE ou FECHADA." |
| `revision` vazia | 400 | `fieldErrors.revision` "Informe a revisão que você está vendo." |
| Revisão não é a atual | 409 | `{"message": "A ocorrência mudou desde que você abriu. Recarregue e tente de novo.", "code": "occurrence_changed"}` |
| Outra empresa, produto sem acesso, inexistente ou inválida | 404 | "Ocorrência não encontrada ou sem acesso." |

## POST /api/occurrences/{id}/archive e /unarchive

```json
{ "revision": "a7d53230-…" }
```

Mesmas respostas da rota de status (sem o erro de `status`). Arquivar uma arquivada ou desarquivar uma ativa → 200 sem mudança.
