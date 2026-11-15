# Feature Specification: Listar, editar e desativar usuários

**Feature Branch**: `LLT-168-listar-editar-e-desativar-usuarios`

**Created**: 2026-11-15

**Status**: Draft

**Input**: User description: "LLT-168 Listar, editar e desativar usuários (épico LLT-99, feature LLT-119 Gestão de usuários). Como administrador, quero manter a lista de usuários da empresa. A lista mostra nome, e-mail, cargo, status e último acesso. Usuário desativado perde o acesso na próxima requisição. Não é possível desativar ou rebaixar o último administrador. Sem chave secreta da Clerk no Core nem no Web."

## Decisões em aberto

Nenhuma opção abaixo foi adotada. O planejamento não deve tratar estes pontos como fechados. A sessão de clarify de 2026-11-15 registrou as perguntas e não gravou resposta.

1. **Perda de acesso na próxima requisição.** A sessão já emitida continua válida até expirar. A pessoa desativada precisa continuar visível na lista, com status desativado. Opções, sem escolha: conferir uma marca de desativado a cada requisição; retirar a pessoa da organização pela sessão do administrador no Web; fazer os dois. Só retirar da organização não deixa, por si, a linha com status desativado.
2. **Último administrador, sem chave secreta.** Opções, sem escolha: a trava só no Web, contando os administradores da organização na sessão do administrador; a trava só num registro da empresa no Core; as duas travas; confiar apenas na recusa da plataforma de identidade.
3. **O que editar cobre.** Opções, sem escolha: esta história não altera cargo nem nome, e a troca de cargo fica na LLT-170; editar é trocar o cargo entre Administrador e Membro; editar é só o nome de outra pessoa; editar é cargo e nome. E-mail não entra em nenhuma dessas opções.
4. **Origem do último acesso.** A coluna é obrigatória. A origem não. Opções, sem escolha: registrar no Core a cada requisição autenticada da pessoa na empresa; registrar no Core só o primeiro uso de cada sessão; ler o dado da plataforma de identidade; mostrar a coluna vazia nesta entrega.
5. **Desativar é reversível?** Opções, sem escolha: sim, o administrador reativa e o acesso volta quando a pessoa volta a ter sessão da empresa; não, voltar é um convite novo da LLT-167; sim, mas só no registro do produto, sem tirar a pessoa da organização.

Quem pode ver a lista ficou de fora desta rodada de cinco perguntas. O ator da história é o administrador. Isso não decide se um membro também consulta a lista.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ver quem está na empresa (Priority: P1)

Como administrador da empresa selecionada, quero ver a lista de usuários dessa empresa, com nome, e-mail, cargo, status e último acesso, para saber quem tem acesso e em que condição.

**Why this priority**: Sem a lista, não há o que editar, desativar ou proteger. É o primeiro critério de aceite.

**Independent Test**: Com a empresa selecionada, o administrador abre a lista e vê, para cada pessoa daquela empresa, os cinco dados. Pessoa de outra empresa não aparece. A coluna de último acesso existe mesmo enquanto a origem desse dado estiver em aberto.

**Acceptance Scenarios**:

1. **Given** a empresa A tem pelo menos uma pessoa, **When** o administrador da empresa A abre a lista, **Then** cada linha mostra nome, e-mail, cargo, status e último acesso.
2. **Given** a empresa A e a empresa B têm pessoas diferentes, **When** o administrador da empresa A abre a lista, **Then** só aparecem pessoas da empresa A.
3. **Given** uma pessoa da empresa ainda não tem uso registrado, **When** o administrador abre a lista, **Then** a linha dela continua na lista e o último acesso indica que não há uso registrado. A origem do valor, quando houver uso, permanece em aberto.
4. **Given** não há sessão, **When** alguém tenta ver a lista, **Then** a resposta é 401.
5. **Given** a sessão não tem empresa selecionada, **When** alguém tenta ver a lista, **Then** a operação é recusada com "Selecione uma empresa para continuar."

---

### User Story 2 - Desativar e cortar o acesso (Priority: P1)

Como administrador, quero desativar um usuário da empresa para que ele perca o acesso a essa empresa na próxima requisição.

**Why this priority**: É o segundo critério de aceite. A LLT-167 deixou esta retirada para cá: revogar o convite não tira quem já entrou.

**Independent Test**: O administrador desativa uma pessoa que não é o último administrador. A requisição seguinte dessa pessoa, nesta empresa, é recusada. A requisição de outra pessoa da mesma empresa continua. O modo de garantir esse corte está em aberto.

