# Feature Specification: Login e organização de teste para desenvolvimento

**Feature Branch**: `LLT-163-validar-o-token-do-clerk-no-core`

**Created**: 2026-11-16

**Status**: Draft

**Input**: User description: "Criar o login e a organização de teste para os desenvolvedores usarem enquanto o fluxo de compra e a criação do Tenant não estão definidos, e o que mais faltar antes de mergear o PR #6 (LLT-163/LLT-164) na Development. Ponto de partida para o plano."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Entrar no sistema integrado com um login e uma organização de teste (Priority: P1)

Um desenvolvedor do time, com o Core e o Web na própria máquina, cria um login e uma organização na instância de desenvolvimento da Clerk, informa essa organização ao Core e entra no Web. Ele vê a tela da Empresa carregada, sem ser deslogado. Hoje, sem o fluxo de compra (LLT-157), nenhuma organização tem Tenant, e toda chamada termina em 403 e logout.

**Why this priority**: Sem isso nenhum desenvolvedor consegue testar Empresa, Produtos, Equipe ou Perfil com a segurança da LLT-163 ligada. Bloqueia o merge e o trabalho de todo o time depois dele.

**Independent Test**: Um desenvolvedor que nunca usou o ambiente segue só o guia do README, do cadastro até `/empresa`, e chega à tela da Empresa sem ajuda e sem ver "Você foi deslogado".

**Acceptance Scenarios**:

1. **Given** o Core e o Web rodando localmente e uma conta nova na instância de desenvolvimento, **When** o desenvolvedor cria uma organização e informa o identificador dela ao Core, **Then** depois de reiniciar o Core a organização tem um Tenant ativo e o Core registra isso no log.
2. **Given** a organização de teste já tem Tenant, **When** o desenvolvedor entra no Web com ela ativa e abre a Empresa, **Then** a tela carrega os dados da empresa.
3. **Given** o Core reiniciado de novo com a mesma configuração, **When** sobe, **Then** o Tenant existente é mantido como está e o log diz que ele já existia.
4. **Given** um e-mail de teste da instância de desenvolvimento (com `+clerk_test`), **When** o desenvolvedor se cadastra, **Then** consegue confirmar o e-mail com o código fixo de teste, sem depender de uma caixa de e-mail real.

---

### User Story 2 - Validar a integração com a Clerk real antes do merge (Priority: P1)

Antes de mergear o PR #6, o responsável pela LLT-163 percorre um roteiro único de validação com a instância de desenvolvimento da Clerk, o Core e o Web da `Development` com o PR #7. Ele registra o resultado de cada item. O roteiro cobre o que os testes automatizados não alcançam: login real com segundo fator, as telas integradas, o 401, o 403 com revogação de sessão e o webhook recebido pelo túnel.

**Why this priority**: A constituição exige testes antes do merge, e o comportamento com tokens e webhooks reais ainda não foi visto de ponta a ponta. É a última pendência do PR #6.

**Independent Test**: Seguir o roteiro do quickstart desta feature, item a item, e anexar o resultado de cada um (passou ou falhou, com observação) ao PR #6.

**Acceptance Scenarios**:

1. **Given** uma conta com segundo fator e uma organização com Tenant, **When** a pessoa entra pelo Web, **Then** depois do código do segundo fator ela chega ao aplicativo, sem ficar num cartão de login vazio.
2. **Given** a sessão ativa, **When** a pessoa abre Empresa, Produtos, Equipe e Perfil, **Then** todas carregam e nenhuma chamada é recusada.
3. **Given** uma organização sem Tenant e a chave secreta da Clerk configurada no Core, **When** a pessoa troca para essa organização e o Web chama o Core, **Then** o Core responde 403, revoga todas as sessões dela na Clerk e o Web volta ao login.
4. **Given** um membro que não administra a empresa, **When** tenta listar os usuários, alterar o cadastro da empresa ou arquivar um produto, **Then** recebe a recusa de negócio, com código e mensagem, e continua logado.
5. **Given** um endpoint de webhook apontado para o túnel e o segredo de assinatura no Core, **When** a pessoa altera o nome ou o papel de um membro na Clerk, **Then** a mudança chega à coleção de membros do Core.
6. **Given** uma chamada sem token ou com token adulterado, **When** chega ao Core, **Then** a resposta é 401, sempre com o mesmo corpo.

---

### User Story 3 - Testar com mais de uma organização (Priority: P2)

O desenvolvedor precisa de duas ou mais organizações de teste para conferir o isolamento entre empresas, os convites (LLT-167) e a troca de empresa. Ele informa todas ao Core de uma vez, sem inserir Tenants à mão no banco.

**Why this priority**: O isolamento entre empresas é regra de segurança, mas o caso principal (uma organização) já destrava o time. Hoje o Core aceita só uma organização.

**Independent Test**: Informar duas organizações ao Core, reiniciar, entrar com cada uma e conferir que os produtos de uma não aparecem na outra.

**Acceptance Scenarios**:

1. **Given** duas organizações informadas ao Core, **When** ele sobe, **Then** as duas têm Tenant ativo e o log mostra uma linha para cada.
2. **Given** um produto criado na organização A, **When** a pessoa troca para a organização B, **Then** o produto não aparece e abrir o endereço dele responde como inexistente.

---

### User Story 4 - O atalho nunca vale fora do desenvolvimento (Priority: P3)

O provisionamento de Tenant de teste existe só para o ambiente local. Em produção, mesmo com a configuração preenchida por engano, nenhum Tenant é criado.

**Why this priority**: Evita que alguém ganhe acesso sem passar pelo fluxo de compra. A proteção já existe pelo perfil local; esta história a mantém e a torna verificável.

