# Feature Specification: Listar, editar e desativar usuários

**Feature Branch**: `LLT-168-listar-editar-e-desativar-usuarios`

**Created**: 2026-11-15

**Status**: Ready

**Input**: User description: "LLT-168 Listar, editar e desativar usuários (épico LLT-99, feature LLT-119 Gestão de usuários). Como administrador, quero manter a lista de usuários da empresa. A lista mostra nome, e-mail, cargo, status e último acesso. Usuário desativado perde o acesso na próxima requisição. Não é possível desativar ou rebaixar o último administrador. Sem chave secreta da Clerk no Core nem no Web."

## Clarifications

### Session 2026-11-15

- Q: Como o usuário desativado perde o acesso na próxima requisição, se a sessão já emitida continua válida até expirar? → A: Só uma marca de desativado no Core, conferida em toda requisição autenticada da empresa. A recusa vale mesmo com o token ainda válido. A pessoa continua na organização da Clerk e aparece na lista com status Desativado.
- Q: De onde vem o último acesso? → A: O Core grava o instante da última requisição autenticada aceita da pessoa naquela empresa. Se a gravação anterior desse instante tem menos de 5 minutos e o cargo observado não mudou, o Core não grava de novo. Cinco minutos é o intervalo desta história.
- Q: O que a edição altera? → A: Só o cargo, entre Administrador e Membro. Quem troca é o Web, pelo SDK da Clerk, na sessão do administrador. O Core não troca cargo.
- Q: Como impedir desativar ou rebaixar o último administrador sem chave secreta? → A: O Web conta os administradores antes de rebaixar ou desativar e não conclui a ação se ela deixar a empresa sem administrador em atividade. O Core recusa desativar o último administrador ativo que ele conhece. O Core conhece esse cargo pelo valor visto no token na última requisição aceita da pessoa naquela empresa. O risco de o Core estar atrasado em relação à Clerk fica registrado no contrato.
- Q: Desativar é reversível? → A: Sim. O administrador tira a marca e o acesso volta na próxima requisição. A pessoa não sai da organização.
- Q: Quem vê e altera a lista, e onde ela aparece? → A: Só o administrador vê e altera. A lista fica na tela `/equipe` do Web, junto com os convites.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ver quem está na empresa (Priority: P1)

Como administrador da empresa selecionada, quero ver na tela `/equipe` a lista de usuários dessa empresa, com nome, e-mail, cargo, status e último acesso, para saber quem tem acesso e em que condição.

**Why this priority**: Sem a lista, não há o que editar, desativar ou proteger. É o primeiro critério de aceite.

**Independent Test**: Com a empresa selecionada, o administrador abre `/equipe` e vê, para cada pessoa da organização, os cinco dados. Nome, e-mail e cargo vêm da organização na sessão do administrador. Status e último acesso vêm do Core, por `userId` da empresa. Pessoa de outra empresa não aparece. Membro não abre essa lista.

**Acceptance Scenarios**:

1. **Given** a empresa A tem pessoas na organização, **When** o administrador da empresa A abre `/equipe`, **Then** cada linha mostra nome, e-mail, cargo, status e último acesso, ao lado dos convites já existentes.
2. **Given** a empresa A e a empresa B têm pessoas diferentes, **When** o administrador da empresa A abre a lista, **Then** só aparecem pessoas da empresa A.
3. **Given** uma pessoa da organização ainda não teve requisição aceita no Core, **When** o administrador abre a lista, **Then** a linha dela continua na lista, o status é Ativo e o último acesso indica que não há uso registrado.
4. **Given** a pessoa teve uma requisição aceita, **When** o administrador abre a lista de novo dentro de 5 minutos e o cargo observado não mudou, **Then** o último acesso mostrado é o instante já gravado.
5. **Given** a sessão é de um membro, **When** ele pede a lista no Core, **Then** a resposta é 403 e a lista não é devolvida.
6. **Given** não há sessão, **When** alguém tenta ver a lista, **Then** a resposta é 401.
7. **Given** a sessão não tem empresa selecionada, **When** alguém tenta ver a lista, **Then** a operação é recusada com "Selecione uma empresa para continuar."

---

### User Story 2 - Desativar, reativar e cortar o acesso (Priority: P1)

