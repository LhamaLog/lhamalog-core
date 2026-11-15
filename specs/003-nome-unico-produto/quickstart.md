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

## Testes

Use um banco de teste novo, sem reaproveitar os de outras features. Bancos já usados, como `lhamalog_teste`, contêm produtos sem o campo, inclusive os 5 de mesmo nome criados pela versão anterior do teste:

```powershell
$env:TEST_MONGODB_URI='mongodb://127.0.0.1:27019/lhamalog_nome_unico_produto_test'
.\mvnw.cmd verify
```

Sem `TEST_MONGODB_URI`, os testes de persistência da 001, 002 e 003 são omitidos (`EnabledIfEnvironmentVariable`), então um verify verde sem a variável não comprova esta feature. O verify completo continua exigindo Docker para Testcontainers com tags fixas.

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

## Aceite local

`.\mvnw.cmd verify` verde com `TEST_MONGODB_URI` definido, na máquina do desenvolvedor. Correspondência com a spec: SC-001 (cenários 1, 2, 3, 7), SC-002 (6, 11), SC-003 (4), SC-004 (8), SC-005 (3, 12, 13), SC-006 (1, 16, 17).

## Resultados executados

- 09/11/2026: `.\mvnw.cmd verify` com `TEST_MONGODB_URI` apontando para o banco `lhamalog_nome_unico_produto_test` no MongoDB 8.0.34 local do container `mongo-teste` (porta 27019), separado do Mongo do Compose. 44 testes, 0 falhas, 0 erros, 0 omitidos. BUILD SUCCESS. Por classe: `ProductUniqueNameIntegrationTests` 18, `ProductEditArchiveIntegrationTests` 10, `CompanyProductIntegrationTests` 4 e demais classes 12. Os 18 cenários acima estão cobertos. JDK do PATH: 26.0.2 (`JAVA_HOME` vazio).