**Acceptance Scenarios**:

1. **Given** a pessoa está ativa na empresa e não é o único administrador, **When** o administrador a desativa, **Then** o status dela na lista passa a desativado.
2. **Given** a pessoa foi desativada, **When** ela faz a próxima requisição nesta empresa, **Then** o acesso é recusado. A sessão de outra pessoa da mesma empresa não é encerrada por essa desativação.
3. **Given** a pessoa foi desativada na empresa A e continua na empresa B, **When** ela usa a empresa B, **Then** a empresa B não é afetada por esta desativação.
4. **Given** a pessoa já está desativada, **When** o administrador desativa de novo, **Then** ela continua desativada e sem acesso. A repetição não reativa.

---

### User Story 3 - Preservar o último administrador (Priority: P1)

Como administrador, quero que a empresa nunca fique sem administrador, para que alguém continue podendo manter usuários e o cadastro.

**Why this priority**: É o terceiro critério de aceite. Sem essa trava, a empresa perde quem pode desfazer uma desativação ou operar o que já é de administrador.

**Independent Test**: Com um único administrador ativo, a tentativa de desativá-lo é recusada e ele continua administrador e com acesso. A tentativa de rebaixá-lo também é recusada. Com dois administradores, desativar um deixa o outro administrador.

**Acceptance Scenarios**:

1. **Given** a empresa tem um único administrador ativo, **When** alguém tenta desativá-lo, **Then** a operação é recusada, ele continua administrador, ativo e com acesso.
2. **Given** a empresa tem um único administrador ativo, **When** alguém tenta rebaixá-lo a membro, **Then** a operação é recusada e ele continua administrador. O que mais a edição altera permanece em aberto.
3. **Given** a empresa tem dois administradores ativos, **When** um deles é desativado, **Then** o outro permanece administrador ativo e a desativação do primeiro vale.
4. **Given** duas desativações do mesmo par de administradores partem juntas e cada uma deixaria a empresa sem administrador, **When** as duas concluem, **Then** pelo menos um administrador ativo permanece.

---

### User Story 4 - Editar um usuário da lista (Priority: P2)

Como administrador, quero editar um usuário da empresa no limite que o produto definir, sem mudar o e-mail de identidade e sem apagar o perfil que a própria pessoa mantém.

**Why this priority**: O título da história inclui editar, e o aceite fala em rebaixar. O que a edição altera não está decidido. A lista e a desativação já entregam valor sem essa resposta.

**Independent Test**: Só é possível testar o campo editável depois da resposta. Até lá, o teste prova que o e-mail não muda por esta história e que idioma, fuso e o próprio perfil da LLT-174 permanecem como estão.

**Acceptance Scenarios**:

1. **Given** o administrador abre um usuário da lista, **When** confirma uma edição, **Then** o efeito é só o que a decisão em aberto permitir: nenhum campo, só o cargo entre Administrador e Membro, só o nome, ou cargo e nome.
2. **Given** qualquer uma dessas opções, **When** a edição é aceita, **Then** o e-mail da pessoa não muda.
3. **Given** a pessoa já gravou idioma e fuso no próprio perfil, **When** o administrador edita o que esta história permitir, **Then** idioma, fuso e o dono desse perfil não mudam.

---

### Edge Cases

