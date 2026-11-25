# Feature Specification: Melhorar a tela de perfil do usuário

**Feature Branch**: `LLT-295-melhorar-a-tela-de-perfil-do-usuario`

**Created**: 2026-11-25

**Status**: Draft

**Input**: User description: "LLT-295 (Core), rodada specify + clarify. O Web reformula /perfil como hub de conta. No Core, estender o perfil já existente com bio e cargo opcionais, mantendo a versão otimista e a compatibilidade. Foto, e-mail, função, organização, membro desde, senha, 2FA e sessões permanecem na Clerk, só no Web. Tema e acessibilidade ficam em pergunta aberta."

## Clarifications

### Session 2026-11-25

Nenhuma resposta foi aceita. As cinco perguntas ficam abertas. A recomendação não entra nos requisitos e não autoriza o plano a escolher no lugar do time.

- **Q1 (aberta)** — Qual é o tamanho máximo da bio e do cargo?
  - A: bio 160 caracteres e cargo 80.
  - B: bio 280 caracteres e cargo 120.
  - C: bio e cargo com 200 caracteres, o mesmo teto do nome.
  - **Recomendação:** A. Os dois são texto curto; o cargo do mock cabe em uma linha e a bio do mock é uma frase.
- **Q2 (aberta)** — Que validação e sanitização esses textos recebem?
  - A: o mesmo texto puro do nome: trim, vazio permitido, recusa caractere de controle que não seja tabulação; o Core não interpreta HTML.
  - B: o de A, e também recusa `<` e `>`.
  - C: trim, vazio permitido, e a bio pode ter quebras de linha.
  - **Recomendação:** A. O perfil de hoje já trata o nome assim, e só a própria pessoa lê esse perfil.
- **Q3 (aberta)** — Tema (claro, escuro ou sistema) e acessibilidade ficam no perfil ou só no cliente?
  - A: os dois só no cliente. Esta entrega não os grava.
  - B: o tema fica no perfil; a acessibilidade continua só no cliente.
  - C: tema e acessibilidade ficam no perfil.
  - **Recomendação:** A. O escopo fechado do Core é bio e cargo. O mock mostra o tema como ajuste de aparência, sem lista de acessibilidade.
- **Q4 (aberta)** — Quais são os nomes desses campos na resposta e na gravação?
  - A: `bio` e `jobTitle`.
  - B: `bio` e `job_title`.
  - C: `biography` e `position`.
  - **Recomendação:** A. São nomes em inglês, no mesmo camelCase de `displayName`, e `jobTitle` é o nome já usado para o cargo livre.
- **Q5 (aberta)** — Se a gravação omitir bio ou cargo, o que acontece com o valor já guardado? Como a consulta mostra que não há texto?
  - A: omitir mantém o valor guardado; `""` ou `null` apaga; a consulta devolve `""` quando não há texto.
  - B: omitir apaga o valor guardado; a consulta devolve `""` quando não há texto.
  - C: omitir mantém o valor guardado; a consulta só inclui o campo quando há texto.
  - **Recomendação:** A. O perfil sem esses campos continua válido, o cliente antigo não apaga o que já foi salvo, e a consulta tem sempre a mesma forma.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Informar bio e cargo (Priority: P1)

Como pessoa autenticada, quero guardar uma bio curta e um cargo em texto livre, e quero ver os dois na consulta seguinte do meu perfil.

**Why this priority**: é o dado novo que o hub de conta mostra e que não vem da identidade. Sem isso, o Core não acompanha a tela.

**Independent Test**: com sessão válida e empresa selecionada, gravar bio e cargo junto do nome, do idioma e do fuso, e consultar de novo. A consulta devolve os dois textos confirmados. O papel de acesso não muda.

**Acceptance Scenarios**:

1. **Given** a pessoa já tem perfil, **When** grava uma bio e um cargo com a versão vigente, **Then** a consulta seguinte devolve esses textos e a versão nova.
2. **Given** a pessoa grava o cargo com o mesmo texto do papel de acesso, por exemplo "Administrador", **When** a gravação é aceita, **Then** o cargo guardado é só esse texto. O papel de acesso, a função, a organização e o "membro desde" não mudam.
3. **Given** a versão enviada não é a vigente, **When** a pessoa grava bio e cargo, **Then** a gravação inteira é recusada e bio, cargo, nome, idioma e fuso permanecem como estavam.

---

### User Story 2 - Perfil antigo continua válido (Priority: P1)

Como pessoa que já salvou o perfil, ou como cliente que ainda não conhece bio e cargo, quero consultar e gravar o perfil sem ser obrigada a preencher esses campos.

**Why this priority**: a tarefa pede compatibilidade. Um perfil sem bio e sem cargo continua válido.

**Independent Test**: consultar um perfil gravado antes desta entrega e gravá-lo de novo só com nome, idioma, fuso e a versão vigente. A gravação é aceita. A consulta não falha por ausência de bio ou cargo.

