# Research: Consultar ocorrências (lista, filtros e detalhe)

**Feature**: `012-consultar-ocorrencias` | **Data**: 19/11/2026

Revisado em 22/11/2026 com o contrato Finder → Módulo aprovado em 20/11 (`proposta-dn-009-2.md` e `.openapi.yaml`, branch `LLT-201-009-publicar-evento-ocorrencia`, commit `158e78c`) e as respostas do João: R5, R8 e R9 mudaram; R13 é nova.

Fontes lidas: Jira LLT-135, LLT-203, LLT-204 e LLT-205; casos de uso USID_001, USID_002, USID_005 e USID_037 (`LhamaLog casos de uso.xlsx`); classes `Ocorrencia` e `FiltroOcorrenciaEmpresarial` (`Modelo de Classes LhamaLog.xlsx`); matriz de permissões da LLT-169; specs 008, 009 e 010 do lhamalog-finder (branch `LLT-201-009-publicar-evento-ocorrencia`); telas oficiais "Design base v2" e "Detalhe da Ocorrência".

## R1. Quem grava e quem lê a ocorrência

- **Decisão**: o Core só lê a coleção `Ocorrencia` do MongoDB do Core. A gravação é do Módulo Ocorrência (ADR_062). O formato esperado fica em [contracts/ocorrencia-documento.md](contracts/ocorrencia-documento.md), como proposta de contrato para o Módulo.
- **Motivo**: decisão do Felipe em 19/11. O finder já registra que a Ocorrência fica num MongoDB compartilhado (DN-009-1 A) e que o Core é o único ponto de contato do front (README do Core). O Módulo ainda não existe em nenhum repositório.
- **Alternativas**: o Core ser dono da Ocorrência (pacote de gravação dentro do Core), descartado nesta entrega porque amplia o escopo para a gravação; API do Módulo chamada pelo Core, descartado porque o Módulo não existe e o finder ainda tem a rota pendente (DN-009-2).

## R2. Status, tipo e recorrente

- **Decisão**: `type` ∈ {ERROR, WARNING, INFO}; `status` ∈ {ABERTA, EM_ANALISE, FECHADA}; `recurring` booleano.
- **Motivo**: ADR_082 e finder 008 (status), finder 009-13 A (tipo), ADR_063 (recorrente é atributo). Confirmado pelo Felipe em 19/11.
- **Alternativas**: rótulos do Jira e do USID_003 (Resolvida, Não resolvida), que ficam para a LLT-206 mapear na tela.

## R3. Quem vê o quê

- **Decisão**: o conjunto de produtos visíveis vem do `ProductService.list(session, false)` somado ao `list(session, true)`: dono e administrador veem todos os produtos do tenant; membro, só os de `AcessoProduto`. A consulta de ocorrências filtra `tenantId` e `productId ∈ visíveis`. Os mesmos produtos dão os nomes para a resposta.
- **Motivo**: reusa a regra da 001 e a matriz da LLT-169 ("consultar dados, logs e ocorrências: Produto" para membro) sem duplicar a lógica de acesso. Produto arquivado entra, como o Felipe confirmou.
- **Alternativas**: ler `AcessoProduto` direto no módulo de ocorrências, descartado por duplicar a regra de administrador.

## R4. Paginação

- **Decisão**: página numerada (`page` a partir de 1, `size` ∈ {10, 20, 50}, padrão 20) com `skip`/`limit` e `count` da mesma consulta. Ordem `lastSeenAt` desc, `_id` desc.
- **Motivo**: a tela mostra "Mostrando X a Y de Z" e números de página (protótipo e USID_005), o que pede total e salto de página. O desempate por `_id` mantém a ordem estável.
- **Alternativas**: cursor (como `UsageFactCursor`), descartado porque não dá número de página nem total.

## R5. Filtros e busca

- **Decisão**: `Criteria` montado no `MongoTemplate`: `type $in`, `status $in`, `productId $in` (interseção com os visíveis), `firstSeenAt <= to`, `lastSeenAt >= from` e, para o texto, `$or` de `title`, `summary` e `signature` com regex `Pattern.quote` sem diferenciar maiúsculas, mais `_id` igual ao texto. A `signature` entra porque é onde está a mensagem normalizada quando não há título (R13).
- **Motivo**: cobre o critério da LLT-204 e o USID_002 (busca por identificador ou termo). O `Pattern.quote` trata caracteres especiais como texto. O período segue a regra confirmada ("aconteceram no período").
- **Alternativas**: índice de texto do MongoDB, descartado porque não acha pedaço de palavra e muda a ordem por relevância; Atlas Search, fora da stack.
- **Custo**: a regex não usa índice; a busca percorre as ocorrências do tenant e dos produtos já filtrados pelo índice. Para 10.000 ocorrências por empresa (SC-004) isso fica abaixo de 1 segundo.

## R6. Produto no filtro sem acesso

