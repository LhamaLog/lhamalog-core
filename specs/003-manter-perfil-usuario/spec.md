# Feature Specification: Manter perfil do usuário

**Feature Branch**: `LLT-174-manter-perfil-do-usuario`

**Created**: 2026-11-12

**Status**: Draft

**Input**: User description: "LLT-174 Manter perfil do usuário (épico LLT-99, feature LLT-121, 8 pontos). Como usuário, quero ajustar nome, idioma e fuso horário. O fuso vale para painéis, agendamento de varredura e e-mails quando esses consumidores já existirem. A identidade na Clerk permanece alinhada ao nome. Fora de escopo: LLT-172 (e-mail) e LLT-173 (notificações). O Core persiste e expõe o perfil; a tela fica no Web."

## Clarifications

### Session 2026-11-12

Decisões do produto já fornecidas nesta execução. Não foram reabertas.

- Q: Onde o perfil é gravado e onde a tela fica? → A: O Core grava e devolve o perfil da pessoa autenticada. A tela fica no Web, em outro repositório, fora desta entrega.
- Q: O fuso horário deve passar a valer em painéis, agendamento de varredura e e-mails? → A: Só onde esses consumidores já existem. Neste Core eles não existem. Esta entrega não cria painel, agendamento nem envio de e-mail. O fuso fica gravado e disponível para esses consumidores nas entregas deles.
- Q: O idioma traduz todo o produto? → A: Não. O Core grava e devolve a preferência. A interface Web aplica o idioma. Traduzir a aplicação inteira fica fora desta entrega.
- Q: E-mail da pessoa e notificações entram aqui? → A: Não. LLT-172 e LLT-173 permanecem fora.

### Session 2026-11-12 — alinhamento do contrato

Decisões do produto confirmadas depois da primeira spec. Substituem o item em aberto da identidade.

- Q: Quem devolve o nome à Clerk? → A: O Web, depois que o Core aceita a gravação, pela sessão do navegador (`user.update`). O Core guarda `displayName` e o devolve na consulta e na gravação. O Core não chama a Clerk e não cria segredo, SDK, cliente de backend nem `PATCH /v1/users`. O critério de aceite da identidade continua atendido por essa divisão, sem segredo neste repositório.
- Q: O Core deve recusar idioma que não seja `pt-BR` ou `en`? → A: Não. O Core mantém a etiqueta já aceita no cadastro (`pt-BR`, `en` e `en-US` são válidos). A tela do Web só oferece `pt-BR` e `en`.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ajustar o próprio perfil (Priority: P1)

Como usuário autenticado, quero consultar e ajustar meu nome, meu idioma e meu fuso horário para que a próxima consulta mostre exatamente o que confirmei.

**Why this priority**: Sem gravar e reler esses três dados, o usuário não cumpre o objetivo da entrega. O Web depende dessa leitura e dessa gravação.

**Independent Test**: Com uma sessão válida e empresa selecionada, consultar o perfil, gravar nome, idioma e fuso, e consultar de novo. A segunda consulta devolve os três valores confirmados. Uma gravação inválida não substitui o que já estava gravado.

**Acceptance Scenarios**:

1. **Given** a pessoa autenticada ainda não gravou perfil, **When** consulta o próprio perfil, **Then** recebe nome vazio, idioma `pt-BR`, fuso `America/Sao_Paulo` e nenhuma versão gravada, e essa consulta não cria o perfil.
2. **Given** não há perfil gravado, **When** a pessoa confirma nome, idioma e fuso com versão ausente, **Then** a consulta seguinte devolve esses três valores e uma versão conhecida.
3. **Given** o perfil já está gravado, **When** a pessoa envia os três campos, muda um deles e confirma a versão vigente, **Then** a consulta seguinte mostra o valor novo e mantém os outros dois como foram enviados.
4. **Given** o perfil já está gravado, **When** duas confirmações partem da mesma versão conhecida, **Then** só a primeira prevalece e a segunda é recusada, sem misturar os dois envios.
5. **Given** o perfil já está gravado, **When** a pessoa envia nome vazio, idioma irreconhecível ou fuso irreconhecível, **Then** a gravação é recusada, o campo recusado é indicado e o perfil permanece como estava.
6. **Given** a pessoa informa o rótulo de idioma ou de fuso já aceito no cadastro da empresa, **When** a gravação é aceita, **Then** a consulta devolve a etiqueta de idioma e o identificador de fuso, não o rótulo.

