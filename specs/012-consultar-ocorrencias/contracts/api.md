# Contrato HTTP: ocorrências

Rotas sob `/api/**`: exigem o token da Clerk com organização ativa e Tenant (spec 008 e LLT-289). Recusas de segurança e `subscription_required` seguem iguais às demais rotas.

## GET /api/occurrences

Lista paginada das ocorrências que o usuário pode ver.

### Parâmetros de consulta

| Nome | Tipo | Padrão | Regra |
|---|---|---|---|
| `page` | inteiro | 1 | ≥ 1 |
| `size` | inteiro | 20 | 10, 20 ou 50 |
| `type` | texto, repetível | todos | `ERROR`, `WARNING`, `INFO` |
| `status` | texto, repetível | todos | `ABERTA`, `EM_ANALISE`, `FECHADA` |
| `productId` | texto, repetível | todos os visíveis | cada um precisa ser visível |
| `from` | data ISO-8601 com fuso | aberto | `lastSeenAt >= from` |
| `to` | data ISO-8601 com fuso | aberto | `firstSeenAt <= to`; `from` ≤ `to` |
| `q` | texto | sem busca | até 200 caracteres; busca em título, resumo e assinatura, ou id exato; vazio ou só espaços = sem busca |

Exemplo: `GET /api/occurrences?page=2&size=20&type=ERROR&status=ABERTA&status=EM_ANALISE&productId=7c1e...&from=2026-11-12T00:00:00-03:00&q=timeout`

### 200

```json
{
  "items": [
    {
      "id": "0f6a1c52-6e1b-4b8c-9b0e-6a5d2f7d1c11",
      "type": "ERROR",
      "title": "Falha na autenticação de token",
      "product": { "id": "7c1e...", "name": "api-gateway" },
      "count": 128,
      "status": "ABERTA",
      "recurring": true,
      "firstSeenAt": "2026-11-10T13:02:11.512Z",
      "lastSeenAt": "2026-11-19T12:24:17.342Z"
    }
  ],
  "page": 2,
  "size": 20,
  "totalItems": 248
}
```

- Ordem: `lastSeenAt` desc, depois `id` desc.
- Página além da última: `items` vazio e `totalItems` real.
- Sem ocorrências ou sem produtos visíveis: `items` vazio e `totalItems` 0.

### 400

```json
{ "message": "Confira os campos informados.", "fieldErrors": { "size": "Use 10, 20 ou 50." } }
```

Campos possíveis em `fieldErrors`: `page`, `size`, `type`, `status`, `from`, `to`, `q`. Mensagens:

| Campo | Mensagem |
|---|---|
| `page` | "A página começa em 1." |
| `size` | "Use 10, 20 ou 50." |
| `type` | "Tipo desconhecido. Use ERROR, WARNING ou INFO." |
| `status` | "Status desconhecido. Use ABERTA, EM_ANALISE ou FECHADA." |
| `from`, `to` | "Data inválida. Use o formato ISO-8601 com fuso." |
| `to` | "O fim do período precisa ser depois do início." |
| `q` | "A busca aceita até 200 caracteres." |

### 404

Produto do filtro inexistente, de outra empresa ou sem acesso:

```json
{ "message": "Produto não encontrado ou sem acesso." }
```

## GET /api/occurrences/{id}

### 200

```json
{
  "id": "0f6a1c52-6e1b-4b8c-9b0e-6a5d2f7d1c11",
  "type": "ERROR",
  "title": "Falha na autenticação de token",
  "summary": "Tokens JWT expirados em várias requisições do auth-service.",
  "product": { "id": "7c1e...", "name": "api-gateway" },
  "count": 128,
  "status": "ABERTA",
  "recurring": true,
  "firstSeenAt": "2026-11-10T13:02:11.512Z",
  "lastSeenAt": "2026-11-19T12:24:17.342Z",
  "sensitiveData": true,
  "samples": [
    "2026-11-19T12:24:17.342Z ERROR [auth-service] JWT token is expired for user <EMAIL>"
  ],
  "attributes": [
    { "name": "host.name", "value": "ip-10-0-12-34.ec2.internal" },
    { "name": "service.name", "value": "auth-service" },
    { "name": "trace_id", "value": "4f7c9e2d8b1a4c6f9d3e2a7b1c8d5e9f" }
  ],
  "statusHistory": [
    { "from": null, "to": "ABERTA", "changedAt": "2026-11-10T13:02:12.004Z", "changedBy": { "type": "SYSTEM", "id": "modulo-ocorrencia" } }
  ]
}
```

- `title`: o título gravado ou, sem ele, a mensagem normalizada da assinatura (o mesmo vale para os itens da lista).
- `summary`: resumo gravado, ou `null`.
- `samples`: lista de textos, até 5, do mais recente para o mais antigo, cada um com até 512 caracteres. O Finder não manda data por trecho.
- `attributes`: em ordem alfabética de `name` (`host.name`, `service.name`, `trace_id`, `correlation_id`, `module_id`, `line_number` e os campos extraídos); sem valor, o atributo não aparece.
- `sensitiveData`: `true`, `false` ou `null` (desconhecido).
- `statusHistory`: em ordem cronológica.
- Partes ausentes no documento vêm como lista vazia.

### 404

Ocorrência inexistente, de outra empresa, de produto sem acesso ou com documento inválido:

```json
{ "message": "Ocorrência não encontrada ou sem acesso." }
```

## Mudanças da spec 014 (LLT-136, 25/11/2026)

- `GET /api/occurrences` aceita `archived` (`false` padrão, `true` só arquivadas; outro valor → 400 `fieldErrors.archived`), e cada item ganha `archived`.
- `GET /api/occurrences/{id}` ganha `revision`, `archived`, `archivedAt` e `archivedBy` (`{type, id}`).
- Ações novas (`PUT …/status`, `POST …/archive` e `…/unarchive`): ver `specs/014-ciclo-de-vida-ocorrencias/contracts/api.md`.

