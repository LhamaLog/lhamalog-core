# Validação Manter contexto do produto

Core apenas. Sem interface neste repositório. Detalhes: [data-model.md](data-model.md), [contracts/api.md](contracts/api.md).

## Requisitos

Java 25, Maven Wrapper, Docker e um Mongo de teste separado. JWT de teste via MockMvc, sem segredos da Clerk.

## Testes

Use um banco de teste novo, sem reaproveitar os de outras features (os antigos têm produtos com os campos de contexto da 001):

```powershell
docker start mongo-teste
$env:TEST_MONGODB_URI='mongodb://127.0.0.1:27019/lhamalog_contexto_produto_test'
.\mvnw.cmd '-Dtest=CompanyProductIntegrationTests,ProductEditArchiveIntegrationTests,ProductContextIntegrationTests' verify
```

Sem `TEST_MONGODB_URI`, os testes de persistência são pulados (`EnabledIfEnvironmentVariable`) e um resultado verde não comprova nada. O `mongo-teste` é o container criado na 002 (`docker run -d --name mongo-teste -p 27019:27017 mongo:8.0.34`).

## Cenários que a suíte deve comprovar

1. POST com contexto completo: 201; os seis campos voltam normalizados; a versão 1 existe com o autor do cadastro.
2. POST sem contexto: personalidade `STANDARD`, idioma `pt-BR`, listas vazias; nenhuma versão.
3. PUT de contexto com mudança: versão 2 no topo da lista; a versão 1 continua com o conteúdo antigo.
4. PUT de contexto igual ao atual: 200, `version` avança, nenhuma versão nova.
5. Limites: descrição com 2.001 caracteres, lista com 31 itens, item de arquitetura com 81 caracteres e idioma fora do formato respondem 400; personalidade desconhecida responde 400; nada muda e nenhuma versão é criada.
6. Versão inexistente: 404 com a mensagem própria.
7. Membro sem acesso e outra empresa: 404 nas versões e no texto para a IA; admin da mesma empresa: 200.
8. Texto para a IA: contém o nome do produto, os campos preenchidos e os rótulos; quebra de linha dentro de um campo vira espaço; `executed` é `false`.
9. Produto arquivado: versões e texto para a IA respondem 200; PUT de contexto responde 409 sem versão nova.
10. Testes da 001, da 002 e da 003 continuam passando com `functionalDescription` no lugar de `problemSolved`.

## Aceite local

`.\mvnw.cmd verify` verde com `TEST_MONGODB_URI` definido, na máquina do desenvolvedor. Correspondência com a spec: SC-001 (1, 3), SC-002 (3, 4), SC-003 (5), SC-004 (7), SC-005 (8).

## Aviso para o front

A tela de produto do `lhamalog-web` envia e lê os campos antigos do contexto. Até ser ajustada, o formulário "Contexto para IA" não grava nada útil. Avisar o time do front antes do merge.

## Resultados executados

- 15/11/2026 17:55 (-03:00), branch `LLT-178-contexto-produto` criada a partir da `Development` (f1e56ef): `docker start mongo-teste`, `$env:TEST_MONGODB_URI='mongodb://127.0.0.1:27019/lhamalog_contexto_produto_test'`, `.\mvnw.cmd verify`. Resultado: BUILD FAILURE na compilação; 0 testes executados, 0 falhas, 0 pulados. Causa: o `ProductService` usa `findByTenantIdAndArchivedAtIsNullOrderByNameAscIdAsc`, `findByTenantIdAndArchivedAtIsNotNullOrderByNameAscIdAsc` e as variantes com `IdIn`, que só existem no `ProductRepository` da LLT-123, ausente na `Development`.
- Aceite pendente: rodar de novo quando a base tiver a LLT-123 e a LLT-177.
