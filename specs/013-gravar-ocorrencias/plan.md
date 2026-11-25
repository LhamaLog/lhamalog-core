# Implementation Plan: Gravar ocorrências a partir do resumo de análise do Finder (Módulo Ocorrência)

**Branch**: `LLT-293-gravar-ocorrencias-a-partir-do-resumo-de-analise-do-finder-modulo-ocorrencia` | **Date**: 25/11/2026 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/013-gravar-ocorrencias/spec.md`

A branch sai da `LLT-135-lista-e-detalhe` em `810e7b4` (PR #19 do core aberto). O PR desta feature é empilhado: base `LLT-135-lista-e-detalhe` até o #19 entrar na `Development`, depois base `Development`, sempre com merge commit (ordem registrada na descrição do PR).

## Summary

O Módulo Ocorrência ganha o lado de escrita dentro do pacote `occurrence` do Core, cumprindo o contrato DN-009-2 do Finder sem mudar nada nele:

- `POST /internal/v1/ocorrencias/consultas`: lê a ocorrência da chave (tenant, produto, assinatura, tipo) e devolve contagem, datas, status e revisão, ou `estado: null`.
- `POST /internal/v1/resumos-analise`: grava os achados de um lote com tudo ou nada e devolve um recibo; reenvio da mesma chave devolve o mesmo recibo.

O tudo ou nada é feito **sem transação do Mongo** (decisão do clarify): um recibo `PENDING` com prazo reserva a chave e guarda um diário com o antes e o depois de cada ocorrência; as gravações são condicionais pela revisão; corrida ou queda é desfeita pelo diário, e só então a entrega responde 409 ou é refeita. As rotas usam um `SecurityFilterChain` próprio com JWT HS256 (segredo compartilhado, até dois para rotação), emissor, audiência e lista de serviços configurados. Um script Java de arquivo único gera o token do Finder. Decisões em [research.md](research.md).

## Technical Context

**Language/Version**: Java 25

**Primary Dependencies**: Spring Boot 4.1.1, Spring Data MongoDB (`MongoTemplate`), Spring Security OAuth2 Resource Server (`NimbusJwtDecoder` com chave secreta), Jackson 3 (`tools.jackson`). Nenhuma dependência nova.

**Storage**: MongoDB. Coleção `Ocorrencia` (spec 012, agora com escrita do Módulo) e coleção nova `ReciboResumoAnalise` (recibos e diário), com índice único da chave de entrega e índice TTL de 30 dias nos recibos confirmados. Sem transação (Mongo local sem replica set).

**Testing**: JUnit 5, MockMvc, `@CoreIntegrationTest` (Testcontainers com tags fixas, Docker ligado). Tokens internos gerados no teste com um segredo fictício. Testes de concorrência com chamadas em paralelo e com falha simulada no meio da aplicação.

**Target Platform**: API HTTP do LhamaLog Core (porta 8080); rotas internas, chamadas pelo Finder na rede privada.

**Project Type**: web-service (backend único neste repositório)

**Performance Goals**: consulta em até 200 ms e entrega de 100 achados em até 2 s com o Core local (SC-004), abaixo dos tempos de espera do cliente do Finder (2 s e 5 s). Uma leitura em lote das ocorrências do resumo, não uma por achado.

**Constraints**: contrato DN-009-2 imutável (rotas, nomes JSON, status e códigos); constituição 2.1.0; logs sem assinatura, trechos de log, títulos, resumos, campos extraídos ou token; formato gravado legível pela consulta da LLT-135 sem mudança nela; inglês no código, português na documentação.

**Scale/Scope**: cerca de 15 classes novas no pacote `occurrence` (`api`, `application`, `infrastructure`) e uma configuração de segurança; um script de token; três classes de teste de integração e testes unitários de validação e de aplicação dos achados.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Spec Kit**: spec, clarify, plano, research, data-model, contratos, quickstart e tasks nesta pasta antes do código.
- **II. IA e revisão**: o código será escrito pela IA; as decisões (JWT, diário sem transação, retenção) estão no clarify e em `research.md`; o Felipe revisa cada arquivo antes do merge.
- **III. Testes**: `OccurrenceLookupIntegrationTests`, `AnalysisSummaryDeliveryIntegrationTests` (entrega, reenvio, revisão, concorrência, queda simulada) e `InternalAuthIntegrationTests`, mais testes unitários de validação e de aplicação. Suíte com `.\mvnw.cmd verify` e o Docker ligado.
- **IV. Idioma**: classes e campos em inglês (`AnalysisSummary`, `DeliveryReceipt`); os nomes JSON do contrato ficam em português porque são o contrato aprovado; coleção nova no padrão de nome em português do Core (`ReciboResumoAnalise`); documentação em português.
- **V. Segredos**: os segredos HS256 vêm de `OCCURRENCE_INTERNAL_JWT_SECRETS` (vazio = rotas internas recusam tudo); nada de segredo ou token em código, teste, exemplo ou log (só tamanho e SHA-256 na subida). O script de token lê o segredo de variável de ambiente e imprime só o token.
- **VI. Logs**: eventos `occurrence.lookup.*`, `occurrence.delivery.*`, `occurrence.auth.*` com `tenantId`, `productId`, `batchId`, `execId`, quantidade de achados e código; WARN para recusa e recuperação, ERROR para chave duplicada na coleção.

Resultado: sem violações. Re-check pós-design: sem violações.

## Project Structure

### Documentation (this feature)

```text
specs/013-gravar-ocorrencias/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── internal-api.md        # o que o Core implementa do DN-009-2 e como responde
├── checklists/requirements.md
└── tasks.md
```

### Source Code (repository root)

```text
src/main/java/br/com/lhamalog/core/
├── config/
│   └── InternalServiceSecurityConfiguration.java   # chain /internal/v1/**, HS256, iss/aud/sub, 401/403 no formato do contrato
└── occurrence/
    ├── api/
    │   ├── InternalOccurrenceController.java        # /internal/v1/ocorrencias/consultas e /internal/v1/resumos-analise
    │   ├── InternalOccurrenceRequests.java          # leitura estrita dos corpos (JsonNode) e regras do contrato → 400/422
    │   ├── InternalOccurrenceResponses.java         # ResultadoConsulta, EstadoOcorrencia, Confirmacao e Erro
    │   └── InternalOccurrenceErrors.java            # {"codigo"} para 400/403/409/422/500
    ├── application/
    │   ├── OccurrenceModuleProperties.java          # lhamalog.occurrence.internal-auth.* e .delivery.*
    │   ├── AnalysisSummary.java                     # resumo validado (DeliveryKey, OccurrenceKey, Finding)
    │   ├── InternalApiException.java                # recusa com status e código do contrato
    │   ├── OccurrenceLookupService.java             # US1
    │   ├── AnalysisSummaryDeliveryService.java      # US2/US3: reserva, conferência, diário, aplicação, desfazer, confirmação
    │   ├── OccurrenceMerge.java                     # antes + achado → depois (regras 1 a 9 do contrato 012)
    │   ├── OccurrenceWriteRepository.java           # porta de escrita condicional
    │   └── DeliveryReceiptRepository.java           # porta dos recibos
    └── infrastructure/
        ├── MongoOccurrenceWriteRepository.java      # leitura em lote, replace/insert condicionais, desfazer
        └── MongoDeliveryReceiptRepository.java      # ReciboResumoAnalise: reserva, diário, confirmação, índices e TTL

