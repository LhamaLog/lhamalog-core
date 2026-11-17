# LLT-173 — Configurar canais e filtros de notificação

**Branch**: `LLT-173-configurar-canais-e-filtros-de-notificacao`
**Data**: 2026-11-16
**Estado**: escopo de estrutura configurável aprovado; catálogo de negócio e entrega real ainda não definidos.
**Fonte única de requisitos**: https://logviewer2026.atlassian.net/jira/browse/LLT-173 e esclarecimentos de Vinicius neste chat.

## Cenários e testes
### US1 — Escolher canais (P1)
Como usuário autenticado, configurar minhas notificações na empresa ativa.
- Ao abrir pela primeira vez: web ligado, e-mail e push desligados.
- Ao ligar/desligar e-mail ou push e salvar: recarregar preserva a escolha sem alterar o outro canal.
- Web permanece ligado; tentar desligar pela API é recusado.
- Outra pessoa ou empresa não vê nem altera minhas preferências.

### US2 — Selecionar produtos (P1)
- Todos os produtos é uma opção explícita, inicialmente ativa, restrita aos produtos autorizados.
- Seleção específica exige ao menos um produto ativo e acessível.
- Salvar produto de outra empresa, arquivado ou sem acesso é recusado.
- Preferência não concede acesso. A avaliação revalida a permissão atual.

### US3 — Selecionar tipos e severidades (P2)
- Catálogo configurável no Core, sem tipos/severidades de negócio inventados.
- Catálogo vazio: todos os tipos/todas as severidades; explicar que opções específicas ainda serão definidas.
- Catálogo preenchido: seleção específica exige itens válidos e ao menos um item.
- Filtros de produto, tipo e severidade são combinados por E; escolhas dentro de cada filtro por OU.
- Canal obrigatório web não contorna os filtros. Não permitir seleção específica vazia como forma de desligá-lo.

## Critérios de aceite originais
1. Cada canal ligado/desligado individualmente, com exceção web confirmada abaixo.
2. Filtros por produto e por tipo/severidade respeitados.
3. Web não pode ser totalmente desligado se for canal mínimo.

## Clarificações — 2026-11-16
- Vinicius confirmou web sempre ligado; e-mail e push opcionais.
- Aprovou preferências por usuário e empresa, filtros compartilhados entre canais, Todos os produtos e os valores iniciais da US1.
- Informou que tipos e severidades ainda não foram decididos.
- Autorizou estrutura configurável, sem inventar catálogo, deixando filtros específicos de negócio e entrega completa aguardando definição da equipe.
- Decisão de implementação: Todos é explícito; modo específico vazio é inválido; início com todos os tipos/severidades.
- A autorização de implementação permite avançar em etapas reportadas; não abrir PR.

## Requisitos
- FR-001: armazenar canais e filtros por identidade e empresa derivadas da sessão.
- FR-002: ler e salvar somente as próprias preferências; não aceitar identidade do corpo como autoridade.
- FR-003: validar canal web no servidor.
- FR-004: validar seleções no servidor e limitar tamanho das listas.
- FR-005: impedir sobrescrita silenciosa por edições concorrentes.
- FR-006: retornar erro de persistência sem apresentar falso sucesso.
- FR-007: avaliação das preferências deve combinar canal, filtros e acesso atual; não substituir regras de notificações obrigatórias já existentes.
- FR-008: interface acessível por teclado, com carregamento, erro, salvar, cancelar e recarregar após conflito.
- FR-009: ignorar respostas de uma empresa anterior quando houver troca.
- FR-010: catálogo vazio não equivale a falha de carga; falha da API não equivale a lista vazia.

## Entidades
- Preferência individual: usuário, empresa, canais, filtros de produtos/tipos/severidades e versão.
- Seleção: modo todos ou lista explícita.
- Catálogo: identificadores e rótulos de tipos/severidades definidos por configuração.

## Casos limite
Sem produtos acessíveis; catálogo vazio ou alterado; perda de acesso/arquivamento de produto; primeira gravação concorrente; versão obsoleta; troca de empresa durante requisição; sessão ausente; dados malformados.

## Resultados verificáveis
- SC-001: canais opcionais persistem independentemente; web falso é recusado.
- SC-002: nenhum teste permite ler/gravar preferências de outra identidade ou empresa.
- SC-003: avaliação recusa produto não autorizado e qualquer filtro incompatível.
- SC-004: filtros configurados são testados com valores sintéticos exclusivamente de teste.
- SC-005: build e testes locais registrados; teste manual Clerk somente após execução por Vinicius.

## Limites da entrega autorizada
Não criar transporte de e-mail/push/WebSocket nem alterar Clerk, cargos, políticas de saldo baixo ou tarefas Jira.
O código atual publica eventos, mas não fornece entrega individual geral nesta área. Disponibilizar avaliação reutilizável para integração futura, documentando que testes dessa avaliação NÃO provam entrega real.
A LLT-173 não será marcada integralmente concluída enquanto catálogo e integração real não forem definidos e validados.
