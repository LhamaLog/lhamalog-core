# Implementation Plan: Alerta do Finder e aviso de ocorrência recorrente

**Branch**: `LLT-201-publicar-evento-de-ocorrencia-nova-ou-atualizada` | **Date**: 2026-11-27 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/016-alerta-finder-evento-ocorrencia/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow. Rodada 3 (27/11): o texto do plano acompanha o `spec.md` já atualizado. Este ajuste não regenera o plano nem `tasks.md`.

## Summary

No Core, depois do merge da pilha #19 ← #20 ← #21 ← #24, esta feature só acrescenta o que a LLT-201 ainda não tem dono. O Core atende `POST /internal/v1/alertas/finder` no pacote `finderalert` (exemplo `br.com.lhamalog.core.finderalert`): grava o alerta uma vez na coleção `finder_alerts`, responde 201 na primeira e 200 na repetição, e, só na primeira gravação, avisa a tela no canal `/ws/occurrences`, tipo `FINDER_ALERT`, corpo `{tenantId, productId, batchId, motivo}`, sem evento novo e sem envelope para a Notificação. A segurança continua na cadeia `/internal/v1/**` do #20. Um único token cujo `aud` contém a audiência do Módulo e `lhamalog-core` autentica esta rota (propriedade `core-audience`, só nela) e as rotas do Módulo (audiência do #20). No aviso que o #24 já publica, entra a mudança "virou recorrente" (`RECURRENT`) quando ela é a mais forte da entrega (nova > reaberta > virou recorrente), com gravidade igual à classe ERROR, WARNING ou INFO no documento e nesse aviso. A homologação percorre o contrato DN-009-2 nas rotas que o #20 já entrega e a chamada `POST /internal/v1/alertas/finder` em `https://` via Caddy, com o token das duas audiências. (fonte: spec.md, User Stories 1–4 e FR-001 a FR-011; decisoes-joao-llt201.md, base da branch, rota, evento, JWT, gravidade, TLS, pendências 1, 3, 5, 6, 13, 14, 15 e 20, formulários 1 e 2; respostas-pendentes-llt201.md, pendências 2, 4, 7, 8, 11 e 16)

Não se recria lista/detalhe, consultas, resumos, ciclo de vida, `CREATED`, `REOPENED`, o canal `lhamalog:occurrence-events` nem o WebSocket `/ws/occurrences`. (fonte: spec.md, Fora de escopo; decisoes-joao-llt201.md, evento para a Notificação)

A coleção é `finder_alerts` e o literal de "virou recorrente" é `RECURRENT` (fonte: decisoes-joao-llt201.md, Rodada 3). Os eventos de log da rota de alerta são `occurrence.alert.received`, `occurrence.alert.accepted`, `occurrence.alert.duplicate` e `occurrence.alert.rejected` (fonte: decisoes-joao-llt201.md, Rodada 4).

## Technical Context

**Language/Version**: Java 25 (`pom.xml`, `<java.version>25</java.version>`; constituição, Stack tecnológico). O pacote base é `br.com.lhamalog.core` e o groupId é `br.com.lhamalog`.

**Primary Dependencies**: as que o `pom.xml` desta branch já declara, sem acréscimo: Spring Boot 4.1.1 (parent) e os starters webmvc, security, security-oauth2-resource-server, data-mongodb, data-redis, websocket, validation, actuator, opentelemetry. Na pilha assumida mergeada, o Módulo é o pacote `br.com.lhamalog.core.occurrence` (não existe na `Development` em `411f79c`; está em `origin/LLT-137-atualizacao-em-tempo-real`, lido com `git show`, sem checkout). O alerta fica no pacote novo `finderalert`, nomeado pelo João (exemplo `br.com.lhamalog.core.finderalert`), sem dependência nova. (fonte: constituição, Stack; contexto-llt201.md, §1 e §2; decisoes-joao-llt201.md, pendência 15, formulário 1; `git show origin/LLT-137-atualizacao-em-tempo-real` da árvore `src/main/java/br/com/lhamalog/core/occurrence/`)

