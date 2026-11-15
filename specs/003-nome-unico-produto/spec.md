# Feature Specification: Nome único de produto por empresa

**Feature Branch**: `feature/LLT-177-nome-unico-produto`

**Created**: 09/11/2026

**Status**: Draft

**Input**: User description: "Nome único de produto por empresa (LLT-177): vale ao cadastrar e ao editar, ignora maiúsculas e espaços nas pontas, produto arquivado continua ocupando o nome, nome repetido responde 409. Fora do escopo: limite por plano (dono decidiu sem limite) e bloqueio de ingestão no Collector (depende do Michel)."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Recusar nome repetido no cadastro (Priority: P1)

Um membro autenticado cadastra um produto na empresa ativa. Se outro produto dessa empresa já usa o mesmo nome, comparando sem diferenciar maiúsculas de minúsculas e desconsiderando espaços nas pontas, o cadastro é recusado como conflito e nada é criado. Não importa se o outro produto está ativo ou arquivado. Outras empresas podem usar o mesmo nome livremente.

**Why this priority**: o cadastro é a principal porta de entrada de nomes. Sem esta regra, a empresa acumula produtos indistinguíveis nas listagens e na operação do dia a dia.

**Independent Test**: cadastrar um produto na empresa ativa e tentar cadastrar outro com o mesmo nome em variações de maiúsculas e espaços nas pontas; confirmar a recusa e que nada foi criado. Repetir o mesmo nome em outra empresa e confirmar que é aceito.

**Acceptance Scenarios**:

1. **Given** um produto "Checkout" na empresa ativa, **When** um membro cadastra outro produto chamado "Checkout", **Then** o cadastro é recusado com 409, o motivo aponta o nome e nenhum produto ou vínculo de acesso é criado.
2. **Given** um produto "Checkout" na empresa ativa, **When** um membro cadastra "checkout", "CHECKOUT" ou "  Checkout  ", **Then** o cadastro é recusado com 409.
3. **Given** um produto "Checkout" arquivado na empresa ativa, **When** um membro cadastra "checkout", **Then** o cadastro é recusado com 409 e o motivo deixa claro que o nome pode estar em uso por um produto arquivado.
4. **Given** um produto "Checkout" existente somente em outra empresa, **When** um membro da empresa ativa cadastra "Checkout", **Then** o cadastro é aceito.
5. **Given** um nome livre na empresa ativa, **When** um membro cadastra "  Meu Produto  ", **Then** o cadastro é aceito e o nome fica gravado como "Meu Produto", sem espaços nas pontas e com as maiúsculas digitadas.
6. **Given** dois cadastros simultâneos com o mesmo nome livre na mesma empresa, **When** ambos são processados, **Then** exatamente um é aceito e o outro é recusado com 409 pelo motivo de nome em uso.

---

### User Story 2 - Recusar nome repetido na edição (Priority: P2)

Um membro com acesso ao produto altera os dados gerais. Se o novo nome já é usado por outro produto da empresa, ativo ou arquivado, a edição é recusada como conflito e o produto permanece como estava. Manter o próprio nome, ou mudar apenas maiúsculas e espaços nas pontas dele, nunca conflita com o próprio produto.

**Why this priority**: sem esta regra, a unicidade do cadastro seria contornada com uma simples renomeação. Vem depois do cadastro porque reaproveita o mesmo critério de comparação.

**Independent Test**: com dois produtos na empresa ativa, tentar renomear um para o nome do outro em variações de maiúsculas e espaços e confirmar a recusa sem alteração; em seguida editar a descrição mantendo o nome e confirmar que é aceito.

**Acceptance Scenarios**:

1. **Given** os produtos "Checkout" e "Pagamentos" na empresa ativa, **When** um membro com acesso renomeia "Pagamentos" para " checkout ", **Then** a edição é recusada com 409, nenhum campo de "Pagamentos" muda e a versão de concorrência não avança.
2. **Given** um produto "Checkout" arquivado e um produto ativo "Pagamentos", **When** um membro com acesso renomeia "Pagamentos" para "Checkout", **Then** a edição é recusada com 409.
3. **Given** um produto "Checkout", **When** um membro com acesso altera a descrição e mantém o nome, **Then** a edição é aceita.
4. **Given** um produto "Checkout", **When** um membro com acesso altera o nome para "CHECKOUT", **Then** a edição é aceita e a nova forma do nome fica gravada.
5. **Given** um produto "Pagamentos" renomeado com sucesso para um nome livre, **When** um membro cadastra outro produto chamado "Pagamentos", **Then** o cadastro é aceito, pois o nome antigo foi liberado.
6. **Given** um nome em uso e um usuário sem acesso ao produto editado, ou um produto de outra empresa, **When** o usuário tenta a edição com esse nome, **Then** ele recebe a mesma recusa de acesso de hoje e nenhuma informação sobre conflito de nome.
7. **Given** duas edições simultâneas de produtos diferentes para o mesmo nome livre, **When** ambas são processadas, **Then** exatamente uma é aceita e a outra é recusada com 409 pelo motivo de nome em uso.

