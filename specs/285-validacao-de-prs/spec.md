# Feature Specification: Validação de pull requests

**Feature Branch**: `LLT-285-estudar-git-hub-actions-e-montar-um-exemplo-de-workflow-yml-para-os-testes-base-do-repositorio`

**Created**: 21/11/2026

**Status**: Implementado

**Input**: User description: "Levar ao lhamalog-core a mesma validação de pull requests do lhamalog-web (workflow, script e testes, inclusive o comando test da LLT-287 e a mensagem de commit com ou sem dois-pontos), adaptada a Java 25, Maven Wrapper e Testcontainers. O job nunca bloqueia o merge."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ver o resultado no pull request (Priority: P1)

Quem abre ou atualiza um pull request para `Development` ou `main` recebe um único comentário com o resultado de cada checagem. O merge não fica bloqueado por esse resultado.

**Why this priority**: é o retorno que a pessoa vê sem abrir o log do Actions.

**Independent Test**: abrir um pull request do próprio repositório e ler o comentário marcado com `<!-- llt-285-validacao-prs -->`.

**Acceptance Scenarios**:

1. **Given** um pull request do próprio repositório para `Development`, **When** ele é aberto, sincronizado, reaberto, editado ou marcado como pronto para revisão, **Then** o workflow comenta ou atualiza o mesmo comentário, com ✅ ou ❌ em cada checagem e um bloco "Para corrigir com IA".
2. **Given** esse comentário já existe, **When** a validação roda de novo, **Then** o texto é atualizado no mesmo comentário.
3. **Given** qualquer resultado das checagens, **When** o job termina, **Then** ele fica verde.
4. **Given** um pull request em rascunho, **When** a validação dispara, **Then** ela roda do mesmo jeito.
5. **Given** um pull request vindo de fork, **When** o evento chega, **Then** o job não roda.

---

### User Story 2 - Conferir branch, título e commits (Priority: P1)

Em pull request para `Development`, o comentário também confere o nome da branch, o título e as mensagens de commit.

**Why this priority**: é a regra de rastreio da tarefa, no mesmo formato já usado no Web.

**Independent Test**: rodar `node --test tests/pr-validation.test.mjs` com exemplos de branch, título e commit válidos e inválidos.

**Acceptance Scenarios**:

1. **Given** a branch `LLT-285-slug`, o título `LLT-285: descricao` e commits `LLT-XXX: descricao` ou `LLT-XXX descricao`, **When** a validação roda, **Then** essas três checagens passam.
2. **Given** o número do título diferente do número da branch, **When** a validação roda, **Then** o título falha e o detalhe cita os dois números.
3. **Given** um commit de merge (mais de um pai), **When** a validação roda, **Then** esse commit é ignorado.
4. **Given** um pull request cuja base é `main`, **When** a validação roda, **Then** branch, título e commits não entram no comentário, e a nota é exatamente `PR de release (Development → main): só testes e build são verificados`.

---

### User Story 3 - Compilar, testar e buildar (Priority: P1)

O comentário mostra três comandos do Maven Wrapper. Se a compilação falha, testes e build não rodam.

**Why this priority**: é o exemplo de workflow para os testes base do repositório.

**Independent Test**: o script recebe o código de saída e o texto de cada comando e monta o comentário; no Actions, os comandos são `./mvnw -B -DskipTests compile`, `./mvnw -B test` e `./mvnw -B -DskipTests verify`.

**Acceptance Scenarios**:

1. **Given** a compilação concluída, **When** a validação segue, **Then** testes e build rodam, mesmo se os testes falharem.
2. **Given** a compilação falhou, **When** o comentário é montado, **Then** testes e build aparecem como não executados, com o aviso de que não rodaram porque a compilação falhou.
3. **Given** a saída de uma falha, **When** ela passa de 30 linhas ou de 1500 caracteres, **Then** o comentário fica com o final desse trecho e a frase `O trecho foi cortado.`
4. **Given** os testes de integração, **When** o job roda em `ubuntu-latest`, **Then** não há serviço de Mongo no workflow: Mongo, Redis e `grafana/otel-lgtm` sobem pelo Testcontainers, usando o Docker do runner.

---

### User Story 4 - Pedir de novo com um comentário (Priority: P2)

Uma pessoa autorizada comenta `test` ou `/test` no pull request e a validação roda outra vez, atualizando o mesmo comentário.