**Storage**: MongoDB, sem ferramenta de migração e, no compose local, sem replica set e portanto sem transação (fonte: constituição, Stack, ADR_116 citada ali; contexto-llt201.md, §2). A ocorrência já fica na coleção `Ocorrencia`, com `recurring` booleano e índice único `(tenantId, productId, signature, type)` (`git show origin/LLT-137-atualizacao-em-tempo-real:src/main/java/br/com/lhamalog/core/occurrence/infrastructure/OccurrenceDocument.java`). O alerta durável é documento na coleção `finder_alerts`, com índice único em `(tenantId, productId, batchId, motivo)`, distinta da coleção do recibo e de `Ocorrencia`. (fonte: respostas-pendentes-llt201.md, pendência 7; origem: padrão do #20 + DF-FINDER §3; spec.md, FR-003; nome: decisoes-joao-llt201.md, Rodada 3). O aviso de ocorrência reutiliza o pub/sub Redis `lhamalog:occurrence-events` (`OccurrenceStreamProperties`, padrão desse canal no mesmo `git show`). O alerta do Finder não usa esse canal Redis: na primeira gravação (201) avisa a tela no WebSocket `/ws/occurrences`, tipo `FINDER_ALERT`, corpo `{tenantId, productId, batchId, motivo}`. A repetição (200) não avisa de novo. Não se usa o stream `lhamalog:notifications:events` como envelope desta feature. (fonte: decisoes-joao-llt201.md, evento para a Notificação, pendência 1, formulário 1, e Rodada 3; spec.md, FR-006)

**Testing**: JUnit pelos starters `*-test` do `pom.xml`, Testcontainers com tags fixas (mongodb e grafana já estão no `pom.xml`; nunca `latest`). O portão de merge é `./mvnw verify` na máquina do desenvolvedor (constituição, princípio III). A pilha já testa o módulo em `src/test/java/br/com/lhamalog/core/occurrence/`. Esta execução não escreve teste. O log da rota de alerta entra na cobertura com `occurrence.alert.received`, `occurrence.alert.accepted`, `occurrence.alert.duplicate` e `occurrence.alert.rejected`. (fonte: decisoes-joao-llt201.md, Rodada 4)

**Target Platform**: serviço HTTP Spring Boot, perfis `local` e `prod` (constituição, Stack, ADR_151 citada ali). No local o Core responde HTTP na porta 8080; o cliente do Finder só chama `https://` sem caminho na URL base. No teste local, o Caddy no compose do Core fica na frente do Core. O proxy é do lado do Core; o Finder só recebe a URL `https://`. (fonte: decisoes-joao-llt201.md, pendência 13, formulário 2; respostas-pendentes-llt201.md, pendência 17; origem: ADR_164 + arquitetura-modulos; contexto-llt201.md, §5 conflito 7; `lhamalog-finder/internal/adapter/out/confirmacao/http.go`, `Novo`)

**Project Type**: web-service.

**Performance Goals**: o aviso "virou recorrente" sai em até 3 s depois do 201, herdado da spec 015, SC-001, porque usa o mesmo aviso do #24. A rota de alerta responde dentro dos 2 s de `FINDER_CORE_TIMEOUT` (DF-FINDER §3 e §8). Os dois tetos valem em alvos diferentes. Não há outra meta numérica de vazão. (fonte: respostas-pendentes-llt201.md, pendência 16; origem: spec 015, SC-001 + DF-FINDER §3 e §8; spec.md, FR-001, FR-007 e SC-005)

**Constraints**:

- Implementar só o acréscimo da LLT-201 sobre a pilha mergeada (fonte: decisoes-joao-llt201.md, base da branch; spec.md, Assumptions e Fora de escopo).
- Sucesso do alerta: 201 na primeira e 200 na repetição. Corpo exatamente `{aceito, tenantId, productId, batchId, motivo}`, sem campo `repeticao` e sem outro campo (fonte: spec.md, FR-002; respostas-pendentes-llt201.md, pendência 8; origem: DF-FINDER §3 + `InternalOccurrenceController.deliver` do #20 + cliente estrito do Finder).
- Uma gravação por `(tenantId, productId, batchId, motivo)`. Quem perde a corrida de duas primeiras gravações relê e responde 200 com a mesma identidade (fonte: spec.md, FR-003; decisoes-joao-llt201.md, pendência 9, formulário 1; respostas-pendentes-llt201.md, pendências 7, 8 e 9).
- Só o motivo `consulta_openobserve_indisponivel`. Corpo incompleto, campo a mais ou motivo fora da lista: 422 `{"codigo":"DADOS_INVALIDOS"}`. JSON ilegível: 400 `{"codigo":"REQUISICAO_INVALIDA"}`, o mesmo envelope `{"codigo":...}` do 422. Produto que não pertence ao tenant: 403 `{"codigo":"ACESSO_NEGADO"}`, sem gravação (fonte: respostas-pendentes-llt201.md, pendência 4; origem: DF-FINDER §3 + padrão do #20, spec 013; analyze-016.md, LOW I3; decisoes-joao-llt201.md, pendência 12, formulário 1; spec.md, FR-004 e FR-014).
- Mesma cadeia `/internal/v1/**` do #20. A rota de alerta exige `lhamalog-core` lida da propriedade `core-audience` (ilustração de caminho, não nome extra decidido: `lhamalog.occurrence.internal-auth.core-audience`). As rotas do Módulo continuam na audiência do #20. Um token cujo `aud` contém as duas audiências autentica as duas, porque o #20 confere `aud` por "contém". Sem o ajuste por rota, um token só com `lhamalog-core` recebe 401 em todo `/internal/v1/**`. `scripts/InternalJwt.java` gera um token com uma única audiência (`--aud`). (fonte: decisoes-joao-llt201.md, pendências 6 e 20, formulário 1; respostas-pendentes-llt201.md, resumo, fato de código; spec.md, FR-005)
- Um aviso por ocorrência por entrega, com a mudança mais forte: nova > reaberta > virou recorrente. Criação já recorrente publica só `CREATED`. FECHADA que reabre e fica recorrente na mesma entrega publica só `REOPENED`. "Virou recorrente" publica `RECURRENT`. Aumento só de contagem, recusa e repetição não avisam. Ausente ou `null` em `recurring` conta como `false`. (fonte: decisoes-joao-llt201.md, pendência 5, formulário 1, e Rodada 3; respostas-pendentes-llt201.md, pendências 5 e 11; spec.md, FR-007 e FR-008)
- Gravidade = classe: copiar ERROR, WARNING ou INFO para o campo `gravidade` e manter o tipo, no documento e no aviso do #24. O Finder não calcula. (fonte: decisoes-joao-llt201.md, pendência 3, formulário 1; respostas-pendentes-llt201.md, pendência 2; origem: Ocorrência.mmd + 009-5 revisto; spec.md, FR-009)
- Log de negócio da rota de alerta, mesmo com o pacote `finderalert`: `occurrence.alert.received` (pedido recebido), `occurrence.alert.accepted` (aceite 201), `occurrence.alert.duplicate` (repetição 200) e `occurrence.alert.rejected` (recusa 4xx). Não há outro literal (fonte: decisoes-joao-llt201.md, pendência 14, formulário 2, e Rodada 4; spec.md, FR-012; constituição, VI).
- Falha do Redis não falha a entrega do resumo (fonte: spec 015, FR-008; `AnalysisSummaryDeliveryService.notifyScreens` no `git show`). Se o POST do alerta falha, o lote permanece pendente no Finder e o aviso é reenviado no reprocessamento; o Core atende o reenvio pela deduplicação (fonte: respostas-pendentes-llt201.md, pendência 19; origem: DF-FINDER §2 e §3).
- Segredo fora do código, do teste versionado e do exemplo commitado; log no princípio VI, sem token nem trecho de log (fonte: constituição, V e VI; spec.md, FR-012). A confiança do Finder no certificado local do teste (`SSL_CERT_FILE`) é da spec do Finder (fonte: decisoes-joao-llt201.md, pendência 18, formulário 2; spec.md, Assumptions).
- Identificadores Java em inglês (constituição, IV). O caminho e os campos JSON do alerta permanecem os do Finder; `gravidade` permanece o nome do campo observável. Isso é divergência registrada no Constitution Check, não uma troca de contrato.

**Scale/Scope**: quatro histórias da spec 016. A entrega de resumo que o detector já percorre aceita no máximo 1000 achados (`lhamalog.occurrence.delivery.max-findings`, spec 013 / `OccurrenceModuleProperties` no `git show`). Não há número novo de tenants, lotes ou conexões nesta feature.

### Residuais sem fonte

Nenhum. O residual dos sufixos de `event=occurrence.alert.*` fechou: `occurrence.alert.received` (pedido recebido), `occurrence.alert.accepted` (aceite 201), `occurrence.alert.duplicate` (repetição 200) e `occurrence.alert.rejected` (recusa 4xx). (fonte: decisoes-joao-llt201.md, Rodada 4)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Constituição lida: `.specify/memory/constitution.md`, versão 2.1.0, ratificada em 2026-11-02, emendada em 2026-11-16. Esta execução não altera esse arquivo.

| Gate | Antes da pesquisa | Depois do desenho |
|---|---|---|
| I. Spec Kit (specify → plan → tasks → implement) | Passa neste passo: a spec 016 existe e este comando só atualiza o plano e o desenho. `tasks.md` não é regenerado aqui. | Passa. Código continua inválido até a implementação descrita por tasks. |
| II. IA pode implementar, com revisão e decisões nos artefatos | Passa: as decisões fechadas nas rodadas 2 e 3 estão neste plano e em `research.md`, com fonte. | Passa. Os quatro eventos de log da Rodada 4 estão neste plano, com fonte. |
| III. Testes antes do merge, `./mvnw verify`, Testcontainers com tag fixa | Passa como regra da implementação futura. Esta execução não escreve nem roda teste. | Passa como regra. O quickstart lista os cenários que os testes terão de cobrir; não inclui suíte. |
| IV. Inglês no código, português na documentação | Passa para os artefatos (português) e para os tipos Java já existentes (inglês). O pacote `finderalert` é inglês. | Divergência no contrato HTTP e no campo `gravidade`: ver abaixo. Identificadores Java novos continuam em inglês; o JSON do Finder e o campo `gravidade` não são traduzidos. |
| V. Segredos fora do código e do commit | Passa: nenhum segredo entra nestes artefatos. O JWT sai de ambiente, como em `OCCURRENCE_INTERNAL_JWT_SECRETS` (spec 013, `application.properties` no `git show`). | Passa. README, `.env.example` e compose ficam na lista de documentos, sem valor de segredo. |
| VI. Log com correlation id, função, aviso e fuso | Passa como regra. O filtro `CorrelationIdFilter` já cobre `/*` (`src/main/java/br/com/lhamalog/core/logging/CorrelationIdFilterConfiguration.java`). O padrão de console/arquivo já está em `application.properties`. | Passa. Os eventos são `occurrence.alert.received`, `occurrence.alert.accepted`, `occurrence.alert.duplicate` e `occurrence.alert.rejected` (fonte: decisoes-joao-llt201.md, Rodada 4). Não se cria outro filtro. |
| Stack (Java 25, Spring Boot 4.1.1, Maven Wrapper, Mongo, Redis) | Passa: nenhuma versão nova e nenhuma dependência nova. | Passa. |
| Fluxo: branch `feature/<chave>-<descricao>` e commit `<CHAVE>: <descrição>` | Divergência de branch: ver abaixo. Nenhum commit nesta execução. | A mesma divergência. A emenda fica pendente. |

### Divergências (sem emendar a constituição)

1. **C1 — Nome da branch.** O fluxo da constituição exige `feature/<chave-jira>-<descricao>`. A branch desta feature, exigida pelo pedido e registrada na spec (`Feature Branch`), é `LLT-201-publicar-evento-de-ocorrencia-nova-ou-atualizada`. A pilha do Felipe usa o mesmo formato sem o prefixo `feature/` (`origin/LLT-135-lista-e-detalhe`, `origin/LLT-137-atualizacao-em-tempo-real`, e as demais citadas no contexto-llt201.md, §0 e §1). Esta execução não renomeia a branch. **Emenda necessária, não aplicada:** o fluxo passar a aceitar `LLT-<chave>-<descricao>` (padrão já usado nessa pilha), ou uma exceção explícita para esta branch. Até a emenda, o merge que cobrar o prefixo `feature/` continua em conflito com a branch pedida.

2. **C2 — Princípio IV contra o contrato já publicado e contra o campo `gravidade`.** A constituição exige endpoints e campos em inglês. O Finder já chama `POST /internal/v1/alertas/finder` com `motivo` e exige a resposta com `aceito` (`lhamalog-finder/internal/adapter/out/core/aviso.go`). O #20 já expõe caminhos em português (`/internal/v1/ocorrencias/consultas`, `/internal/v1/resumos-analise`; spec 013, `contracts/internal-api.md` no `git show`). A decisão de 26/11 manda implementar essa rota, não traduzi-la (fonte: decisoes-joao-llt201.md, rota POST). O campo observável da gravidade é `gravidade`, copiado da classe (fonte: decisoes-joao-llt201.md, pendência 3, formulário 1; respostas-pendentes-llt201.md, pendência 2; origem: Ocorrência.mmd). **Emenda necessária, não aplicada:** o princípio IV passar a abrir exceção para contrato HTTP já consumido pelo Finder (esta rota e o DN-009-2) e para o campo `gravidade` já nomeado no modelo da ocorrência, mantendo pacotes, classes, métodos e os demais identificadores Java em inglês. Sem essa emenda, implementar o contrato do Finder e o campo `gravidade` viola o texto atual do princípio IV.

Nenhuma das duas divergências foi tratada como se a constituição já permitisse. São os achados CRITICAL C1 e C2: ficam só registradas. Esta execução não emenda `.specify/memory/constitution.md`. As duas bloqueiam um merge estrito até a emenda ou até a correção. Não bloqueiam este desenho: o pedido manda registrar a emenda e seguir sem editar a constituição. (fonte: analyze-016.md, CRITICAL C1 e C2; spec.md, Constituição — só registro; `.specify/memory/constitution.md`, Fluxo de desenvolvimento e princípio IV)

### Conflitos registrados (não são emenda da constituição)

Não se resolvem de outro modo. Vale o João. (fonte: spec.md, Conflitos registrados)

- ADR_165 (ocorrências pela Notificação via stream) × decisão do João "só o aviso do #24" e pendência 1 A (gravar o alerta e avisar por WebSocket, sem evento novo para a Notificação). Vale o João. (fonte: respostas-pendentes-llt201.md, conflito 1; decisoes-joao-llt201.md, pendência 1, formulário 1)
- 009-5 revisto (gravidade no aviso) × spec 015, FR-007 (aviso sem gravidade). Vale o João. A emenda da 015 fica como documento necessário; a 015 não é editada aqui. (fonte: respostas-pendentes-llt201.md, pendência 2 e conflito 2)
- ADR_062 (escala "critico"/"alto", matriz pendente) × gravidade = classe ERROR, WARNING ou INFO. Vale o João (pendência 3 D). (fonte: decisoes-joao-llt201.md, pendência 3, formulário 1; respostas-pendentes-llt201.md, conflito 3)

## Project Structure

### Documentation (this feature)

```text
specs/016-alerta-finder-evento-ocorrencia/
├── plan.md              # este arquivo
├── research.md          # fase 0
├── data-model.md        # fase 1
├── quickstart.md        # fase 1
├── contracts/           # fase 1
│   ├── alerta-finder.md
│   └── aviso-ocorrencia.md
├── spec.md              # já existia; não reescrito aqui
├── checklists/          # já existia
└── tasks.md             # fase 2 já existente; este comando não a regenera
```

### Source Code (repository root)

Árvore real do topo da pilha (`git ls-tree` / `git show` em `origin/LLT-137-atualizacao-em-tempo-real`, commit `beb288a`), mais o que esta feature só altera. Não há pasta nova de serviço. Não há dependência nova.

```text
pom.xml                                          # sem dependência nova
compose.yaml                                     # proxy Caddy do Core, na frente do serviço api em HTTP na porta 8080
src/main/java/br/com/lhamalog/core/
├── config/InternalServiceSecurityConfiguration.java
│     # cadeia única em /internal/v1/**; audiência passa a depender da rota
│     # alerta: propriedade core-audience (lhamalog-core)
│     # consultas e resumos: audiência do #20
├── logging/CorrelationIdFilter.java             # já cobre /*; reutilizar
├── finderalert/                                 # pacote novo; exemplo br.com.lhamalog.core.finderalert
│     # POST /internal/v1/alertas/finder; coleção finder_alerts; a segurança não mora aqui
└── occurrence/
    ├── api/InternalOccurrenceController.java    # POST consultas e resumos; o alerta não entra aqui
    ├── application/AnalysisSummaryDeliveryService.java
    │     # notifyScreens só depois do 201; não duplicar a entrega
    ├── application/OccurrenceChangeDetector.java
    │     # hoje CREATED e REOPENED; acrescer "virou recorrente" com a precedência fechada
    ├── application/OccurrenceEvent.java         # enum Change: CREATED, REOPENED e RECURRENT
    ├── application/OccurrenceMerge.java         # já grava recurring a partir do achado
    ├── application/OccurrenceModuleProperties.java
    │     # internal-auth.audience padrão lhamalog-modulo-ocorrencia permanece nas rotas do Módulo
    ├── infrastructure/OccurrenceDocument.java   # coleção Ocorrencia; gravidade recebe a classe
    └── stream/
          ├── RedisOccurrenceEventPublisher.java # mesmo canal; o aviso passa a levar gravidade
          ├── RedisOccurrenceEventListener.java  # change precisa existir no enum
          └── OccurrenceConnections.java         # WebSocket type=occurrence
src/main/resources/application.properties        # chaves do #20 e do #24; core-audience só na rota de alerta
src/test/java/br/com/lhamalog/core/occurrence/   # testes novos só na implementação
```

**Structure Decision**: o Core continua um único serviço Maven. O Módulo permanece o pacote `br.com.lhamalog.core.occurrence` já entregue pela pilha (fonte: contexto-llt201.md, §1; spec 013 no `git show`). O código do alerta fica no pacote novo `finderalert`, nomeado pelo João (exemplo `br.com.lhamalog.core.finderalert`), e grava na coleção `finder_alerts`. A segurança dessa rota continua na cadeia `/internal/v1/**` do #20; não nasce outra `SecurityFilterChain`. (fonte: decisoes-joao-llt201.md, pendência 15, formulário 1, e Rodada 3; spec.md, FR-001 e FR-005). A publicação "virou recorrente" entra em `OccurrenceChangeDetector` e em `OccurrenceEvent.Change` como `RECURRENT`, nos tipos que o #24 já usa (`git show` desses arquivos). O publicador Redis do aviso de ocorrência não ganha canal nem envelope novos. O alerta do Finder usa o WebSocket `/ws/occurrences` com tipo `FINDER_ALERT` (fonte: decisoes-joao-llt201.md, evento para a Notificação e Rodada 3; spec.md, FR-006). O proxy do teste local é o `compose.yaml` na raiz. Os eventos `occurrence.alert.received`, `occurrence.alert.accepted`, `occurrence.alert.duplicate` e `occurrence.alert.rejected` ficam no pacote `finderalert` e não criam caminho novo nesta árvore. (fonte: decisoes-joao-llt201.md, Rodada 4)

## Complexity Tracking

> Divergências da constituição registradas acima. Não são alternativa de desenho escolhida no lugar da regra; são pendência de emenda.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Branch sem o prefixo `feature/` exigido no Fluxo de desenvolvimento | O pedido e a spec fixam `LLT-201-publicar-evento-de-ocorrencia-nova-ou-atualizada`, e a pilha #19–#24 usa o mesmo formato (`contexto-llt201.md`, §0–§1). Esta execução não pode trocar de branch. | Renomear para `feature/LLT-201-...` quebraria a instrução desta execução e o nome já escrito na spec. A saída prevista é emenda da constituição, não feita aqui. |
| Endpoint e campos JSON em português (`/internal/v1/alertas/finder`, `motivo`, `aceito`) e campo observável `gravidade`, contra o princípio IV | Esse é o contrato que o Finder já chama (`aviso.go`) e que a decisão de 26/11 manda atender no Core. O #20 já tem rotas internas em português. `gravidade` é o campo nomeado no modelo da ocorrência e na pendência 3 D. | Traduzir o caminho ou os campos do alerta faria o cliente atual rejeitar a resposta (`confirmacao/http.go` exige esses nomes) e contradiria a decisão da rota. Trocar `gravidade` por outro nome contradiria a pendência 3 e o Ocorrência.mmd. A saída prevista é emenda do princípio IV, não feita aqui. |

## Pós-desenho

A fase 0 está em [research.md](./research.md). A fase 1 está em [data-model.md](./data-model.md), [contracts/](./contracts/) e [quickstart.md](./quickstart.md). O Constitution Check foi refeito na tabela acima: os gates de stack, segredo, teste, log e Spec Kit seguem de pé; as duas divergências da constituição continuam abertas como emenda, não como licença permanente. Os três conflitos de fonte ficam registrados; vale o João.

O desenho segue o que as rodadas 2 e 3 fecharam: aceite 201/200, coleção `finder_alerts`, dedupe, corrida, motivo e corpos de erro, 403 de tenant/produto, aviso `FINDER_ALERT` no canal `/ws/occurrences` só na primeira gravação, sem evento novo para a Notificação, pacote `finderalert`, propriedade `core-audience`, um token com as duas audiências, precedência do aviso com `RECURRENT`, `recurring` ausente como `false`, gravidade igual à classe no documento e no aviso, os eventos `occurrence.alert.received`, `occurrence.alert.accepted`, `occurrence.alert.duplicate` e `occurrence.alert.rejected`, Caddy em `compose.yaml`, teto de 3 s no aviso e de 2 s na rota. (fonte dos eventos: decisoes-joao-llt201.md, Rodada 4)

## Pendências — documentos necessários

Nenhum destes arquivos é criado nesta execução.

| Documento | Onde | Por quê |
|-----------|------|---------|
| ADR nova "Módulo Ocorrência integrado ao Core" | Drive e branch nova no lhamalog-docs | Registrar que a Ocorrência fica dentro do Core e substituir a parte da ADR_054 que a trata como serviço e repositório separados. O Drive ainda descreve o serviço separado. (fonte: decisoes-joao-llt201.md, registro de que a Ocorrência fica dentro do Core; origem: contexto-llt201.md, §5 conflito 1 e §7 Q8 opção A; spec.md, FR-013) |
| Fechamento do trecho pendente da ADR_051 | Drive | A ADR_051 ainda deixa pendente o protocolo no documento. Para o Core, a rota, o efeito e o aviso já estão fechados: canal `/ws/occurrences`, tipo `FINDER_ALERT`, corpo `{tenantId, productId, batchId, motivo}`, só na primeira gravação (201), sem evento novo para a Notificação. O documento da ADR não foi atualizado e pode registrar este contrato. (fonte: decisoes-joao-llt201.md, pendência 1, formulário 1, e Rodada 3; contexto-llt201.md, §8; spec.md, tabela de documentos) |
| Emenda da spec 015, FR-007 | spec 015, noutra execução | O aviso do #24 passa a levar gravidade, igual à classe, além dos campos que a 015 já lista. Esta execução não edita a 015. (fonte: respostas-pendentes-llt201.md, pendência 2; origem: 009-5 revisto) |
| Alinhamento da ADR_062 | Drive | A matriz "critico"/"alto" continua pendente na ADR. A regra adotada é gravidade = classe ERROR, WARNING ou INFO. O documento da ADR não foi atualizado. (fonte: decisoes-joao-llt201.md, pendência 3, formulário 1) |
| README, exemplo de ambiente e compose do Core | lhamalog-core, na implementação | Registrar a propriedade `core-audience` (audiência `lhamalog-core`, só na rota de alerta), o token único com as duas audiências e o Caddy no compose do Core, sem colocar segredo no repositório. Fora desta escrita do plano. (fonte: decisoes-joao-llt201.md, pendências 6, 13 e 20; spec.md, FR-011 e tabela de documentos) |
| Alinhamento dos endpoints de ocorrência, do Finder e do Core com a Notificação | lhamalog-docs, no material já citado no PR de documentação | Citar o contrato DN-009-2 e a rota de alerta quando essa documentação for atualizada. Não é escrita aqui. (fonte: contexto-llt201.md, §8; spec.md, tabela de documentos) |
| Emenda da constituição do Core (fluxo da branch e princípio IV) | `.specify/memory/constitution.md` | Cobrir as duas divergências deste plano. Não editar neste comando. |
| Spec da mesma feature no repositório do Finder | lhamalog-finder | A decisão manda specs nos dois repositórios. Este plano é só o do Core. O que o Finder faz se o POST falha, e a CA local do teste via `SSL_CERT_FILE`, ficam na spec do Finder. (fonte: decisoes-joao-llt201.md, rota POST e pendência 18, formulário 2; respostas-pendentes-llt201.md, pendência 19; spec.md, Fora de escopo) |

Não criar ADR_146 nem um documento de envelope novo para o aviso de ocorrência. O aviso de ocorrência desta feature é o do #24. O alerta do Finder, na primeira gravação (201), avisa a tela no canal `/ws/occurrences`, tipo `FINDER_ALERT`, corpo `{tenantId, productId, batchId, motivo}`. (fonte: decisoes-joao-llt201.md, evento para a Notificação, pendência 1, formulário 1, e Rodada 3)
