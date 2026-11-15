# Feature Specification: Convidar usuário para a empresa

**Feature Branch**: `LLT-167-convidar-usuario-para-a-empresa`

**Created**: 2026-11-12

**Status**: Draft

**Input**: User description: "LLT-167 Convidar usuário para a empresa (épico LLT-99, feature LLT-119 Gestão de usuários). Como administrador, quero convidar pessoas para acessar o sistema com um cargo. O convite é enviado por e-mail com prazo de validade. Ao aceitar, o usuário fica vinculado ao tenant com o cargo escolhido. Convite expirado ou revogado não pode ser usado."

## Clarifications

### Session 2026-11-12

Decisões finais do produto, alinhadas com o Web. Substituem os marcadores que estavam em aberto.

- Q: Quem envia o e-mail e como o Web participa? → A: O Web cria o convite de organização da Clerk na sessão do administrador, pelo SDK do navegador. A Clerk envia o e-mail, controla a validade e a revogação. O Core não envia e-mail, não chama a Clerk e não usa chave secreta.
- Q: O Core guarda o convite e o vínculo? → A: Não. O Core não guarda convite nem vínculo. Depois do aceite, a sessão com a organização e o cargo é o vínculo.
- Q: Quais cargos o convite oferece? → A: Só o administrador pode convidar. No convite ele escolhe se a pessoa entra como Administrador (`admin`) ou Membro (`member`).
- Q: Qual é a validade? → A: 7 dias, contados do envio. A configuração fica no dashboard da Clerk e é feita por outra pessoa. Esta entrega só documenta.
- Q: O convite pode ser reenviado? → A: Sim. O administrador vê a lista de pendentes, pode revogar e reenviar. Isso é feito no Web, via Clerk.
- Q: Onde a pessoa aceita? → A: Nas telas de entrada que já existem. Não há rota nova no Web nem no Core.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Enviar convite com cargo e prazo (Priority: P1)

Como administrador da empresa selecionada, quero convidar uma pessoa por e-mail, já como Administrador ou Membro, para que ela receba um convite com prazo de 7 dias.

**Why this priority**: Sem o envio, não há o que aceitar, expirar, revogar ou reenviar.

**Independent Test**: No Web, com a sessão de administrador, informar um e-mail válido e o cargo `admin` ou `member`. O Web cria o convite de organização na Clerk pelo SDK do navegador. A pessoa recebe o e-mail. O Core não envia e-mail, não chama a Clerk e não oferece caminho de convite. Um membro não dispara esse fluxo.

**Acceptance Scenarios**:

1. **Given** o administrador está na empresa selecionada, **When** informa o e-mail de uma pessoa e o cargo Administrador ou Membro e confirma no Web, **Then** o Web cria o convite de organização da Clerk na sessão do navegador e a pessoa recebe o e-mail para essa empresa, com esse cargo e prazo de 7 dias.
2. **Given** o administrador informa e-mail vazio, **When** confirma no Web, **Then** o Web recusa com "Informe o e-mail." e não chama a Clerk.
3. **Given** o administrador informa um e-mail irreconhecível, **When** confirma no Web, **Then** o Web recusa com "Informe um e-mail válido." e não chama a Clerk.
4. **Given** a sessão é de um membro, **When** ele tenta convidar, **Then** o Web não cria convite e a sessão dele continua válida. No Core, essa sessão segue sem poder de administrador nas regras já existentes.
5. **Given** não há sessão, **When** alguém tenta usar o Core, **Then** a resposta é 401.
6. **Given** a sessão não tem empresa selecionada, **When** alguém tenta usar o Core, **Then** a operação é recusada com "Selecione uma empresa para continuar."

---

### User Story 2 - Aceitar e ficar na empresa com o cargo (Priority: P1)

Como pessoa convidada, quero aceitar o convite nas telas de entrada que já existem para acessar aquela empresa como Administrador ou Membro, conforme o convite.

**Why this priority**: O aceite é o que cria o acesso. O Core reconhece esse acesso pela sessão, não por um registro próprio.

**Independent Test**: A pessoa aceita pela tela de entrada já existente. A sessão seguinte traz a organização do convite e o cargo `admin` ou `member`. No Core, `admin` (e `org:admin`) é administrador e `member` (e `org:member`) não é. Não surge rota nova. O cadastro da empresa, os produtos, o perfil e o acesso por produto permanecem como estavam.