**Why this priority**: repete a checagem sem um push novo. O gatilho só existe de verdade depois que o workflow está na branch padrão.

**Independent Test**: os testes do script cobrem o comando, a associação e o autor. No GitHub, o comando só vale depois do merge deste workflow em `main`.

**Acceptance Scenarios**:

1. **Given** um comentário cujo texto, sem espaços nas pontas, é `test` ou `/test`, de um autor `OWNER`, `MEMBER` ou `COLLABORATOR` que não é bot, **When** o comentário é criado num pull request, **Then** a validação reexecuta.
2. **Given** outro texto, outra associação, um bot, um comentário em issue ou um pull request de fork, **When** o evento chega, **Then** a validação não reexecuta.
3. **Given** a reexecução aceita, **When** ela começa, **Then** o comentário recebe a reação de olhos; no fim, recebe foguete se tudo passou ou o polegar para baixo se algo falhou.

### Edge Cases

- Branch, título ou commit sem descrição, com espaço a mais ou com maiúsculas no slug são recusados.
- Falha ao ler commits ou ao publicar o comentário não deixa o job vermelho.
- O wrapper `mvnw` está versionado sem bit de execução; o job torna o arquivo executável antes de chamar `./mvnw`.
- O comando `test` usa a versão do workflow que está em `main`. Enquanto esta alteração não chega lá, comentar `test` não reexecuta esta validação.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O workflow dispara em `pull_request` dos tipos `opened`, `synchronize`, `reopened`, `edited` e `ready_for_review`, nas bases `Development` e `main`.
- **FR-002**: O job de pull request só roda quando a head pertence a este repositório.
- **FR-003**: O comentário é único, identificado por `<!-- llt-285-validacao-prs -->`, e cada checagem usa ✅ ou ❌.
- **FR-004**: O job termina verde, com ou sem checagem falha.
- **FR-005**: Em base diferente de `main`, as checagens são branch `LLT-XXX-slug`, título `LLT-XXX: descricao` com o mesmo número da branch, commits `LLT-XXX: descricao` ou `LLT-XXX descricao` (merges ignorados) e os três comandos Maven.
- **FR-006**: Em base `main`, o comentário só traz os três comandos e a nota de release.
- **FR-007**: Os comandos exibidos são configuráveis. O padrão do Core é `./mvnw -B -DskipTests compile`, `./mvnw -B test` e `./mvnw -B -DskipTests verify`.
- **FR-008**: Java 25 (Temurin) com cache do Maven; Node só para o script, sem `package.json` e sem cache npm.
- **FR-009**: O comando `test` ou `/test` em `issue_comment` reexecuta a validação só para `OWNER`, `MEMBER` ou `COLLABORATOR`, sem bots, e atualiza o mesmo comentário.
- **FR-010**: O workflow não contém segredo, chave `sk_` nem `CLERK_SECRET_KEY`.

### Key Entities

- **Checagem**: nome, se passou, texto encontrado, texto esperado e trecho de saída.
- **Comentário de validação**: marcador, nota opcional de release, lista de checagens e bloco "Para corrigir com IA".

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: `node --test tests/pr-validation.test.mjs` passa sem dependência de `resolve-ts.mjs` nem de `package.json`.
- **SC-002**: Um pull request deste repositório para `Development` recebe um comentário com as seis checagens, e o job fica verde.
- **SC-003**: Falha de comando no comentário não passa de 30 linhas nem de 1500 caracteres, e avisa `O trecho foi cortado.` quando houve corte.
- **SC-004**: README e CONTRIBUTING deixam de dizer que o repositório não tem GitHub Actions.

## Assumptions

- O comportamento de comentário, marcador, rascunho, fork, release e comando `test` segue o que já está na branch `Development` do lhamalog-web.
- A suite Maven não é alterada. A referência de `./mvnw verify` verde (394 testes, 0 falhas) é a `Development` `23a69d9`, rodada na máquina de quem pediu a tarefa em 21/11/2026. Esta entrega não repete essa suite fora do Actions.
- O comando `test` não é exercitado no GitHub nesta entrega, porque o gatilho `issue_comment` só lê o workflow que está em `main`.
- A constituição não é emendada: o Actions não substitui o `make verify` local e não bloqueia o merge.
- A branch desta tarefa é a pedida no card, no formato `LLT-XXX-slug`, sem o prefixo `feature/` citado na constituição.
