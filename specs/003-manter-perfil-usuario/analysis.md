# Specification Analysis Report

Data: 2026-11-12. Artefatos lidos: `spec.md`, `plan.md`, `tasks.md`, `research.md`, `data-model.md`, `contracts/user-profile.md`, `quickstart.md` e `.specify/memory/constitution.md`.

A passagem inicial achou os itens abaixo. Os de severidade alta e média foram corrigidos nos artefatos antes deste registro. Nenhum código de aplicação foi escrito.

| ID | Category | Severity | Location(s) | Summary | Recommendation |
|----|----------|----------|-------------|---------|----------------|
| I1 | Inconsistency | HIGH | tasks.md T005; research.md validação | O teste citava o idioma `xx` como inválido. O formato `^[a-z]{2}(-[A-Z]{2})?$` aceita `xx`. | Corrigido: o exemplo inválido passou a ser `pt_BR`. |
| I2 | Inconsistency | MEDIUM | spec.md FR-005; contract; data-model; research | A spec rejeitava todo caractere de controle. A pesquisa, alinhada a `CompanyProfiles`, permite tabulação. | Corrigido: spec, contrato, modelo e tasks dizem "exceto tabulação". |
| U1 | Underspecification | MEDIUM | spec.md FR-015; contract; tasks T008 | O contrato e as tasks exigiam 401 sem sessão. A spec só dizia que a sessão ausente impede o acesso. | Corrigido: a spec passa a exigir 401, no mesmo espírito da LLT-175. |
| U2 | Underspecification | MEDIUM | spec.md edge cases; research versão | Duas primeiras gravações sem versão não estavam descritas. As duas podem passar da leitura e colidir na chave. | Corrigido: a spec, o modelo, o contrato, a pesquisa, o quickstart e T006 tratam essa colisão como 409 já existente. |
| T1 | Terminology | LOW | spec.md; contracts/user-profile.md | A spec diz nome, idioma e fuso. O JSON diz `displayName`, `language` e `timezone`. | Mantido de propósito. A pesquisa ganhou a seção Terminologia com o mapa. |
| D1 | Duplication | LOW | spec.md FR-010 e FR-011 | Os dois pedem que a consulta devolva o nome gravado no LhamaLog. | Mantido. FR-011 acrescenta o item em aberto da Clerk, que FR-010 não cobre. |
| C1 | Constitution | LOW | plan.md Complexity Tracking; constitution fluxo | A branch não é `feature/LLT-174-...` e o commit de spec não usa dois-pontos. | Exceção aceita nesta execução, registrada no plano. A constituição não foi emendada. A branch não foi renomeada. |

**Coverage Summary Table:**

| Requirement Key | Has Task? | Task IDs | Notes |
|-----------------|-----------|----------|-------|
| FR-001 | Sim | T005, T007 | Consulta do próprio perfil |
| FR-002 | Sim | T005, T006 | Padrão sem gravar na consulta |
| FR-003 | Sim | T005, T006, T007 | Gravação dos três campos |
| FR-004 | Sim | T005, T006 | Substituição, campo omitido e 409 |
| FR-005 | Sim | T004, T005 | Nome, 200 caracteres, tabulação |
| FR-006 | Sim | T004, T005 | Idioma e aliases |
| FR-007 | Sim | T004, T005 | Fuso IANA e aliases |
| FR-008 | Sim | T005, T006 | 400 não altera o perfil |
| FR-009 | Sim | T014 | Mensagens permanecem em português |
| FR-010 | Sim | T006, T010 | Nome devolvido é o gravado |
| FR-011 | Sim | T001, T002, T011 | Sem cliente, SDK ou segredo novo |
| FR-012 | Sim | T010, T011 | Segundo GET não relê a Clerk |
| FR-013 | Sim | T003, T008 | Um perfil por pessoa |
| FR-014 | Sim | T008, T009 | Identificadores do corpo ignorados |
| FR-015 | Sim | T008 | 401 e 403 de empresa |
| FR-016 | Sim | T008, T009 | Membro grava; administrador não é exigido |
| FR-017 | Sim | T012, T013 | Cadastro da empresa intacto |
| FR-018 | Sim | T013 | Sem painel, agenda ou e-mail |
| FR-019 | Sim | T013, T015 | Sem e-mail nem notificação no perfil |
| FR-020 | Sim | T005, T008, T010, T012, T016 | Testes HTTP e `verify` |
| SC-001 | Não | — | Tempo de uso, sem obra de teste de carga |
| SC-002 | Sim | T005 | Consulta seguinte e 409 |
| SC-003 | Sim | T008, T009 | Isolamento |
| SC-004 | Sim | T012, T013 | Empresa intacta |
| SC-005 | Sim | T010, T011 | Nome gravado e gap da Clerk |
| SC-006 | Sim | T013, T014 | Sem consumidores novos e sem tradução |

**Constitution Alignment Issues:**

O desvio de nome de branch e de mensagem de commit está justificado no plano por instrução explícita desta execução. Não há outro conflito com os princípios I a V nem com o stack. Testes HTTP com `mongo:8.0.34` estão nas tasks. Nenhum segredo é gravado. Documentação em português e identificadores novos em inglês.

**Unmapped Tasks:**

Nenhuma. T001 e T002 sustentam FR-011. T014 sustenta FR-009. T015 sustenta FR-019. T016 sustenta FR-020.

**Metrics:**

- Total Requirements: 20 funcionais (FR-001 a FR-020); 6 critérios de sucesso, dos quais 5 exigem obra nas tasks
- Total Tasks: 16 (T001 a T016)
- Coverage % (requirements with >=1 task): 100% dos FR; SC-001 fica de fora por ser tempo de uso, não obra
- Ambiguity Count: 0 após a correção (o fuso "reconhecido" da spec está definido no contrato como `ZoneId`)
- Duplication Count: 1 (D1, mantida)
- Critical Issues Count: 0

## Next Actions

Não há issue crítica. A implementação pode seguir `/speckit-implement` quando alguém for executar as tasks. Esta execução para aqui, sem T001 de código.

Itens só de leitura que permanecem de propósito:

- C1: branch `LLT-174-manter-perfil-do-usuario` e commit de spec sem dois-pontos.
- D1: FR-010 e FR-011 compartilham a frase do nome gravado.
- T1: vocabulário de negócio na spec e campos em inglês no JSON.
- O write-back do nome na Clerk continua item em aberto. Não é defeito de cobertura: FR-011 manda não criar a integração que o repositório ainda não tem.