- **Decisão**: se algum `productId` do filtro não está entre os visíveis, a lista responde 404 "Produto não encontrado ou sem acesso." (o mesmo texto do `ProductService`).
- **Motivo**: mesmo comportamento da consulta de produto; não revela se o produto existe.

## R7. Documento inválido

- **Decisão**: a consulta da lista sempre exige `type` e `status` válidos e a presença de `productId`, `title`, `firstSeenAt`, `lastSeenAt` e `count`; quem não atende fica fora da lista e do total. No detalhe, o documento é lido sem esses critérios; se for inválido, a resposta é 404 e sai `WARN event=occurrence.read.invalid | Stored occurrence is invalid; hidden from the user | tenantId= occurrenceId= reason=`.
- **Motivo**: o documento é escrito por outro serviço; um registro quebrado não pode derrubar a lista. O WARN sai no detalhe porque é ali que o Core vê o documento individual (constituição VI: degradação monitorada, sem conteúdo de log).

## R8. Exemplos de log

- **Decisão**: `samples` é uma lista de textos, do mais antigo para o mais novo, com no máximo 20 (o Módulo acrescenta as `linhas` de cada achado no fim). O detalhe devolve os 5 últimos, do mais novo para o mais antigo, cada um cortado em 512 caracteres. O Core não mascara de novo.
- **Motivo**: no contrato aprovado, `linhas` é `string[]` sem data e sem limite de tamanho (resposta do João em 22/11). A ordem de chegada é a única ordem confiável. 512 caracteres vem da classe Ocorrência do modelo de classes.
- **Alternativas**: pedir ao Finder a data por linha, descartado para não reabrir o contrato aprovado.

## R9. Atributos técnicos

- **Decisão**: o documento guarda os campos do contrato (`hostName`, `traceId`, `correlationId`, `moduleId`, `lineNumber`, `extractedFields`); a resposta monta uma lista `{name, value}` em ordem alfabética, com `service.name` tirado da assinatura e as chaves de `extractedFields`.
- **Motivo**: o contrato tem campos fixos, não um mapa OTel livre; `service.name` só existe dentro da assinatura. A lista mantém a tela estável e aceita campos extraídos novos sem mudar a API.

## R13. Título sem `titulo`

- **Decisão**: `title` exibido = `titulo` gravado; sem ele, a mensagem normalizada, que é a segunda parte da assinatura `nível|mensagem|serviço|host`, separando por `|` não escapado e desfazendo `\|`; se a assinatura não tiver quatro partes, a assinatura inteira. O cálculo fica no `OccurrenceSignature`, no Core.
- **Motivo**: o João confirmou que `titulo` e `resumo` podem vir `null` e que a mensagem normalizada não tem campo próprio, só compõe a assinatura. O fallback "segue em aberto" do lado dele; como o Módulo é do Felipe, a regra fica definida aqui.
- **Alternativas**: o Módulo gravar o título derivado, descartado porque o título real chegaria depois e não daria para distinguir; título genérico por tipo ("Erro sem título"), descartado por não ajudar a distinguir ocorrências.

## R10. Carga de exemplo no perfil local

- **Decisão**: `LocalOccurrenceSampleSeeder`, bean só do perfil `local`, ligado por `lhamalog.occurrences.sample-data.enabled` (`OCCURRENCE_SAMPLE_DATA`, padrão `false`). Na subida, para cada `Produto`, grava 12 ocorrências com `_id` fixo `sample-<productId>-<n>` por `upsert` com `setOnInsert`, cobrindo os três tipos, os três status, recorrentes, ocorrências com e sem `title` e `summary`, assinaturas no formato do Finder, exemplos mascarados (`<EMAIL>`, `<TOKEN>`), atributos e histórico. Datas relativas ao momento da primeira carga.
- **Motivo**: o Módulo não existe e a tela precisa de dados; o `_id` fixo com `setOnInsert` evita duplicar (SC-006) e não sobrescreve nada. Mesmo padrão do `LocalDevTenantSeeder`.
- **Limite**: produto criado depois da subida só ganha exemplos na subida seguinte (quickstart explica).

## R11. Índices

- **Decisão**: `@CompoundIndex` em `OccurrenceDocument`: `occurrence_tenant_product_last_seen` `{tenantId: 1, productId: 1, lastSeenAt: -1, _id: -1}` para a lista; `occurrence_unique_key` único `{tenantId: 1, productId: 1, signature: 1, type: 1}`, a chave de unicidade do finder (FR-005 da 008).
- **Motivo**: `spring.data.mongodb.auto-index-creation=true` já cria os índices; o índice único protege a regra "uma ocorrência por chave" também para o Módulo. O contrato avisa o Módulo de que os índices existem.

## R12. Pacote

- **Decisão**: pacote novo `br.com.lhamalog.core.occurrence`, com `api`, `application` e `infrastructure`, como o `ingestiontoken`.
- **Motivo**: módulo novo com consulta própria ao MongoDB; o recorte em camadas é o mais recente no Core.
