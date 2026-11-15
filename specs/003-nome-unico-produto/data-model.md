# Modelo de dados

Estende o Produto da 001 e da 002. Empresa, AcessoProduto, sessão e papéis não mudam.

## Produto

Coleção `Produto`. Todos os campos da [002](../002-editar-arquivar-produto/data-model.md) permanecem, com um acréscimo:

| Campo | Tipo | Regras |
| --- | --- | --- |
| normalizedName | string | derivado de `name` com `trim()` e `toLowerCase(Locale.ROOT)`; não vem do cliente; não aparece nas respostas; recalculado no cadastro e na edição dos dados gerais; preservado na gravação de contexto, no arquivamento e no desarquivamento |

`name` continua gravado sem espaços nas pontas e com as maiúsculas informadas, e segue sendo o único nome exibido e usado na ordenação.

## Exemplos de normalização

| `name` recebido | `name` gravado | `normalizedName` |
| --- | --- | --- |
| `  Checkout  ` | `Checkout` | `checkout` |
| `CHECKOUT` | `CHECKOUT` | `checkout` |
| `AÇÃO` | `AÇÃO` | `ação` |
| `Acao` | `Acao` | `acao` |
| `Meu  App` | `Meu  App` | `meu  app` |

## Validações

- Nome em branco ou acima de 200 caracteres: 400 em `name`, como hoje.
- `normalizedName` já usado por outro produto da empresa: 409 de nome em uso; nada é gravado.
- Editar o próprio produto mantendo o mesmo `normalizedName` não viola o índice, porque a entrada pertence ao mesmo documento.

## Relacionamentos

Unicidade por Empresa (`tenantId`). Um cadastro recusado não deixa documento em `Produto` nem em `AcessoProduto`.

## Índices

| Nome | Chaves | Único | Situação |
| --- | --- | --- | --- |
| `product_tenant_name` | `tenantId`, `name`, `_id` | não | mantido; atende a listagem ordenada |
| `product_tenant_normalized_name` | `tenantId`, `normalizedName` | sim | novo; sem filtro por `archivedAt`, para o arquivado continuar ocupando o nome |

## Migração

Não há. Documentos gravados antes desta feature não têm `normalizedName`; por isso esta branch roda em banco próprio, sem apagar nem alterar o banco compartilhado. Ver [quickstart.md](quickstart.md).