**Acceptance Scenarios**:

1. **Given** a pessoa aceita um convite pendente dentro de 7 dias para a empresa A como Membro, **When** entra pelas telas já existentes, **Then** a sessão traz a empresa A e o cargo `member`, e o Core trata essa sessão como membro, não como administrador.
2. **Given** a pessoa aceita um convite pendente dentro de 7 dias para a empresa A como Administrador, **When** entra pelas telas já existentes, **Then** a sessão traz a empresa A e o cargo `admin`, e o Core trata essa sessão como administrador.
3. **Given** o convite é da empresa A, **When** a pessoa aceita, **Then** a sessão não passa a valer para outra empresa por causa desse convite.
4. **Given** a pessoa já está na empresa com um cargo, **When** o administrador envia outro convite para o mesmo e-mail, **Then** o cargo vigente da sessão não muda por uma gravação do Core. Trocar o cargo de quem já entrou fica na LLT-170.
5. **Given** a pessoa aceita, **When** passa a usar o Core, **Then** o cadastro da empresa, os produtos, o perfil e o acesso por produto permanecem como estavam. O Core não grava convite nem vínculo.

---

### User Story 3 - Convite expirado, revogado ou reenviado (Priority: P1)

Como administrador, quero ver os convites pendentes, revogar, reenviar e confiar que um convite vencido ou revogado não dá acesso.

**Why this priority**: O prazo de 7 dias, a revogação e o reenvio são o controle do convite. Sem isso, um link antigo continuaria valendo.

**Independent Test**: No Web, o administrador vê a lista de pendentes da Clerk, revoga um e reenvia outro. Um convite com mais de 7 dias, ou revogado, não é aceito pela Clerk e não produz sessão daquela empresa. O Core não lista, não revoga e não reenvia.

**Acceptance Scenarios**:

1. **Given** passaram 7 dias do envio e o convite não foi aceito, **When** a pessoa tenta aceitar, **Then** a Clerk recusa e não nasce sessão dessa empresa por esse convite.
2. **Given** o convite ainda está pendente, **When** o administrador o revoga no Web, **Then** a Clerk recusa um aceite posterior e não nasce sessão dessa empresa por esse convite.
3. **Given** o convite está pendente, **When** o administrador reenvia no Web, **Then** a Clerk reenvia o e-mail e o prazo volta a contar 7 dias a partir desse reenvio.
4. **Given** a pessoa já aceitou e a sessão dela já traz a empresa e o cargo, **When** alguém revoga o convite que ela usou, **Then** a sessão já emitida não é apagada por este fluxo. Tirar o acesso de quem já entrou fica na LLT-168.
5. **Given** a sessão é de um membro, **When** ele tenta ver pendentes, revogar ou reenviar, **Then** o Web não faz essa operação na Clerk.

---

### Edge Cases