- Sessão ausente responde 401. Sessão sem empresa usa "Selecione uma empresa para continuar."
- Identificador de outra empresa enviado no pedido é ignorado. A lista e a desativação valem para a empresa da sessão.
- Desativar a si mesmo é permitido quando existe outro administrador ativo. Desativar a si mesmo sendo o único administrador é recusado.
- Repetir a desativação de quem já está desativado não devolve o acesso.
- Desativar na empresa A não desativa na empresa B.
- Duas tentativas simultâneas não podem deixar a empresa com zero administradores ativos.
- Pessoa sem uso registrado continua na lista, com último acesso vazio de uso. A origem do valor, quando houver uso, está em aberto.
- Nome ainda não gravado no perfil não tira a pessoa da lista. Qual nome a lista mostra, se o perfil estiver vazio, acompanha a origem dos dados da lista e não está decidido além da obrigação de haver a coluna.
- E-mail vazio não é um caso desta história: quem está na empresa já entrou por um e-mail. Esta história não cria convite.
- Membro e administrador continuam com o significado já aceito na LLT-167: `admin` e `org:admin` são administrador; `member` e `org:member` não são. O nome exibido "Administrador" não concede o cargo.
- Reativar, se um dia existir, não está especificada aqui. A repetição da desativação não serve de reativação.
- Nenhum segredo é gravado para a lista ou a desativação funcionarem.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O administrador da empresa selecionada MUST poder ver a lista de usuários dessa empresa. Cada linha MUST mostrar nome, e-mail, cargo, status e último acesso. Pessoa de outra empresa MUST NOT aparecer.
- **FR-002**: O status MUST distinguir pelo menos ativo e desativado. Usuário desativado MUST aparecer na lista com status desativado, para que o administrador ainda o veja.
- **FR-003**: Quando não houver uso registrado, a lista MUST manter a pessoa e MUST indicar ausência de último acesso. A origem do último acesso, quando houver uso, permanece a decisão em aberto número 4. Nenhuma origem foi escolhida.
- **FR-004**: Desativar um usuário que não é o único administrador ativo MUST fazer a próxima requisição dele nesta empresa ser recusada. A sessão das outras pessoas MUST permanecer. Desativar na empresa A MUST NOT desativar na empresa B. O modo de garantir esse corte, com a sessão já emitida ainda válida até expirar, é [NEEDS CLARIFICATION: marca de desativado conferida a cada requisição, retirar a pessoa da organização pelo Web na sessão do administrador, ou os dois].
- **FR-005**: Repetir a desativação de quem já está desativado MUST NOT reativar essa pessoa e MUST NOT devolver o acesso.
- **FR-006**: MUST ser impossível desativar o último administrador ativo. A tentativa MUST ser recusada e ele MUST continuar administrador, ativo e com acesso. Desativar a si mesmo MUST ser permitido quando restar outro administrador ativo.
- **FR-007**: MUST ser impossível rebaixar o último administrador ativo. A tentativa MUST ser recusada e ele MUST continuar administrador. Como a trava de FR-006 e FR-007 é garantida sem chave secreta é [NEEDS CLARIFICATION: contagem no Web pela sessão do administrador, contagem num registro da empresa no Core, as duas, ou só a recusa da plataforma de identidade].
- **FR-008**: Se duas desativações concorrentes pudessem zerar os administradores ativos, o resultado MUST preservar pelo menos um administrador ativo.
- **FR-009**: O que a edição altera é [NEEDS CLARIFICATION: nada além de desativar, só o cargo entre Administrador e Membro, só o nome de outra pessoa, ou cargo e nome]. Em qualquer opção, o e-mail MUST NOT ser alterado por esta história. Idioma, fuso e o perfil que a própria pessoa mantém MUST NOT ser alterados por esta edição.
- **FR-010**: Se a desativação é reversível permanece em aberto. Esta especificação MUST NOT fixar botão de reativar nem fixar que a volta só ocorre por convite novo.
- **FR-011**: Quem além do administrador pode ver a lista permanece adiado. Esta especificação MUST NOT fixar que o membro vê a lista nem que só o administrador vê.
- **FR-012**: Sessão ausente MUST responder 401. Sessão sem empresa selecionada MUST ser recusada com "Selecione uma empresa para continuar." Identificador de outra empresa enviado no pedido MUST ser ignorado.
- **FR-013**: O cargo continua o já aceito na LLT-167. Com a lista cujo padrão é `admin`, `admin` e `org:admin` MUST ser administrador. `member` e `org:member` MUST NOT ser administrador. O nome exibido do cargo MUST NOT conceder poder de administrador. Esta história MUST NOT criar Gestor, Visualizador nem outra função. Isso permanece na LLT-169.
- **FR-014**: Esta entrega MUST NOT incluir convite, revogação de convite pendente nem reenvio (LLT-167), o catálogo e a matriz de permissões (LLT-169), o efeito do cargo sobre cada ação, a auditoria da troca de cargo e o 403 fino (LLT-170), acesso por produto (LLT-171), preferência de e-mail (LLT-172), canais de notificação (LLT-173) nem a manutenção do próprio perfil de nome, idioma e fuso (LLT-174). Se a decisão de FR-009 trouxer a troca entre Administrador e Membro para cá, a LLT-170 permanece dona da matriz, da auditoria e do que a interface esconde.
- **FR-015**: Esta entrega MUST NOT gravar segredo em código, teste, exemplo ou documentação versionada. Chave secreta da Clerk, inclusive valor que comece com `sk_` ou a variável `CLERK_SECRET_KEY`, MUST NOT entrar neste repositório nem no Web. O Core MUST NOT passar a chamar a plataforma de identidade com segredo. A configuração do dashboard da Clerk, se a decisão exigir alguma, é tarefa de outra pessoa. Esta entrega só documenta o que configurar, sem valor secreto.
- **FR-016**: Os comportamentos já decididos — lista com os cinco dados, isolamento entre empresas, recusa sem sessão e sem empresa, desativação que corta a próxima requisição, recusa de desativar ou rebaixar o último administrador, preservação de pelo menos um administrador sob concorrência, e-mail inalterado e ausência de segredo — MUST ser demonstráveis por testes. O teste do corte de acesso MUST falhar se a próxima requisição da pessoa desativada ainda for aceita. O teste não escolhe o mecanismo enquanto FR-003, FR-004, FR-007, FR-009 e FR-010 estiverem em aberto.

