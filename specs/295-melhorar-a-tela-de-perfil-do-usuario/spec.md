# Feature Specification: Melhorar a tela de perfil do usuário

**Feature Branch**: `LLT-295-melhorar-a-tela-de-perfil-do-usuario`

**Created**: 2026-11-25

**Status**: Draft

**Input**: User description: "LLT-295 (Core), rodada specify + clarify. O Web reformula /perfil como hub de conta. No Core, estender o perfil já existente com bio e cargo opcionais, mantendo a versão otimista e a compatibilidade. Foto, e-mail, função, organização, membro desde, senha, 2FA e sessões permanecem na Clerk, só no Web. Tema e acessibilidade foram fechados na sessão de esclarecimento: ficam só no navegador."

## Clarifications

### Session 2026-11-25

Decisões do usuário nesta rodada. Substituem as cinco perguntas que estavam abertas. A recomendação anterior não foi copiada como requisito: o que vale é a resposta abaixo.

- Q: Qual é o tamanho máximo da bio e do cargo? → A: Bio até 160 caracteres e cargo até 80.
- Q: Que validação e sanitização esses textos recebem? → A: Texto puro igual ao nome: trim, vazio permitido, recusa caractere de controle exceto tabulação, sem quebra de linha. O Core não interpreta HTML.
- Q: Tema (claro, escuro ou sistema) e acessibilidade ficam no perfil ou só no cliente? → A: Os dois ficam só no navegador, fora do Core. Esta entrega não os grava.
- Q: Quais são os nomes desses campos na resposta e na gravação? → A: `bio` e `jobTitle`.
- Q: Se a gravação omitir bio ou cargo, o que acontece com o valor já guardado? Como a consulta mostra que não há texto? → A: Omitir o campo mantém o valor. `""` ou `null` apaga. O GET sempre devolve `""` quando não há texto.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Informar bio e cargo (Priority: P1)

Como pessoa autenticada, quero guardar uma bio curta e um cargo em texto livre, e quero ver os dois na consulta seguinte do meu perfil.

**Why this priority**: é o dado novo que o hub de conta mostra e que não vem da identidade. Sem isso, o Core não acompanha a tela.

**Independent Test**: com sessão válida e empresa selecionada, gravar bio e cargo junto do nome, do idioma e do fuso, e consultar de novo. A consulta devolve os dois textos confirmados. O papel de acesso não muda.

**Acceptance Scenarios**:

1. **Given** a pessoa já tem perfil, **When** grava uma bio e um cargo com a versão vigente, **Then** a consulta seguinte devolve esses textos e a versão nova.
2. **Given** a pessoa grava o cargo com o mesmo texto do papel de acesso, por exemplo "Administrador", **When** a gravação é aceita, **Then** o cargo guardado é só esse texto. O papel de acesso, a função, a organização e o "membro desde" não mudam.
3. **Given** a versão enviada não é a vigente, **When** a pessoa grava bio e cargo, **Then** a gravação inteira é recusada e bio, cargo, nome, idioma e fuso permanecem como estavam.
4. **Given** a bio tem 160 caracteres e o cargo tem 80, **When** a gravação usa a versão vigente, **Then** a consulta devolve os dois textos inteiros. Uma bio de 161 caracteres ou um cargo de 81 é recusado, e o perfil já gravado permanece.

---

### User Story 2 - Perfil antigo continua válido (Priority: P1)

Como pessoa que já salvou o perfil, ou como cliente que ainda não conhece bio e cargo, quero consultar e gravar o perfil sem ser obrigada a preencher esses campos.

**Why this priority**: a tarefa pede compatibilidade. Um perfil sem bio e sem cargo continua válido.

**Independent Test**: consultar um perfil gravado antes desta entrega e gravá-lo de novo só com nome, idioma, fuso e a versão vigente. A gravação é aceita e o texto que já existia permanece. A consulta devolve `""` em `bio` e em `jobTitle` quando não há texto.

**Acceptance Scenarios**:

1. **Given** o perfil não tem bio nem cargo, **When** a pessoa consulta, **Then** a resposta é válida, `bio` e `jobTitle` vêm como `""`, e nome, idioma, fuso e versão seguem a regra já aceita.
2. **Given** o cliente grava nome, idioma e fuso sem enviar `bio` nem `jobTitle`, **When** a versão está vigente, **Then** a gravação é aceita e o valor já guardado de cada campo omitido permanece. Se não havia texto, a consulta segue com `""`.
3. **Given** não há perfil gravado, **When** a pessoa consulta, **Then** nada é criado, como já acontece hoje, e `bio` e `jobTitle` vêm como `""`.
4. **Given** a bio ou o cargo já tem texto, **When** a gravação envia `""` ou `null` nesse campo, com a versão vigente, **Then** esse campo é apagado e a consulta seguinte devolve `""`. O outro campo, se foi omitido, permanece.

---

### User Story 3 - Identidade e segurança continuam fora do Core (Priority: P1)

Como pessoa, quero ajustar foto, e-mail, função, organização, membro desde, senha, verificação em duas etapas e sessões na conta, sem o serviço central passar a guardar ou alterar esses dados. Tema e acessibilidade continuam só no navegador.