Como administrador, quero desativar um usuário da empresa para que a próxima requisição dele nesta empresa seja recusada, e quero reativá-lo para o acesso voltar, sem tirá-lo da organização.

**Why this priority**: É o segundo critério de aceite. A LLT-167 deixou esta retirada para cá: revogar o convite não tira quem já entrou.

**Independent Test**: O administrador desativa uma pessoa que não é o último administrador em atividade. A requisição seguinte dessa pessoa, nesta empresa, é recusada mesmo com token ainda válido. Ela continua na organização e na lista, com status Desativado. O administrador tira a marca e a próxima requisição volta a ser aceita.

**Acceptance Scenarios**:

1. **Given** a pessoa está ativa na empresa e não é o único administrador em atividade, **When** o administrador a desativa, **Then** o status dela na lista passa a Desativado e ela continua na organização.
2. **Given** a pessoa foi desativada, **When** ela faz a próxima requisição nesta empresa, **Then** o acesso é recusado. O token pode continuar válido. A sessão de outra pessoa da mesma empresa não é encerrada.
3. **Given** a pessoa foi desativada na empresa A e continua na empresa B, **When** ela usa a empresa B, **Then** a empresa B não é afetada por esta desativação.
4. **Given** a pessoa já está desativada, **When** o administrador desativa de novo, **Then** ela continua desativada e sem acesso. A repetição não reativa.
5. **Given** a pessoa está desativada, **When** o administrador reativa, **Then** a marca sai e a próxima requisição dela nesta empresa volta a ser aceita.
6. **Given** a pessoa já está ativa, **When** o administrador reativa de novo, **Then** ela continua ativa.

---

### User Story 3 - Preservar o último administrador (Priority: P1)

Como administrador, quero que a empresa nunca fique sem administrador em atividade, para que alguém continue podendo manter usuários e o cadastro.

**Why this priority**: É o terceiro critério de aceite. Sem essa trava, a empresa perde quem pode reativar uma pessoa ou operar o que já é de administrador.

**Independent Test**: Com um único administrador em atividade, a tentativa de desativá-lo é recusada no Core e no Web, e a tentativa de rebaixá-lo é recusada no Web antes de chamar a Clerk. Ele continua administrador, ativo e com acesso. Com dois administradores em atividade, desativar um deixa o outro.

**Acceptance Scenarios**:

1. **Given** a empresa tem um único administrador em atividade, **When** alguém tenta desativá-lo, **Then** o Core recusa, ele continua administrador, ativo e com acesso.
2. **Given** a empresa tem um único administrador em atividade, **When** alguém tenta rebaixá-lo a membro no Web, **Then** o Web não chama a Clerk e ele continua administrador.
3. **Given** a empresa tem dois administradores em atividade, **When** um deles é desativado, **Then** o outro permanece administrador ativo e a desativação do primeiro vale.
4. **Given** duas desativações do mesmo par de administradores partem juntas e cada uma deixaria a empresa sem administrador em atividade, **When** as duas concluem, **Then** pelo menos um administrador ativo permanece.

---

### User Story 4 - Editar o cargo (Priority: P2)

Como administrador, quero trocar o cargo de um usuário entre Administrador e Membro para que a próxima sessão dele reflita o cargo escolhido, sem mudar o e-mail e sem o Core gravar essa troca.

**Why this priority**: O aceite fala em rebaixar, e a edição desta história é só o cargo. A lista e a desativação já entregam valor sem esta troca.

**Independent Test**: No Web, na sessão do administrador, o cargo de uma pessoa que não é o último administrador em atividade muda de Membro para Administrador, ou o inverso. O Core não oferece caminho de troca de cargo. E-mail, idioma, fuso e o perfil da própria pessoa permanecem.

**Acceptance Scenarios**:

1. **Given** a pessoa é membro e existe outro administrador em atividade, **When** o administrador confirma o cargo Administrador no Web, **Then** o Web atualiza o cargo na organização pela sessão do navegador e o Core não grava essa troca.
2. **Given** a pessoa é administrador e existe outro administrador em atividade, **When** o administrador confirma o cargo Membro no Web, **Then** o Web atualiza o cargo na organização e o Core não grava essa troca.
3. **Given** qualquer troca de cargo aceita no Web, **When** a lista é aberta de novo, **Then** o e-mail da pessoa é o mesmo. Idioma, fuso e o dono do perfil da LLT-174 não mudam.
4. **Given** uma sessão chama um caminho de troca de cargo no Core, **When** o pedido chega, **Then** esse caminho não existe.

