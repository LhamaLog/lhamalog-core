# Feature Specification: Manter dados da empresa

**Feature Branch**: `LLT-175-manter-dados-da-empresa`

**Created**: 2026-11-09

**Status**: Draft

**Input**: User description: "LLT-175 Manter dados da empresa. Como administrador, quero cadastrar, editar e excluir os dados da empresa, com isolamento entre empresas. Critérios: só admin da organização (CLERK_ADMIN_ROLES) edita ou exclui; membro só lê e recebe 403; exclusão do cadastro da empresa; nenhum dado de outra organização acessível, com testes HTTP."

## Clarifications

### Session 2026-11-09

- Q: Ao excluir a empresa, o que deve ser apagado? → A: Só o cadastro da empresa (dados cadastrais, contexto, endereço, preferências, logotipo e histórico de contexto). Produtos e a organização continuam.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Administrador cadastra e edita a empresa (Priority: P1)

Como administrador da organização, quero cadastrar e depois alterar os dados da minha empresa para que o cadastro da organização ativa fique correto e possa ser consultado de novo.

**Why this priority**: Sem gravar e reler o cadastro, o administrador não cumpre o objetivo da entrega. A leitura posterior é a prova de que o dado permaneceu na empresa certa.

**Independent Test**: Com uma sessão de administrador da organização A, gravar um cadastro, consultá-lo, alterar um dado e consultar outra vez. O segundo resultado mostra a alteração e continua sendo o cadastro de A.

**Acceptance Scenarios**:

1. **Given** a organização ativa ainda não tem cadastro gravado, **When** o administrador informa os dados da empresa e confirma, **Then** a consulta seguinte devolve esses dados para a mesma organização.
2. **Given** a organização ativa já tem cadastro, **When** o administrador altera dados cadastrais, contexto, endereço, preferências ou logotipo e confirma, **Then** a consulta seguinte devolve os valores novos e preserva o que não foi alterado.
3. **Given** o administrador grava um cadastro incompleto, dentro do que o produto já aceita hoje, **When** a consulta é refeita, **Then** o cadastro incompleto permanece disponível para a mesma organização.
4. **Given** duas alterações partem da mesma versão conhecida do cadastro, **When** as duas são confirmadas, **Then** só a primeira prevalece e a segunda é recusada, sem misturar os dois envios.

---

### User Story 2 - Membro consulta e não altera (Priority: P1)

Como membro da organização que não é administrador, quero consultar os dados da empresa e quero que o produto impeça meu cadastro, minha edição e minha exclusão, para que eu veja a informação sem modificá-la.

**Why this priority**: A entrega só está correta se a leitura do membro e a recusa das demais ações ocorrerem juntas. Um membro que altera o cadastro quebra o critério de acesso.

**Independent Test**: Com o cadastro da organização A já gravado, uma sessão de membro não administrador de A consulta os dados com sucesso. As tentativas de cadastrar, editar, trocar ou remover o logotipo e excluir o cadastro são recusadas, e uma nova consulta mostra o cadastro intacto.

**Acceptance Scenarios**:

1. **Given** o membro autenticado pertence à organização do cadastro e não tem papel de administrador, **When** consulta os dados da empresa, **Then** recebe o cadastro dessa organização.
2. **Given** o mesmo membro, **When** tenta cadastrar, editar, substituir o logotipo, remover o logotipo ou excluir o cadastro, **Then** a operação é recusada como não autorizada (403), a sessão permanece válida e o cadastro não muda.
3. **Given** a pessoa tem um nome de papel parecido com administrador, mas o identificador do papel não está na lista de administradores configurada, **When** tenta editar ou excluir, **Then** vale a mesma recusa do membro, e a consulta continua permitida.
4. **Given** a sessão é inválida ou não traz organização, **When** a pessoa tenta consultar, editar ou excluir, **Then** não há acesso ao cadastro de nenhuma empresa.

---

### User Story 3 - Administrador exclui o cadastro (Priority: P1)

Como administrador da organização, quero excluir o cadastro da empresa para remover os dados gravados dessa organização sem apagar a organização em si e sem afetar outra empresa.

**Why this priority**: A exclusão do cadastro é critério explícito da entrega e precisa ser distinta de apagar a organização ou os produtos.

**Independent Test**: Um administrador grava um cadastro com logotipo, exclui o cadastro e consulta de novo. A consulta volta ao estado sem cadastro gravado. O mesmo administrador consegue cadastrar a empresa outra vez.

**Acceptance Scenarios**:

1. **Given** a organização ativa tem cadastro gravado, inclusive logotipo e histórico desse cadastro, **When** o administrador exclui o cadastro, **Then** a consulta seguinte dessa organização não devolve os dados removidos, nem o logotipo, nem o histórico desse cadastro.
2. **Given** o cadastro acabou de ser excluído, **When** o administrador cadastra a empresa outra vez, **Then** a nova consulta devolve somente o cadastro novo.
3. **Given** a organização ativa não tem cadastro gravado, **When** o administrador pede a exclusão, **Then** a operação termina sem efeito e sem alterar o cadastro de outra organização.
4. **Given** existe cadastro em outra organização, **When** o administrador exclui o cadastro da própria organização, **Then** o cadastro da outra organização permanece igual.
5. **Given** a organização ativa tem cadastro e também tem produtos, **When** o administrador exclui o cadastro, **Then** os produtos e a organização continuam, e a consulta da empresa fica sem o cadastro removido.

---

### User Story 4 - Isolamento entre empresas (Priority: P1)

Como administrador de uma organização, quero que nenhuma consulta, alteração ou exclusão alcance o cadastro de outra organização, para que cada empresa só trabalhe com os próprios dados.

**Why this priority**: O isolamento é critério de aceite da entrega. Sem ele, cadastro, edição e exclusão vazam dados entre clientes.

**Independent Test**: Existem cadastros diferentes nas organizações A e B. Testes de acesso autenticado, feitos como cliente, cobrem consulta, alteração e exclusão nas duas organizações. Nenhuma resposta revela ou modifica o cadastro da organização que não é a da sessão, mesmo quando o pedido cita a outra organização.

**Acceptance Scenarios**:

1. **Given** A e B têm cadastros diferentes e a sessão é da organização A, **When** o administrador ou o membro consulta a empresa, **Then** o resultado contém apenas o cadastro de A.
2. **Given** a sessão é da organização A, **When** o pedido de consulta, alteração ou exclusão informa o identificador da organização B, **Then** a operação continua valendo somente para A e o cadastro de B permanece intacto e oculto.
3. **Given** a sessão é de um administrador de A, **When** ele altera ou exclui o cadastro, **Then** uma consulta com sessão de B devolve o cadastro de B como estava antes.
4. **Given** a sessão é de um membro de A, **When** ele tenta alterar ou excluir usando qualquer referência à organização B, **Then** a operação é recusada (403), B não muda e A também não muda.

---

### Edge Cases

- Membro não administrador tenta cadastrar, editar, trocar logotipo, remover logotipo ou excluir: a resposta é não autorizada (403), a sessão segue válida e os dados ficam como estavam.
- Administrador de uma organização tenta ler, alterar ou excluir o cadastro de outra: a outra empresa não aparece e não muda.
- Identificador de organização enviado no pedido é ignorado. A empresa da operação é sempre a organização da sessão autenticada.
- Exclusão com cadastro existente remove dados cadastrais, contexto, endereço, preferências, logotipo e histórico de contexto. A consulta seguinte fica sem cadastro gravado. Produtos, vínculos de acesso a produtos e a organização continuam.
- Exclusão sem cadastro gravado não produz efeito e não alcança outra organização.
- Duas gravações de administrador partem da mesma versão conhecida: a primeira vale; a segunda é recusada.
- Sessão ausente, inválida ou sem organização não consulta, não grava e não exclui.
- Papel reconhecido só pelo nome exibido, fora da lista configurada de administradores, não edita nem exclui.
- Depois da exclusão, um novo cadastro do administrador substitui o estado vazio e não reaproveita os dados removidos.
- Cadastro incompleto continua podendo ser gravado pelo administrador, com a validação já existente quando o campo vem preenchido.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema MUST permitir que o administrador da organização ativa cadastre os dados da empresa quando ainda não houver cadastro gravado.
- **FR-002**: O sistema MUST permitir que o administrador altere o cadastro já gravado, incluindo dados cadastrais, contexto, endereço, preferências e logotipo, e MUST preservar os valores não enviados para alteração.
- **FR-003**: O sistema MUST devolver, na consulta seguinte da mesma organização, os valores confirmados na última gravação bem-sucedida. Dados que o administrador não alterou MUST permanecer. Dados de controle da própria gravação, como a versão do cadastro, podem acompanhar a resposta e não substituem os valores informados.
- **FR-004**: O sistema MUST tratar como administrador somente quem possui papel cujo identificador está na lista configurada de papéis administrativos da organização (`CLERK_ADMIN_ROLES`, padrão `admin`). Nome exibido do papel MUST NOT conceder esse acesso.
- **FR-005**: Membro autenticado da mesma organização, sem papel de administrador, MUST conseguir consultar o cadastro, o logotipo e o histórico de contexto dessa organização.
- **FR-006**: Membro sem papel de administrador MUST ser recusado com não autorizado (403) ao cadastrar, editar, substituir logotipo, remover logotipo ou excluir o cadastro. A recusa MUST manter a sessão e MUST NOT alterar dados.
- **FR-007**: O administrador MUST poder excluir o cadastro da empresa da organização ativa. A exclusão MUST remover os dados cadastrais, o contexto, o endereço, as preferências, o logotipo e o histórico desse cadastro.
- **FR-008**: Depois da exclusão, a consulta da mesma organização MUST mostrar a ausência de cadastro gravado, e o administrador MUST poder cadastrar a empresa novamente sem recuperar o conteúdo excluído.
- **FR-009**: Exclusão pedida quando não há cadastro gravado MUST terminar sem efeito sobre qualquer organização.
- **FR-010**: Toda consulta, gravação e exclusão MUST usar somente a organização da sessão autenticada. Identificador de organização enviado pelo cliente MUST ser ignorado.
- **FR-011**: Nenhuma operação MUST revelar ou modificar cadastro, logotipo ou histórico de outra organização.
- **FR-012**: Quando duas gravações partem da mesma versão conhecida, o sistema MUST aceitar uma e recusar a outra, sem combinar os dois conteúdos.
- **FR-013**: Sessão ausente, inválida ou sem organização MUST impedir consulta, gravação e exclusão do cadastro.
- **FR-014**: A recusa por falta de papel de administrador MUST ser distinta da rejeição de sessão inválida e MUST NOT encerrar a sessão de quem continua autenticado.
- **FR-015**: Os comportamentos de cadastro, edição, exclusão, recusa do membro (403) e isolamento entre organizações MUST ser demonstrados por testes de acesso autenticado, exercidos como um cliente faz, cobrindo sucesso do administrador, leitura do membro, recusa 403 e ausência de dados de outra organização na consulta, na alteração e na exclusão.
- **FR-016**: A exclusão MUST remover somente o cadastro da organização ativa: dados cadastrais, contexto, endereço, preferências, logotipo e histórico de contexto. Produtos, vínculos de acesso a produtos, tokens, alertas, faturamento, métricas e a organização no serviço de identidade MUST permanecer.