---

### User Story 3 - Nome reservado durante o arquivamento (Priority: P2)

Um DONO ou ADMIN arquiva um produto. O nome continua ocupado enquanto o produto estiver arquivado, de modo que o desarquivamento nunca encontra o nome tomado por outro produto. O nome só fica livre se o produto for desarquivado e renomeado.

**Why this priority**: arquivar preserva o cadastro para eventual retorno; se o nome fosse liberado, o desarquivamento poderia criar duplicidade ou falhar sem que o usuário entendesse a razão.

**Independent Test**: arquivar um produto, tentar cadastrar outro com o mesmo nome e confirmar a recusa; desarquivar o produto e confirmar que a operação conclui sem conflito.

**Acceptance Scenarios**:

1. **Given** um produto ativo "Checkout", **When** um DONO ou ADMIN o arquiva, **Then** o nome permanece ocupado e novos cadastros ou renomeações para "Checkout" são recusados com 409.
2. **Given** um produto "Checkout" arquivado, **When** um DONO ou ADMIN o desarquiva, **Then** o desarquivamento conclui sem conflito de nome.
3. **Given** um produto "Checkout" que foi desarquivado e renomeado para um nome livre, **When** um membro cadastra "Checkout", **Then** o cadastro é aceito.

---

### Edge Cases

- Letras acentuadas: "AÇÃO" e "ação" são o mesmo nome, porque diferem apenas em maiúsculas; "Acao" e "Ação" são nomes diferentes.
- Espaços internos contam: "Meu App" e "Meu  App" (dois espaços) são nomes diferentes.
- Nome vazio, só com espaços ou acima de 200 caracteres: continua recusado como dado inválido, não como conflito de nome.
- Nome ocupado por produto que o membro não consegue ver, por falta de acesso ou por estar arquivado: a recusa vale do mesmo modo, porque a regra é da empresa, e não revela identificador nem outros dados desse produto.
- Edição de produto arquivado com nome repetido: prevalece a recusa de produto arquivado já existente; nenhum dado muda.
- Edição com versão de concorrência desatualizada e nome repetido: a gravação é recusada como conflito por qualquer um dos motivos e nenhum dado muda.
- Gravação de contexto, arquivar e desarquivar não alteram o nome e, portanto, não são recusados por esta regra.
- Troca de empresa no meio da operação: a comparação considera sempre a empresa ativa da requisição.
- Sessão inválida ou organização ausente: a operação é recusada como hoje, sem avaliar o nome.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema MUST impedir que um cadastro ou uma edição deixe dois produtos da mesma empresa com o mesmo nome, estejam eles ativos ou arquivados.
- **FR-002**: A comparação de nomes MUST ignorar diferenças entre maiúsculas e minúsculas, inclusive em letras acentuadas, e MUST ignorar espaços nas pontas; acentos e espaços internos MUST continuar diferenciando os nomes.
- **FR-003**: O cadastro com nome já em uso na empresa ativa MUST ser recusado com HTTP 409.
- **FR-004**: A edição dos dados gerais com nome já em uso por outro produto da empresa ativa MUST ser recusada com HTTP 409.
- **FR-005**: A edição MUST NOT conflitar com o próprio produto: manter o nome ou alterar apenas maiúsculas e espaços nas pontas MUST ser aceito, e a nova forma do nome MUST ficar gravada.
- **FR-006**: Produto arquivado MUST continuar ocupando o nome; arquivar MUST NOT liberar o nome e desarquivar MUST NOT ser recusado por causa do nome.
- **FR-007**: A unicidade MUST valer por empresa; o mesmo nome MUST ser aceito em empresas diferentes e a comparação MUST NOT revelar dados de outra empresa.
- **FR-008**: O nome MUST continuar sendo gravado sem espaços nas pontas e com as maiúsculas informadas pelo usuário.
- **FR-009**: A recusa por nome repetido MUST informar que o nome já está em uso na empresa, inclusive por produto arquivado; MUST ser distinguível dos conflitos de versão de concorrência e de produto arquivado; e MUST NOT expor identificador ou outros dados do produto que ocupa o nome.
- **FR-010**: Um cadastro recusado MUST NOT deixar produto nem vínculo de acesso criados; uma edição recusada MUST NOT alterar campo algum nem avançar a versão de concorrência.
- **FR-011**: A regra MUST valer também em gravações simultâneas: entre cadastros ou edições concorrentes com o mesmo nome na mesma empresa, exatamente uma gravação MUST ser aceita e as demais MUST receber a recusa de FR-009.
- **FR-012**: As recusas já existentes de sessão, validação do nome, acesso ao produto e isolamento por empresa MUST ser avaliadas antes da unicidade; quem não tem acesso ao produto editado ou opera em produto de outra empresa MUST receber a mesma recusa de hoje, nunca a de nome repetido.
- **FR-013**: A gravação de contexto, o arquivamento e o desarquivamento MUST NOT ser recusados por esta regra.
- **FR-014**: A recusa por nome repetido MUST NOT encerrar a sessão do usuário.
- **FR-015**: O sistema MUST NOT introduzir limite de quantidade de produtos por plano e MUST NOT alterar regras de ingestão de telemetria nesta feature.