**Acceptance Scenarios**:

1. **Given** o perfil não tem bio nem cargo, **When** a pessoa consulta, **Then** a resposta é válida e nome, idioma, fuso e versão seguem a regra já aceita.
2. **Given** o cliente grava nome, idioma e fuso sem enviar bio nem cargo, **When** a versão está vigente, **Then** a gravação não é recusada por causa desses campos.
3. **Given** não há perfil gravado, **When** a pessoa consulta, **Then** nada é criado, como já acontece hoje.

O efeito de omitir um campo quando já existe valor, e o jeito de mostrar "sem bio" ou "sem cargo", dependem da Q5. Esta história não escolhe entre manter e apagar, nem se o campo vazio aparece na consulta.

---

### User Story 3 - Identidade e segurança continuam fora do Core (Priority: P1)

Como pessoa, quero ajustar foto, e-mail, função, organização, membro desde, senha, verificação em duas etapas e sessões na conta, sem o serviço central passar a guardar ou alterar esses dados.

**Why this priority**: o hub mostra esses dados, e a fronteira já é a da conta. Gravá-los no perfil duplicaria a identidade e exigiria segredo.

**Independent Test**: gravar bio e cargo. A consulta do perfil devolve o que o Core guarda. Foto, e-mail, função, organização, membro desde, senha, verificação em duas etapas e sessões não entram nessa resposta e não são alterados por essa gravação. O Core não chama a conta externa nem cria segredo.

**Acceptance Scenarios**:

1. **Given** a pessoa grava bio e cargo, **When** a consulta responde, **Then** ela não traz foto, e-mail, função, organização, membro desde, senha, verificação em duas etapas nem sessões.
2. **Given** a gravação foi aceita, **When** se observa o que o Core fez, **Then** ele não atualiza a identidade, não altera senha e não cria chave secreta, SDK nem cliente de conta.
3. **Given** a tela do hub tem as áreas Conta, Preferências, Notificações, Segurança e Ajuda, **When** esta entrega termina, **Then** a tela continua no Web. O Core não publica essa página.

---

### Edge Cases

- Bio ou cargo só com espaços: o tratamento é o da Q2. Até a resposta, não há regra fechada de vazio, de tamanho nem de caractere de controle.
- Texto acima do limite e texto com marcação: dependem de Q1 e Q2. A gravação recusada não altera o perfil já gravado.
- Tema claro, escuro ou sistema, e qualquer ajuste de acessibilidade: dependem da Q3. O mock mostra aparência com "Claro" e não mostra acessibilidade. Nenhum dos dois vira requisito fechado nesta spec.
- Duas gravações com a mesma versão: a primeira vale; a segunda é recusada por inteiro, com a mesma orientação já usada quando os dados mudaram.
- Sessão ausente responde 401. Sessão sem empresa é recusada com "Selecione uma empresa para continuar." Nenhum perfil muda.
- Membro e administrador gravam o próprio perfil. Papel administrativo não é exigido.
- A mesma pessoa, em duas empresas, continua com um único perfil. Bio e cargo acompanham esse perfil, não a empresa.
- Gravar o perfil não altera o cadastro da empresa, os cargos de notificação nem as preferências de notificação já existentes.
- Identificadores de pessoa ou de empresa enviados no pedido continuam ignorados.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: A pessoa autenticada, com empresa selecionada, MUST continuar consultando e gravando o próprio perfil de nome, idioma e fuso. As regras já aceitas desses três campos, inclusive obrigatoriedade, limites, rótulos aceitos e versão otimista, MUST permanecer.
- **FR-002**: O perfil MUST poder guardar uma bio de texto curto e um cargo em texto livre. O cargo MUST ser distinto do papel de acesso (administrador ou membro) e dos cargos de notificação. Guardar o texto "Administrador" no cargo MUST NOT alterar o papel de acesso.
- **FR-003**: Bio e cargo MUST ser opcionais. Um perfil sem esses campos MUST continuar válido na consulta e na gravação. O tamanho máximo de cada um é [NEEDS CLARIFICATION: Q1, bio e cargo — 160/80, 280/120 ou 200/200].
- **FR-004**: A validação e a sanitização de bio e cargo são [NEEDS CLARIFICATION: Q2 — texto puro como o nome, texto puro recusando `<` e `>`, ou bio com quebra de linha]. Recusa de validação MUST indicar o campo e MUST NOT alterar o perfil já gravado.
- **FR-005**: Persistir tema (claro, escuro ou sistema) e acessibilidade no perfil, ou deixá-los só no cliente, é [NEEDS CLARIFICATION: Q3]. Esta spec MUST NOT tratá-los como campos fechados enquanto a pergunta estiver aberta.
- **FR-006**: Os nomes de bio e cargo na consulta e na gravação, o efeito de omitir um deles quando já há valor, e a forma de mostrar a ausência ficam nas perguntas abertas Q4 e Q5. Esta spec não escolhe o nome, nem se a omissão mantém ou apaga, nem se a consulta inclui o campo vazio. A omissão MUST NOT, por si só, tornar inválido um perfil que antes era válido.
- **FR-007**: Foto, e-mail, função, organização, membro desde, senha, verificação em duas etapas e sessões MUST permanecer na conta externa. Quem os mostra e altera é o Web, pelo SDK da sessão. O Core MUST NOT gravá-los, lê-los nem devolvê-los neste perfil, e MUST NOT acrescentar chave secreta, SDK, cliente de backend nem atualização de usuário na API dessa conta.
- **FR-008**: A página do hub, com as áreas Conta, Preferências, Notificações, Segurança e Ajuda, MUST permanecer no Web. Esta entrega MUST NOT criar essa tela.
- **FR-009**: Notificações, aparência e ajuda mostradas no hub MUST NOT ser absorvidas por este perfil nesta entrega, salvo se a Q3 vier a incluir o tema ou a acessibilidade. As preferências de notificação já existentes ficam onde estão.
- **FR-010**: Continua valendo o isolamento já aceito: um perfil por pessoa, a mesma pessoa nas duas empresas vê o mesmo perfil, sessão ausente responde 401, sessão sem empresa é recusada com "Selecione uma empresa para continuar.", membro grava o próprio perfil, e gravar o perfil não altera o cadastro da empresa.
- **FR-011**: Consulta sem perfil gravado MUST continuar sem criar perfil. Versão divergente MUST recusar o envio inteiro.
- **FR-012**: Testes de acesso MUST demonstrar a consulta e a gravação de bio e cargo, a aceitação de um perfil sem esses campos, a versão conflitante sem mistura, e a ausência de chamada à conta externa.

