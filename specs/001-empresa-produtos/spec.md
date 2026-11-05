# Especificação Empresa e Produtos E04.F1

**Branch**: `dev/empresa-produtos`
**Data**: 04/11/2026
**Status**: pronta para implementação.

## Fontes

Inventário e divergências: [documentação](../../../lhamalog_docs/entrega-academica/empresa-produtos.md). USID_021–024, USID_029.1/030.1, modelos Empresa/Produto/contextos e ADR_022/029/122. A transcrição do backlog fornecida pelo usuário define E04.F1; arquivo original ausente.

## Esclarecimentos

### Sessão 04/11/2026

- Usuário autorizou cadastro por usuário/gestor, sem limite de empresas ou produtos. Cobrança por milhão de logs lidos, fora desta implementação. Substitui limite por plano de USID_024.
- Usuário confirmou campos de contexto opcionais do modelo, com aviso de campos vazios.
- Usuário manteve acesso por produto da ADR_122 e confirmou liberação automática para quem cadastra o produto. Administradores consultam todos os produtos; outros membros apenas produtos com vínculo.
- Empresa permanece vinculada à organização ativa. Membros autenticados dessa organização podem manter seus dados; não há acesso entre organizações.

## Cenários e testes

### US0 — Autenticação da integração (P1)

Como usuário autenticado, acesso a API usando a mesma sessão Clerk do frontend. Sessão inválida não permite ler nem gravar dados. Teste independente: JWT assinado de teste passa; assinatura incorreta, expiração, issuer/origem incorretos e sessão pending retornam 401. Saúde permanece pública. Token válido sem organização não dá acesso aos dados de empresa.

### US1 — Empresa e contexto geral (P1)

Consultar e alterar dados da empresa ativa e contexto geral, persistindo após recarregar. Empresa corresponde à organização Clerk. A criação reutiliza a tela Clerk existente e a seleção de organização permite trabalhar em empresas distintas. Teste: salvar em A, recarregar e verificar; selecionar B não mostra dados de A.

### US2 — Produtos da empresa (P2)

Cadastrar produtos sem cota de plano, listar, consultar e manter seu contexto conforme USID_023/024. Produto sempre pertence à empresa ativa. Teste: criar, recarregar e consultar; tentar o ID em outra empresa não expõe nem altera dados. Não há exclusão, ativação/suspensão, geração de tokens ou ingestão nesta feature.

## Requisitos funcionais

- FR-001: preservar telas, rotas e comportamento de login/logout Clerk.
- FR-002: API exige assinatura RS256 válida, issuer configurado, expiração e nbf válidos, azp permitido, sub preenchido e sts diferente de pending. Não aceitar autenticação Basic como alternativa.
- FR-003: derivar organização apenas do JWT validado (o.id no formato v2); IDs enviados pelo cliente não selecionam a empresa. Todas as operações de armazenamento incluem a empresa.
- FR-004: consultar e salvar dados cadastrais e contexto da empresa, com erros claros e persistência real.
- FR-005: cadastrar/listar/consultar produtos da empresa e salvar contexto do produto sem limite de quantidade.
- FR-006: controlar consulta/edição de Produto por vínculo de acesso; administradores da organização podem acessar todos, criador recebe vínculo automático.
- FR-007: formulários incluem carregamento, vazio, falha recuperável, envio bloqueado contra duplicação e confirmação apenas após sucesso do backend. Troca de empresa cancela/descarta respostas anteriores.
- FR-008: recusa de autorização de negócio não encerra sessão. Sessão inválida usa o fluxo de saída existente.
- FR-009: manter cadastro de organizações pela Clerk; documentar configuração externa para quantidade ilimitada. Não implementar faturamento.

## Entidades

Empresa/Tenant corresponde à Organization. Produto pertence a uma única empresa. Contextos de empresa e produto são distintos. Campos definidos em data-model.md. Os contextos são opcionais e sua ausência produz aviso.

## Casos de borda

API indisponível, sessão expirada ou pending, organização ausente, ID de outra empresa, troca de organização com requisição em andamento, duplo clique em salvar, campos vazios e gravações concorrentes.

## Critérios de sucesso

- SC-001: todos os testes de rejeição de JWT inválido e isolamento passam.
- SC-002: dados salvos permanecem após recarregar em Empresa e Produtos.
- SC-003: nenhum fluxo integrado apresenta dados demonstrativos como persistidos.
- SC-004: login/logout mantêm suas rotas e componentes.

## Limites

Não incluir exclusão, edição de cargos, alertas, faturamento, métricas, preferências ou integração de telemetria. A ausência de um produto no primeiro cadastro não pode exigir dados simulados para testar a API. Dados cadastrais incompletos são permitidos, conforme fluxo alternativo USID_021; mostrar aviso. Nome do produto identifica o cadastro e não pode ser vazio. Limites de tamanho técnicos são 200 caracteres para nomes/metadados e 10000 para textos de contexto; não são cotas comerciais.