---

### Edge Cases

- Sessão ausente responde 401. Sessão sem empresa usa "Selecione uma empresa para continuar."
- Identificador de outra empresa enviado no pedido é ignorado. A lista, a desativação e a reativação valem para a empresa da sessão.
- Desativar a si mesmo é permitido quando existe outro administrador em atividade. Desativar a si mesmo sendo o único administrador em atividade conhecido pelo Core é recusado.
- Repetir a desativação não reativa. Repetir a reativação não desativa.
- Desativar na empresa A não desativa na empresa B.
- Duas tentativas simultâneas não podem deixar a empresa sem administrador em atividade entre os que o Core conhece.
- Pessoa sem requisição aceita continua na lista montada pelo Web, com status Ativo e sem último acesso. O Core só passa a ter registro dela na primeira requisição aceita, ou quando o administrador a desativa.
- Uma requisição recusada por desativação não atualiza o último acesso nem o cargo observado. O corpo traz `code` `user_deactivated` e a frase "Seu acesso a esta empresa foi desativado."
- Com menos de 5 minutos, e com o mesmo cargo observado, uma nova requisição aceita não regrava o último acesso. Aos 5 minutos, grava. A conferência da marca de desativado acontece em toda requisição, inclusive nesse intervalo.
- Duas requisições aceitas da mesma pessoa ao mesmo tempo não derrubam a chamada se as duas tentam gravar o último acesso. Vale o instante de uma delas.
- Se o cargo no token difere do cargo observado, a requisição aceita grava o cargo novo mesmo dentro dos 5 minutos.
- Nome ainda não gravado no perfil não tira a pessoa da lista. O nome e o e-mail exibidos são os da organização na sessão do administrador, não os do perfil.
- E-mail não é editado. Esta história não cria convite. Os convites continuam na mesma tela `/equipe`, como na LLT-167.
- Membro e administrador continuam com o significado já aceito na LLT-167: `admin` e `org:admin` são administrador; `member` e `org:member` não são. O nome exibido "Administrador" não concede o cargo.
- Membro não lista, não desativa, não reativa e não troca cargo.
- Pessoa desativada não reativa a si mesma: a requisição dela já é recusada.
- Nenhum segredo é gravado para a lista, a desativação ou a troca de cargo funcionarem. Não há configuração nova no dashboard da Clerk.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O administrador da empresa selecionada MUST poder ver a lista de usuários dessa empresa na tela `/equipe` do Web, junto com os convites. Cada linha MUST mostrar nome, e-mail, cargo, status e último acesso. Pessoa de outra empresa MUST NOT aparecer. Membro MUST NOT receber essa lista.
- **FR-002**: Nome, e-mail e cargo exibidos MUST vir da organização na sessão do administrador. O Core MUST expor, só para o administrador, o status e o último acesso de cada usuário que ele conhece na empresa, identificados pelo `userId`. O Web MUST montar a linha juntando os dois. Ausência de registro no Core MUST ser mostrada como status Ativo e último acesso sem uso registrado.
- **FR-003**: O status MUST distinguir Ativo e Desativado. No contrato do Core os valores são `active` e `deactivated`. O Web mostra Ativo e Desativado. Usuário desativado MUST continuar na organização e MUST aparecer na lista com status Desativado.
- **FR-004**: O Core MUST gravar o instante da última requisição autenticada aceita da pessoa naquela empresa. Se esse instante tem menos de 5 minutos e o cargo observado no token é o mesmo, o Core MUST NOT gravar de novo. Se o cargo observado difere, ou se ainda não há registro, a requisição aceita MUST gravar. Requisição recusada por desativação MUST NOT atualizar esse instante nem o cargo observado.
- **FR-005**: Desativar um usuário que não é o único administrador em atividade MUST gravar a marca de desativado no Core e MUST fazer a próxima requisição dele nesta empresa ser recusada, mesmo com token ainda válido, com `code` `user_deactivated` e "Seu acesso a esta empresa foi desativado." A pessoa MUST permanecer na organização. A sessão das outras pessoas MUST permanecer. Desativar na empresa A MUST NOT desativar na empresa B. O Core MUST NOT remover a pessoa da organização e MUST NOT chamar a Clerk.
- **FR-006**: Repetir a desativação de quem já está desativado MUST NOT reativar essa pessoa e MUST NOT devolver o acesso.
- **FR-007**: O administrador MUST poder tirar a marca de desativado. A próxima requisição da pessoa nesta empresa MUST voltar a ser aceita. Repetir a reativação de quem já está ativo MUST NOT desativar.
- **FR-008**: MUST ser impossível desativar o último administrador em atividade. O Web MUST contar, antes de desativar, os administradores da organização que não estão desativados no Core e MUST NOT pedir a desativação se ela deixar a contagem em zero. O Core MUST recusar desativar o último administrador ativo que ele conhece, com `code` `last_admin` e "Não é possível desativar o último administrador." A tentativa MUST deixar essa pessoa administrador, ativa e com acesso. Desativar a si mesmo MUST ser permitido quando restar outro administrador em atividade.
- **FR-009**: MUST ser impossível rebaixar o último administrador em atividade. O Web MUST contar antes de rebaixar, com a mesma regra de FR-008, e MUST NOT chamar a Clerk se a troca deixar a contagem em zero. A frase do Web é "Não é possível rebaixar o último administrador." O Core MUST NOT trocar cargo e MUST NOT oferecer caminho de troca de cargo.
- **FR-010**: Se duas desativações concorrentes pudessem zerar os administradores ativos que o Core conhece, o resultado MUST preservar pelo menos um.
- **FR-011**: A edição desta história MUST ser só a troca de cargo entre Administrador e Membro, feita pelo Web, pelo SDK da Clerk, na sessão do administrador. O e-mail MUST NOT mudar. Idioma, fuso e o perfil que a própria pessoa mantém MUST NOT mudar. O Core MUST NOT gravar o cargo novo por causa dessa troca. O cargo que o Core conhece MUST ser o visto no token da última requisição aceita da pessoa naquela empresa.
- **FR-012**: Só o administrador MUST ver e alterar a lista, desativar, reativar e trocar cargo. Membro MUST ser recusado sem encerrar a sessão, com `code` `not_admin` e "Somente o administrador da empresa pode gerenciar os usuários." `userId` vazio ou acima de 200 caracteres MUST ser recusado com "Informe o usuário." e MUST NOT gravar.
- **FR-013**: Sessão ausente MUST responder 401. Sessão sem empresa selecionada MUST ser recusada com "Selecione uma empresa para continuar." Identificador de outra empresa enviado no pedido MUST ser ignorado.
- **FR-014**: O cargo continua o já aceito na LLT-167. Com a lista cujo padrão é `admin`, `admin` e `org:admin` MUST ser administrador. `member` e `org:member` MUST NOT ser administrador. O nome exibido do cargo MUST NOT conceder poder de administrador. Esta história MUST NOT criar Gestor, Visualizador nem outra função. Isso permanece na LLT-169.
- **FR-015**: Esta entrega MUST NOT incluir convite, revogação de convite pendente nem reenvio (LLT-167 já os colocou no Web), o catálogo e a matriz de permissões (LLT-169), o efeito do cargo sobre cada ação, a auditoria da troca de cargo e o 403 fino por ação (LLT-170), acesso por produto (LLT-171), preferência de e-mail (LLT-172), canais de notificação (LLT-173) nem a manutenção do próprio perfil de nome, idioma e fuso (LLT-174). A troca entre Administrador e Membro desta história não transfere esses itens da LLT-170.
- **FR-016**: Esta entrega MUST NOT gravar segredo em código, teste, exemplo ou documentação versionada. Chave secreta da Clerk, inclusive valor que comece com `sk_` ou a variável `CLERK_SECRET_KEY`, MUST NOT entrar neste repositório nem no Web. O Core MUST NOT chamar a plataforma de identidade. Esta história MUST NOT pedir configuração nova no dashboard da Clerk.
- **FR-017**: Os comportamentos de lista, intervalo de 5 minutos, marca conferida em toda requisição, desativação, reativação, último administrador, troca de cargo só no Web, membro recusado e ausência de segredo MUST ser demonstráveis por testes. O teste do corte de acesso MUST falhar se a próxima requisição da pessoa desativada ainda for aceita.

