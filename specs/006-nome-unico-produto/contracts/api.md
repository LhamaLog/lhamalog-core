# Contrato REST Nome único de produto

Autenticação, empresa via `o.id` e formato de erro `{message, fieldErrors}` permanecem os da 001 e da 002 ([contrato 002](../../002-editar-arquivar-produto/contracts/api.md)). Não há endpoint novo nem campo novo nas requisições. As respostas de produto não incluem `normalizedName`.

## Recusa por nome em uso

Status 409, com `fieldErrors` vazio, sem identificador ou outro dado do produto que ocupa o nome:

```json
{
  "message": "Já existe um produto com este nome nesta empresa. Ele pode estar arquivado.",
  "fieldErrors": {}
}
```

A mensagem é diferente das outras duas recusas 409 de produto:

| Motivo | Mensagem |
| --- | --- |
| Nome em uso | `Já existe um produto com este nome nesta empresa. Ele pode estar arquivado.` |
| Versão de concorrência desatualizada | `Os dados foram alterados. Recarregue antes de salvar novamente.` |
| Produto arquivado | `Produto arquivado não pode ser editado.` |

## Endpoints existentes (comportamento adicional)

### POST /api/products

Acrescenta 409 de nome em uso. Em ordem de avaliação:

| Ordem | Condição | Status |
| --- | --- | --- |
| 1 | JWT ausente ou inválido | 401 |
| 2 | Corpo inválido (inclui nome vazio ou acima de 200) | 400 |
| 3 | Sem organização ativa | 403 |
| 4 | Nome em uso na empresa, por produto ativo ou arquivado | 409 |
| 5 | Sucesso | 201 |

### PUT /api/products/{id}

Acrescenta 409 de nome em uso quando o nome pertence a outro produto. Manter o próprio nome, ou mudar só maiúsculas e espaços nas pontas dele, responde 200. Em ordem de avaliação:

| Ordem | Condição | Status |
| --- | --- | --- |
| 1 | JWT ausente ou inválido | 401 |
| 2 | Corpo inválido | 400 |
| 3 | Sem organização ativa | 403 |
| 4 | Produto inexistente na empresa, de outra empresa ou sem acesso | 404 |
| 5 | Produto arquivado | 409 |
| 6 | `version` desatualizado | 409 |
| 7 | Nome em uso por outro produto da empresa | 409 |
| 8 | Sucesso, com `version` avançado | 200 |

Na recusa por nome, nenhum campo muda e `version` não avança.

### PUT /api/products/{id}/context, POST /api/products/{id}/archive e POST /api/products/{id}/unarchive

Sem mudança de contrato. Não são recusados por nome em uso.

## Fora do contrato

Sem endpoint de consulta de disponibilidade de nome. Sem limite de quantidade de produtos por plano. Sem bloqueio de ingestão no Collector.