**Independent Test**: Subir o Core com o perfil de produção e a configuração preenchida e conferir que nenhum Tenant foi criado.

**Acceptance Scenarios**:

1. **Given** o perfil de produção e a configuração de organização de teste preenchida, **When** o Core sobe, **Then** nenhum Tenant é criado.

---

### Edge Cases

- O Tenant da organização de teste já existe, mas não está ativo (por exemplo, suspenso à mão): o Core não o reativa. O acesso continua recusado com 403, e o guia explica como corrigir.
- O identificador informado tem espaços, vírgula sobrando ou não parece um identificador de organização da Clerk (não começa com `org_`): o Core ignora o valor inválido, avisa no log e provisiona os demais.
- A organização foi apagada na Clerk, mas o Tenant continua no banco local: nada quebra, porque nenhum token válido traz essa organização.
- O Web e o Core apontam para instâncias diferentes da Clerk: toda chamada responde 401. O guia mostra como conferir que as duas apontam para a mesma instância.
- O relógio da máquina está atrasado ou adiantado além da tolerância: as chamadas respondem 401. O guia cita esse sintoma.
- Sem a chave secreta da Clerk no Core: o 403 ainda leva o Web ao login, mas as sessões não são revogadas na Clerk. O roteiro de validação exige a chave.
- Um 403 de negócio sem código seria lido pelo Web como 403 de segurança e deslogaria a pessoa (spec 005 do web). Por isso a FR-010.
- A pessoa cria uma organização nova pelo Web durante o teste: ela não tem Tenant e recebe 403. É o esperado até a LLT-157.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O README do Core MUST trazer um guia, passo a passo, para criar o login, criar a organização, obter o identificador dela, informá-lo ao Core e conferir o acesso, na instância de desenvolvimento da Clerk.
- **FR-002**: No ambiente local, o Core MUST garantir, ao subir, um Tenant ativo para cada organização de teste configurada. Ele cria o Tenant que faltar e nunca altera um Tenant existente, inclusive o status.
- **FR-003**: A configuração de organizações de teste MUST aceitar uma ou mais organizações. A configuração atual, de uma organização só, continua valendo sem mudança.
- **FR-004**: Para cada organização configurada, o Core MUST registrar no log se criou o Tenant ou se ele já existia, com o status. Valores inválidos MUST ser ignorados com aviso, sem impedir a subida.
- **FR-005**: Fora do ambiente local, o Core MUST NOT criar Tenants a partir dessa configuração.
- **FR-006**: A feature MUST ter um roteiro de validação ponta a ponta (quickstart) com a Clerk real, cobrindo os itens da User Story 2. O resultado de cada item MUST ser registrado no PR #6 antes do merge.
- **FR-007**: O roteiro e o guia MUST deixar claro que logins, senhas, identificadores de organização e chaves de teste ficam só no ambiente de cada desenvolvedor, nunca no repositório, em issues ou em PRs.
- **FR-008**: O guia MUST orientar o uso dos e-mails de teste da instância de desenvolvimento (`+clerk_test` e o código fixo), para criar várias contas sem caixa de e-mail real.
- **FR-009**: O roteiro MUST incluir a validação do webhook real pelo túnel (quickstart da spec 003), com o segredo de assinatura configurado só localmente.
- **FR-010**: Toda recusa 403 de negócio do Core MUST trazer um código e uma mensagem, para o Web distingui-la do 403 de segurança, que encerra a sessão. Hoje alterar o cadastro da empresa e arquivar ou desarquivar produto como membro respondem 403 só com mensagem; esses dois passam a ter o código `not_admin`.

### Key Entities

- **Organização de teste**: organização da instância de desenvolvimento da Clerk, criada por um desenvolvedor e identificada pelo `org_id`. Pertence a um desenvolvedor e não representa cliente real.
- **Tenant de desenvolvimento**: Tenant ativo criado pelo Core local para uma organização de teste. Mesmo formato do Tenant real (ADR_121), com nome que indica a origem de desenvolvimento. É substituído pelo Tenant do fluxo de compra quando a LLT-157 existir.
- **Roteiro de validação**: lista de verificações manuais com a Clerk real, cada uma com o resultado e a data.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Um desenvolvedor sem o ambiente preparado chega do cadastro à tela da Empresa, seguindo só o guia, em até 15 minutos.
- **SC-002**: 100% dos itens do roteiro de validação têm resultado registrado no PR #6 antes do merge.
- **SC-003**: Com duas organizações de teste configuradas, 0 produtos de uma aparecem na outra.
- **SC-004**: 0 credenciais, chaves ou identificadores de organização de teste são encontrados no repositório ou nos PRs.
- **SC-005**: Com o perfil de produção, 0 Tenants são criados pela configuração de teste.

## Assumptions

- Cada desenvolvedor usa o próprio login e a própria organização. Não há conta de teste compartilhada, para não circular senha nem segundo fator entre pessoas.
- Todos usam a mesma instância de desenvolvimento da Clerk, a da chave publicável do Web. A instância de produção fica fora do escopo.
- A instância de desenvolvimento mantém os e-mails de teste (`+clerk_test`, código `424242`) habilitados, como é o padrão da Clerk.
- O Tenant de desenvolvimento é provisório: some do fluxo quando a LLT-157 (fluxo de compra e criação do Tenant) for implementada. Migrar esses Tenants não faz parte desta feature.
- O tratamento das respostas 401 e 403 no Web (sair na hora no 403 de segurança e manter a pessoa na tela no 403 de negócio) é a spec 005 do `lhamalog-web`. O roteiro desta feature confere o resultado com o Web disponível na `Development`.
- O guia de login e organização de teste já está no README do Core (commit `d7ef2f3`). Esta feature o completa com a configuração de várias organizações e com o roteiro de validação.
