# Feature Specification: Validação do session token da Clerk no Core

**Feature Branch**: `LLT-163-validar-o-token-do-clerk-no-core` (padrão do Jira; diretório de spec `specs/002-validacao-token-clerk`)

**Created**: 2026-11-06

**Status**: Draft

**Jira**: LLT-163

**Input**: "Validar no Core o session token da Clerk enviado pelo front (Authorization: Bearer), responder 401/403 conforme a ADR_032, resolver o Tenant pela Organization ativa e expor o contexto da requisição ao restante do Core."

**Fontes**: ADR_022, ADR_032, ADR_121, ADR_122, ADR_123, ADR_158, ADR_159 (as mais novas prevalecem), constituição v2.0.0.

## Clarifications

### Session 2026-11-06

- Q: Como validar o token: Spring resource server com JWKS ou SDK `com.clerk:backend-api`? → A: Spring resource server (já no pom) com o JWKS da instância da Clerk e validadores próprios de `azp` e `sts`. Nenhuma dependência nova.
- Q: A feature gera o JWT interno da ADR_028? → A: Não. Fica para feature própria.
- Q: Como obter Tenants para teste, já que o fluxo de compra não existe? → A: Só fixtures nos testes. Nenhum seed no código de produção. **Atualizado em 2026-11-09 (research R16):** o perfil `local` ganhou um Tenant de desenvolvimento opcional (`DEV_TENANT_CLERK_ORG_ID`), criado na subida e nunca alterado depois. Ele nunca fica ativo em prod; sem ele, o ambiente integrado deslogaria todo usuário enquanto o fluxo de compra não existe.
- Q: Origens permitidas (`azp`) em desenvolvimento? → A: `http://localhost:5173` (lhamalog-web e projeto de teste usam essa porta com `strictPort`). Em prod, só por variável de ambiente.
- Q: Formato de `o.rol`? → A: Pela documentação da Clerk (token v2), `rol` vem sem o prefixo `org:` (ex.: `admin`). O Core aceita as duas formas (`admin` e `org:admin`). Confirmação com token real da instância de dev: [PENDENTE], feita no teste manual (quickstart).
- Q: Tenant vs. Empresa (branch `Development` tem `Company` com `clerkOrgId`)? → A: Pela ADR_158, Tenant (identidade e chave de isolamento) e Empresa (cadastro) são entidades distintas, 1:1. Esta feature cria só a coleção `tenant`.
- Q: Git? → A: O desenvolvedor cria as branches e faz os commits. A IA não cria branch, não faz commit nem push.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Requisição autenticada com sessão e organização válidas (Priority: P1)

Um usuário logado no lhamalog-web, com uma Organization ativa que corresponde a um Tenant ativo, chama uma rota protegida do Core enviando o session token no header `Authorization: Bearer <token>`. O Core aceita a requisição e o código de negócio recebe o contexto (usuário, sessão, organização, papel e tenantId) sem conhecer Spring nem Clerk.

**Why this priority**: sem isso nenhuma rota de negócio do Core pode ser usada pelo front.

**Independent Test**: token assinado por chave de teste, com claims válidas e `o.id` de um Tenant cadastrado como fixture, chamando uma rota protegida de teste → 200 e o contexto exposto contém os valores esperados.

**Acceptance Scenarios**:

1. **Given** um Tenant ativo com `clerkOrgId = org_A`, **When** chega um token válido (assinatura, `exp`, `nbf`, `iss`, `azp` permitido, `sts = active`, `o.id = org_A`), **Then** a resposta é 200 e o contexto tem `clerkUserId = sub`, `sessionId = sid`, `clerkOrgId = org_A`, `role` normalizado e `tenantId` do Tenant.
2. **Given** qualquer estado, **When** chega uma requisição para `/actuator/health` ou `/actuator/info` sem token, **Then** a resposta não é 401/403 (rotas públicas).

---

### User Story 2 - Token inválido ou sessão não ativa responde 401 (Priority: P1)

Qualquer requisição a rota protegida sem token válido recebe 401 com corpo básico, sem detalhar o motivo, e o front trata como "Você foi deslogado" (ADR_032).

**Why this priority**: é a barreira de autenticação; sem ela o Core fica aberto.

