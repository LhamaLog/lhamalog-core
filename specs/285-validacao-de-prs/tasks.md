# Tasks: Validação de pull requests

**Input**: `specs/285-validacao-de-prs/spec.md` e `plan.md`

**Prerequisites**: spec.md e plan.md. Sem research, data-model ou contracts: a feature não tem entidade de negócio nem API.

**Tests**: `node --test tests/pr-validation.test.mjs` cobre o script. A suite Maven não muda.

## Phase 1: Spec Kit

- [x] T001 Escrever `specs/285-validacao-de-prs/spec.md` com comentário único, job verde, branch/título/commit, comandos Maven, release para `main` e comando `test`.
- [x] T002 Escrever `specs/285-validacao-de-prs/plan.md` com Java 25, Node 22 sem pacote, comandos configuráveis e Testcontainers sem serviço de Mongo.
- [x] T003 Escrever `specs/285-validacao-de-prs/tasks.md`.

## Phase 2: Script e testes (US2, US3, US4)

- [x] T004 Implementar `scripts/pr-validation.mjs` a partir do Web, com `CI_COMMAND`, `TEST_COMMAND` e `BUILD_COMMAND` no lugar dos comandos npm fixos.
- [x] T005 Implementar `tests/pr-validation.test.mjs` sem `resolve-ts.mjs`, cobrindo formato, corte de trecho, compilação que interrompe o resto, release, comando `test` e nomes de comando configuráveis.

## Phase 3: Workflow (US1, US3, US4)

- [x] T006 Criar `.github/workflows/pr-validation.yml` com os gatilhos do Web, `setup-java` Temurin 25 com cache Maven, `setup-node` sem cache e os três comandos `./mvnw`.
- [x] T007 No mesmo workflow, pular testes e build quando a compilação falha, publicar ou atualizar o comentário `<!-- llt-285-validacao-prs -->` e reexecutar por `test` ou `/test`.

## Phase 4: Documentação

- [x] T008 Trocar o trecho "Sem GitHub Actions" de `README.md` pela validação de pull requests, incluindo a limitação do comando `test` até chegar em `main`.
- [x] T009 Trocar o trecho "não há GitHub Actions" de `CONTRIBUTING.md`, mantendo o pedido de rodar os testes localmente.

## Phase 5: Conferência

- [x] T010 Rodar `node --test tests/pr-validation.test.mjs`. Não marcar o comando `test` no GitHub como testado: o gatilho só existe depois do merge em `main`.