---

### User Story 2 - Só o próprio perfil (Priority: P1)

Como usuário, quero que ninguém consulte nem altere o meu perfil no lugar do próprio, e quero que a minha sessão continue valendo só para mim.

**Why this priority**: Nome, idioma e fuso são dados da pessoa. Sem isolamento, uma sessão lê ou grava o perfil de outra.

**Independent Test**: Duas pessoas da mesma empresa gravam perfis diferentes. Cada consulta devolve só o perfil de quem está na sessão. Um pedido que cita a outra pessoa, outra empresa ou outro identificador continua gravando só o perfil de quem está autenticado. A mesma pessoa, em duas empresas, vê o mesmo perfil.

**Acceptance Scenarios**:

1. **Given** Ana e Bruno, da mesma empresa, gravaram perfis diferentes, **When** Ana consulta ou altera o perfil, **Then** o resultado é só o perfil de Ana e o de Bruno permanece intacto.
2. **Given** a sessão é de Ana, **When** o pedido cita o identificador de Bruno ou de outra empresa, **Then** a operação continua sendo o perfil de Ana e o de Bruno não aparece nem muda.
3. **Given** Ana grava o perfil com a empresa A selecionada, **When** a mesma Ana consulta o perfil com a empresa B selecionada, **Then** recebe o mesmo nome, idioma e fuso.
4. **Given** a sessão é de um membro que não é administrador, **When** ele consulta ou grava o próprio perfil, **Then** a operação é aceita. O papel de administrador não é exigido.
5. **Given** não há sessão, **When** alguém tenta consultar ou gravar o perfil, **Then** a resposta é 401 e não há acesso a perfil nenhum.
6. **Given** a sessão não tem empresa selecionada, **When** a pessoa tenta consultar ou gravar o perfil, **Then** a operação é recusada com a mesma orientação já usada no produto para selecionar uma empresa, e nenhum perfil muda.

---

### User Story 3 - Nome gravado no Core e identidade no Web (Priority: P1)

Como usuário, quero que o nome que confirmo fique gravado no LhamaLog e que a identidade na Clerk acompanhe essa alteração pela tela, sem o serviço central chamar a Clerk.

**Why this priority**: O aceite pede a identidade sincronizada. Quem faz isso é o Web, na sessão do navegador, depois que a gravação é aceita. O Core guarda o nome e o devolve. Não cria segredo e não chama a Clerk.

**Independent Test**: A pessoa grava um nome novo e consulta de novo. A consulta devolve o nome gravado. A gravação não chama a Clerk e não depende de segredo. O teste do Core prova a ausência dessa chamada. O alinhamento da identidade fica com o Web.

**Acceptance Scenarios**:

1. **Given** a pessoa autenticada confirma um nome novo, **When** a consulta seguinte é feita, **Then** o nome devolvido é o que ela confirmou no LhamaLog.
2. **Given** a gravação do nome foi aceita, **When** a identidade precisa acompanhar, **Then** o Web atualiza a Clerk na sessão do navegador (`user.update`). O Core não chama a Clerk e não cria segredo, SDK, cliente de backend nem atualização de usuário na API da Clerk.
3. **Given** o nome da pessoa é alterado fora do LhamaLog, **When** ela consulta o perfil sem gravar de novo aqui, **Then** a consulta continua devolvendo o último nome gravado no LhamaLog. O Core não escuta mudanças externas.
4. **Given** a gravação do nome é aceita, **When** a pessoa consulta o perfil, **Then** idioma e fuso são os gravados no perfil, não valores lidos da Clerk.

---

### User Story 4 - Fuso e idioma sem mudar o resto do produto (Priority: P1)

Como usuário, quero que meu fuso e meu idioma fiquem guardados para o que vier a usá-los, sem alterar o cadastro da empresa e sem criar painel, agendamento de varredura, e-mail ou tradução do produto.

**Why this priority**: O aceite cita painéis, agendamento e e-mails. Esses consumidores não existem neste Core. Tratar o fuso da pessoa como se fosse o da empresa misturaria dois cadastros.

