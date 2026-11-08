# Feature Specification: Editar e arquivar Produto

**Feature Branch**: `002-editar-arquivar-produto`

**Created**: 06/11/2026

**Status**: Draft

**Input**: User description: "Editar e arquivar Produto (LLT-123). O cadastro, a listagem e o contexto do produto já existem (specs/001-empresa-produtos). Esta feature acrescenta: 1. Editar os dados gerais do produto: nome (obrigatório, até 200), descrição, categoria e versão, com controle de concorrência pelo campo version, como já é feito no contexto. 2. Arquivar e desarquivar produto: o arquivado some da listagem padrão (com filtro para ver os arquivados), não pode ser editado e mantém todos os dados. Só quem tem acesso ao produto pode editar; arquivar e desarquivar só DONO e ADMIN. Respeitar o isolamento por empresa da 001. Fora do escopo: excluir definitivamente e bloquear a ingestão no Collector."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Editar dados gerais do produto (Priority: P1)

Um membro autenticado, na empresa ativa, com acesso ao produto, abre o cadastro existente e atualiza nome, descrição, categoria e versão do produto. O nome continua obrigatório e limitado a 200 caracteres. A gravação só ocorre se a versão de concorrência enviada for a atual; após sucesso, os novos dados permanecem ao recarregar. Quem não tem acesso ao produto, ou quem tenta operar em produto de outra empresa, não altera nem vê o cadastro.

**Why this priority**: sem edição dos dados gerais o produto fica congelado no momento do cadastro; é o valor principal desta entrega e reutiliza o isolamento e o controle de concorrência já aceitos no contexto.

**Independent Test**: criar um produto na empresa ativa, alterar os quatro campos com a versão de concorrência atual, recarregar e confirmar persistência; repetir com versão desatualizada e com usuário sem acesso e verificar recusa sem alteração.

**Acceptance Scenarios**:

1. **Given** um produto ativo da empresa ativa e um usuário com acesso a ele, **When** ele grava nome, descrição, categoria e versão do produto com a versão de concorrência atual, **Then** os dados são persistidos e reaparecem iguais após recarregar.
2. **Given** um produto cujo nome seria vazio ou maior que 200 caracteres, **When** o usuário tenta gravar, **Then** a alteração é recusada, o motivo aponta o nome e o cadastro anterior permanece.
3. **Given** dois usuários com acesso gravando o mesmo produto, **When** o segundo envia a versão de concorrência anterior à gravação do primeiro, **Then** a segunda gravação é recusada por conflito e os dados da primeira gravação permanecem.
4. **Given** um membro autenticado na mesma empresa sem acesso ao produto (e que não é administrador da organização), **When** ele tenta editar os dados gerais, **Then** a operação é recusada e o produto não muda.
5. **Given** o identificador de um produto de outra empresa, **When** qualquer usuário da empresa ativa tenta consultá-lo ou editá-lo, **Then** o produto não é exposto nem alterado.

---

### User Story 2 - Arquivar produto (Priority: P2)

Um DONO ou ADMIN da empresa ativa arquiva um produto que ainda não está arquivado. O produto desaparece da listagem padrão, permanece consultável quando o filtro de arquivados está ativo, conserva todos os dados (gerais, contexto e vínculos de acesso) e deixa de aceitar edição de dados gerais ou de contexto. Membros com acesso que não são DONO nem ADMIN não arquivam.

**Why this priority**: retira o produto do uso corrente sem destruir histórico; depende de o produto já existir e poder ser identificado, mas entrega valor mesmo antes do desarquivamento.

**Independent Test**: como DONO ou ADMIN, arquivar um produto visível na listagem padrão; confirmar que some da listagem padrão, aparece com o filtro de arquivados, recusa edição e permanece inalterado para um membro comum que tente arquivar.

**Acceptance Scenarios**:

1. **Given** um produto não arquivado da empresa ativa e um usuário DONO ou ADMIN, **When** ele arquiva o produto, **Then** o produto deixa a listagem padrão, aparece ao filtrar arquivados e todos os dados anteriores permanecem.
2. **Given** um produto arquivado, **When** um usuário com acesso tenta alterar dados gerais ou contexto, **Then** a alteração é recusada e os dados permanecem iguais.
3. **Given** um membro com acesso ao produto que não é DONO nem ADMIN, **When** ele tenta arquivar, **Then** a operação é recusada e o produto continua na listagem padrão.
4. **Given** um produto já arquivado, **When** DONO ou ADMIN tenta arquivá-lo de novo, **Then** o estado não muda de forma destrutiva (permanece arquivado, dados intactos) e a listagem padrão continua sem ele.

---

### User Story 3 - Desarquivar e filtrar arquivados (Priority: P2)

Um DONO ou ADMIN localiza um produto arquivado pelo filtro da listagem e o desarquiva. O produto volta à listagem padrão, volta a aceitar edição por quem tem acesso e conserva os dados que tinha ao ser arquivado. Membros comuns não desarquivam. A listagem, com ou sem filtro, só mostra produtos da empresa ativa aos quais o usuário tem direito de ver, conforme a feature 001.

**Why this priority**: completa o ciclo de vida sem exclusão definitiva; só faz sentido depois que o arquivo existe.

**Independent Test**: arquivar, listar com filtro, desarquivar como DONO ou ADMIN, confirmar retorno à listagem padrão e nova edição bem-sucedida; confirmar recusa de desarquive por membro comum.

**Acceptance Scenarios**:

1. **Given** produtos ativos e arquivados na empresa ativa, **When** o usuário lista sem filtro, **Then** só vê os não arquivados a que tem acesso; **When** aplica o filtro para ver arquivados, **Then** vê os arquivados a que tem acesso e nenhum produto de outra empresa.
2. **Given** um produto arquivado e um usuário DONO ou ADMIN, **When** ele desarquiva, **Then** o produto volta à listagem padrão, permanece com os mesmos dados e volta a aceitar edição por quem tem acesso.
3. **Given** um membro com acesso que não é DONO nem ADMIN, **When** ele tenta desarquivar, **Then** a operação é recusada e o produto permanece arquivado.
4. **Given** um produto não arquivado, **When** DONO ou ADMIN tenta desarquivá-lo, **Then** o produto permanece não arquivado, sem perda de dados.

---

### Edge Cases

- Sessão inválida ou organização ausente: nenhuma edição, arquivo ou listagem de produtos da empresa.
- Identificador inexistente na empresa ativa: operação recusada sem vazar se o produto existe em outra empresa.
- Troca de empresa no meio da edição: a gravação não se aplica ao produto da empresa anterior.
- Nome só com espaços: tratado como nome vazio e recusado.
- Descrição, categoria ou versão do produto omitidas: permitidas; valores anteriores podem ser limpos se o usuário gravar vazio nesses campos.
- Edição concorrente do contexto e dos dados gerais: cada gravação usa a versão de concorrência atual; a segunda com versão velha é recusada.
- Arquivar ou editar enquanto outro usuário acaba de arquivar: edição e novo arquivo não alteram dados; o produto permanece arquivado.
- Administrador da organização sem vínculo explícito no produto: pode editar (acesso total da 001); arquivar e desarquivar seguem a regra DONO/ADMIN.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Usuários com acesso ao produto MUST poder atualizar nome, descrição, categoria e versão de negócio do produto na empresa ativa.
- **FR-002**: O nome MUST ser obrigatório, sem ser apenas espaços, e MUST ter no máximo 200 caracteres; categoria e versão de negócio MUST ter no máximo 200 caracteres cada quando informadas; a descrição MUST ter no máximo 10000 caracteres quando informada, o mesmo teto do cadastro na feature 001.
- **FR-003**: A edição dos dados gerais MUST recusar gravação quando a versão de concorrência enviada não for a atual, no mesmo critério já usado na gravação do contexto do produto.
- **FR-004**: Após gravação bem-sucedida dos dados gerais, a versão de concorrência MUST avançar e os dados MUST persistir após recarregar.
- **FR-005**: DONO e ADMIN da empresa ativa MUST poder arquivar um produto não arquivado dessa empresa; o arquivo MUST ocultar o produto da listagem padrão e MUST preservar dados gerais, contexto, identificador, empresa e vínculos de acesso.
- **FR-006**: DONO e ADMIN da empresa ativa MUST poder desarquivar um produto arquivado dessa empresa; após desarquivar, o produto MUST voltar à listagem padrão e MUST voltar a aceitar edição.
- **FR-007**: A listagem padrão MUST excluir produtos arquivados; MUST existir filtro explícito para o usuário ver os arquivados da empresa ativa respeitando as regras de visibilidade da feature 001.
- **FR-008**: Produto arquivado MUST recusar qualquer alteração de dados gerais ou de contexto; a única mudança de estado permitida nesta feature é o desarquive por DONO ou ADMIN.
- **FR-009**: Membros autenticados que não são DONO nem ADMIN MUST NOT arquivar nem desarquivar, mesmo que tenham acesso ao produto.
- **FR-010**: Somente quem tem acesso ao produto (incluindo administradores da organização, que consultam todos os produtos da empresa) MUST editar dados gerais; a regra de acesso é a da feature 001.
- **FR-011**: Todas as consultas e alterações MUST permanecer isoladas pela empresa ativa; identificador de produto de outra empresa MUST NOT expor nem alterar dados.
- **FR-012**: Recusa de autorização ou conflito MUST NOT encerrar a sessão do usuário.
- **FR-013**: O sistema MUST NOT excluir definitivamente o produto e MUST NOT alterar regras de ingestão de telemetria nesta feature.