### Key Entities *(include if feature involves data)*

- **Usuário da empresa**: Pessoa da organização, com nome, e-mail e cargo vindos da sessão do administrador, e com status e último acesso vindos do Core para aquele `userId` naquela empresa. O mesmo usuário pode existir em outra empresa com status independente.
- **Registro do Core**: O que o Core conhece de uma pessoa numa empresa: `userId`, se o cargo visto no último token aceito é de administrador, status e instante do último acesso aceito. Pode estar ausente.
- **Status**: Ativo ou Desativado, por empresa. Desativado é uma marca do Core. A pessoa continua na organização.
- **Cargo**: Administrador ou Membro, como na LLT-167. A troca é feita no Web. Rebaixar é passar de Administrador a Membro.
- **Último acesso**: Instante gravado pelo Core na última requisição aceita, com folga de até 5 minutos quando o cargo observado não muda.
- **Administrador em atividade**: Administrador que não está desativado. A empresa não pode ficar sem um. Para o Core, é quem ele viu como administrador num token aceito e que continua ativo.
- **Empresa**: A organização da sessão. A lista e a marca não atravessam empresa.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Um administrador encontra um usuário da própria empresa na tela `/equipe` e lê nome, e-mail, cargo, status e último acesso em até 1 minuto.
- **SC-002**: Em 100% das listas consultadas por um administrador, nenhuma linha é de outra empresa. Em 100% das consultas de membro, a lista não é devolvida.
- **SC-003**: Em 100% das desativações aceitas, a próxima requisição da pessoa desativada naquela empresa é recusada, mesmo com token ainda válido, e a requisição de outra pessoa da mesma empresa continua aceita. A pessoa desativada continua na organização.
- **SC-004**: Em 100% das reativações aceitas, a próxima requisição da pessoa naquela empresa volta a ser aceita.
- **SC-005**: Em 100% das tentativas de desativar ou rebaixar o único administrador em atividade, a pessoa permanece administrador, ativa e com acesso, e a empresa não fica sem administrador em atividade.
- **SC-006**: Em 100% das trocas de cargo aceitas, quem chama a Clerk é o Web na sessão do administrador, e o e-mail da pessoa permanece o mesmo.
- **SC-007**: Depois da entrega, o material versionado do Core e do Web continua sem chave secreta da plataforma de identidade, e esta história não exige configuração nova no dashboard.

