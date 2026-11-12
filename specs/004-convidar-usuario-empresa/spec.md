# Feature Specification: Convidar usuário para a empresa

**Feature Branch**: `LLT-167-convidar-usuario-para-a-empresa`

**Created**: 2026-11-12

**Status**: Draft

**Input**: User description: "LLT-167 Convidar usuário para a empresa (épico LLT-99, feature LLT-119 Gestão de usuários). Como administrador, quero convidar pessoas para acessar o sistema com um cargo. O convite é enviado por e-mail com prazo de validade. Ao aceitar, o usuário fica vinculado ao tenant com o cargo escolhido. Convite expirado ou revogado não pode ser usado. A tela fica no Web. Configuração do dashboard da Clerk é tarefa de outra pessoa. Nenhum segredo entra neste repositório."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Enviar convite com cargo e prazo (Priority: P1)

Como administrador da empresa selecionada, quero convidar uma pessoa por e-mail, já com um cargo, para que ela receba um convite com prazo de validade e saiba a qual empresa está sendo chamada.

**Why this priority**: Sem o envio, a pessoa não recebe acesso e os outros critérios não têm o que aceitar, expirar ou revogar.

**Independent Test**: Com sessão de administrador e empresa selecionada, informar um e-mail válido e um cargo permitido e confirmar. A pessoa recebe um e-mail de convite com prazo. Um membro sem papel de administrador não envia. Sem sessão, a resposta é 401. Sem empresa selecionada, vale a recusa já usada no produto.

**Acceptance Scenarios**:

1. **Given** o administrador está na empresa selecionada, **When** informa o e-mail de uma pessoa que ainda não está vinculada e um cargo permitido e confirma, **Then** essa pessoa recebe um e-mail de convite para essa empresa, com o cargo escolhido e um prazo de validade.
2. **Given** o convite foi enviado, **When** a pessoa recebe o e-mail, **Then** o e-mail é o convite para aquela empresa e esse convite deixa de poder ser aceito depois do prazo.
3. **Given** a sessão é de um membro sem papel de administrador, **When** ele tenta enviar um convite, **Then** o convite não é enviado e a sessão dele continua válida.
4. **Given** não há sessão, **When** alguém tenta enviar um convite, **Then** a resposta é 401 e nenhum convite é enviado.
5. **Given** a sessão não tem empresa selecionada, **When** o administrador tenta enviar um convite, **Then** a operação é recusada com "Selecione uma empresa para continuar." e nenhum convite é enviado.
6. **Given** o administrador informa e-mail vazio, **When** confirma, **Then** a operação é recusada com "Informe o e-mail." e nenhum convite é enviado.
7. **Given** o administrador informa um e-mail irreconhecível, **When** confirma, **Then** a operação é recusada com "Informe um e-mail válido." e nenhum convite é enviado.

---

### User Story 2 - Aceitar e ficar na empresa com o cargo (Priority: P1)

Como pessoa convidada, quero aceitar o convite dentro do prazo para acessar aquela empresa com o cargo que o administrador escolheu.

**Why this priority**: O aceite é o que cria o vínculo. Sem ele, o e-mail não entrega acesso.

**Independent Test**: A pessoa aceita um convite pendente, dentro do prazo, que não foi revogado. Ela passa a acessar somente aquela empresa, com o cargo do convite. Aceitar de novo não cria outro vínculo. Convidar quem já está vinculado não muda o cargo.

**Acceptance Scenarios**:

1. **Given** existe um convite pendente dentro do prazo para a empresa A com um cargo permitido, **When** a pessoa aceita, **Then** ela fica vinculada à empresa A com esse cargo.
2. **Given** o convite é da empresa A, **When** a pessoa aceita, **Then** ela não fica vinculada a outra empresa por causa desse convite.
3. **Given** a pessoa já aceitou o convite, **When** o mesmo convite é usado de novo, **Then** nenhum vínculo adicional é criado.
4. **Given** a pessoa já está vinculada à empresa com um cargo, **When** o administrador envia outro convite para o mesmo e-mail, **Then** o cargo vigente não muda e não nasce um segundo vínculo. Mudar o cargo de quem já entrou fica na LLT-170.
5. **Given** a pessoa aceita o convite, **When** o acesso passa a valer, **Then** o cadastro da empresa, os produtos, o perfil de nome, idioma e fuso, as notificações e o acesso por produto permanecem como estavam.

