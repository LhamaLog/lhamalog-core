# Feature Specification: Manter contexto do produto

**Feature Branch**: `feature/LLT-178-contexto-produto`

**Created**: 10/11/2026

**Status**: Draft

**Input**: User description: "Manter contexto do produto (LLT-178): como usuário, quero descrever o produto (tecnologias, arquitetura, observações) para a IA. Contexto é salvo por produto e versionado. É enviado à LLM junto com o contexto da empresa ao pedir sugestão. Decisões: campos da tela 'Contexto para IA' do Figma (descrição funcional, personalidade, idioma da resposta, arquitetura, escopo e dados sensíveis); versionamento no mesmo padrão do contexto da empresa (LLT-176); nesta entrega, o texto para a IA contém só o produto — a junção com o contexto da empresa fica para quando a LLT-176 entrar na Development."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Descrever o produto para a IA (Priority: P1)

Um membro com acesso ao produto preenche o contexto para a IA: o que o produto faz, o nível de detalhe e o idioma das respostas, as tecnologias da arquitetura, o que está no escopo e quais dados são sensíveis. Os campos são os da tela "Contexto para IA" do protótipo. O contexto é gravado junto do produto e volta igual ao recarregar.

**Why this priority**: é o conteúdo que a IA vai usar para entender o produto; sem ele as sugestões ficam genéricas.

**Independent Test**: cadastrar um produto com contexto, editar o contexto e recarregar, conferindo os seis campos.

**Acceptance Scenarios**:

1. **Given** um produto da empresa ativa e um membro com acesso, **When** ele grava descrição funcional, personalidade, idioma, arquitetura, escopo e dados sensíveis, **Then** os valores persistem e reaparecem iguais ao consultar o produto.
2. **Given** um contexto com espaços nas pontas dos textos e itens vazios nas listas, **When** ele é gravado, **Then** os textos ficam sem espaços nas pontas e os itens vazios são descartados, mantendo a ordem dos demais.
3. **Given** um contexto sem personalidade ou sem idioma, **When** ele é gravado, **Then** a personalidade fica "Padrão" e o idioma fica "pt-BR".
4. **Given** um produto cadastrado sem contexto, **When** ele é consultado, **Then** o contexto aparece vazio, com personalidade "Padrão" e idioma "pt-BR".
5. **Given** valores acima dos limites ou uma personalidade desconhecida, **When** o usuário grava, **Then** a gravação é recusada com 400 e nada muda.

---

### User Story 2 - Histórico de versões do contexto (Priority: P1)

Cada alteração do contexto gera uma nova versão numerada, com data e autor. O usuário consulta a lista de versões, da mais recente para a mais antiga, e cada versão isolada.

**Why this priority**: é critério de aceite do card ("contexto é salvo por produto e versionado") e permite saber o que a IA recebia em cada momento.

**Independent Test**: cadastrar com contexto, alterar duas vezes, gravar uma vez sem mudança e conferir que existem 3 versões, cada uma com o conteúdo daquele momento.

**Acceptance Scenarios**:

1. **Given** um produto cadastrado com contexto preenchido, **When** a lista de versões é consultada, **Then** existe a versão 1, com o autor do cadastro.
2. **Given** um produto cadastrado sem contexto, **When** a lista de versões é consultada, **Then** ela está vazia.
3. **Given** um produto com versões, **When** o contexto é alterado, **Then** surge uma versão com número seguinte, data e autor, e as anteriores ficam iguais.
4. **Given** um produto com versões, **When** o contexto é gravado sem nenhuma mudança, **Then** nenhuma versão nova é criada.
5. **Given** um número de versão inexistente, **When** ele é consultado, **Then** a resposta é 404.
6. **Given** um produto arquivado, **When** a lista de versões é consultada, **Then** ela continua disponível; a edição do contexto continua recusada como na 002.

---

### User Story 3 - Texto do contexto para a IA (Priority: P2)

O sistema monta, a partir do contexto atual do produto, a seção de texto que será enviada à LLM ao pedir sugestão. O texto deixa claro que é dado do cliente e não instrução ao modelo, no mesmo formato do contexto da empresa.

**Why this priority**: prepara o envio à LLM pedido no card. O módulo de Sugestão ainda não existe, então esta entrega fornece o texto pronto; juntar com o contexto da empresa fica para a integração.

**Independent Test**: preencher o contexto, consultar o texto para a IA e conferir nome do produto, campos e o aviso de que não houve chamada à LLM.

**Acceptance Scenarios**:

1. **Given** um produto com contexto, **When** o texto para a IA é consultado, **Then** ele contém o nome do produto e todos os campos preenchidos, com rótulos em português.
2. **Given** textos com quebras de linha ou caracteres de controle, **When** o texto é montado, **Then** cada campo fica em uma única linha, sem caracteres de controle.
3. **Given** qualquer consulta do texto, **When** ele é devolvido, **Then** a resposta informa que nenhuma chamada à LLM foi feita.

