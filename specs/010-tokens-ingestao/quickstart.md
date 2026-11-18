# Validação Tokens de ingestão

Core apenas. Detalhes: [data-model.md](data-model.md), [contracts/api.md](contracts/api.md).

## Requisitos

Java 25, Maven Wrapper e Docker ligado. Os testes de integração sobem o Mongo pelo Testcontainers (`@CoreIntegrationTest`) e usam tokens Clerk reais assinados pelo `TestJwks` (`TestTenants`). Desde 18/11/2026 não usam mais `TEST_MONGODB_URI` nem o `mongo-teste`.

## Testes

Os testes unitários (`IngestionTokenServiceTest`, `TokenSecretsTest`) não precisam de banco. A suíte inteira, que é o portão do merge:

```powershell
.\mvnw.cmd -B clean verify
```

Só os da feature, mais a regra do 403 com código:

```powershell
.\mvnw.cmd -B '-Dtest=TokenSecretsTest,IngestionTokenServiceTest,MongoIngestionTokenRepositoryIntegrationTest,IngestionTokenControllerIntegrationTest,BusinessForbiddenHasCodeTests' verify
```

A falha `CompanyRegistrationHttpTests.adminDeletesRegistrationAndMemberCannot` (R-14) já existia na `Development` antes desta feature.

## Cenários que a suíte deve comprovar

1. Gerar: resposta com `secret` começando por `llt_` (47 caracteres) e aviso; listagem sem `secret` e sem hash.
2. O documento em `product_tokens` tem `_id` texto, `productId`, `tokenHash` = SHA-256 hex minúsculo do `secret`, `status = "ACTIVE"`, `createdAt` como data e nenhum campo com o valor do token.
3. Dois documentos com o mesmo `tokenHash` são recusados pelo índice único.
4. Membro comum: 403 com `code = not_admin` em todas as rotas; sem token: 401; outra empresa: 404; produto arquivado: 409 ao gerar e rotacionar, 200 ao revogar.
5. Nome vazio ou com 101 caracteres: 400; transição 0 ou 169: 400.
6. Revogar: `REVOKED`, `revokedAt`, `revokedBy`; revogar de novo não cria auditoria.
7. Rotacionar com 1 h: os dois tokens válidos às 0 h 59; só o novo à 1 h (relógio controlado no teste unitário).
8. Rotacionar token revogado, expirado ou já em transição: 409.
9. Auditoria: `CREATED`, `ROTATED`, `CREATED` (novo), `REVOKED` na ordem inversa; sem valor ou hash.
10. Concorrência: segunda gravação com versão antiga responde 409.
11. `tenantId` em `product_tokens` é o id interno do tenant, não o id da organização na Clerk.
12. Os logs têm `event=ingestion_token.created`, `.rotated` e `.revoked`, e nunca o valor nem o hash do token.

## Validação manual com o Collector

Fica bloqueada até o time resolver a busca do produto em `products.productId` (o Core grava em `Produto._id`). Com isso resolvido: gerar o token pela API, configurar o exporter OTLP com `Authorization: Bearer <secret>`, revogar e conferir que o envio passa a receber 401 em até 30 s.

## Resultados executados

- 15/11/2026 17:59 (-03:00), branch `LLT-179-tokens-ingestao` criada a partir da `Development` (f1e56ef): `docker start mongo-teste`, `$env:TEST_MONGODB_URI='mongodb://127.0.0.1:27019/lhamalog_tokens_test'`, `.\mvnw.cmd verify`. Resultado: BUILD FAILURE na compilação; 0 testes executados, 0 falhas, 0 pulados. Causa: o `ProductCatalogAdapter` chama `Product.archivedAt()`, que só existe a partir da LLT-123, ausente na `Development`.
- Aceite pendente: rodar de novo quando a base tiver a LLT-123.
- 18/11/2026, branch `LLT-179-tokens-ingestao` com a `Development` (`de4cd79`) mesclada sem commit: `.\mvnw.cmd -B '-Dtest=TokenSecretsTest,IngestionTokenServiceTest,BusinessForbiddenHasCodeTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`. Resultado: BUILD SUCCESS, com 24 testes, 0 falhas e 0 pulados. O código e todos os testes compilaram, inclusive os de integração migrados. Os testes com Testcontainers não rodaram porque o Docker estava desligado. Verify completo pendente (T021).
- 18/11/2026, de 05:53 a 05:56 (-03:00), mesma branch e mesmo estado, com o Docker Desktop ligado (engine 29.6.2): `.\mvnw.cmd -B clean verify`. Resultado: BUILD SUCCESS, com 339 testes, 0 falhas, 0 erros e 0 pulados. Os testes da feature foram `IngestionTokenControllerIntegrationTest` (4), `MongoIngestionTokenRepositoryIntegrationTest` (4), `IngestionTokenServiceTest` (20) e `TokenSecretsTest` (3), mais a `BusinessForbiddenHasCodeTests` (1). A falha R-14 (`CompanyRegistrationHttpTests`) não apareceu: os 7 testes passaram. O log do build tem as linhas `event=ingestion_token.*` com `cid=` e nenhum valor `llt_`. Aceite: cenários 1 a 6 e 8 a 12 comprovados pela API e pelo Mongo; cenário 7 pelo teste unitário com relógio controlado.