- E-mail vazio é recusado no Web com "Informe o e-mail." E-mail irreconhecível é recusado no Web com "Informe um e-mail válido." A Clerk não é chamada.
- Sessão ausente no Core responde 401. Sessão sem empresa usa "Selecione uma empresa para continuar."
- Membro não convida, não lista pendentes, não revoga e não reenvia. Quem faz isso é o Web, via Clerk, só na sessão de administrador. A recusa não encerra a sessão.
- O nome exibido "Administrador" não torna a sessão administradora. O identificador é `admin`. `member` não é administrador.
- O identificador com prefixo `org:` equivale ao identificador sem prefixo: `org:admin` é administrador e `org:member` é membro, com a lista atual cujo padrão é `admin`.
- Identificador de outra empresa enviado ao Core é ignorado. O convite criado no Web usa a organização da sessão do administrador.
- Convite já aceito não é aceito de novo. O Core não cria um segundo vínculo porque não guarda vínculo.
- Convite expirado não é aceito. Convite revogado não é aceito. Os dois casos ficam na Clerk.
- Revogar o convite não remove quem já entrou.
- Reenviar é ação do administrador no Web e renova o prazo para 7 dias. O Core não reenvia.
- Aceitar não altera cadastro da empresa, produtos, perfil nem acesso por produto.
- Não há rota nova de aceite. A pessoa usa as telas de entrada já existentes.
- Nenhum segredo é gravado para o convite funcionar.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O administrador da empresa selecionada MUST poder convidar uma pessoa informando o e-mail e um dos cargos `admin` ou `member`. O Web MUST criar o convite de organização da Clerk na sessão do navegador. A Clerk MUST enviar o e-mail. O Core MUST NOT enviar e-mail e MUST NOT oferecer caminho de convite.
- **FR-002**: O convite MUST valer 7 dias contados do envio. Depois desse prazo, a Clerk MUST NOT aceitar o convite e MUST NOT emitir sessão dessa empresa por ele. O reenvio MUST recomeçar a contagem de 7 dias. O valor MUST ser configurado no dashboard da Clerk por outra pessoa. O Core MUST NOT guardar esse prazo.
- **FR-003**: O administrador MUST poder revogar um convite pendente no Web, via Clerk. O convite revogado MUST NOT ser aceito e MUST NOT gerar sessão dessa empresa. Revogar o convite de quem já aceitou MUST NOT remover a pessoa. Essa remoção permanece na LLT-168. O Core MUST NOT oferecer revogação.
- **FR-004**: O canal MUST ser o SDK do navegador, na sessão do administrador. A Clerk controla e-mail, validade e revogação. O Core MUST NOT chamar a Clerk e MUST NOT usar chave secreta.
- **FR-005**: Ao aceitar dentro do prazo, a pessoa MUST ficar vinculada somente à empresa do convite, com o cargo escolhido. O vínculo MUST ser a sessão que traz essa organização e esse cargo. O aceite MUST usar as telas de entrada já existentes. O Web e o Core MUST NOT criar rota nova de aceite.
- **FR-006**: Um convite já aceito MUST NOT ser aceito de novo. O Core MUST NOT criar outro vínculo, porque não guarda vínculo.
- **FR-007**: Convidar um e-mail que já está na mesma empresa MUST NOT fazer o Core trocar o cargo vigente. A troca de cargo de quem já entrou permanece na LLT-170.
- **FR-008**: O convite MUST oferecer só Administrador (`admin`) e Membro (`member`). Só o administrador MUST poder convidar. O nome exibido do cargo MUST NOT conceder poder de administrador. No Core, com a lista cujo padrão é `admin`, os identificadores `admin` e `org:admin` MUST ser administrador, e `member` e `org:member` MUST NOT ser administrador.
- **FR-009**: Membro MUST NOT enviar, listar pendentes, revogar nem reenviar. Essa restrição é do Web e da Clerk. No Core, a sessão de membro MUST continuar recusada nas alterações de administrador já existentes, sem encerrar a sessão. Sessão ausente MUST responder 401. Sessão sem empresa MUST ser recusada com "Selecione uma empresa para continuar."
- **FR-010**: O convite MUST pertencer à organização da sessão do administrador. Identificador de outra empresa enviado ao Core MUST ser ignorado, como nas operações já existentes.
- **FR-011**: O Core MUST NOT guardar convite nem vínculo. Depois do aceite, a sessão com a organização e o cargo MUST ser o vínculo.
- **FR-012**: E-mail vazio MUST ser recusado no Web com "Informe o e-mail." E-mail irreconhecível MUST ser recusado no Web com "Informe um e-mail válido." Nesses casos o Web MUST NOT chamar a Clerk. O Core não valida o e-mail do convite.
- **FR-013**: Esta entrega MUST NOT incluir listar, editar ou desativar usuários (LLT-168), fechar o catálogo e a matriz de permissões (LLT-169), atribuir outro cargo a quem já está vinculado (LLT-170), restringir acesso por produto (LLT-171), preferência de forma de e-mail (LLT-172), canais de notificação (LLT-173) nem perfil de nome, idioma e fuso (LLT-174).
- **FR-014**: Esta entrega MUST NOT gravar segredo em código, teste, exemplo ou documentação versionada. Chave secreta da Clerk, inclusive valor que comece com `sk_` ou a variável `CLERK_SECRET_KEY`, MUST NOT entrar neste repositório. A configuração do dashboard da Clerk é tarefa de outra pessoa. Esta entrega só documenta o que configurar.
- **FR-015**: O Core MUST demonstrar por testes que uma sessão recém-aceita com cargo `member` ou `org:member` é membro e que uma sessão com cargo `admin` ou `org:admin` é administrador, nas regras já existentes, inclusive na forma antiga do papel (`org_role`). Os testes MUST mostrar que não há caminho de convite, aceite, revogação ou reenvio no Core e que não há segredo versionado. O e-mail, os 7 dias, a lista de pendentes, a revogação e o reenvio são provados no Web, contra a Clerk, e não por um enviador neste repositório.
- **FR-016**: O administrador MUST poder ver a lista de convites pendentes e reenviar um convite pendente no Web, via Clerk. O Core MUST NOT listar pendentes e MUST NOT reenviar.

