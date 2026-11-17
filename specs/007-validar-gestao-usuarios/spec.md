# Feature Specification: Validar e fechar a Gestão de usuários

**Feature Branch**: `LLT-119-validar-gestao-usuarios`

**Created**: 2026-11-17

**Status**: Draft

**Input**: User description: "LLT-119 Gestão de usuários: validar e fechar a Feature. Revisar as entregas LLT-167 e LLT-168 (Core e Web, do Vinicius), provar os critérios AC-01 a AC-20 da seção 3 do documento-base, cobrir as lacunas com testes de aceite só de teste (AT-01 a AT-04), executar o roteiro E2E da seção 6 e registrar o resultado em relatorio-aceite.md. Sem alterar código de produção nem branches de outras pessoas."

## Contexto

A Feature "Gestão de usuários" (E03.F1, LLT-119, épico "Usuários, cargos e configurações do usuário") tem dois itens, os dois implementados pelo Vinicius:

- **LLT-167 Convidar usuário para a empresa.** Core: `specs/004-convidar-usuario-empresa`, mergeada na `Development`. Web: `specs/003-convidar-usuario-empresa`, ainda não mergeada.
- **LLT-168 Listar, editar e desativar usuários.** Core: `specs/005-listar-editar-usuarios`, mergeada na `Development` pelo PR #10 (commit `6ac2f01`). Web: `specs/004-listar-editar-desativar-usuarios`, ainda não mergeada.

Esta feature não entrega produto. Ela prova o aceite das duas entregas de ponta a ponta (Core, Web e Clerk), cobre com testes de aceite só de teste o que falta e entrega a lista do que impede fechar a LLT-119. O documento-base é `C:\Users\Pedro\Desktop\LhamaLog\LLT-119-validar-gestao-usuarios.md`, de 16/11/2026, guardado fora do repositório.

### Mudanças desde o documento-base (17/11/2026)