**Why this priority**: o hub mostra esses dados, e a fronteira já é a da conta. Gravá-los no perfil duplicaria a identidade e exigiria segredo.

**Independent Test**: gravar bio e cargo. A consulta do perfil devolve o que o Core guarda. Foto, e-mail, função, organização, membro desde, senha, verificação em duas etapas, sessões, tema e acessibilidade não entram nessa resposta e não são alterados por essa gravação. O Core não chama a conta externa nem cria segredo.

**Acceptance Scenarios**:

1. **Given** a pessoa grava bio e cargo, **When** a consulta responde, **Then** ela não traz foto, e-mail, função, organização, membro desde, senha, verificação em duas etapas nem sessões.
2. **Given** a gravação foi aceita, **When** se observa o que o Core fez, **Then** ele não atualiza a identidade, não altera senha e não cria chave secreta, SDK nem cliente de conta.
3. **Given** a tela do hub tem as áreas Conta, Preferências, Notificações, Segurança e Ajuda, **When** esta entrega termina, **Then** a tela continua no Web. O Core não publica essa página.
4. **Given** o pedido traz tema ou acessibilidade, **When** a gravação é aceita, **Then** esses dados não são guardados e a consulta não os devolve.

---

### Edge Cases

- Bio ou cargo só com espaços: o trim deixa o texto vazio. Vazio é permitido e apaga o valor já guardado, no mesmo efeito de `""`.
- Bio acima de 160 caracteres, cargo acima de 80, ou texto com caractere de controle que não seja tabulação (incluindo quebra de linha): a gravação é recusada, o campo é indicado e o perfil já gravado não muda. O critério de contagem é o mesmo já usado no nome, depois do trim.
- Texto com marcação, por exemplo `<b>oi</b>`: o Core guarda os caracteres como texto puro. Não interpreta HTML e não remove `<` nem `>`.
- Tema claro, escuro ou sistema, e qualquer ajuste de acessibilidade: ficam só no navegador. Esta entrega não os grava nem os devolve.
- Duas gravações com a mesma versão: a primeira vale; a segunda é recusada por inteiro, com a mesma orientação já usada quando os dados mudaram.
- Sessão ausente responde 401. Sessão sem empresa é recusada com "Selecione uma empresa para continuar." Nenhum perfil muda.
- Membro e administrador gravam o próprio perfil. Papel administrativo não é exigido.
- A mesma pessoa, em duas empresas, continua com um único perfil. Bio e cargo acompanham esse perfil, não a empresa.
- Gravar o perfil não altera o cadastro da empresa, os cargos de notificação nem as preferências de notificação já existentes.
- Identificadores de pessoa ou de empresa enviados no pedido continuam ignorados. Tema e acessibilidade enviados no pedido também são ignorados.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: A pessoa autenticada, com empresa selecionada, MUST continuar consultando e gravando o próprio perfil de nome, idioma e fuso. As regras já aceitas desses três campos, inclusive obrigatoriedade, limites, rótulos aceitos e versão otimista, MUST permanecer.
- **FR-002**: O perfil MUST poder guardar uma bio de texto curto e um cargo em texto livre. O cargo MUST ser distinto do papel de acesso (administrador ou membro) e dos cargos de notificação. Guardar o texto "Administrador" no cargo MUST NOT alterar o papel de acesso.
- **FR-003**: Bio e cargo MUST ser opcionais. Um perfil sem esses campos MUST continuar válido na consulta e na gravação. A bio MUST ter no máximo 160 caracteres e o cargo no máximo 80, no mesmo critério de contagem já usado no nome, depois do trim. O valor no limite MUST ser aceito. Um caractere além do limite MUST ser recusado.
- **FR-004**: Bio e cargo MUST ser texto puro, no mesmo tratamento do nome: trim; vazio permitido; caractere de controle recusado, exceto tabulação; quebra de linha recusada. O Core MUST NOT interpretar HTML e MUST NOT remover `<` nem `>`. Recusa de validação MUST indicar o campo e MUST NOT alterar o perfil já gravado. Texto acima do limite ou com caractere de controle recusado usa a mesma orientação já dada ao nome ("O texto excede o limite permitido."). Tipo que não é texto usa "Confira o preenchimento deste campo."
- **FR-005**: Tema (claro, escuro ou sistema) e acessibilidade MUST permanecer só no navegador. O Core MUST NOT gravá-los, lê-los nem devolvê-los neste perfil.
- **FR-006**: Na consulta e na gravação, a bio MUST se chamar `bio` e o cargo MUST se chamar `jobTitle`. Omitir o campo MUST manter o valor já guardado. `""` ou `null` MUST apagar esse campo. Texto que fica vazio depois do trim MUST apagar, no mesmo efeito de `""`. A consulta (`GET`) e a resposta da gravação aceita MUST sempre devolver as duas chaves, com `""` quando não há texto, inclusive quando o perfil antigo não tem o campo e quando não há perfil gravado. A omissão MUST NOT, por si só, tornar inválido um perfil que antes era válido.
- **FR-007**: Foto, e-mail, função, organização, membro desde, senha, verificação em duas etapas e sessões MUST permanecer na conta externa. Quem os mostra e altera é o Web, pelo SDK da sessão. O Core MUST NOT gravá-los, lê-los nem devolvê-los neste perfil, e MUST NOT acrescentar chave secreta, SDK, cliente de backend nem atualização de usuário na API dessa conta.
- **FR-008**: A página do hub, com as áreas Conta, Preferências, Notificações, Segurança e Ajuda, MUST permanecer no Web. Esta entrega MUST NOT criar essa tela.
- **FR-009**: Notificações, aparência e ajuda mostradas no hub MUST NOT ser absorvidas por este perfil nesta entrega. Tema e acessibilidade não entram no perfil. As preferências de notificação já existentes ficam onde estão.
- **FR-010**: Continua valendo o isolamento já aceito: um perfil por pessoa, a mesma pessoa nas duas empresas vê o mesmo perfil, sessão ausente responde 401, sessão sem empresa é recusada com "Selecione uma empresa para continuar.", membro grava o próprio perfil, e gravar o perfil não altera o cadastro da empresa.
- **FR-011**: Consulta sem perfil gravado MUST continuar sem criar perfil. Versão divergente MUST recusar o envio inteiro, inclusive bio e cargo desse envio.
- **FR-012**: Testes de acesso MUST demonstrar a consulta e a gravação de bio e cargo, o limite de 160 e de 80, a recusa de quebra de linha sem alterar o perfil, a aceitação de um perfil sem esses campos, a omissão que mantém o valor, `""` ou `null` que apaga, a consulta que devolve `""` quando não há texto, a versão conflitante sem mistura, e a ausência de chamada à conta externa. O perfil gravado antes desta entrega, sem `bio` e sem `jobTitle`, MUST continuar legível.