### Key Entities

- **Perfil do usuário**: o perfil já existente da pessoa, com nome, idioma, fuso e versão. Passa a poder ter bio e cargo. Não guarda foto, e-mail, função, organização, membro desde, senha, verificação em duas etapas nem sessões.
- **Bio**: texto curto opcional, separado do nome. Tamanho e validação em aberto (Q1, Q2).
- **Cargo**: texto livre opcional. Não é papel de acesso nem cargo de notificação. Nome do campo em aberto (Q4).
- **Conta externa**: origem de foto, e-mail, função, organização, membro desde, senha, verificação em duas etapas e sessões. O Web a usa na sessão. O Core não a altera.
- **Tema e acessibilidade**: preferências de aparência e de uso. Onde persistem está em aberto (Q3). Não são campos desta spec até a resposta.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A pessoa confirma bio e cargo e vê os dois na consulta seguinte, no mesmo fluxo em que já confirma nome, idioma e fuso, em até 1 minuto.
- **SC-002**: Em 100% dos perfis gravados antes desta entrega, a consulta continua válida e uma gravação sem bio e sem cargo continua aceita quando nome, idioma, fuso e versão estão válidos.
- **SC-003**: Em 100% das gravações desta entrega, papel de acesso, cadastro da empresa e dados de conta (foto, e-mail, função, organização, membro desde, senha, verificação em duas etapas e sessões) permanecem como estavam, e a gravação não depende de segredo.
- **SC-004**: Em 100% das versões recusadas, bio e cargo não se misturam com o envio rejeitado.

## Assumptions

- A branch é `LLT-295-melhorar-a-tela-de-perfil-do-usuario`, a partir de `Development`. Esta rodada não planeja, não gera tasks, não implementa e não abre pull request.
- O ponto de partida é o perfil já especificado em `specs/003-manter-perfil-usuario` e no contrato `specs/003-manter-perfil-usuario/contracts/user-profile.md`: consulta e gravação do próprio perfil, versão otimista, nome, idioma e fuso. Esta feature estende esse recurso. Não cria outro.
- A fonte visual do hub é o mock `MOBILE_Administracao_Perfil_Usuario.png` em lhamalog-docs. No mock, a bio é a frase sob a identidade, o cargo livre aparece ao lado da função, e a aparência mostra o tema "Claro". A composição das abas é do Web.
- A decisão de fronteira é a ADR_171 em lhamalog-docs, "Conta do usuário no Web - fronteira Clerk, Core e preferências de perfil". Esta spec só aponta esse documento. Não copia o corpo nem o substitui.
- Bio e cargo seguem o perfil da pessoa, não o da empresa, porque o recurso estendido já é um perfil por pessoa.
- Nome, idioma e fuso não mudam de regra. E-mail da pessoa e notificações continuam fora deste perfil, como na entrega anterior.
- Q1 a Q5 precisam de resposta antes do plano fechar tamanho, validação, tema, acessibilidade, nomes e omissão.