---

### User Story 3 - Convite expirado ou revogado não entra (Priority: P1)

Como administrador, quero que um convite vencido ou que eu revoguei não dê acesso, para que um link antigo não coloque alguém na empresa.

**Why this priority**: É critério de aceite explícito. Enviar e aceitar sem essa trava deixaria o prazo e a revogação sem efeito.

**Independent Test**: Um convite é aceito depois do prazo e o vínculo não nasce. Outro convite é revogado pelo administrador da mesma empresa e, em seguida, a tentativa de aceite não vincula ninguém. Revogar um convite já aceito não remove a pessoa.

**Acceptance Scenarios**:

1. **Given** o prazo do convite já passou e ele não foi aceito, **When** a pessoa tenta aceitar, **Then** o aceite é recusado e ela não fica vinculada à empresa.
2. **Given** o convite ainda está pendente, **When** o administrador da mesma empresa o revoga, **Then** uma tentativa posterior de aceite é recusada e nenhum vínculo novo nasce.
3. **Given** a pessoa já aceitou e está vinculada, **When** alguém tenta revogar o convite que ela usou, **Then** o vínculo já criado permanece. Tirar o acesso de quem já entrou fica na LLT-168.
4. **Given** a sessão é de um membro sem papel de administrador, **When** ele tenta revogar um convite da empresa, **Then** o convite continua pendente e utilizável até o prazo.

---

### Edge Cases