### Key Entities *(include if feature involves data)*

- **Convite**: Chamado criado na Clerk, pelo Web, para um e-mail, uma organização e um cargo (`admin` ou `member`), com prazo de 7 dias. Pode estar pendente, aceito, expirado ou revogado. O Core não o guarda.
- **Pessoa convidada**: Identificada pelo e-mail enquanto o convite está pendente na Clerk. Depois do aceite, é a pessoa da sessão.
- **Administrador**: Quem pode convidar, listar pendentes, revogar e reenviar. No Core, é a sessão cujo identificador de papel, sem o prefixo `org:`, está na lista `CLERK_ADMIN_ROLES` (padrão `admin`).
- **Membro**: A sessão com identificador `member`. Não convida e não administra o cadastro da empresa.
- **Empresa**: A organização da sessão. É o tenant do convite. O cliente não escolhe outra no Core.
- **Vínculo**: Não é um documento do Core. É a sessão que traz a organização e o cargo depois do aceite.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Um administrador envia um convite com e-mail e cargo `admin` ou `member` em até 2 minutos, e a pessoa recebe um e-mail com prazo de 7 dias.
- **SC-002**: Em 100% dos aceites feitos dentro de 7 dias, a próxima entrada pelas telas já existentes produz sessão só da empresa do convite e com o cargo escolhido. O Core trata `admin` como administrador e `member` como membro.
- **SC-003**: Em 100% das tentativas com convite expirado ou revogado, a Clerk não emite sessão dessa empresa por esse convite.
- **SC-004**: Em 100% das tentativas de envio, listagem, revogação ou reenvio por quem não é administrador, nenhum convite novo sai e nenhum pendente muda.
- **SC-005**: Depois da entrega, o material versionado continua sem chave secreta da plataforma de identidade.

## Assumptions

- A branch de trabalho é `LLT-167-convidar-usuario-para-a-empresa`, criada a partir do HEAD de `LLT-174-manter-perfil-do-usuario`. Esta especificação não abre pull request e não grava nada nas branches LLT-174 ou LLT-175.
- A empresa do convite é a organização ativa na sessão do administrador. O Core já recusa sessão sem empresa com "Selecione uma empresa para continuar." e já trata como administrador somente o identificador presente em `CLERK_ADMIN_ROLES` (padrão `admin`), depois de remover o prefixo `org:`.
- O Core não envia e-mail e não passa a enviar. O e-mail do cadastro da empresa continua sendo contato da empresa. Não há coleção de convite nem de vínculo. O acesso por produto permanece na LLT-171. O perfil da pessoa não guarda e-mail nem cargo.
- O acesso ao Core continua exigindo sessão com empresa. A plataforma dessa sessão é a Clerk. Um registro só no Core não concederia acesso.
- A tela de convidar, a lista de pendentes, a revogação e o reenvio ficam no Web, em outro repositório. O aceite reutiliza as telas de entrada já existentes.
- A lista do que outra pessoa configura no dashboard da Clerk, sem valor secreto neste repositório, é: convites de organização habilitados; cargos atribuíveis `admin` e `member`, os mesmos identificadores que a sessão compara; prazo de 7 dias; página de aceite apontando para as telas de entrada já existentes, sem rota nova; texto do e-mail de convite. Esta entrega não configura o dashboard.
- Autenticação, organizações e a instância Clerk continuam com quem já cuida dessa plataforma. Esta feature não os substitui.
- LLT-168, LLT-169, LLT-170, LLT-171, LLT-172, LLT-173 e LLT-174 permanecem fora. A LLT-169 continua sendo o lugar de qualquer cargo além de `admin` e `member`.