- A LLT-168 do Core **já está na `Development`** (PR #10). O estado validado é a própria `Development` (`a64559d`), e não um merge local.
- A pasta `specs/006` foi ocupada por `006-nome-unico-produto` (LLT-177). Pela numeração sequencial do Spec Kit, esta spec é a `007`.
- A branch LLT-163 do Felipe foi atualizada em 16/11, às 23:44. Ela já trouxe a `Development` com a 168 para dentro dela e mudou o comportamento da 168. Detalhes no achado R-12.
- O `lhamalog-web` local está na `main`, que não tem a tela `/equipe` nem testes.

## Clarifications

### Session 2026-11-17

- Q: D1. Qual modelo de membro o time escolheu, o da LLT-163 (webhook, chave secreta, DONO/ADMIN/MEMBRO) ou o da 167/168 (o Core não chama a Clerk, só `admin`/`member`)? → A: **Vale o modelo da LLT-163** (resposta do Pedro em 17/11/2026, como decisão do time). Ainda falta registrar em ADR.
- Q: D2, D4, D5 e D6 já têm resposta do time? → A: Todas pendentes com o time.
- Q: O `lhamalog-web` pode ser trocado agora para a branch da LLT-168? → A: Agora não. A T010 (`npm ci`, `npm test`, `npm run build`) e a parte Web do roteiro ficam Bloqueadas.
- Q: D3. A instância Clerk de desenvolvimento, as contas e os 7 dias estão prontos para o E2E? → A: Ainda não. D3 continua aberta e os E2E ficam Bloqueados. O roteiro fica pronto no `quickstart.md`.

### Consequências da D1 para esta validação

- As premissas "o Core não chama a Clerk" e "sem `CLERK_SECRET_KEY`" da 167 (FR-004, FR-014) e da 168 (FR-016 e a seção "Fronteira" do contrato) deixam de valer quando a LLT-163 entrar. O AC-19 continua valendo como "nenhum **valor** secreto versionado": a LLT-163 lê a chave de variável de ambiente.
- Os testes desta spec seguem o contrato que está na `Development` hoje. Na branch da LLT-163, a sessão sem empresa responde 403 `{"error":"forbidden"}` e revoga as sessões, no lugar de "Selecione uma empresa para continuar.". Também o `tenantId` passa a ser o id interno do tenant, e os testes usam tokens reais (`TestTenants`). Quando a 163 for mergeada, a parte "sem empresa" do AC-08 muda, e a classe de aceite precisa ser migrada como o Felipe migrou a `CompanyMemberHttpTests`.
- O modelo `CompanyMember`/`CompanyAdminCount` da 168 convive com a coleção `member` da 163 no mesmo pacote. Unificar os dois fica com o time (bloqueio da LLT-119), não com esta validação.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Prova automatizada do aceite no Core (Priority: P1)

Como desenvolvedor responsável por fechar a LLT-119, quero que cada critério de aceite que depende do Core tenha um teste automatizado que passa no estado da `Development`, para que o fechamento não dependa de leitura nem de confiança.

**Why this priority**: A constituição (princípio III) proíbe merge sem testes verdes. Sem essa prova, nenhum outro passo fecha a Feature.

**Independent Test**: Rodar a suíte do Core no estado da `Development` mais os testes de aceite novos e conferir, critério por critério, qual teste demonstra AC-07 a AC-18 e AC-20.

**Acceptance Scenarios**:

1. **Given** a `Development` com a 168, **When** a suíte inteira roda, **Then** o relatório registra quantos testes rodaram, passaram, falharam e foram ignorados, antes e depois dos testes de aceite.
2. **Given** um usuário desativado com um token que diz administrador, **When** ele faz uma requisição, **Then** a requisição é recusada e nem o último acesso nem o cargo observado mudam (AC-10).
3. **Given** um administrador desativado, **When** ele tenta listar ou reativar, **Then** é recusado como desativado (AC-18).
4. **Given** uma requisição sem sessão ou sem empresa, **When** ela tenta desativar ou reativar, **Then** recebe 401 ou "Selecione uma empresa para continuar." (AC-08).
5. **Given** dois administradores no primeiro acesso ao mesmo tempo, **When** tentam desativar os dois em sequência, **Then** pelo menos um continua ativo (investigação do R-01).

---

### User Story 2 - Roteiro de ponta a ponta com o Web e a Clerk (Priority: P1)

Como desenvolvedor, quero executar no navegador o roteiro E2E-01 a E2E-18 com a instância de desenvolvimento da Clerk, o Web da 168 e o Core da `Development`, para provar o que só existe no Web e na Clerk: convite, validade, revogação, reenvio, troca de cargo e as travas do Web.

**Why this priority**: AC-01 a AC-06, AC-15 e a parte de tela de AC-07, AC-11 a AC-14 e AC-17 só podem ser provados com o Web e a Clerk de verdade.

**Independent Test**: Seguir o `quickstart.md`. Cada passo do roteiro termina como OK, Falhou (com evidência) ou Bloqueado (com o motivo).

**Acceptance Scenarios**:

1. **Given** a instância Clerk de desenvolvimento, as contas Ana, Bruno e Carla e as empresas A e B, **When** o roteiro é executado, **Then** cada E2E-xx tem resultado registrado no relatório.
2. **Given** um passo que depende de algo indisponível (por exemplo, o Web fora da branch 168 ou a instância Clerk sem os 7 dias), **When** o passo é tentado, **Then** ele fica Bloqueado com o motivo e o critério correspondente fica aberto.
3. **Given** o roteiro concluído, **When** o JWT for usado em chamadas diretas, **Then** ele não aparece em nenhum arquivo, log ou relatório.

---

### User Story 3 - Achados de revisão com destino (Priority: P2)

Como desenvolvedor, quero confirmar ou descartar cada achado preliminar (R-01 a R-13, mais o R-14 encontrado na linha de base) com evidência de arquivo e linha, e deixar rascunhos de comentários de revisão para os PRs do Vinicius, para que cada problema tenha dono e destino sem que eu corrija o código de outra pessoa.

**Why this priority**: Os achados não bloqueiam a prova do aceite, mas um achado confirmado sem destino deixa a Feature aberta.

**Independent Test**: Cada R-xx no `relatorio-aceite.md` tem status (Confirmado, Descartado ou Risco aceito), evidência e destino sugerido. O `revisao-prs.md` tem os comentários por arquivo e linha.

**Acceptance Scenarios**:

1. **Given** um achado, **When** a evidência é coletada, **Then** o relatório registra o status e o arquivo:linha que o sustenta.
2. **Given** um bug confirmado em código de produção, **When** ele é registrado, **Then** vira achado e comentário de revisão, nunca correção nesta branch.

---

### User Story 4 - Lista do que impede fechar a LLT-119 (Priority: P1)

Como desenvolvedor, quero uma lista objetiva dos bloqueios para fechar a LLT-119, separando o que é decisão do time (D1 a D6), o que é achado confirmado e o que é passo pendente, para levar ao time e ao card.

**Why this priority**: É o resultado que o card pede: saber se a Feature fecha e, se não fecha, o que falta.

**Independent Test**: A seção "Bloqueios" do relatório lista cada item com o responsável sugerido e o checklist de fechamento (seção 10 do documento-base) marcado.

**Acceptance Scenarios**:

1. **Given** a validação concluída, **When** o relatório é lido, **Then** ele diz se a LLT-119 pode fechar e lista cada bloqueio.
2. **Given** uma decisão do time ainda aberta, **When** ela aparece no relatório, **Then** fica como "pendente com o time", sem que o desenvolvedor decida sozinho.

### Edge Cases

- A suíte do Core já falha na `Development` antes de qualquer teste novo. A falha entra no relatório como linha de base e é investigada até saber se tem relação com a 168, sem que o código de produção seja corrigido.
- Um teste de aceite falha por comportamento do código de produção. O teste não é alterado para passar: o comportamento vira achado.
- O POST sem JWT responde 403 (CSRF) em vez de 401. Isso é achado do contrato, e o teste continua esperando 401.
- O teste exploratório (AT-04) é instável ou confirma o R-01. Ele fica marcado como investigação e não bloqueia o merge, e o resultado das repetições entra no relatório.
- O `lhamalog-web` local não está na branch da 168. A parte Web fica Bloqueada até a troca de branch pelo GitHub Desktop, que é feita pelo desenvolvedor, nunca pelo agente.
- A LLT-163 muda o contrato da 168 (sessão sem empresa, `tenantId`, chave secreta). Os testes desta spec seguem o contrato que está na `Development` hoje, e a diferença entra no R-12/D1.
- A instância Clerk, as contas ou a configuração de 7 dias não estão disponíveis (D3). Os E2E que dependem delas ficam Bloqueados.

## Requirements *(mandatory)*

### Critérios de aceite da Feature (a provar)

Cada critério vem da LLT-167 ou da LLT-168. A coluna "Prova" do documento-base vira a evidência exigida.

- **AC-01**: O administrador MUST poder convidar por e-mail com o cargo `admin` ou `member` (167 FR-001/008). Prova: E2E-02.
- **AC-02**: E-mail vazio ou inválido MUST ser recusado no Web, sem chamar a Clerk (167 FR-012). Prova: E2E-03 e `tests/team-invitations.test.mjs`.
- **AC-03**: O convite MUST valer 7 dias; o expirado ou revogado MUST NOT dar acesso (167 FR-002/003). Prova: E2E-05 e E2E-06.
- **AC-04**: O administrador MUST ver os pendentes, revogar e reenviar; reenviar MUST renovar os 7 dias (167 FR-016). Prova: E2E-04, E2E-05 e E2E-07.
- **AC-05**: Quem aceita MUST ficar só na empresa do convite, com o cargo escolhido (167 FR-005). Prova: E2E-08.
- **AC-06**: O membro MUST NOT convidar, listar pendentes, revogar nem reenviar (167 FR-009). Prova: E2E-16.
- **AC-07**: A lista MUST mostrar nome, e-mail, cargo, status e último acesso, só da própria empresa (168 FR-001/002). Prova: `CompanyMemberHttpTests` e E2E-09.
- **AC-08**: O membro MUST receber 403 `not_admin`; sem sessão, 401; sem empresa, "Selecione uma empresa para continuar." (168 FR-012/013). Prova: testes existentes e AT-03.
- **AC-09**: O último acesso MUST ser regravado só depois de 5 minutos ou quando o cargo muda (168 FR-004). Prova: `keepsLastAccessUntilFiveMinutesPass`.
- **AC-10**: A requisição recusada por desativação MUST NOT atualizar o último acesso nem o cargo (168 FR-004). Prova: AT-01.
- **AC-11**: Quem foi desativado MUST perder o acesso na próxima requisição, com o token ainda válido; os outros MUST seguir normais (168 FR-005). Prova: teste existente e E2E-11.
- **AC-12**: Desativar na empresa A MUST NOT afetar a empresa B (168 FR-005). Prova: teste existente e E2E-12.
- **AC-13**: Repetir desativar ou reativar MUST NOT inverter o estado; reativar MUST devolver o acesso (168 FR-006/007). Prova: teste existente e E2E-13.
- **AC-14**: MUST ser impossível desativar o último administrador ativo, no Core e no Web (168 FR-008). Prova: testes existentes e E2E-14.
- **AC-15**: MUST ser impossível rebaixar o último administrador ativo, no Web (168 FR-009). Prova: `tests/team-members.test.mjs` e E2E-15.
- **AC-16**: Duas desativações simultâneas MUST manter pelo menos um administrador (168 FR-010). Prova: `simultaneousDeactivationKeepsOneActiveAdministrator`.
- **AC-17**: A troca de cargo MUST ser feita só pelo Web; o Core MUST NOT ter rota de cargo (404) (168 FR-011). Prova: teste existente e E2E-10.
- **AC-18**: O administrador desativado MUST perder também a gestão: não lista e não reativa (contrato da 168). Prova: AT-02.
- **AC-19**: Nenhum segredo MUST estar versionado no Core nem no Web (167 FR-014 e 168 FR-016). Prova: E2E-18.
- **AC-20**: A suíte inteira do Core MUST ficar verde no estado da `Development` com a 168, inclusive Produto, porque o interceptor é global (constituição III). Prova: T-SUITE.

### Requisitos da validação

- **FR-001**: A validação MUST registrar, no `relatorio-aceite.md`, o status de cada AC-01 a AC-20 (OK, Falhou, Bloqueado ou Risco aceito), com a prova usada.
- **FR-002**: Os testes de aceite novos MUST ficar só em código de teste, numa classe própria no pacote `member` do Core, sem alterar código de produção.
- **FR-003**: Os testes de aceite MUST usar o próprio container de teste. Eles MUST NOT usar os bancos `lhamalog_core` nem `lhamalog_teste`.
- **FR-004**: Um teste que falha por comportamento do código de produção MUST continuar como está e MUST virar achado no relatório.
- **FR-005**: Cada achado R-01 a R-14 MUST terminar como Confirmado, Descartado ou Risco aceito, com evidência de arquivo e linha ou de execução.
- **FR-006**: A validação MUST NOT alterar, commitar nem fazer push nas branches `LLT-167-*`, `LLT-168-*` e `LLT-163-*`, em nenhum dos repositórios. No `lhamalog-web`, o agente MUST NOT rodar Git.
- **FR-007**: Nenhum segredo MUST entrar em arquivo novo: `CLERK_SECRET_KEY`, valores que começam com `sk_` ou o JWT copiado do navegador.
- **FR-008**: Os comentários de revisão MUST ficar só como rascunho em `revisao-prs.md`. Publicar é decisão do desenvolvedor.
- **FR-009**: O relatório MUST ter uma seção de bloqueios para fechar a LLT-119, com as decisões D1 a D6, os achados confirmados e os passos pendentes.
- **FR-010**: Nenhum push, PR ou commit MUST acontecer sem a confirmação explícita do desenvolvedor.

### Itens a confirmar (achados preliminares do documento-base)

| ID | Sev. | Achado a confirmar |
|---|---|---|
| R-01 | Média | O contador de administradores ativos pode inflar no primeiro acesso simultâneo de dois administradores |
| R-02 | Média | O Core só conhece o cargo pelo último token aceito; rebaixar no Web sem nova requisição deixa o Core aceitar desativar o administrador real |
| R-03 | Baixa | Desativar um usuário que nunca entrou grava o registro como não administrador |
| R-04 | Baixa | O interceptor roda em todo `/api/**` (uma leitura por requisição); a listagem por empresa não tem índice |
| R-05 | Baixa | O pacote `member` é plano, o relógio tem setters de teste em produção, e desativar e reativar não geram log de negócio |
| R-06 | Processo | Os commits e as branches da 167 e da 168 fogem do padrão `LLT-xx: descricao` e `feature/` |
| R-07 | Processo | A numeração das specs colide entre branches e entre Core e Web |
| R-08 | Processo | A 168 foi escrita antes da LLT-183/185 e da constituição 2.1.0; a suíte precisa rodar no estado mesclado |
| R-09 | Processo | A 168 do Web carrega a 122, a 176, a 174 e a 167 junto |
| R-10 | Baixa | O e-mail exibido na `/equipe` pode ser telefone ou username |
| R-11 | Info | "Reenviar" no Web é revogar e criar outro convite |
| R-12 | Bloqueio | A LLT-163 cria outro modelo de membro no mesmo pacote, com premissas opostas às da 167/168 |
| R-13 | Média | A 168 do Web não tem a correção `f9a1a5f` da 167 (carregamento infinito dos convites) |
| R-14 | Bloqueio do AC-20 | (novo, 17/11) A suíte da `Development` já falha antes da 168: `CompanyRegistrationHttpTests.adminDeletesRegistrationAndMemberCannot:163` espera 409 e recebe 200. Fora da Feature (LLT-175), mas impede a suíte verde |

### Decisões em aberto (do time)

| ID | Decisão | Estado em 17/11/2026 |
|---|---|---|
| **D1** | Qual modelo de membro vale: LLT-163 (webhook, chave secreta, DONO/ADMIN/MEMBRO) ou 167/168 (o Core não chama a Clerk; só `admin`/`member`) | **Decidido: vale o modelo da LLT-163** (resposta do Pedro). Falta registrar em ADR e unificar o pacote `member` |
| D2 | Quem corrige os achados confirmados (o sugerido é o Vinicius; o Pedro só reporta) | Pendente com o time |
| D3 | Quem fornece a instância Clerk de dev, as três contas de teste e a configuração dos 7 dias | Pendente com o time. Os E2E ficam Bloqueados |
| D4 | Padrão de branch e commit (`feature/` e `LLT-xx: ...`) e renumeração das specs duplicadas | Pendente com o time |
| D5 | Desativar e reativar devem gerar log de negócio (princípio VI) ou auditoria (LLT-170) | Pendente com o time |
| D6 | O roteiro E2E substitui a T011 pendente do Web? Qual a ordem de merge no Web (122→176→174→167→168) para não perder a correção `f9a1a5f` | Pendente com o time |

### Key Entities *(include if feature involves data)*

- **Critério de aceite (AC-xx)**: comportamento que a Feature precisa demonstrar, com origem (167 ou 168), prova exigida e status.
- **Teste de aceite (AT-xx)**: teste automatizado novo, só de teste, que cobre uma lacuna da matriz.
- **Passo E2E (E2E-xx)**: ação manual no navegador com resultado esperado e resultado observado.
- **Achado (R-xx)**: possível problema encontrado na revisão, com severidade, evidência, status e destino.
- **Decisão (Dx)**: questão do time que bloqueia o fechamento, com o estado "pendente com o time" ou a resposta registrada.
- **Bloqueio**: item que impede fechar a LLT-119, com o responsável sugerido.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 20 de 20 critérios de aceite têm status e prova registrados no relatório.
- **SC-002**: 14 de 14 achados (R-01 a R-14) têm status final e evidência.
- **SC-003**: 3 lacunas da matriz (AC-08 nas rotas de escrita, AC-10 e AC-18) passam a ter teste automatizado.
- **SC-004**: O teste exploratório do R-01 roda pelo menos 20 vezes, e o número de execuções que violam a regra (0 ou mais) fica registrado.
- **SC-005**: 0 arquivos novos com segredo, chave secreta ou token.
- **SC-006**: 0 alterações em código de produção e em branches de outras pessoas.
- **SC-007**: O desenvolvedor consegue explicar cada teste de aceite escrito, porque cada um tem explicação em português no relatório.

## Assumptions

- O estado validado do Core é a `Development` atual (`a64559d`), que já contém a LLT-167 e a LLT-168. Não há merge local.
- O estado validado do Web é a branch `LLT-168-listar-editar-e-desativar-usuarios`, trocada pelo desenvolvedor no GitHub Desktop.
- O contrato vigente da 168 é `specs/005-listar-editar-usuarios/contracts/company-members.md`. Nenhum contrato novo é criado.
- A ADR mais recente é a fonte de verdade. As ADRs locais encontradas (ADR_022 de 02/10 e ADR_122 de 11/10) entram como evidência da D1. A ADR_123 está só no Drive e não foi lida.
- O roteiro E2E é executado pelo desenvolvedor no navegador, que dita os resultados. O agente não guarda token.
- Jira e Drive são só leitura.