### Key Entities *(include if feature involves data)*

- **Empresa (cadastro)**: Dados da organização ativa. Inclui dados cadastrais, contexto, endereço, preferências, logotipo e o histórico de contexto. Existe no máximo um cadastro por organização. Pode estar ausente. A exclusão apaga este cadastro e preserva os produtos e a organização.
- **Organização**: Agrupamento ao qual a sessão autenticada pertence. Define qual cadastro pode ser lido ou mantido. Não é criada nem removida por esta feature.
- **Membro**: Pessoa autenticada dentro de uma organização. Consulta o cadastro da própria organização.
- **Administrador**: Membro cujo identificador de papel está na lista configurada de papéis administrativos. Cadastra, edita e exclui o cadastro da própria organização.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Um administrador conclui o cadastro da própria empresa e, em seguida, uma alteração, confirmando cada resultado na consulta seguinte, em até 2 minutos por operação.
- **SC-002**: Em 100% das tentativas de cadastro, edição, troca de logotipo, remoção de logotipo e exclusão feitas por membro não administrador, o cadastro permanece igual e a pessoa recebe recusa de permissão, continuando autenticada.
- **SC-003**: Em 100% dos cenários verificados com duas organizações, consulta, alteração e exclusão de uma organização deixam o cadastro da outra invisível e inalterado.
- **SC-004**: Após uma exclusão confirmada, a consulta da mesma organização não devolve o cadastro removido, os produtos dessa organização continuam disponíveis, a organização continua existindo, e o administrador consegue concluir um novo cadastro na tentativa seguinte.
- **SC-005**: Os cenários de administrador, membro e isolamento desta feature têm evidência de teste de acesso cobrindo leitura, gravação, exclusão e recusa de permissão, sem lacuna entre o que a spec exige e o que foi exercitado.

## Assumptions

- A empresa continua sendo a organização da sessão. Cadastrar grava o primeiro cadastro dessa organização; não cria uma organização nova no serviço de identidade.
- Excluir remove somente o cadastro guardado no produto: dados cadastrais, contexto, endereço, preferências, logotipo e histórico de contexto. A organização no serviço de identidade, os produtos, os vínculos de acesso a produtos, tokens, alertas, faturamento e métricas permanecem.
- A lista que define o administrador é a já configurada em `CLERK_ADMIN_ROLES`. O padrão permanece `admin`. Esta feature não cria papéis nem altera essa lista.
- A recusa do membro em cadastro, edição e exclusão é não autorizado, observável como 403. Rejeição de sessão inválida continua sendo falha de autenticação, diferente desse 403.
- Consulta sem cadastro gravado continua apresentando a empresa vazia da própria organização, sem dados de outra empresa. A exclusão devolve a organização a esse estado.
- Validação, cadastro incompleto, limites de conteúdo e conflito de versão permanecem os já aceitos no cadastro atual da empresa.
- Consulta de CEP, exportação de PDF e sincronização seguem indisponíveis e fora do escopo. Esta feature não muda esses caminhos.
- A evidência pedida para o aceite são testes de acesso HTTP do cadastro da empresa, alinhados ao critério da LLT-175 e ao princípio de testes antes do merge.
- A branch de trabalho já é `LLT-175-manter-dados-da-empresa`. Esta especificação não cria nem troca de branch.
