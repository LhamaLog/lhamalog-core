# Validação Nome único de produto por empresa

Core apenas. Sem interface neste repositório. Detalhes de campos e rotas: [data-model.md](data-model.md), [contracts/api.md](contracts/api.md).

## Requisitos

Java 25, Maven Wrapper, Mongo de teste isolado. Mesma preparação da 001 (README, `infra/local/.env` ignorado). JWT de teste via MockMvc, sem colar segredos Clerk.

## Limpeza de bancos locais

Não há dados de produção, por isso esta feature não migra dados. Bancos locais com produtos gravados antes dela precisam ser limpos antes de subir esta versão. Isso vale para nomes duplicados e também para nomes todos diferentes, porque esses produtos não têm `normalizedName` e o índice único trata o campo ausente como nulo:

- Com dois ou mais produtos antigos na mesma empresa, o índice `product_tenant_normalized_name` não é criado e a aplicação não sobe.
- Com um só, a aplicação sobe, mas esse produto não bloqueia novos cadastros com o mesmo nome.

Enquanto outras branches usarem o banco `lhamalog_core` e o volume `lhamalog-core-local_mongo-data`, não os limpe nem suba esta branch apontando para eles, o que inclui `docker compose up`, cuja API usa esse banco: o índice criado ali continuaria lá ao trocar de branch, e branches sem esta feature passariam a receber 409 a partir do segundo produto da mesma empresa.

Para validar manualmente, rode o Java no host, seguindo o README, contra um banco novo no Mongo separado usado nos testes (porta 27019):

```powershell
$env:MONGODB_URI='mongodb://127.0.0.1:27019/lhamalog_nome_unico_produto_local'
```

Depois do merge, cada desenvolvedor limpa o próprio banco compartilhado quando nenhuma branch sem esta feature depender dele.

## Antes de deploy em banco com produtos

Situação em 16/11/2026: só existem bancos locais e de teste, e nenhum ambiente tem dados de produção. Por isso esta feature não traz migração automática.

Antes de subir esta versão em qualquer banco que já tenha produtos (homologação, produção ou o banco de outra pessoa), siga os passos abaixo nesta ordem, no banco do `MONGODB_URI` do ambiente, com `mongosh`. Na subida, `spring.data.mongodb.auto-index-creation=true` cria `product_tenant_normalized_name`. Se dois documentos tiverem o mesmo par (`tenantId`, `normalizedName`), o que inclui dois produtos sem o campo na mesma empresa, a criação falha e a aplicação não sobe. Se a versão anterior continuar gravando produtos entre o backfill e a subida, esses produtos ficam sem o campo. Nesse caso, repita os passos 2 a 4 logo antes da subida, ou faça tudo com a API parada.

1. Faça backup da coleção `Produto` (`mongodump --collection=Produto`).
2. Cheque duplicatas por empresa, sem alterar nada. A função `normalize` reproduz `Product.normalizeName`: corta das pontas os caracteres até U+0020, como `String.trim()`, e `toLowerCase()` do JavaScript não depende de locale, como `Locale.ROOT`. Não use `$toLower` nem `$trim` do Mongo: `$toLower` só é definido para ASCII (`AÇÃO` não vira `ação`), e `$trim` corta outros caracteres além dos que `trim()` corta.

   ```js
   const normalize = name => name.replace(/^[\u0000- ]+|[\u0000- ]+$/g, '').toLowerCase();
   db.Produto.countDocuments({ name: { $not: { $type: 'string' } } }); // deve ser 0
   const groups = {};
   db.Produto.find({ name: { $type: 'string' } }, { tenantId: 1, name: 1, archivedAt: 1 }).forEach(p => {
     const key = JSON.stringify([p.tenantId, normalize(p.name)]);
     groups[key] = (groups[key] || []).concat({ id: p._id, name: p.name, archived: p.archivedAt != null });
   });
   Object.entries(groups).filter(([, items]) => items.length > 1).forEach(([key, items]) => printjson({ key, items }));
   ```

   Cada grupo impresso precisa ser resolvido antes de seguir, renomeando produtos junto com o dono da empresa. Este procedimento não escolhe qual produto fica com o nome nem apaga produtos.
3. Preencha `normalizedName` nos documentos que não têm o campo. O `version` não muda, então quem estiver com a tela aberta continua conseguindo salvar.

   ```js
   db.Produto.find({ normalizedName: null, name: { $type: 'string' } }, { name: 1 }).forEach(p =>
     db.Produto.updateOne({ _id: p._id, normalizedName: null }, { $set: { normalizedName: normalize(p.name) } }));
   ```

4. Confira. Os dois comandos devem voltar `0` e vazio, respectivamente:

   ```js
   db.Produto.countDocuments({ normalizedName: null });
   db.Produto.aggregate([
     { $group: { _id: { tenantId: '$tenantId', normalizedName: '$normalizedName' }, total: { $sum: 1 } } },
     { $match: { total: { $gt: 1 } } }
   ]);
   ```

5. Suba a versão e confirme que `db.Produto.getIndexes()` lista `product_tenant_normalized_name` com `unique: true`.

Depois do merge, este procedimento pode substituir a limpeza do banco local compartilhado, com a mesma condição da seção anterior: nenhuma branch sem esta feature pode depender desse banco.

## Testes

Use um banco de teste novo, sem reaproveitar os de outras features. Bancos já usados, como `lhamalog_teste`, contêm produtos sem o campo, inclusive os 5 de mesmo nome criados pela versão anterior do teste:

```powershell
$env:TEST_MONGODB_URI='mongodb://127.0.0.1:27019/lhamalog_nome_unico_produto_test'
.\mvnw.cmd verify
```