---

### Edge Cases

- Membro sem acesso ao produto, ou produto de outra empresa: 404 em contexto, versões e texto para a IA, sem revelar se o produto existe.
- Gravação concorrente com `version` desatualizado: 409, como na 001; nenhuma versão nova.
- Produto arquivado: versões e texto para a IA disponíveis; gravar contexto recusado com 409 (regra da 002).
- Itens repetidos em uma lista: mantidos como digitados (não há deduplicação).
- Documentos gravados com os campos antigos (`problemSolved`, `targetAudience`, `differentiators`, `keywords`, `restrictions`): não há dados de produção; os campos antigos são ignorados e o contexto aparece vazio.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O contexto do produto MUST ter os campos: descrição funcional (texto), personalidade (`SIMPLE`, `STANDARD` ou `DETAILED`), idioma da resposta (código de idioma, ex.: `pt-BR`), arquitetura (lista), escopo (lista) e dados sensíveis (lista).
- **FR-002**: Todos os campos MUST ser opcionais, mantendo a decisão da 001 de contexto opcional; sem personalidade MUST valer `STANDARD` e sem idioma MUST valer `pt-BR`.
- **FR-003**: Textos MUST ser gravados sem espaços nas pontas; itens de lista vazios MUST ser descartados, preservando a ordem dos demais.
- **FR-004**: Limites: descrição funcional até 2.000 caracteres; cada lista até 30 itens; item de arquitetura até 80 caracteres; item de escopo e de dados sensíveis até 200 caracteres; idioma até 20 caracteres no formato de código de idioma. Acima disso MUST responder 400 sem alterar nada.
- **FR-005**: O cadastro com contexto preenchido MUST criar a versão 1; o cadastro com contexto vazio MUST NOT criar versão.
- **FR-006**: Cada gravação de contexto com conteúdo diferente do atual MUST criar uma nova versão com número seguinte, data e autor; gravação sem mudança MUST NOT criar versão.
- **FR-007**: Versões MUST NOT ser alteradas nem apagadas por esta feature.
- **FR-008**: MUST existir consulta da lista de versões (mais recente primeiro) e de uma versão pelo número.
- **FR-009**: MUST existir consulta do texto para a IA, montado a partir do contexto atual, contendo o nome do produto e os campos preenchidos, marcado como dado do cliente e não instrução ao modelo, com cada campo em uma linha e sem caracteres de controle.
- **FR-010**: A resposta do texto para a IA MUST indicar que nenhuma chamada à LLM foi feita.
- **FR-011**: Contexto, versões e texto MUST respeitar o acesso ao produto e o isolamento por empresa da 001 (404 para quem não pode ver).
- **FR-012**: Produto arquivado MUST manter versões e texto consultáveis e MUST continuar recusando a gravação de contexto (002).
- **FR-013**: Esta feature MUST NOT chamar a LLM, MUST NOT juntar o contexto da empresa e MUST NOT alterar a ingestão de telemetria.

### Key Entities

- **Contexto do produto**: os seis campos acima, gravados dentro do Produto.
- **Versão do contexto**: cópia do contexto em um momento, com número sequencial por produto, data e autor; nunca muda depois de criada.
- **Texto para a IA**: seção de texto derivada do contexto atual, pronta para compor o prompt da Sugestão.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Em 100% dos testes, o contexto gravado reaparece igual ao consultar o produto.
- **SC-002**: Em 100% das gravações com mudança surge exatamente uma versão nova; em 100% das gravações sem mudança, nenhuma.
- **SC-003**: 100% das gravações acima dos limites são recusadas com 400 sem alterar produto nem versões.
- **SC-004**: 100% das consultas de contexto, versões ou texto por quem não pode ver o produto respondem 404.
- **SC-005**: Em 100% dos testes, o texto para a IA contém o nome do produto e cada campo preenchido, sem caracteres de controle.

## Assumptions

- Cadastro, edição, arquivamento, papéis, acesso e isolamento das features 001 e 002 permanecem válidos.
- Os campos seguem o protótipo do Figma, como o contexto da empresa (LLT-176) já segue. Os campos antigos da 001 deixam de existir; não há dados de produção.
- No Figma a descrição funcional e a personalidade aparecem como obrigatórias. Aqui ficam opcionais (com padrão para a personalidade) para manter a decisão da 001 de contexto opcional; a tela pode exigir o preenchimento.
- O formato do versionamento e do texto para a IA segue o contexto da empresa da LLT-176 (lista de versões, versão por número e `{prompt, executed}`).
- A tela de produto no `lhamalog-web` usa os campos antigos e precisará ser ajustada em tarefa própria do front.
- Fora de escopo: chamada à LLM, junção com o contexto da empresa, restauração de versão antiga, exclusão de versões, nome único (LLT-177) e bloqueio de ingestão.