**Independent Test**: A empresa já tem fuso e idioma próprios. A pessoa grava outro fuso e outro idioma no perfil. A consulta do perfil devolve os valores da pessoa. A consulta da empresa devolve os valores da empresa, iguais aos de antes. Não surge painel, agenda de varredura, envio de e-mail nem texto traduzido por esta entrega.

**Acceptance Scenarios**:

1. **Given** a empresa tem fuso e idioma já gravados, **When** a pessoa grava outro fuso e outro idioma no próprio perfil, **Then** o perfil muda e o cadastro da empresa permanece igual, inclusive fuso, idioma, logotipo e demais dados.
2. **Given** não há painel, agendamento de varredura nem envio de e-mail neste produto, **When** a pessoa grava o fuso, **Then** o fuso fica disponível na consulta do perfil e nenhum desses consumidores é criado.
3. **Given** a pessoa grava o idioma `en`, `pt-BR` ou `en-US`, **When** a consulta do perfil responde, **Then** o idioma gravado volta na resposta. A tela só oferece `pt-BR` e `en`, e o Core não recusa `en-US`. As mensagens já existentes do produto permanecem em português.
4. **Given** a entrega é só o perfil, **When** a pessoa grava nome, idioma e fuso, **Then** e-mail da pessoa e preferências de notificação não fazem parte do perfil e não são alterados.

---

### Edge Cases