scripts/
└── InternalJwt.java                                 # java scripts/InternalJwt.java → imprime o JWT do Finder

src/test/java/br/com/lhamalog/core/
├── config/InternalServiceSecurityConfigurationAccess.java
└── occurrence/
    ├── InternalTokens.java                          # tokens de teste (segredos fictícios)
    ├── InternalModuleWorld.java                     # empresa, produto e corpos do contrato
    ├── InternalAuthIntegrationTests.java
    ├── OccurrenceLookupIntegrationTests.java
    ├── AnalysisSummaryDeliveryIntegrationTests.java
    ├── AnalysisSummaryDeliveryServiceTest.java      # queda, corrida e recuperação com repositórios em memória
    ├── InternalOccurrenceRequestsTest.java
    ├── OccurrenceMergeTest.java
    └── OccurrenceModulePerformanceTests.java        # SC-004, sob demanda
```

**Structure Decision**: tudo do Módulo fica no pacote `occurrence` já criado pela LLT-135, separado da leitura da tela por classes próprias (`Internal*`, `*Delivery*`). A segurança interna é uma `SecurityFilterChain` com `@Order` acima da chain da Clerk e restrita a `/internal/v1/**`, como a `UsageFactsSecurityConfiguration` faz para `/internal/usage-*` (que não muda). O `ApiExceptionHandler` geral não muda: o formato `{"codigo"}` fica num `@RestControllerAdvice` limitado ao controller interno. O `OccurrenceDocument` da LLT-135 continua o mesmo; a escrita usa `MongoTemplate` com `Document` para gravar `extractedFields` com ponto nas chaves.

## Complexity Tracking

| Violação ou complexidade | Por que é necessária | Alternativa mais simples rejeitada porque |
|---|---|---|
| Diário com antes/depois e desfazer, em vez de transação | Tudo ou nada com vários documentos no Mongo local sem replica set (clarify de 25/11) | Transação exigiria replica set no compose e no `mongo-teste`, mudando a infraestrutura do time; gravar sem diário deixaria efeito parcial em corrida ou queda |
| `SecurityFilterChain` própria para `/internal/v1/**` | O JWT interno tem outro emissor, algoritmo e formato de erro que o da Clerk | Reusar o token fixo das rotas de uso fugiria do contrato aprovado (Bearer JWT) |