- E-mail vazio é recusado com "Informe o e-mail." E-mail irreconhecível é recusado com "Informe um e-mail válido." Nenhum convite sai nesses casos.
- Sessão ausente responde 401 e não envia, não aceita no lugar de outra pessoa e não revoga. Sessão sem empresa usa "Selecione uma empresa para continuar."
- Membro sem papel de administrador não envia e não revoga. A recusa não encerra a sessão. Nome exibido parecido com administrador não concede esse poder: vale o identificador de papel já usado no produto.
- Identificador de outra empresa enviado no pedido é ignorado. O convite é sempre da empresa da sessão.
- Convite já aceito não pode ser aceito outra vez.
- Convite expirado não pode ser aceito. Convite revogado não pode ser aceito.
- Revogar não desfaz aceite já concluído.
- Convidar um e-mail que já está vinculado à mesma empresa não cria segundo vínculo e não troca o cargo.
- Enviar ou aceitar convite não altera cadastro da empresa, produtos, perfil, notificações nem acesso por produto.
- O número de dias do prazo, a possibilidade de reenviar o mesmo convite, a lista fechada de cargos, quem envia o e-mail e onde o convite fica guardado aguardam esclarecimento. Esta spec não escolhe essas respostas.
- Nenhum segredo é gravado para fazer o convite funcionar.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O administrador da empresa selecionada MUST poder convidar uma pessoa informando o e-mail e um cargo permitido. O convite MUST ser enviado por e-mail e MUST indicar a empresa e o cargo escolhido.
- **FR-002**: O convite MUST ter um prazo de validade contado a partir do envio. Depois desse instante, o convite MUST NOT ser aceito e MUST NOT criar vínculo. A quantidade de dias do prazo padrão aguarda esclarecimento e MUST NOT ser tratada como decidida nesta spec.
- **FR-003**: O administrador da mesma empresa MUST poder revogar um convite ainda pendente. O convite revogado MUST NOT ser aceito e MUST NOT criar vínculo. A revogação de um convite já aceito MUST NOT remover a pessoa que já entrou.
- **FR-004**: O canal do e-mail e a participação do Web MUST seguir [NEEDS CLARIFICATION: quem envia o e-mail e como o Web participa — convite de organização da Clerk disparado na sessão do administrador no Web, e-mail próprio do Core, ou o Core pedindo o envio à Clerk].
- **FR-005**: Ao aceitar dentro do prazo um convite pendente e não revogado, a pessoa MUST ficar vinculada somente à empresa desse convite, com o cargo escolhido nele.
- **FR-006**: Um convite já aceito MUST NOT ser aceito de novo e MUST NOT criar outro vínculo.
- **FR-007**: Convidar um e-mail que já está vinculado à mesma empresa MUST NOT criar segundo vínculo e MUST NOT alterar o cargo vigente. A troca de cargo de quem já entrou permanece na LLT-170.
- **FR-008**: Os cargos oferecidos no convite MUST ser [NEEDS CLARIFICATION: quais cargos existem e onde ficam — apenas administrador e membro já reconhecidos na sessão, os exemplos Administrador, Gestor e Visualizador da LLT-169, ou um cargo informado livremente até o catálogo ser fechado]. O nome exibido do cargo MUST NOT conceder poder de administrador.
- **FR-009**: Membro sem papel de administrador MUST NOT enviar nem revogar convite. A recusa MUST NOT encerrar a sessão. Sessão ausente MUST responder 401 e MUST impedir envio e revogação. Sessão sem empresa selecionada MUST ser recusada com "Selecione uma empresa para continuar."
- **FR-010**: O convite MUST pertencer à empresa da sessão. Identificador de outra empresa enviado pelo cliente MUST ser ignorado.
- **FR-011**: A guarda do convite e do vínculo MUST seguir [NEEDS CLARIFICATION: o Core guarda o convite e o vínculo, guarda só o convite pendente, ou não guarda nenhum dos dois porque a organização da sessão já representa o vínculo depois do aceite].
- **FR-012**: E-mail vazio MUST ser recusado com "Informe o e-mail." E-mail irreconhecível MUST ser recusado com "Informe um e-mail válido." Nessas recusas nenhum convite MUST ser enviado. Se o esclarecimento entregar o formulário à Clerk, o efeito MUST permanecer: e-mail inválido não gera convite.
- **FR-013**: Esta entrega MUST NOT incluir listar, editar ou desativar usuários (LLT-168), fechar o catálogo e a matriz de permissões (LLT-169), atribuir outro cargo a quem já está vinculado (LLT-170), restringir acesso por produto (LLT-171), preferência de forma de e-mail (LLT-172), canais de notificação (LLT-173) nem perfil de nome, idioma e fuso (LLT-174).
- **FR-014**: Esta entrega MUST NOT gravar segredo em código, teste, exemplo ou documentação versionada. Chave secreta da Clerk, inclusive valor que comece com `sk_` ou a variável `CLERK_SECRET_KEY`, MUST NOT entrar neste repositório. A configuração do dashboard da Clerk, quando o esclarecimento escolher esse caminho, é tarefa de outra pessoa.
- **FR-015**: Envio por administrador, recusa de quem não é administrador, aceite dentro do prazo com o cargo escolhido, recusa de convite expirado e recusa de convite revogado MUST ser demonstrados por testes antes do merge. A forma do teste acompanha a decisão de canal do esclarecimento. O teste MUST afirmar a ausência de segredo versionado.

### Key Entities *(include if feature involves data)*

