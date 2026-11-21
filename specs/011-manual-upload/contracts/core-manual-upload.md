# Contrato: POST /api/manual/upload no Core

Status: fechado no clarify de 20/11/2026 22:45 (America/Sao_Paulo).

## Requisição

```
POST /api/manual/upload
Authorization: Bearer <Clerk session JWT>
X-Correlation-Id: <opcional; o Core gera se vier vazio e sempre devolve>
Content-Type: multipart/form-data

productId: <id do card>
files: <um ou mais arquivos>
```

Autenticação e tenant: iguais às demais rotas `/api/**` (Clerk + `TenantAccessAuthorizationManager`). CORS já permite `Authorization` e `Content-Type`.

O Core revalida extensão (`.txt`, `.json`, `.csv`, `.log`, lista configurável) e tamanho (100 MB por arquivo e 100 MB por requisição) **antes** do repasse. Autorização de produto (visível no tenant e não arquivado) acontece antes do repasse; falha → 403, Collector não é chamado.

## Respostas

| Status | Quando |
| --- | --- |
| 202 Accepted | pelo menos um arquivo enfileirado no Collector (corpo com `loteId` e lista) |
| 400 | multipart inválido, extensão fora da lista, ou lote inteiro recusado por validação/binário óbvio |
| 401 | sem JWT, JWT inválido ou sessão inativa (ADR_032) |
| 403 | produto de outro tenant, sem acesso, arquivado ou id desconhecido (Q11; diverge do 404 de `ProductService.get`) |
| 409 | todos os arquivos do lote são duplicata (`ALREADY_EXISTS`) |
| 413 | um arquivo ou a requisição inteira acima de 100 MB |
| 429 | sem saldo (`RESOURCE_EXHAUSTED`) |
| 503/504 | sem plano, Collector indisponível (inclusive URL do Collector vazia ou sem esquema http/https), ou timeout do repasse |

## Corpo 202 (e 409 de duplicata total, com a mesma forma)

```json
{
  "loteId": "8c2f0a1e-…",
  "files": [
    {"name": "app.log", "fileId": "…", "batchId": "…", "status": "ACCEPTED"},
    {"name": "app-old.log", "status": "DUPLICATE", "reason": "ALREADY_EXISTS"}
  ]
}
```

Status por arquivo: `ACCEPTED`, `DUPLICATE`, `INVALID_EXTENSION`, `TOO_LARGE`, `OBVIOUS_BINARY`, `UNKNOWN`. Em 409, todos os itens são `DUPLICATE` e não há `fileId`/`batchId`. `UNKNOWN` com `reason=collector_result_missing` é um placeholder do Core quando uma parte aceita não veio no corpo do Collector (o status HTTP da resposta continua o que o Collector devolveu, por exemplo 202).

O header `X-Correlation-Id` volta na resposta.

## Detalhes de status e corpo

- **409 → 400/413 quando há recusa local.** 409 só é devolvido ao front quando todos os arquivos encaminhados (e os recusados no Core) são duplicata. Se o Core já recusou algum arquivo (extensão, vazio, `TOO_LARGE`) e o Collector devolve 409 para os demais, o Core responde 400, ou 413 se alguma recusa local for `TOO_LARGE`, com `loteId` e `files[]` na ordem da requisição.
- **Arquivo vazio:** item `INVALID_EXTENSION` com `reason=empty_file` (não é 400 da requisição inteira se houver outros arquivos).
- **413 do contêiner** (tamanho da requisição, `max-part-count` ou `max-part-header-size`) e **503/504** do repasse: corpo `ApiError` (`message`, `fieldErrors`) **sem** `files[]`. O 413 de partes/cabeçalho não usa a frase “100 MB”.
- **504** significa resultado desconhecido: o Collector pode ter enfileirado depois que o Core desistiu. O Core não inventa 503 para a B1 da LLT-290 (claim em andamento); esse 409 é herdado até a T041 da 290.
- **Teto de partes:** `server.tomcat.max-part-count=50` (o campo `productId` conta: no máximo 49 arquivos). `server.tomcat.max-part-header-size=512B`. Um arquivo de exatamente 100 MB é recusado, porque o envelope multipart conta no `max-request-size`.
- **URL do Collector vazia ou sem esquema** (variável definida em branco no compose / `.env.example`, ou um valor como `lhamalog-collector:8080`): 503 com `ApiError`, sem `files[]`. O default `http://localhost:8080` só vale se a variável não existir. A URL utilizável MUST começar com `http://` ou `https://`.
- **`Retry-After`:** se o Collector devolver esse header (por exemplo em 429 ou 503), o Core o repassa ao front. Nenhum outro header da resposta interna é copiado neste MVP.
- A ordem de `files[]` segue a ordem das partes enviadas, inclusive quando recusas locais se misturam com resultados do Collector. Se o Collector omitir uma parte aceita (ou devolver um `name` que não casa), o Core mantém o item no `files[]` público com o filename original, `status=UNKNOWN` e `reason=collector_result_missing`.