- Consulta sem perfil gravado devolve nome vazio, idioma `pt-BR`, fuso `America/Sao_Paulo` e versão ausente. Nada é gravado nessa consulta.
- Nome vazio ou só com espaços é recusado com "Informe o nome." O perfil não muda.
- Nome acima de 200 caracteres, ou com caractere de controle que não seja tabulação, é recusado com "O texto excede o limite permitido." O perfil não muda.
- Idioma vazio é recusado com "Informe o idioma." Idioma que não seja uma etiqueta reconhecida é recusado com "Informe um idioma válido."
- Os rótulos "Português (Brasil)" e "Portugues (Brasil)" são aceitos e gravados como `pt-BR`.
- Fuso vazio é recusado com "Informe o fuso horário." Fuso que não seja um identificador reconhecido é recusado com "Informe um fuso horário válido."
- Os rótulos "(UTC-03:00) Brasília" e "(UTC-03:00) Brasilia" são aceitos e gravados como `America/Sao_Paulo`. A consulta devolve o identificador, não o rótulo.
- Campo ausente, de tipo errado ou corpo ilegível é recusado. O perfil não muda.
- Duas gravações com a mesma versão conhecida: a primeira vale; a segunda é recusada com a mesma orientação já usada quando os dados mudaram e precisam ser recarregados.
- Duas primeiras gravações da mesma pessoa, ambas sem versão: uma prevalece e a outra é recusada com essa mesma orientação. O perfil não mistura os dois envios.
- Sessão ausente responde 401 e não consulta nem grava. Sessão sem empresa usa a recusa já existente: "Selecione uma empresa para continuar."
- Identificadores de pessoa ou de empresa enviados no pedido são ignorados.
- Membro e administrador gravam o próprio perfil. Não há recusa por falta de papel administrativo.
- Gravar o perfil não altera fuso nem idioma da empresa, não apaga cadastro e não mexe em produtos.
- Não há consumidor de painel, agenda de varredura ou e-mail para receber o fuso nesta entrega.
- O Core não consulta nem atualiza a Clerk. O nome da consulta e da gravação aceita é o último nome gravado no LhamaLog. Quem devolve o nome à Clerk é o Web, na sessão do navegador, depois do sucesso.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema MUST permitir que a pessoa autenticada, com empresa selecionada, consulte o próprio perfil de nome, idioma e fuso horário.
- **FR-002**: Quando não houver perfil gravado, a consulta MUST devolver nome vazio, idioma `pt-BR`, fuso `America/Sao_Paulo` e versão ausente, e MUST NOT gravar um perfil nessa consulta.
- **FR-003**: A pessoa MUST poder gravar nome, idioma e fuso do próprio perfil. Os três valores são obrigatórios na gravação. A consulta seguinte MUST devolver os valores da última gravação aceita.
- **FR-004**: A gravação MUST substituir nome, idioma e fuso pelos três valores enviados. Os três MUST estar presentes. Campo omitido MUST ser recusado e o perfil MUST permanecer como estava. A versão conhecida MUST ser enviada quando o perfil já existe. Versão divergente MUST ser recusada sem misturar os dois envios, com a mesma orientação já usada no cadastro da empresa.
- **FR-005**: O nome MUST ser texto de até 200 caracteres, sem caractere de controle exceto tabulação, depois de remover espaços nas pontas. Nome vazio MUST ser recusado com "Informe o nome." Texto longo demais ou com outro caractere de controle MUST ser recusado com "O texto excede o limite permitido."
- **FR-006**: O idioma MUST ser gravado como etiqueta no formato já aceito no cadastro da empresa. `pt-BR`, `en` e `en-US` são válidos. A tela do Web só oferece `pt-BR` e `en`. O Core MUST NOT recusar `en-US` nem outra etiqueta que caiba nesse formato. Idioma vazio MUST ser recusado com "Informe o idioma." Etiqueta irreconhecível MUST ser recusada com "Informe um idioma válido." Os rótulos "Português (Brasil)" e "Portugues (Brasil)" MUST ser gravados como `pt-BR`.
- **FR-007**: O fuso MUST ser gravado como identificador reconhecido, no exemplo `America/Sao_Paulo`. Fuso vazio MUST ser recusado com "Informe o fuso horário." Valor irreconhecível MUST ser recusado com "Informe um fuso horário válido." Os rótulos "(UTC-03:00) Brasília" e "(UTC-03:00) Brasilia" MUST ser gravados como `America/Sao_Paulo`.
- **FR-008**: Recusa de validação MUST indicar o campo e MUST NOT alterar o perfil já gravado. Corpo ilegível MUST ser recusado sem alterar o perfil.
- **FR-009**: Esta entrega MUST NOT traduzir as mensagens já existentes do produto nem o restante da aplicação. O idioma do perfil é preferência gravada para o Web aplicar.
- **FR-010**: O nome devolvido na consulta MUST ser o último nome gravado no LhamaLog para aquela pessoa. Idioma e fuso devolvidos MUST ser os gravados no perfil, não valores lidos da Clerk.
- **FR-011**: Depois de uma gravação aceita, o alinhamento do nome com a identidade na Clerk MUST ser feito pelo Web, na sessão do navegador (`user.update`). O Core MUST gravar o nome e devolvê-lo na consulta e na resposta da gravação aceita. O Core MUST NOT acrescentar `CLERK_SECRET_KEY`, SDK da Clerk, cliente de API de backend nem `PATCH /v1/users`. O critério de aceite da identidade fica atendido por essa divisão, sem segredo neste repositório.
- **FR-012**: Mudança de nome feita fora do LhamaLog MUST NOT alterar o perfil já gravado. Esta entrega não escuta essa mudança.
- **FR-013**: Cada pessoa MUST ter no máximo um perfil, identificado pela pessoa da sessão, não pela empresa. A mesma pessoa em duas empresas MUST ver o mesmo perfil. Duas pessoas MUST NOT compartilhar perfil.
- **FR-014**: Consulta e gravação MUST usar somente a pessoa da sessão autenticada. Identificador de pessoa ou de empresa enviado no pedido MUST ser ignorado.
- **FR-015**: Sessão ausente MUST responder 401 e MUST impedir consulta e gravação. Sessão sem empresa selecionada MUST ser recusada com "Selecione uma empresa para continuar." e MUST NOT alterar perfil.
- **FR-016**: Membro e administrador da empresa selecionada MUST poder consultar e gravar o próprio perfil. Papel administrativo MUST NOT ser exigido para este perfil.
- **FR-017**: Gravar o perfil MUST NOT alterar o cadastro da empresa, inclusive o fuso e o idioma desse cadastro, o logotipo, os produtos e os vínculos de acesso.
- **FR-018**: O fuso gravado MUST ficar disponível na consulta do perfil. O sistema MUST NOT criar painel, agendamento de varredura nem envio de e-mail nesta entrega, porque esses consumidores não existem neste Core. Nenhum consumidor existente MUST passar a ler o fuso da pessoa, porque não há consumidor desses três tipos.
- **FR-019**: E-mail da pessoa, notificações, LLT-172 e LLT-173 MUST permanecer fora do perfil e fora desta entrega.
- **FR-020**: Os comportamentos de consulta, gravação, validação, versão, isolamento entre pessoas, mesma pessoa em duas empresas, sessão ausente, empresa não selecionada, nome gravado sem chamada à Clerk e cadastro da empresa intacto MUST ser demonstrados por testes de acesso autenticado, exercidos como um cliente faz. O teste do nome MUST afirmar que o Core não chama a Clerk.