**Independent Test**: cada caso de token inválido abaixo retorna 401 com o mesmo corpo e sem `error_description`.

**Acceptance Scenarios**:

1. **When** não há header `Authorization`, **Then** 401.
2. **When** a assinatura não confere com o JWKS (chave diferente ou token adulterado), **Then** 401.
3. **When** `exp` já passou, **Then** 401.
4. **When** `nbf` está no futuro, **Then** 401.
5. **When** `iss` difere do issuer configurado, **Then** 401.
6. **When** `azp` está ausente ou fora da lista de origens permitidas, **Then** 401.
7. **When** `sts = pending` (ou qualquer valor diferente de `active`, ou ausente), **Then** 401.
8. **Then** em todos os casos o corpo é o mesmo erro básico e nenhum log contém o token.

---

### User Story 3 - Token válido sem acesso responde 403 e revoga as sessões (Priority: P2)

Quando o token é válido mas o acesso é negado, o Core responde 403 e revoga todas as sessões ativas do usuário na Clerk, para ele recomeçar o login (ADR_032).

**Why this priority**: contenção de acesso indevido; depende da US1/US2.

**Independent Test**: com a porta de revogação substituída por um fake, cada caso abaixo retorna 403 e o fake registra a revogação para o `sub` do token. Um fake que falha não altera o 403.

**Acceptance Scenarios**:

1. **When** o token não tem a claim `o` (sem organização ativa), **Then** 403 e revogação das sessões do usuário.
2. **When** `o.id` não corresponde a nenhum Tenant, **Then** 403 e revogação.
3. **When** o Tenant existe mas não está ativo, **Then** 403 e revogação.
4. **When** `o.rol` está ausente ou não é um papel conhecido (`admin`/`member`), **Then** 403 e revogação.
5. **When** a revogação na Clerk falha (erro HTTP, timeout, chave não configurada), **Then** a resposta continua 403 e o erro é registrado em log sem segredo nem token.
6. **Then** o corpo do 403 é um erro básico, sem detalhar o motivo.

### Edge Cases

- Header `Authorization` com esquema diferente de `Bearer` ou token malformado → 401.
- Token com algoritmo diferente de RS256 → 401.
- JWKS indisponível na Clerk → a requisição não é aceita (401), sem derrubar o serviço; chaves já obtidas ficam em cache.
- Margem de relógio: tolerância pequena e configurável (padrão 5 s), para não estender o token de 60 s.
- Usuário com mais de uma sessão ativa (vários dispositivos) → todas são revogadas no 403.
- Requisição de preflight CORS (`OPTIONS`) da origem do front → respondida sem exigir token.
- Configuração obrigatória ausente (`CLERK_ISSUER`) → a aplicação não sobe (falha rápida), em vez de rodar aberta ou recusando tudo em silêncio.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O Core MUST ler o session token só do header `Authorization: Bearer` (o front está em outra origem).
- **FR-002**: O Core MUST validar a assinatura RS256 pelo JWKS da instância da Clerk (`<Frontend API URL>/.well-known/jwks.json`, sobrescrevível por configuração), além de `exp`, `nbf` e `iss`.
- **FR-003**: O Core MUST validar `azp` contra uma lista configurável de origens permitidas e rejeitar `azp` ausente.
- **FR-004**: O Core MUST aceitar só `sts = active`; qualquer outro valor ou ausência é 401.
- **FR-005**: Falhas de autenticação MUST resultar em 401 com corpo básico idêntico para todos os motivos, sem o header `WWW-Authenticate` detalhado (sem `error_description`).
- **FR-006**: Com token válido, o Core MUST resolver o Tenant pela claim `o.id` (`clerkOrgId`) na coleção `tenant` e exigir Tenant com status ativo.
- **FR-007**: O Core MUST responder 403, com corpo básico, quando falta a claim `o`, não há Tenant para `o.id`, o Tenant não está ativo ou o papel `o.rol` é desconhecido. O mesmo tratamento vale para futuras negações de permissão (qualquer 403 da cadeia de segurança).
- **FR-008**: Em todo 403 o Core MUST revogar todas as sessões ativas do usuário (`sub`) na Clerk (Backend API: listar sessões `user_id` + `status=active`, revogar cada uma), autenticando com `CLERK_SECRET_KEY`. Falha na revogação MUST NOT alterar a resposta 403 e MUST ser registrada em log sem segredo.
- **FR-009**: O Core MUST expor ao restante do código um contexto da requisição (clerkUserId, sessionId, clerkOrgId, role, tenantId) por uma porta da camada de aplicação, sem tipos do Spring nem da Clerk.
- **FR-010**: O papel exposto no contexto MUST ser o papel da Organization na Clerk normalizado (`ADMIN` para `admin`/`org:admin`, `MEMBER` para `member`/`org:member`). O papel de negócio DONO/ADMIN/MEMBRO (coleção Membro, ADR_122/123) fica para a LLT-164 e features seguintes.
- **FR-011**: Rotas públicas: `/actuator/health`, `/actuator/health/**`, `/actuator/info` e `POST /webhooks/clerk` (autenticação própria por assinatura, LLT-164). Todo o resto MUST exigir token válido.
- **FR-012**: O Core MUST permitir CORS só para as mesmas origens da lista de `azp`, com o header `Authorization`, sem cookies (decisão proposta: sem isso o front em outra origem não consegue chamar o Core).
- **FR-013**: Logs MUST NOT conter token, segredo nem dados pessoais desnecessários (no máximo ids da Clerk).
- **FR-014**: Toda configuração nova MUST vir de `application*.properties` via `${VARIAVEL}`, sem valor secreto; `CLERK_ISSUER` é obrigatória em todos os perfis, `CLERK_SECRET_KEY` e `CLERK_AUTHORIZED_PARTIES` são obrigatórias em prod.
- **FR-015**: Autenticação HTTP Basic e sessão de servidor MUST ficar desligadas (stateless).