### Key Entities *(include if feature involves data)*

- **Usuário da empresa**: Pessoa que já entrou na empresa, com nome, e-mail, cargo, status e último acesso naquela empresa. O e-mail identifica a pessoa e não é editado aqui. O mesmo usuário pode existir em outra empresa com status independente.
- **Status**: Ativo ou desativado, por empresa. Desativado perde o acesso àquela empresa na próxima requisição. Se o status volta a ativo não está decidido.
- **Cargo**: Administrador ou Membro, como na LLT-167. Rebaixar é passar de Administrador a Membro. Outros cargos não entram.
- **Último acesso**: Dado mostrado na lista. A origem não está decidida. Sem uso registrado, a lista mostra a ausência.
- **Administrador**: Quem mantém a lista nesta história. É também o cargo que não pode chegar a zero na empresa.
- **Empresa**: A organização da sessão. A lista e a desativação não atravessam empresa.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Um administrador encontra um usuário da própria empresa na lista e lê nome, e-mail, cargo, status e último acesso em até 1 minuto.
- **SC-002**: Em 100% das listas consultadas por um administrador, nenhuma linha é de outra empresa.
- **SC-003**: Em 100% das desativações aceitas, a próxima requisição da pessoa desativada naquela empresa é recusada, e a requisição de outra pessoa da mesma empresa continua aceita.
- **SC-004**: Em 100% das tentativas de desativar ou rebaixar o único administrador ativo, a pessoa permanece administrador, ativa e com acesso, e a empresa não fica com zero administradores.
- **SC-005**: Em 100% das edições aceitas desta história, o e-mail da pessoa permanece o mesmo de antes.
- **SC-006**: Depois da entrega, o material versionado do Core e do Web continua sem chave secreta da plataforma de identidade.

## Assumptions

- A branch de trabalho é `LLT-168-listar-editar-e-desativar-usuarios`, criada a partir do HEAD de `LLT-167-convidar-usuario-para-a-empresa`. Esta especificação não abre pull request, não altera outra branch e não edita o Jira.
- A empresa da lista é a organização ativa na sessão. O Core já recusa sessão sem empresa com "Selecione uma empresa para continuar." e já trata como administrador o identificador presente em `CLERK_ADMIN_ROLES` (padrão `admin`), depois de remover o prefixo `org:`.
- A LLT-167 não guarda vínculo. Quem já entrou só existe, para o Core de hoje, quando a sessão traz a organização e o cargo. Esta história não decide se passa a guardar um registro de usuários da empresa. Isso depende das decisões em aberto.
- O perfil da LLT-174 guarda nome, idioma e fuso da própria pessoa, não o e-mail nem o cargo, e não é por empresa. Esta história não reabre esse perfil.
- A tela fica no Web, em outro repositório. O que o Core precisa expor só pode ser fechado depois das decisões em aberto.
- Desativar é por empresa. A pessoa desativada na empresa A pode continuar na empresa B.
- Há sempre, antes da operação, pelo menos um administrador ativo. A história não cria a primeira pessoa da empresa. Isso continua no convite e nas telas de entrada já existentes.
- A configuração do dashboard da Clerk continua com outra pessoa. Sem a decisão do corte de acesso, esta spec não lista um item novo de dashboard além do que a LLT-167 já documentou: cargos `admin` e `member`.
- LLT-169, LLT-170, LLT-171, LLT-172, LLT-173 e LLT-174 permanecem nas fronteiras de FR-014. A LLT-170 continua a história em que o cargo define as ações, com auditoria, mesmo se a troca entre Administrador e Membro for trazida para cá.