Sem `TEST_MONGODB_URI`, os testes de persistência da 001, 002 e 006 são omitidos (`EnabledIfEnvironmentVariable`), então um verify verde sem a variável não comprova esta feature. O verify completo continua exigindo Docker para Testcontainers com tags fixas.

## Cenários que a suíte deve comprovar

1. POST com nome em uso na mesma empresa: 409 com a mensagem de nome em uso; nenhum documento novo em `Produto` ou `AcessoProduto`.
2. POST com `checkout`, `CHECKOUT` e `  Checkout  ` quando existe `Checkout`: 409.
3. POST com nome de produto arquivado: 409.
4. POST com nome usado só em outra empresa: 201.
5. POST com `  Meu Produto  `: 201, `name` gravado como `Meu Produto`.
6. POSTs paralelos com o mesmo nome livre: exatamente um 201; os demais 409 com a mensagem de nome em uso; um único produto criado.
7. PUT para o nome de outro produto, ativo ou arquivado: 409; campos e `version` inalterados.
8. PUT mantendo o nome, ou mudando só maiúsculas e espaços nas pontas: 200; a nova forma fica gravada.
9. Após renomear, POST com o nome antigo: 201.
10. PUT com nome ocupado por usuário sem acesso ou de outra empresa: 404.
11. PUTs paralelos de produtos diferentes para o mesmo nome livre: exatamente um 200; os demais 409.
12. Arquivar mantém o nome ocupado; desarquivar responde 200; desarquivar e renomear libera o nome.
13. Depois de PUT de contexto, arquivar e desarquivar, `normalizedName` segue gravado e o nome segue ocupado.
14. `AÇÃO` colide com `ação`; `Acao` não colide com `Ação`; `Meu App` não colide com `Meu  App`.
15. Nome vazio ou acima de 200 caracteres: 400 em `name`, não 409.
16. Respostas de produto não contêm `normalizedName`.
17. A mensagem de nome em uso difere das mensagens de versão desatualizada e de produto arquivado.
18. `validatesProductNameAndAcceptsEmptyOptionalContext` cria 5 produtos com nomes distintos e lista 5.
19. Recusa por nome em uso no POST e no PUT gera log INFO `event=product.create.name_in_use` ou `event=product.update.name_in_use` com `tenantId` e `productId`, sem o nome digitado e sem stack trace (constituição VI).

## Aceite local

`.\mvnw.cmd verify` verde com `TEST_MONGODB_URI` definido, na máquina do desenvolvedor. Correspondência com a spec: SC-001 (cenários 1, 2, 3, 7), SC-002 (6, 11), SC-003 (4), SC-004 (8), SC-005 (3, 12, 13), SC-006 (1, 16, 17); constituição VI (19).

## Resultados executados

- 09/11/2026: `.\mvnw.cmd verify` com `TEST_MONGODB_URI` apontando para o banco `lhamalog_nome_unico_produto_test` no MongoDB 8.0.34 local do container `mongo-teste` (porta 27019), separado do Mongo do Compose. 44 testes, 0 falhas, 0 erros, 0 omitidos. BUILD SUCCESS. Por classe: `ProductUniqueNameIntegrationTests` 18, `ProductEditArchiveIntegrationTests` 10, `CompanyProductIntegrationTests` 4 e demais classes 12. Os 18 cenários acima estão cobertos. JDK do PATH: 26.0.2 (`JAVA_HOME` vazio).
- 16/11/2026, commit `31ffea3` (merge da Development nesta branch), árvore limpa, antes dos ajustes da Phase 7: `.\mvnw.cmd verify` com o mesmo `TEST_MONGODB_URI` (`lhamalog_nome_unico_produto_test`, container `mongo-teste`, MongoDB 8.0.34, porta 27019). 179 testes, 1 falha, 0 erros, 0 omitidos. BUILD FAILURE. Classes de produto verdes: `ProductUniqueNameIntegrationTests` 18, `ProductEditArchiveIntegrationTests` 10 e `CompanyProductIntegrationTests` 5. Falha única: `CompanyRegistrationHttpTests.adminDeletesRegistrationAndMemberCannot:163` (`Status expected:<409> but was:<200>`), no cadastro da empresa (LLT-175), fora desta feature. A mesma falha se repete ao rodar só essa classe (`.\mvnw.cmd test -Dtest=CompanyRegistrationHttpTests`, Testcontainers, sem `TEST_MONGODB_URI`) em cópias limpas, via `git archive`, de `origin/Development` (`6ac2f01`) e de `31ffea3`. Ela vem da Development, não desta branch, e não foi corrigida aqui por pertencer a outra feature. JDK do PATH: 26.0.2 (`JAVA_HOME` vazio).
- 16/11/2026, sobre `31ffea3` com os ajustes da Phase 7 (renumeração para 006, log `event=` e teste do log): `.\mvnw.cmd verify` com o mesmo `TEST_MONGODB_URI`. 180 testes, 1 falha, 0 erros, 0 omitidos. BUILD FAILURE só pela mesma falha herdada da Development (`CompanyRegistrationHttpTests.adminDeletesRegistrationAndMemberCannot:163`). Classes de produto verdes: `ProductUniqueNameIntegrationTests` 19 (os 19 cenários acima, inclusive o log da recusa), `ProductEditArchiveIntegrationTests` 10 e `CompanyProductIntegrationTests` 5. O merge continua bloqueado pela constituição III até a falha herdada ser corrigida na Development ou o time decidir como tratá-la. JDK do PATH: 26.0.2 (`JAVA_HOME` vazio).