### Key Entities

- **Produto**: cadastro da empresa ativa com nome obrigatório, descrição, categoria e versão de negócio opcionais, estado arquivado ou não, dados de contexto já existentes e versão de concorrência usada em toda gravação.
- **Acesso ao produto**: vínculo que autoriza membros comuns a ver e editar o produto; não substitui a restrição de arquivo/desarquivo a DONO e ADMIN.
- **Empresa ativa**: organização em que o usuário trabalha; todo produto listado, editado, arquivado ou desarquivado pertence exclusivamente a ela.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Em 100% dos casos de teste de edição autorizada, os dados gerais gravados reaparecem iguais após recarregar a ficha do produto.
- **SC-002**: 100% das listagens sem filtro omitem produtos arquivados; com o filtro de arquivados, 100% dos produtos arquivados visíveis ao usuário aparecem e nenhum de outra empresa aparece.
- **SC-003**: 100% das tentativas de editar dados gerais ou contexto de produto arquivado são recusadas e deixam o cadastro inalterado.
- **SC-004**: 100% das tentativas de arquivar ou desarquivar por quem não é DONO nem ADMIN são recusadas.
- **SC-005**: 100% das tentativas de editar, arquivar ou consultar produto de outra empresa falham sem expor o cadastro.
- **SC-006**: Quando duas gravações concorrentes usam a mesma versão de concorrência, exatamente uma persiste e a outra é recusada, sem misturar campos das duas.

## Assumptions

- Cadastro, listagem padrão, consulta, contexto, autenticação, papéis e isolamento por empresa já entregues em `specs/001-empresa-produtos` permanecem válidos e não são reespecificados aqui.
- “Versão” nos dados gerais é o campo de negócio do produto (opcional no cadastro); “version” de concorrência é o mesmo controle já usado ao salvar o contexto.
- Limites de tamanho da edição coincidem com o cadastro da feature 001: 200 caracteres para nome, categoria e versão de negócio; 10000 caracteres para descrição.
- Produto arquivado continua consultável por quem tem acesso quando o filtro de arquivados está ativo ou quando o identificador é aberto diretamente dentro da empresa, para permitir conferência e desarquive.
- Administrador da organização equivale a ADMIN; DONO é o papel de dono da empresa já reconhecido na 001. Ambos operam só na empresa ativa.
- Desarquivar restaura a capacidade de edição sem apagar histórico e sem exigir recadastro.
- Arquivar de novo um já arquivado, ou desarquivar um já ativo, é operação inócua (sem perda de dados).
- Fora de escopo: exclusão definitiva, bloqueio de ingestão no Collector, gestão de membros, tokens de API, faturamento e mudança de papéis.