### Key Entities

- **Perfil do usuário**: o perfil já existente da pessoa, com nome, idioma, fuso e versão. Passa a poder ter `bio` e `jobTitle`. Não guarda foto, e-mail, função, organização, membro desde, senha, verificação em duas etapas, sessões, tema nem acessibilidade.
- **Bio** (`bio`): texto curto opcional, separado do nome, até 160 caracteres depois do trim. Vazio é ausência de texto. A consulta devolve `""` quando não há texto.
- **Cargo** (`jobTitle`): texto livre opcional, até 80 caracteres depois do trim. Não é papel de acesso nem cargo de notificação. A consulta devolve `""` quando não há texto.
- **Conta externa**: origem de foto, e-mail, função, organização, membro desde, senha, verificação em duas etapas e sessões. O Web a usa na sessão. O Core não a altera.
- **Tema e acessibilidade**: preferências de aparência e de uso que ficam só no navegador. Não são campos deste perfil.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A pessoa confirma bio e cargo e vê os dois na consulta seguinte, no mesmo fluxo em que já confirma nome, idioma e fuso, em até 1 minuto.
- **SC-002**: Em 100% dos perfis gravados antes desta entrega, a consulta continua válida, `bio` e `jobTitle` vêm como `""` quando não há texto, e uma gravação sem esses campos continua aceita quando nome, idioma, fuso e versão estão válidos, sem apagar texto que já existia.
- **SC-003**: Em 100% das gravações desta entrega, papel de acesso, cadastro da empresa e dados de conta (foto, e-mail, função, organização, membro desde, senha, verificação em duas etapas e sessões) permanecem como estavam, tema e acessibilidade não passam a ser gravados, e a gravação não depende de segredo.
- **SC-004**: Em 100% das versões recusadas, bio e cargo não se misturam com o envio rejeitado.

## Assumptions

- A branch é `LLT-295-melhorar-a-tela-de-perfil-do-usuario`, a partir de `Development`. Esta rodada grava as respostas, o plano, as tasks e a análise. Não implementa código e não abre pull request.
- O ponto de partida é o perfil já especificado em `specs/003-manter-perfil-usuario` e no contrato `specs/003-manter-perfil-usuario/contracts/user-profile.md`: consulta e gravação do próprio perfil, versão otimista, nome, idioma e fuso. Esta feature estende esse recurso. Não cria outro.
- A fonte visual do hub é o mock `MOBILE_Administracao_Perfil_Usuario.png` em lhamalog-docs. No mock, a bio é a frase sob a identidade, o cargo livre aparece ao lado da função, e a aparência mostra o tema "Claro". A composição das abas é do Web. O tema do mock não entra no perfil.
- A decisão de fronteira é a ADR_171 em lhamalog-docs, "Conta do usuário no Web - fronteira Clerk, Core e preferências de perfil". Esta spec só aponta esse documento. Não copia o corpo nem o substitui.
- Bio e cargo seguem o perfil da pessoa, não o da empresa, porque o recurso estendido já é um perfil por pessoa.
- Nome, idioma e fuso não mudam de regra. E-mail da pessoa e notificações continuam fora deste perfil, como na entrega anterior.
- Q1 a Q5 estão respondidas nesta sessão. O plano usa esses limites, essa validação, a permanência de tema e acessibilidade no navegador, os nomes `bio` e `jobTitle`, e a omissão que mantém o valor.
