# Modelo de dados

Estende o Produto da 001. Empresa, AcessoProduto, sessão e papéis não mudam.

## Produto

Coleção `Produto`. Identificador UUID, `tenantId` da empresa ativa, campos cadastrais e contexto da 001.

| Campo | Tipo | Regras |
| --- | --- | --- |
| id | string UUID | imutável; não vem no DTO de edição |
| tenantId | string | empresa da sessão; não gravável pelo cliente |
| name | string | obrigatório; trim; não vazio; máx. 200 |
| description | string opcional | máx. 10000; vazio permitido e limpa valor anterior |
| category | string opcional | máx. 200; categoria livre |
| productVersion | string opcional | versão de negócio; máx. 200 |
| status | string | inalterado nesta feature; cadastro continua `CONFIGURING` |
| createdAt | instante | imutável na edição/arquivo |
| archivedAt | instante ou nulo | nulo = listagem padrão e editável; preenchido = arquivado |
| context | embutido | imutável no PUT de dados gerais; recusado se arquivado |
| version | long | `@Version`; obrigatório em mutação real |

Arquivado: `archivedAt != null`. Não há exclusão física. Vínculos `ProductAccess` permanecem.

## Transições

```text
archivedAt == null  --POST archive (admin, version atual)--> archivedAt = agora
archivedAt != null  --POST unarchive (admin, version atual)--> archivedAt = null
já no estado pedido --POST archive|unarchive--> sem gravação (idempotente)
```

Edição de dados gerais e de contexto só com `archivedAt == null`.

## Validações

- Nome em branco após trim: 400 em `name`.
- Estouro de tamanho: 400 no campo (`Size`).
- `version` divergente em mutação real: 409.
- Produto arquivado em PUT geral ou PUT contexto: 409, documento intacto.
- Sem acesso / outro tenant: 404.
- Arquivo/desarquivo sem papel administrador: 403 se o produto é visível; 404 se não.

## Relacionamentos

Produto pertence a uma Empresa (`tenantId`). AcessoProduto autoriza membro comum a GET/PUT geral/PUT contexto. Administrador da organização vê todos os produtos da empresa. Arquivo/desarquivo não cria nem remove acesso.

## Índices

Mantém `product_tenant_name` (`tenantId`, `name`, `_id`). Consultas novas filtram `archivedAt` na mesma chave de tenant. Índice extra não é requisito desta feature.

## Migração

Documentos da 001 sem `archivedAt` equivalem a não arquivados. Nenhuma coleção nova. Nenhuma alteração em Tenant nem AcessoProduto.