## Assumptions

- A branch de trabalho é `LLT-168-listar-editar-e-desativar-usuarios`, criada a partir do HEAD de `LLT-167-convidar-usuario-para-a-empresa`. Esta especificação não abre pull request, não altera outra branch e não edita o Jira.
- A empresa da lista é a organização ativa na sessão. O Core já recusa sessão sem empresa com "Selecione uma empresa para continuar." e já trata como administrador o identificador presente em `CLERK_ADMIN_ROLES` (padrão `admin`), depois de remover o prefixo `org:`.
- A tela `/equipe` fica no Web, em outro repositório. O contrato do Core é a fonte da verdade do que o Web consulta e do que o Web faz na Clerk sem passar pelo Core.
- O perfil da LLT-174 guarda nome, idioma e fuso da própria pessoa, não o e-mail nem o cargo, e não é por empresa. O nome da lista não é esse perfil.
- Desativar é por empresa. A pessoa desativada na empresa A pode continuar na empresa B.
- Há sempre, antes da operação, pelo menos um administrador. A história não cria a primeira pessoa da empresa. Isso continua no convite e nas telas de entrada já existentes.
- O intervalo de 5 minutos é fixo nesta história. Ele reduz gravação repetida. A marca de desativado não espera esse intervalo.
- A Clerk permanece como está para outra pessoa. Esta história não muda dashboard, não remove membro da organização e não usa chave secreta.
- LLT-169, LLT-170, LLT-171, LLT-172, LLT-173 e LLT-174 permanecem nas fronteiras de FR-015. A LLT-170 continua a história em que o cargo define cada ação, com auditoria.
