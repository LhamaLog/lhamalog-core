# Pesquisa e decisões técnicas

## Campo de arquivo

- **Decisão**: `archivedAt` (`Instant` ou nulo) no documento Produto. Nulo = não arquivado; instante = arquivado naquele momento. Desarquivar zera o campo. `status` não muda (permanece `CONFIGURING` no cadastro).
- **Rationale**: pedido explícito desta etapa de plano; preserva o ciclo da 001 e evita misturar “configurando” com “arquivado”. Documentos já gravados sem o campo são lidos como nulo (não arquivados); sem migração.
- **Alternatives considered**: boolean `archived` (não guarda quando); reutilizar `status` (`ARCHIVED`) — rejeitado pelo usuário.

## Idempotência

- **Decisão**: arquivar já arquivado ou desarquivar já ativo devolve o documento atual sem gravar de novo (não altera `archivedAt` nem `version`).
- **Rationale**: spec exige operação inócua, sem perda de dados, inclusive em duplo envio.
- **Alternatives considered**: 409 no segundo arquivo; regravar `archivedAt` e avançar `version` — destrutivo ou ruidoso para concorrência.

## Concorrência

- **Decisão**: PUT de dados gerais, PUT de contexto, POST de arquivo e POST de desarquive exigem `version` igual ao persistido quando há mudança real. Conflito → `OptimisticLockingFailureException` → 409, igual ao contexto da 001. No-op de arquivo/desarquivo não exige `version` coincidente.
- **Rationale**: o usuário pediu o mesmo controle do contexto; arquivo que avança `version` faz a edição concorrente falhar em vez de misturar campos.
- **Alternatives considered**: arquivo sem `version` (corrida com edição); `If-Match` HTTP (fora do padrão 001).

## Contratos HTTP

- **Decisão**: `PUT /api/products/{id}` para dados gerais; `POST /api/products/{id}/archive` e `POST /api/products/{id}/unarchive` para estado; `GET /api/products?archived=true` para listar só arquivados. Omissão ou `archived=false` lista só `archivedAt == null`. GET por id continua devolvendo arquivado a quem tem acesso.
- **Rationale**: cadastro da 001 já usa POST na coleção e PUT no contexto; recurso de estado separado evita que membro comum envie `archivedAt` no DTO de edição. Filtro binário cobre “ver os arquivados” sem misturar ativos na mesma resposta.
- **Alternatives considered**: PATCH genérico; query `includeArchived`; gravar `archivedAt` no mesmo PUT de nome — mistura papéis (editar vs DONO/ADMIN).

## Autorização

- **Decisão**: edição usa `get` da 001 (administrador da org ou `ProductAccess`). Arquivar/desarquivar exige `session.administrator()`. Membro com acesso que não é admin recebe 403 (não 404), sem encerrar sessão. Sem acesso ou outro tenant: 404, como na 001.
- **Rationale**: spec separa “quem tem acesso edita” de “só DONO e ADMIN arquivam”. `administrator` já inclui slugs de `CLERK_ADMIN_ROLES` (padrão `admin`; DONO entra pelo slug configurado, sem inferir pelo rótulo).
- **Alternatives considered**: exigir `ProductAccess` também para admin arquivar (quebra FR de admin da 001); 404 para membro com acesso (esconderia um produto que ele já lista).

## Produto arquivado

- **Decisão**: `update` e `updateContext` recusam se `archivedAt != null` com 409 e mensagem de produto arquivado. GET e listagens filtradas permanecem. Sem DELETE. Sem alteração no Collector.
- **Rationale**: spec bloqueia edição e preserva dados; 409 distingue de falta de permissão (403) e de isolamento (404); o handler da 001 já traduz conflito.
- **Alternatives considered**: 422; 403 para edição de arquivado (confundiria com papel).

## Persistência e consultas

- **Decisão**: métodos no `ProductRepository` filtrando `archivedAt` nulo vs não nulo, sempre com `tenantId`, ordem nome/id da 001. Construtores/record `Product` ganham `archivedAt` entre `createdAt` e `context`. Cadastro grava `archivedAt = null`.
- **Rationale**: isolamento continua na query, não só no JWT. Spring Data deriva as queries; documentos antigos sem campo equivalem a nulo.
- **Alternatives considered**: filtrar arquivados só em memória após listar tudo; índice extra tenantId+archivedAt — adiar até haver volume; não é requisito desta feature.

## Testes

- **Decisão**: estender a suíte MockMvc + Mongo da 001 (`CompanyProductIntegrationTests` ou classe irmã no mesmo pacote, mesma condição `TEST_MONGODB_URI`). JWT de teste via `jwt()` com `o.id` e `rol`. `.\mvnw.cmd verify` no aceite.
- **Rationale**: constituição exige testes do comportamento novo na máquina do desenvolvedor, com imagens fixas quando houver Testcontainers.
- **Alternatives considered**: só testes unitários do service (não cobrem isolamento HTTP); testes de UI (Core não tem interface).
