# Implementation Plan: Validação de pull requests

**Branch**: `LLT-285-estudar-git-hub-actions-e-montar-um-exemplo-de-workflow-yml-para-os-testes-base-do-repositorio` | **Date**: 21/11/2026 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/285-validacao-de-prs/spec.md`

## Summary

Copiar a validação de pull requests do lhamalog-web (workflow, script e testes de `node:test`) e trocar os comandos npm pelos três comandos do Maven Wrapper. O script recebe os textos dos comandos por variável de ambiente, com padrão do Core. O job comenta e fica verde. Não há `package.json`, serviço de Mongo nem segredo.

## Technical Context

**Language/Version**: Java 25 (Temurin) para o Maven; Node.js 22 só para `scripts/pr-validation.mjs` e `node --test`.

**Primary Dependencies**: `actions/checkout@v4`, `actions/setup-java@v4` (`distribution: temurin`, `java-version: '25'`, `cache: maven`), `actions/setup-node@v4` sem cache, `actions/github-script@v7`. Maven Wrapper já presente. Sem dependência npm.

**Storage**: N/A

**Testing**: `node --test tests/pr-validation.test.mjs`. A suite Java continua em `./mvnw test`, disparada pelo workflow, com Testcontainers.

**Target Platform**: `ubuntu-latest` no GitHub Actions (Docker já disponível no runner).

**Project Type**: workflow de repositório mais um script Node sem pacote.

**Performance Goals**: N/A. A duração é a da suite Testcontainers.

**Constraints**: o job não bloqueia o merge; um comentário só; forks de fora; rascunho entra; sem `services:` de Mongo; sem chave secreta; sem alterar arquivos de instrução de agentes.

**Scale/Scope**: um workflow, um script, um arquivo de teste e o trecho de documentação que dizia não haver Actions.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Spec Kit**: spec, plan e tasks deste diretório antecedem o código no mesmo commit. Não há `research.md`, `data-model.md`, `contracts/` nem `quickstart.md`: a feature não tem entidade de negócio nem API HTTP. As decisões ficam neste plano.
- **II. IA implementa com revisão**: a lógica do comentário está no script, coberta por teste, e as decisões estão aqui.
- **III. Testes antes do merge**: o comportamento novo tem `node --test`. O workflow não é o portão de merge. `./mvnw verify` verde na `23a69d9` (394 testes, 0 falhas, 21/11/2026, máquina de quem pediu) continua a referência da suite Java, que este diff não altera. O Actions executa `./mvnw -B test` no pull request.
- **IV. Inglês no código, português na documentação**: identificadores do script em inglês; textos do comentário, spec, README e CONTRIBUTING em português.
- **V. Segredos**: o workflow não declara segredo, `sk_` nem `CLERK_SECRET_KEY`.
- **VI. Logs**: nenhum log de aplicação é alterado.

A constituição permanece na versão 2.1.0. O racional do princípio III ("sem GitHub Actions") descreve o portão local, que continua valendo porque este job não bloqueia o merge. Emendar a constituição fica fora desta tarefa.

## Decisões

1. **Comandos configuráveis.** `CI_COMMAND`, `TEST_COMMAND` e `BUILD_COMMAND` definem o texto e o processo de cada checagem. O padrão no script, e o valor no workflow, é `./mvnw -B -DskipTests compile`, `./mvnw -B test` e `./mvnw -B -DskipTests verify`. O script não cita npm.
2. **Compilação primeiro.** Código de saída diferente de zero no primeiro comando deixa os outros como "não executado", com o detalhe `Não rodaram porque a compilação falhou.` Teste falho ainda assim roda o build.
3. **Node sem pacote.** `setup-node` instala Node 22 e não usa `cache: npm`, porque não existe `package.json`. O teste do script não registra `resolve-ts.mjs`.
4. **Java e Docker.** `setup-java` usa Temurin 25 e cache Maven a partir do `pom.xml`. Não há `services:`. Testcontainers sobe `mongo:8.0.34`, `redis:8.2.10` e `grafana/otel-lgtm:0.37.0` com o Docker do `ubuntu-latest`.
5. **`mvnw` sem bit de execução.** O índice guarda `mvnw` como `100644`. O passo do Maven faz `chmod +x mvnw` quando o arquivo existe e em seguida executa `./mvnw`. O nome da checagem continua `./mvnw ...`.
6. **Job sempre verde.** Cada passo tem `continue-on-error` ou termina com `exit 0`. Falha de checagem aparece só no comentário.
7. **Comentário e reexecução.** Mesmo marcador, mesmas reações e as mesmas regras de `test` / `/test` do Web. O gatilho `issue_comment` lê o workflow da branch padrão (`main`); o README diz isso. A LLT-294 separou esse gatilho para `.github/workflows/pr-rerun.yml`. `.github/workflows/pr-validation.yml` ficou só com `pull_request`.
8. **Branch e commit desta tarefa.** A branch pedida é `LLT-XXX-slug`, que é o formato que o próprio workflow confere. A mensagem principal do commit não leva dois-pontos (`LLT-285` seguido de espaço), formato que a checagem de commit aceita. O CONTRIBUTING segue descrevendo o padrão da ADR_148, com dois-pontos.

## Project Structure

### Documentation (this feature)

```text
specs/285-validacao-de-prs/
├── spec.md
├── plan.md
└── tasks.md
```

### Source Code (repository root)

```text
.github/workflows/pr-validation.yml
.github/workflows/pr-rerun.yml
scripts/pr-validation.mjs
tests/pr-validation.test.mjs
README.md
CONTRIBUTING.md
```

## Constitution Check (depois do desenho)

Os gates acima continuam verdadeiros. Nenhum arquivo de instrução de agentes é editado. README e CONTRIBUTING deixam de afirmar que não existe GitHub Actions e mantêm o pedido de rodar a suite localmente.