### Fora do escopo

- JWT interno da ADR_028 e sua invalidação.
- Camada centralizada que filtra todas as consultas por `tenantId` (ADR_121): proposta no plan, implementada em feature própria quando existir a primeira coleção de dados de tenant consultada por requisição.
- Leitura do papel DONO/ADMIN/MEMBRO na coleção Membro (depende da LLT-164).
- Criação de Tenant (fluxo de compra, ADR_121) e cadastro da Empresa (ADR_158).
- Webhooks da Clerk (LLT-164); aqui só a rota fica pública.

### Key Entities

- **Tenant**: empresa cliente, espelho da Organization da Clerk (ADR_121/122). Atributos: tenantId, clerkOrgId (único), name, status. Nesta feature é só lido.
- **RequestContext**: identidade da requisição atual (clerkUserId, sessionId, clerkOrgId, role, tenantId). Não é persistido.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% dos casos de aceitação das US1 a US3 cobertos por testes automatizados que passam em `.\mvnw.cmd clean verify`.
- **SC-002**: Nenhuma rota além das públicas responde 2xx sem token válido.
- **SC-003**: Os corpos de 401 são idênticos para todos os motivos de falha (verificado por teste).
- **SC-004**: A validação do token não chama a Clerk por requisição (JWKS em cache); a Clerk só é chamada para buscar chaves e no 403.
- **SC-005**: Nenhum segredo ou token aparece em código, testes, docs ou logs.

## Assumptions

- O front usa session token v2 da Clerk (claims `sts`, `o.id`, `o.rol`).
- O Tenant é criado pelo fluxo de compra (feature futura); até lá, só existe em fixtures de teste ou inserido manualmente no Mongo local para teste manual.
- Status de Tenant conhecidos nesta feature: `ACTIVE` e `SUSPENDED`; só `ACTIVE` dá acesso. Outros status entram com a feature de Assinatura.
- A janela de até 60 s após a revogação (token já emitido ainda válido) é aceita, conforme a ADR_032.
- A branch `origin/Development` (Empresa e Produtos) tem um `SecurityConfig` e um `CompanySession` próprios. **Atualizado em 2026-11-09 (research R16):** a branch passou a partir da `Development` e a junção foi feita nesta feature: uma camada de segurança só, `CompanySession` montado pelo `CurrentRequestContext` e a Empresa gravada no documento do Tenant.