- **Convite**: Chamado de uma pessoa, por e-mail, para uma empresa, com um cargo e um prazo. Pode estar pendente, aceito, expirado ou revogado. Onde ele fica guardado está em aberto no FR-011. Reenviar o mesmo convite não faz parte do aceite escrito.
- **Pessoa convidada**: Identificada pelo e-mail enquanto o convite está pendente. Depois do aceite, é a pessoa vinculada à empresa com o cargo do convite.
- **Administrador**: Quem pode enviar e revogar. É quem possui identificador de papel na lista já configurada de papéis administrativos da organização (`CLERK_ADMIN_ROLES`, padrão `admin`). O nome exibido do papel não basta.
- **Empresa**: A organização da sessão. É o tenant do convite. O cliente não escolhe outra.
- **Cargo**: O papel aplicado no aceite. A lista que o convite pode oferecer está em aberto no FR-008. A matriz do que cada cargo pode fazer fica na LLT-169 e na LLT-170.
- **Vínculo**: A pessoa passa a pertencer à empresa com um cargo. Hoje o produto reconhece essa pertença pela organização da sessão e reconhece administrador pela lista acima. Se este serviço também grava o vínculo está em aberto no FR-011.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Um administrador envia um convite com e-mail e cargo em até 2 minutos, e a pessoa recebe um e-mail com prazo de validade.
- **SC-002**: Em 100% dos aceites feitos dentro do prazo, de convite pendente e não revogado, a pessoa fica vinculada só à empresa do convite e com o cargo escolhido nele.
- **SC-003**: Em 100% das tentativas com convite expirado ou revogado, nenhum vínculo novo é criado.
- **SC-004**: Em 100% das tentativas de envio ou revogação por quem não é administrador, ou sem empresa selecionada, nenhum convite novo sai e nenhum convite pendente muda de estado.
- **SC-005**: Depois da entrega, o material versionado continua sem chave secreta da plataforma de identidade.

## Assumptions

- A branch de trabalho é `LLT-167-convidar-usuario-para-a-empresa`, criada a partir do HEAD de `LLT-174-manter-perfil-do-usuario`. Esta especificação não abre pull request e não grava nada nas branches LLT-174 ou LLT-175.
- A empresa do convite é a organização da sessão. O produto já recusa sessão sem empresa com "Selecione uma empresa para continuar." e já trata como administrador somente o identificador presente em `CLERK_ADMIN_ROLES` (padrão `admin`). Papéis como `member`, `owner` e o nome exibido "Administrador" não são administrador, salvo se o identificador estiver nessa lista.
- Não existe no Core envio de e-mail, coleção de convite nem coleção de vínculo da pessoa com a empresa. O e-mail guardado no cadastro da empresa é contato da empresa, não caixa de saída. O vínculo de acesso a produto é outra relação e permanece na LLT-171. O perfil da pessoa não guarda e-mail nem cargo.
- O acesso atual existe quando a sessão traz a empresa. A plataforma dessa sessão é a Clerk. Um registro só no Core, sem a pessoa entrar na organização da sessão, não passaria nas regras já existentes de empresa e de papel. Por isso o canal do e-mail e a guarda do vínculo ficaram em aberto, em vez de serem escolhidos aqui.
- A tela de convidar fica no Web, em outro repositório, no mesmo espírito da LLT-174. Está em aberto se o Web dispara o convite na sessão do navegador ou se chama o Core.
- O prazo existe. A quantidade de dias padrão não foi escolhida. Reenviar o mesmo convite não está no aceite. Esta versão não exige reenvio. As duas decisões ficam no esclarecimento e não entram como regra fechada.
- Convite é de uso único: aceito uma vez, não serve de novo. Enquanto houver convite pendente para o mesmo e-mail na mesma empresa, outro envio não cria um segundo pendente. Essa espera pode ser revista se o esclarecimento escolher reenvio.
- A configuração do dashboard da Clerk é tarefa de outra pessoa. Se o esclarecimento escolher convite de organização, o que precisaria ser configurado lá, sem valor secreto neste repositório, é: convites de organização habilitados; cargos que o convite pode atribuir, com os mesmos identificadores que a sessão já compara; prazo de validade do convite; página para onde a pessoa vai depois de aceitar; texto do e-mail de convite. Nenhum desses itens é configurado por esta entrega.
- Autenticação, organizações e a instância Clerk continuam com quem já cuida dessa plataforma. Esta feature não os substitui e não adiciona chave secreta.
- LLT-168, LLT-169, LLT-170, LLT-171, LLT-172, LLT-173 e LLT-174 permanecem fora. A LLT-169 continua sendo o lugar do catálogo de cargos. Esta história só precisa da lista mínima que o convite pode oferecer, e essa lista ainda não foi escolhida.