### Key Entities *(include if feature involves data)*

- **Perfil do usuário**: Nome, idioma e fuso horário de uma pessoa. Existe no máximo um perfil por pessoa. Pode estar ausente. Não pertence à empresa e não guarda e-mail nem notificação.
- **Pessoa autenticada**: Quem está na sessão. É a única dona do perfil lido ou gravado. O papel de administrador não muda esse direito.
- **Empresa selecionada**: A organização da sessão. É exigida para consultar ou gravar, como nas demais operações do produto. Não é a chave do perfil.
- **Cadastro da empresa**: Continua com o próprio fuso e o próprio idioma. Não é lido nem alterado pela manutenção do perfil.
- **Identidade**: O nome da pessoa na Clerk. O Web o atualiza na sessão do navegador depois que o Core aceita a gravação. O Core não lê nem grava essa identidade.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Uma pessoa consulta o próprio perfil e confirma uma alteração de nome, idioma e fuso em até 1 minuto, vendo os três valores na consulta seguinte.
- **SC-002**: Em 100% das gravações aceitas, a consulta seguinte da mesma pessoa devolve o nome, o idioma e o fuso confirmados, e a versão recusada não mistura dois envios.
- **SC-003**: Em 100% dos cenários com duas pessoas, a consulta e a gravação de uma deixam o perfil da outra invisível e inalterado, mesmo quando o pedido cita a outra pessoa ou outra empresa.
- **SC-004**: Em 100% das gravações de perfil, o fuso e o idioma do cadastro da empresa permanecem iguais aos de antes.
- **SC-005**: Em 100% das gravações de nome desta entrega, a consulta seguinte mostra o nome gravado no produto e a gravação é aceita sem chamada à Clerk. O alinhamento da identidade é do Web, na sessão do navegador, depois do sucesso, e não exige segredo no Core.
- **SC-006**: Depois da entrega, continuam inexistentes o painel, o agendamento de varredura e o envio de e-mail que usariam o fuso, e as mensagens já existentes do produto seguem em português.

## Assumptions

- A branch de trabalho é `LLT-174-manter-perfil-do-usuario`, criada a partir do HEAD de `LLT-175-manter-dados-da-empresa`. Esta especificação não troca de branch e não abre pull request.
- O perfil é da pessoa, não da empresa. A sessão continua exigindo empresa selecionada, porque as operações atuais do produto já funcionam assim. O identificador enviado pelo cliente não escolhe a pessoa nem a empresa.
- Sem perfil gravado, a sugestão inicial é idioma `pt-BR` e fuso `America/Sao_Paulo`, sem gravar. O nome começa vazio porque o Core não lê a identidade.
- A primeira gravação não envia versão. A alteração envia a versão vigente. O conflito segue a regra já aceita no cadastro da empresa.
- O limite de 200 caracteres do nome é o limite de texto curto já usado no cadastro da empresa.
- A etiqueta de idioma segue o formato já aceito nesse cadastro. `pt-BR`, `en` e `en-US` são válidos. A tela do Web só oferece `pt-BR` e `en`. O Core não recusa `en-US`.
- O fuso segue o identificador já reconhecido nesse cadastro. A consulta do perfil devolve o identificador, não o rótulo de tela.
- Membro e administrador alteram o próprio perfil. A lista `CLERK_ADMIN_ROLES` não é usada aqui e não muda.
- A evidência de aceite são testes de acesso do perfil, no mesmo espírito da LLT-175 e do princípio de testes antes do merge.
- Autenticação, organizações, webhooks e a instância Clerk continuam tarefa de quem já cuida dessa plataforma. Esta feature não os substitui. Devolver o nome à Clerk, depois da gravação aceita, é do Web (`user.update` na sessão do navegador), não do Core.
- O contrato consumido pelo Web descreve a consulta e a gravação. Ele não entra nesta seção de negócio; o plano o registra.
