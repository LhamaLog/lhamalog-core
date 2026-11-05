# Análise de consistência antes da implementação

04/11/2026. Spec, plano, tarefas, constituição e contrato confrontados. Nenhum conflito crítico de negócio pendente após as respostas do usuário.

| Requisitos | Cobertura |
| --- | --- |
| FR-001, FR-007, FR-008 | Web T003–T008; login/logout preservados, 403 local |
| FR-002 | Core T003–T005; decoder real e filtro HTTP |
| FR-003, FR-006 | Core T007–T010; tenant e vínculo por produto |
| FR-004 | Core T006–T008; Web T004/T005 |
| FR-005 | Core T009/T010; Web T006/T007 |
| FR-009 | Web T005 e documentação de configuração da Clerk |

As cotas antigas foram explicitamente substituídas pelo usuário. Os campos opcionais não serão tratados como obrigatórios. Acesso do criador foi confirmado, sem abrir demais produtos a membros comuns. Billing por milhão de logs é referência comercial e não adiciona implementação de cobrança.

Riscos operacionais: testes com containers dependem do Docker; teste de sessão real depende da configuração Clerk; limite externo de organizações precisa ser liberado na instância. Nenhum desses riscos autoriza simular integração concluída. Tarefas de verificação só serão concluídas mediante execução.