### Key Entities

- **Produto**: cadastro da empresa ativa cujo nome é único dentro da empresa, esteja ativo ou arquivado. O nome é exibido como foi digitado, sem espaços nas pontas.
- **Nome para comparação**: forma do nome usada apenas para decidir se dois nomes são iguais, sem distinção de maiúsculas e sem espaços nas pontas; não é exibida ao usuário.
- **Empresa ativa**: organização em que o usuário trabalha e escopo da unicidade.
- **Acesso ao produto**: vínculo criado no cadastro; não pode sobrar quando o cadastro é recusado.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% das tentativas de cadastrar ou renomear para um nome já em uso na mesma empresa, em qualquer variação de maiúsculas e espaços nas pontas, são recusadas e deixam os dados inalterados.
- **SC-002**: Em 100% dos testes com gravações simultâneas do mesmo nome na mesma empresa, exatamente uma gravação é aceita.
- **SC-003**: 100% dos cadastros com nome usado apenas em outra empresa são aceitos.
- **SC-004**: 100% das edições que mantêm o próprio nome, ou mudam só maiúsculas e espaços nas pontas dele, são aceitas.
- **SC-005**: 100% dos produtos arquivados mantêm o nome reservado, e 100% dos desarquivamentos concluem sem conflito de nome.
- **SC-006**: Em 100% das recusas por nome repetido, o motivo identifica o nome como causa e nenhum dado do produto que o ocupa é exposto.

## Assumptions

- Cadastro, edição, arquivamento, papéis, acesso e isolamento por empresa entregues em `specs/001-empresa-produtos` e `specs/002-editar-arquivar-produto` permanecem válidos e não são reespecificados aqui.
- O status 409 faz parte do pedido e é o contrato externo da recusa; os demais detalhes de resposta seguem o padrão de erro já adotado pelo Core.
- O pedido citou apenas maiúsculas e espaços nas pontas; por isso, acentos e espaços internos seguem diferenciando nomes.
- Considera-se "espaço nas pontas" o mesmo que o cadastro e a edição já removem hoje ao gravar o nome.
- Não há dados de produção, portanto não há migração nem correção de cadastros anteriores. Esta versão precisa subir em banco local sem produtos gravados antes dela, e nenhum banco usado por outras branches é apagado ou alterado para isso, conforme [quickstart.md](quickstart.md).
- A recusa pode indicar a um membro da empresa que um nome está em uso por produto que ele não vê; isso é inerente à unicidade por empresa e não expõe dados além do próprio nome digitado por ele.
- O Core não tem interface; a exibição da recusa ao usuário é responsabilidade do frontend, em repositório próprio.
- Fora de escopo: limite de produtos por plano (decisão do dono: sem limite), bloqueio de ingestão no Collector (depende do Michel), exclusão definitiva de produto e liberação manual de nome de produto arquivado.
