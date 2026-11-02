<!--
Sync Impact Report
- Version change: 1.0.0 -> 2.0.0 (MAJOR: principio II removido e redefinido)
- Modified principles:
  - II. Regra 60/40 de codigo por IA -> II. IA pode implementar 100%, com revisao e dominio do desenvolvedor
- Added sections: nenhuma
- Removed sections: nenhuma (a regra 60/40 e as marcas de autoria saem do principio II e do fluxo de merge)
- Templates requiring updates: nenhum (templates padrao do Spec Kit, sem referencia a 60/40)
- Follow-up TODOs: nenhum (alinhado a constituicao v2.0.0 do lhamalog-collector)
-->
# LhamaLog Core Constitution

## Core Principles

### I. Spec Kit em toda feature (NON-NEGOTIABLE)

Toda feature MUST seguir o Spec Kit nesta ordem: specify, plan, tasks e implement.
Codigo de feature sem spec, plan e tasks correspondentes e invalido e MUST NOT
entrar na branch principal.

Rationale: o Spec Kit e o contrato de escopo. Implementar antes de fechar
specify, plan e tasks mistura entrega com requisito ainda aberto.

### II. IA pode implementar 100%, com revisao e dominio do desenvolvedor (NON-NEGOTIABLE)

A IA MAY implementar 100% do codigo de uma feature, inclusive a logica de
negocio. Toda implementacao MUST seguir o Spec Kit (principio I): nenhuma linha
de codigo entra sem spec, plan e tasks que a descrevam. O desenvolvedor MUST
revisar cada entrega e manter o dominio do que foi implementado; a IA MUST
explicar ao desenvolvedor o que implementou, por qual perspectiva e com quais
decisoes, e as decisoes MUST ficar registradas nos artefatos do Spec Kit
(research, plan, tasks). Tasks nao levam marca de autoria.

Rationale: a regra 60/40 foi derrubada no alinhamento de gestores de
2026-10-31. O controle passa a ser o Spec Kit como documentacao viva e a
revisao do desenvolvedor, que precisa entender e conseguir manter o Core.

### III. Testes antes do merge (NON-NEGOTIABLE)

Nenhuma alteracao MUST ser mergeada sem testes que cubram o comportamento novo
ou alterado. Build e testes MUST rodar na maquina do desenvolvedor com o Maven
Wrapper (`./mvnw` ou `.\mvnw.cmd`, `verify`) e MUST passar antes do merge. Os
testes de integracao usam Testcontainers com tags de imagem fixas (nunca `latest`).

Rationale: a suite local e o portao de qualidade; sem GitHub Actions, resultado
nao reproduzido localmente nao serve como evidencia.

### IV. Ingles no codigo, portugues na documentacao

Identificadores, nomes de pacotes, classes, metodos, campos, endpoints e
comentarios no codigo MUST estar em ingles. Documentacao para pessoas - README,
ADRs, specs, planos, tasks, esta constituicao e descricoes de commit - MUST
estar em portugues.

Rationale: o codigo segue o vocabulario da plataforma e das bibliotecas. Quem
le o repositorio trabalha em portugues.

### V. Segredos fora do codigo e do commit (NON-NEGOTIABLE)

Tokens, senhas, chaves de API (Clerk, Svix, tokens de API dos tenants) e
qualquer segredo MUST NOT aparecer em codigo, teste, documentacao versionada,
exemplo commitado ou historico do Git. Configuracao sensivel MUST vir de
variavel de ambiente ou de arquivo local ignorado pelo Git (`infra/local/.env`).
Se um segredo for commitado, o trabalho MUST parar ate a rotacao do segredo e a
remocao do valor do historico.

Rationale: o Core autentica usuarios e emite tokens de API; segredo no
repositorio vaza para todo clone e para o historico.

## Stack tecnologico

O LhamaLog Core MUST ser implementado em Java 25, Spring Boot 4.1.1 e Maven,
com o Maven Wrapper do repositorio. O pacote base MUST ser `br.com.lhamalog.core`
e o groupId MUST ser `br.com.lhamalog`. Banco: MongoDB (ADR_116); cache e
mensageria: Redis; log store: OpenObserve (ADR_168); perfis `local` e `prod`
(ADR_151); log de inicializacao padrao (ADR_149).

Mudanca de versao de Java, Spring Boot ou do sistema de build e emenda desta
constituicao e MUST ser registrada aqui antes de entrar no codigo.

## Fluxo de desenvolvimento

- A branch de feature MUST seguir `feature/<chave-jira>-<descricao>`.
  Exemplo: `feature/LLT-82-inicializacao-core`.
- A mensagem de commit MUST seguir `<CHAVE-JIRA>: <descricao>` (ADR_148).
  Exemplo: `LLT-82: inicializacao do modulo: Core`.
- O merge MUST confirmar, nesta ordem: Spec Kit completo, revisao do desenvolvedor sobre o que a IA implementou,
  build e testes locais verdes, branch e commit no padrao, e ausencia de segredos.

## Governance

Esta constituicao prevalece sobre pratica informal do repositorio. Emenda MUST
ser feita neste arquivo, com versao semantica e data `Last Amended`.

- MAJOR: remocao ou redefinicao incompativel de principio.
- MINOR: principio ou secao nova, ou expansao material de uma regra.
- PATCH: clarificacao, redacao ou correcao que nao muda a regra.

Revisao de conformidade: specify, plan, tasks e o review de merge MUST checar
os cinco principios, o stack e o fluxo. Violacao bloqueia o merge ate a
correcao ou ate uma emenda explicita desta constituicao.

**Version**: 2.0.0 | **Ratified**: 2026-11-02 | **Last Amended**: 2026-11-02