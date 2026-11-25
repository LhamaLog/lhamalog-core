# Plano Consultar ocorrências (lista, filtros e detalhe)

**Branch**: `LLT-135-lista-e-detalhe` | **Data**: 19/11/2026 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/012-consultar-ocorrencias/spec.md`

Orientação do Felipe nesta etapa: o Core só lê a coleção de ocorrências gravada pelo Módulo Ocorrência e publica o formato esperado como contrato; carga de exemplo no perfil local; status da ADR_082; specs no core e no web com a chave LLT-135. A branch sai da `Development` em `2261876` (com o PR #14 da LLT-289 e o #16 da LLT-285 já mergeados). A pasta é a 012 porque a `Development` tem duas pastas 010 (`010-tokens-ingestao` e `010-criar-tenant-pagamento`), e o 011 fica livre caso uma delas seja renumerada.

Revisão de 22/11/2026: o contrato Finder → Módulo (DN-009-2 e N3 A) foi aprovado em 20/11 e o Felipe assumiu o Módulo Ocorrência. O documento passou a seguir o `AchadoAnalise` (título e resumo opcionais, exemplos sem data, atributos em campos fixos), e o título sem `titulo` vem da assinatura (research R13). A gravação pelo Módulo continua fora desta feature.

## Summary

Módulo novo `occurrence` com duas rotas de leitura: `GET /api/occurrences` (lista paginada com filtros) e `GET /api/occurrences/{id}` (detalhe). O service resolve os produtos visíveis pelo `ProductService` (ativos e arquivados), recusa com 404 produto do filtro que não é visível e monta a consulta no `MongoTemplate` sobre a coleção `Ocorrencia`, com ordem `lastSeenAt` desc e `_id` desc, `skip`/`limit` e `count`. A busca usa regex literal sem diferenciar maiúsculas. O detalhe corta os exemplos em 5 e cada linha em 512 caracteres e esconde documento inválido com 404 e WARN. Um seeder só do perfil `local` grava 12 ocorrências de exemplo por produto, sem duplicar. Decisões em [research.md](research.md); o formato da coleção é proposta de contrato para o Módulo em [contracts/ocorrencia-documento.md](contracts/ocorrencia-documento.md).

## Technical Context

**Language/Version**: Java 25

**Primary Dependencies**: Spring Boot 4.1.1, Spring Data MongoDB (`MongoTemplate`), Spring Security OAuth2 Resource Server. Nenhuma dependência nova.

**Storage**: MongoDB, coleção nova `Ocorrencia` (leitura; escrita só da carga local), com os índices `occurrence_tenant_product_last_seen` e `occurrence_unique_key` criados pelo `auto-index-creation`

**Testing**: JUnit 5, MockMvc, `@CoreIntegrationTest` (Testcontainers com tags fixas, Docker ligado) e `TestTenants` (tokens reais e Tenant ACTIVE), como nas specs 008 e 009

**Target Platform**: API HTTP do LhamaLog Core (porta 8080)

**Project Type**: web-service (backend único neste repositório)

**Performance Goals**: primeira página em menos de 1 s com 10.000 ocorrências na empresa (SC-004), apoiada no índice `{tenantId, productId, lastSeenAt, _id}`

**Constraints**: constituição 2.1.0; inglês no código, português na documentação; somente leitura da coleção fora do perfil local; sem mudança de status, arquivamento, tempo real, gravidade ou métricas; sem interface neste repositório

**Scale/Scope**: pacote novo com cerca de 12 classes, um bean no `LocalDevConfig`, uma propriedade nova, uma classe de teste de integração e um teste unitário do filtro

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Spec Kit**: spec, clarify, plano, research, data-model, contratos, quickstart e tasks nesta pasta antes do código.
- **II. IA e revisão**: o código será escrito pela IA; as decisões estão em `research.md`; o Felipe revisa cada arquivo antes do merge.
- **III. Testes**: `OccurrenceQueryIntegrationTests` (lista, filtros, acesso, detalhe, documento inválido, carga sem duplicar) e `OccurrenceFilterTest` (validação dos parâmetros). Suíte local `.\mvnw.cmd verify` com o Docker ligado (Testcontainers) e a validação de PR do GitHub Actions (LLT-285).
- **IV. Idioma**: identificadores em inglês (`Occurrence*`, `lastSeenAt`, `statusHistory`); a coleção segue o padrão de nome em português do Core (`Produto`, `AcessoProduto`) → `Ocorrencia`; documentação em português.
- **V. Segredos**: nada novo; a carga de exemplo usa só textos fictícios e já mascarados (`<EMAIL>`, `<TOKEN>`).
- **VI. Padrão de logs**: documento inválido no detalhe → `WARN event=occurrence.read.invalid | Stored occurrence is invalid; hidden from the user | tenantId= occurrenceId= reason=`, sem conteúdo de log. Carga local → `INFO event=occurrence.sample.seeded | Sample occurrences ready | productId= inserted=`. Consultas comuns não geram log de negócio. Nenhum log com título, mensagem ou linha de exemplo.
- **Stack**: sem mudança de Java, Spring Boot ou Maven. Pacote `br.com.lhamalog.core.occurrence`.
- **Fluxo**: branch no padrão do Jira (`LLT-135-lista-e-detalhe`), como as demais do time, apesar de a constituição citar `feature/...` (decisão já registrada nas specs 008 e 009); commits `LLT-135: <descrição>`.

Nenhuma violação. Reavaliado depois do desenho (fase 1): sem mudanças.

## Project Structure

### Documentation (this feature)

```text
specs/012-consultar-ocorrencias/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── api.md                    # rotas consumidas pelo web
│   └── ocorrencia-documento.md   # formato proposto ao Módulo Ocorrência
├── checklists/
│   └── requirements.md
└── tasks.md
```

### Source Code (repository root)

```text
src/main/java/br/com/lhamalog/core/occurrence/
├── api/
│   ├── OccurrenceController.java          # GET /api/occurrences e /api/occurrences/{id}
│   └── OccurrenceResponses.java           # Page, ListItem, Detail, ProductRef, Sample, Attribute, StatusChange
├── application/
│   ├── OccurrenceType.java                # ERROR, WARNING, INFO
│   ├── OccurrenceStatus.java              # ABERTA, EM_ANALISE, FECHADA
│   ├── OccurrenceFilter.java              # parse e validação dos parâmetros (FieldValidationException)
│   ├── OccurrenceQueryRepository.java     # porta de leitura
│   ├── OccurrenceRecord.java              # ocorrência válida, já convertida do documento
│   ├── OccurrenceSignature.java           # partes da assinatura (escape \|): título e serviço
│   └── OccurrenceService.java             # produtos visíveis, 404, montagem das respostas
└── infrastructure/
    ├── OccurrenceDocument.java            # @Document("Ocorrencia") + índices
    ├── MongoOccurrenceQueryRepository.java # Criteria, ordem, skip/limit, count, validação do documento
    └── LocalOccurrenceSampleSeeder.java   # carga de exemplo (perfil local)

src/main/java/br/com/lhamalog/core/config/
└── LocalDevConfig.java                    # + bean e ApplicationRunner da carga (propriedade desligada por padrão)

src/main/resources/
└── application-local.properties           # + lhamalog.occurrences.sample-data.enabled=${OCCURRENCE_SAMPLE_DATA:false}

src/test/java/br/com/lhamalog/core/occurrence/
├── OccurrenceQueryIntegrationTests.java   # US1 a US4 pela API
├── OccurrenceFilterTest.java              # validação dos parâmetros
└── OccurrenceSignatureTest.java           # partes da assinatura e título de reserva

README.md                                  # seção "Ocorrências" (rotas, carga local)
```

**Structure Decision**: pacote em camadas como o `ingestiontoken`. O acesso a produto não é duplicado: o `OccurrenceService` usa `ProductService.list`. O `ApiExceptionHandler` não muda: `FieldValidationException` já responde 400 com `fieldErrors` e `ResponseStatusException` responde 404 com a mensagem. Os parâmetros entram como `String`/`List<String>` e são convertidos no `OccurrenceFilter`, para o erro de enum ou data sair como `fieldErrors` e não como 400 genérico.

## Complexity Tracking

> Sem violações da constituição.
