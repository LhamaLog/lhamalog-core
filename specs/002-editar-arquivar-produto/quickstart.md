# Validação Editar e arquivar Produto

Core apenas. Sem interface neste repositório. Detalhes de campos e rotas: [data-model.md](data-model.md), [contracts/api.md](contracts/api.md).

## Requisitos

Java 25, Maven Wrapper, Mongo de teste isolado. Mesma preparação da 001 (README, `infra/local/.env` ignorado). Não usar banco de produção. JWT de teste via MockMvc; sem colar segredos Clerk.

## Testes

Com Mongo de teste:

```powershell
$env:TEST_MONGODB_URI='mongodb://127.0.0.1:27019/lhamalog_editar_arquivar_produto_test'
.\mvnw.cmd verify
```

Sem `TEST_MONGODB_URI`, os testes de persistência da 001/002 são omitidos (`EnabledIfEnvironmentVariable`). Verify completo continua exigindo Docker para Testcontainers com tags fixas.

## Cenários que a suíte deve comprovar

1. Membro criador (ou admin) altera nome, descrição, categoria e `productVersion` com `version` atual; GET posterior devolve os mesmos valores; `status` e contexto intactos.
2. Nome em branco ou maior que 200: 400 em `name`; documento anterior intacto. Descrição maior que 10000: 400.
3. Segunda PUT com `version` velho: 409.
4. Membro da mesma empresa sem acesso: PUT 404. Outro tenant: 404.
5. Admin arquiva: some de `GET /api/products`, aparece em `GET /api/products?archived=true`, GET por id ainda 200, `archivedAt` preenchido, `status` igual.
6. PUT geral e PUT contexto em arquivado: 409; dados iguais.
7. Membro com acesso e papel `member`: POST archive e unarchive 403; produto segue na listagem padrão.
8. Admin desarquiva: volta à listagem padrão; PUT geral volta a 200.
9. Arquivar já arquivado e desarquivar já ativo: 200, dados e `archivedAt` estáveis.
10. Admin de outra empresa: 404 em GET/PUT/archive.

## Aceite local

`.\mvnw.cmd verify` verde na máquina do desenvolvedor. Critérios SC-001 a SC-006 da spec correspondem aos cenários acima, sem tela.  
  
## Resultados executados

- 08/11/2026: `.\mvnw.cmd '-Dtest=CompanyProductIntegrationTests,ProductEditArchiveIntegrationTests' verify` com `TEST_MONGODB_URI` apontando para MongoDB 8.0.34 local (banco `lhamalog_teste`, container separado). 14 testes, 0 falhas, 0 omitidos. BUILD SUCCESS.
