# Validação Tokens de ingestão

Core apenas. Detalhes: [data-model.md](data-model.md), [contracts/api.md](contracts/api.md).

## Requisitos

Java 25, Maven Wrapper, Docker e um Mongo de teste separado. JWT de teste via MockMvc.

## Testes

Os testes unitários (`IngestionTokenServiceTest`, `TokenSecretsTest`) não precisam de banco. Os de integração precisam de `TEST_MONGODB_URI` em banco novo:

```powershell
docker start mongo-teste
$env:TEST_MONGODB_URI='mongodb://127.0.0.1:27019/lhamalog_tokens_test'
.\mvnw.cmd '-Dtest=TokenSecretsTest,IngestionTokenServiceTest,MongoIngestionTokenRepositoryIntegrationTest,IngestionTokenControllerIntegrationTest,ProductEditArchiveIntegrationTests' verify
```

Sem `TEST_MONGODB_URI`, os de integração são pulados; os unitários rodam sempre.

## Cenários que a suíte deve comprovar

1. Gerar: resposta com `secret` começando por `llt_` (47 caracteres) e aviso; listagem sem `secret` e sem hash.
2. O documento em `product_tokens` tem `_id` texto, `productId`, `tokenHash` = SHA-256 hex minúsculo do `secret`, `status = "ACTIVE"`, `createdAt` como data e nenhum campo com o valor do token.
3. Dois documentos com o mesmo `tokenHash` são recusados pelo índice único.
4. Membro comum: 403 em todas as rotas; outra empresa: 404; produto arquivado: 409 ao gerar e rotacionar, 200 ao revogar.
5. Nome vazio ou com 101 caracteres: 400; transição 0 ou 169: 400.
6. Revogar: `REVOKED`, `revokedAt`, `revokedBy`; revogar de novo não cria auditoria.
7. Rotacionar com 1 h: os dois tokens válidos às 0 h 59; só o novo à 1 h (relógio controlado no teste unitário).
8. Rotacionar token revogado, expirado ou já em transição: 409.
9. Auditoria: `CREATED`, `ROTATED`, `CREATED` (novo), `REVOKED` na ordem inversa; sem valor ou hash.
10. Concorrência: segunda gravação com versão antiga responde 409.

## Validação manual com o Collector

Fica bloqueada até o time resolver a busca do produto em `products.productId` (o Core grava em `Produto._id`). Com isso resolvido: gerar o token pela API, configurar o exporter OTLP com `Authorization: Bearer <secret>`, revogar e conferir que o envio passa a receber 401 em até 30 s.

## Resultados executados

- 15/11/2026 17:59 (-03:00), branch `LLT-179-tokens-ingestao` criada a partir da `Development` (f1e56ef): `docker start mongo-teste`, `$env:TEST_MONGODB_URI='mongodb://127.0.0.1:27019/lhamalog_tokens_test'`, `.\mvnw.cmd verify`. Resultado: BUILD FAILURE na compilação; 0 testes executados, 0 falhas, 0 pulados. Causa: o `ProductCatalogAdapter` chama `Product.archivedAt()`, que só existe a partir da LLT-123, ausente na `Development`.
- Aceite pendente: rodar de novo quando a base tiver a LLT-123.
