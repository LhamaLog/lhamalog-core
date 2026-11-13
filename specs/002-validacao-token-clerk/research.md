# Research: Validação do session token da Clerk (LLT-163)

Data: 2026-11-06. Decisões tomadas pela IA por delegação do desenvolvedor ("o que achar melhor"); revisar no ponto de parada do plan.

Fontes consultadas: ADR_022, ADR_032, ADR_121, ADR_122, ADR_123, ADR_158, ADR_159 (Drive da equipe); https://clerk.com/docs/guides/sessions/session-tokens e https://clerk.com/docs/reference/backend/sessions/get-session-list (consultadas em 2026-11-06); branch `origin/Development` (commit `f1e56ef`, só como referência).

## R1. Mecanismo de validação do token

- **Decisão**: Spring Security OAuth2 Resource Server (`spring-boot-starter-security-oauth2-resource-server`, já no pom) com `NimbusJwtDecoder` apontando para o JWKS da instância da Clerk, algoritmo fixo RS256.
- **Por quê**: a ADR_022 aceita a verificação manual por JWKS; não exige dependência nova; o doc de dependências reserva o resource server para JWT; a validação é local (networkless), com as chaves em cache, sem chamar a Clerk por requisição.
- **Alternativa rejeitada**: SDK `com.clerk:backend-api` (`authenticateRequest`): dependência nova fora do doc de dependências, duplicaria a função do resource server e não se encaixa como filtro do Spring Security sem código de cola.

## R2. Origem das chaves e do issuer

- **Decisão**: `clerk.issuer` (`CLERK_ISSUER`) = Frontend API URL da instância (ex. de formato: `https://<subdominio>.clerk.accounts.dev`, sem barra final). JWKS = `${issuer}/.well-known/jwks.json`, sobrescrevível por `clerk.jwks-uri` (`CLERK_JWKS_URI`), usado nos testes.
- **Por quê**: a ADR_022 lista esse endpoint; o mesmo valor serve para validar `iss`.
- **Como obter o issuer em dev**: a publishable key do front (`pk_test_...`) é o Base64 do host da Frontend API seguido de `$`; também aparece no Dashboard da Clerk (API keys). Nenhum valor real é gravado no repositório.

## R3. Validadores

- **Decisão**: `DelegatingOAuth2TokenValidator` com:
  1. `JwtTimestampValidator(clockSkew)` para `exp` e `nbf`, com tolerância de **5 s** (configurável em `clerk.clock-skew`). O padrão do Spring é 60 s, que dobraria a vida útil de um token de 60 s.
  2. `JwtIssuerValidator(issuer)`.
  3. `AuthorizedPartyValidator`: `azp` obrigatório e dentro de `clerk.authorized-parties` (comparação exata de string).
  4. `ActiveSessionValidator`: `sts` obrigatório e igual a `active`.
- **Por quê**: são as checagens obrigatórias da ADR_022/032. Todas as falhas viram `BadJwtException`, que o resource server traduz para 401.
- **Nota**: tokens v1 (sem `sts`) são rejeitados. O front usa token v2.

## R4. Corpo e cabeçalho do 401

- **Decisão**: `AuthenticationEntryPoint` próprio que responde 401, `Content-Type: application/json`, corpo `{"error":"unauthorized"}` e `WWW-Authenticate: Bearer` sem `error`/`error_description`.
- **Por quê**: o `BearerTokenAuthenticationEntryPoint` padrão coloca o motivo no header (ex.: "Jwt expired at ..."), o que contraria a ADR_032 (erro básico, sem pistas). O mesmo entry point cobre token ausente.

## R5. Decisão de acesso (403) e onde ela mora

- **Decisão**:
  - Um `Converter<Jwt, AbstractAuthenticationToken>` próprio extrai as claims para um record puro (`ClerkSessionClaims`) e chama o serviço de aplicação `AccessResolver`, que devolve `AccessDecision` (concedido com `RequestContext`, ou negado). O resultado vai dentro da `Authentication` (`ClerkAuthentication`).
  - Um `AuthorizationManager` para `anyRequest()` concede acesso só se a decisão for "concedido". Negação → `AccessDeniedException` → `AccessDeniedHandler`.
  - O `AccessDeniedHandler` próprio responde 403 `{"error":"forbidden"}` e aciona a revogação (R6). Por ficar no handler, qualquer 403 futuro da cadeia (ex.: permissão por rota ou por método) também revoga, como pede a ADR_032.
- **Regras do `AccessResolver`** (sem Spring/Clerk, testável em unidade): sem claim `o` → negado; `o.rol` desconhecido → negado; Tenant inexistente para `o.id` → negado; Tenant não `ACTIVE` → negado; senão concedido.
- **Por quê**: separa autenticação (401) de autorização (403) no ponto em que o Spring já separa; a regra de negócio fica na camada de aplicação.
- **Alternativa rejeitada**: lançar exceção no converter (viraria 401, não 403); filtro próprio depois do `BearerTokenAuthenticationFilter` (duplica o que o `ExceptionTranslationFilter` já faz).
- **Falha de banco** no lookup do Tenant: a exceção não é de autenticação, então a requisição termina em 5xx (não em 401/403, e não revoga sessões).

## R6. Revogação das sessões na Clerk

- **Decisão**: adaptador de saída com o `RestClient` do Spring (sem biblioteca nova), base `clerk.api-url` (`CLERK_API_URL`, padrão `https://api.clerk.com`), header `Authorization: Bearer ${CLERK_SECRET_KEY}`:
  1. `GET /v1/sessions?user_id={sub}&status=active&limit=500` (máximo permitido: menor que 501). A resposta pode ser um array ou um objeto paginado com `data`; o adaptador aceita as duas formas.
  2. `POST /v1/sessions/{id}/revoke` para cada sessão.
- **Execução**: síncrona, antes de escrever o 403, com timeouts curtos (conexão 2 s, leitura 3 s, configuráveis). Qualquer erro é capturado e registrado em `WARN` com o `clerkUserId` e o status HTTP, nunca o segredo ou o token. Sem `CLERK_SECRET_KEY` (só possível no perfil local), registra `WARN` e não chama a Clerk.
- **Por quê**: síncrono é previsível e simples de testar; os timeouts limitam a latência do 403. Assíncrono ficou como evolução se a latência incomodar.
- **Risco aceito**: cada 403 consome rate limit da Backend API (ADR_032).

## R7. Contexto da requisição para o restante do Core

- **Decisão**: porta `CurrentRequestContext` (interface na camada de aplicação, método `RequestContext current()`), com adaptador que lê o `SecurityContextHolder`. `RequestContext` é um record do domínio (clerkUserId, sessionId, clerkOrgId, role, tenantId). Fora de requisição autenticada, lança `IllegalStateException`.
- **Papel**: `OrganizationRole` (`ADMIN`, `MEMBER`), normalizado de `admin`/`org:admin` e `member`/`org:member`. O token v2 manda sem prefixo (doc da Clerk); aceitar o prefixo cobre token v1 e ruído de configuração. DONO/ADMIN/MEMBRO (Membro) virá depois da LLT-164.

## R8. Persistência do Tenant

- **Decisão**: coleção `tenant` no MongoDB; `tenantId` (UUID em texto) é o `_id`; campos `clerkOrgId`, `name`, `status`. Índice único em `clerkOrgId`, criado de forma explícita e idempotente na subida pelo adaptador (`IndexOperations.createIndex`), sem ligar o `auto-index-creation` global.
- **Por quê**: o tenantId é a identidade do documento (ADR_121/122) e `clerkOrgId` é a chave de busca por requisição. Criação explícita deixa claro quais índices existem.
- **Nota**: o lookup por `clerkOrgId` é a única consulta legítima sem filtro de tenantId, porque é ela que descobre o tenant.

## R9. Camada centralizada de filtro por tenantId (ADR_121): proposta, não implementada

- **Proposta**: todo documento de dados de tenant implementa um contrato `TenantOwned` (campo `tenantId`). Os adaptadores Mongo desses documentos estendem uma base `TenantScopedMongoRepository` que obtém o `tenantId` de `CurrentRequestContext` e o acrescenta a toda `Query` (leitura, update e delete) e a todo documento salvo, sem expor métodos que aceitem consulta sem tenant. Processos sem requisição (webhooks, jobs) passam o `tenantId` de forma explícita.
- **Por que não agora**: nesta feature não existe coleção de dados de tenant consultada por requisição; implementar a base sem um consumidor real seria especulativo. Entra junto da primeira coleção desse tipo.

## R10. CORS

- **Decisão**: `CorsConfigurationSource` com as mesmas origens de `clerk.authorized-parties`, métodos `GET, POST, PUT, PATCH, DELETE, OPTIONS`, headers `Authorization` e `Content-Type`, sem credenciais (o token vai no header, não em cookie), `maxAge` de 1 h. Preflight `OPTIONS` passa sem token.
- **Por quê**: o front está em outra origem; sem CORS o navegador bloqueia as chamadas. Reusar a lista do `azp` mantém uma fonte só de verdade. **Confirmado pelo desenvolvedor em 2026-11-06 (aprovação do plan).**

## R11. Configuração

- **Decisão**: `@ConfigurationProperties("clerk")` validado (`@Validated`), em record:

| Propriedade | Variável | Padrão | Obrigatória |
|---|---|---|---|
| `clerk.issuer` | `CLERK_ISSUER` | nenhum | sim (todos os perfis; a app não sobe sem) |
| `clerk.jwks-uri` | `CLERK_JWKS_URI` | `${issuer}/.well-known/jwks.json` | não |
| `clerk.authorized-parties` | `CLERK_AUTHORIZED_PARTIES` | local: `http://localhost:5173`; prod: nenhum | sim em prod |
| `clerk.clock-skew` | `CLERK_CLOCK_SKEW` | `5s` | não |
| `clerk.secret-key` | `CLERK_SECRET_KEY` | local: vazio (revogação desligada com WARN); prod: nenhum | sim em prod |
| `clerk.api-url` | `CLERK_API_URL` | `https://api.clerk.com` | não |

- Prefixo `clerk.` e nomes de variáveis iguais aos da branch `Development`, para facilitar o merge.
- **Falha rápida**: sem `CLERK_ISSUER`, a aplicação não sobe. A alternativa da `Development` (subir e recusar tudo) foi rejeitada porque esconde erro de configuração.

## R12. Estratégia de testes

- **Chaves**: par RSA gerado no teste; tokens assinados com a Nimbus JOSE (já vem com o resource server). Nenhuma chamada à Clerk.
- **JWKS real no teste**: `com.sun.net.httpserver.HttpServer` do JDK (não é biblioteca) servindo o JWK público em porta aleatória, com `clerk.jwks-uri` apontando para ele via `@DynamicPropertySource`. Assim o decoder de produção é testado de ponta a ponta, com os mesmos validadores.
- **Rota protegida de teste**: controller só em `src/test` que devolve o `RequestContext` obtido pela porta, para provar o 200 e o contexto.
- **Revogação**: nos testes de segurança, a porta `SessionRevocation` é trocada por um fake que registra chamadas e pode falhar. O adaptador `RestClient` tem teste próprio contra outro `HttpServer` do JDK (paths, query, header de autorização, as duas formas de resposta, erro e timeout), sem biblioteca de mock HTTP.
- **Tenants**: fixtures inseridas no Mongo do Testcontainers (`mongo:8.0.34`).
- **Propriedades de teste**: `src/test/resources/config/application.properties` com issuer e origens fictícios (não são segredo), para todos os testes de contexto subirem.

## R13. Ambiente de build [RESOLVIDA em 2026-11-06, ver R15]

- A máquina só tem o JDK 27 (`%USERPROFILE%\.jdks\openjdk-27`) e o `JAVA_HOME` não está definido; o `mvnw.cmd` falha com "The JAVA_HOME environment variable is not defined correctly".
- A constituição e o README pedem Java 25. O pom usa `java.version=25`, então compilar com JDK 27 geraria bytecode 25 (`--release 25`), mas o runtime dos testes seria 27.
- Opções: (a) instalar o JDK 25 e definir `JAVA_HOME` (recomendado, aderente ao README); (b) usar o JDK 27 com `release 25` só para rodar o `verify` (decisão do desenvolvedor).

## R15. Ajustes feitos na implementação (2026-11-06)

- **JDK**: instalado o Temurin 25.0.5 (`winget`, `EclipseAdoptium.Temurin.25.JDK`) e definido `JAVA_HOME` do usuário. R13 resolvida pela opção (a).
- **`ActiveSessionValidator`** passou a exigir também `sub` e `sid`. Sem isso, um token assinado sem `sid` geraria erro 500 no converter em vez de 401.
- **`RestClient.builder()`** direto do spring-web: o Boot 4 separou a autoconfiguração do `RestClient.Builder` no módulo `spring-boot-restclient`, que não está no pom. Não foi adicionado para cumprir "nenhuma dependência nova".
- **Dispatch de erro liberado** (`DispatcherType.ERROR`): a página de erro (ex.: 404 de rota inexistente com token válido) não passa de novo pela decisão de acesso, evitando revogação indevida.
- **Contexto único nos testes**: anotação `support.CoreIntegrationTest` (Spring Boot Test + MockMvc + Testcontainers + `ClerkTestConfiguration`), e o JWKS de teste entra por um `DynamicPropertyRegistrar`. Todos os testes compartilham um contexto e um conjunto de containers.
- **Status desconhecido de Tenant** no banco é lido como `SUSPENDED` (com `WARN`), para nunca dar acesso.
- Teste manual com token real (formato de `o.rol`): continua [PENDENTE]; depende das chaves da instância de dev no ambiente do desenvolvedor.
- **Smoke test** (2026-11-06): aplicação real subida com `spring-boot:test-run` (Testcontainers) e `CLERK_ISSUER` da instância de dev (derivado da publishable key pública; JWKS público com uma chave RS256). Resultados: health 200; sem token 401; token com o `kid` real e assinatura falsa 401 (o decoder buscou o JWKS da Clerk); webhook sem assinatura 400; preflight CORS de `http://localhost:5173` com `Access-Control-Allow-Origin`.
- Teste adicional: rota inexistente com token válido responde 404 e não revoga sessões (confirma o passo 7 do quickstart).

## R14. Relação com a branch `Development` [SUBSTITUÍDA pela R16]

> Histórico: a branch passou a partir da `Development` (`f1e56ef`) e a junção foi feita conforme a R16.

- `Development` tem `config/ClerkTokenValidator`, um `SecurityConfig` com `JwtDecoder` e `shared/CompanySession*`. Esta feature parte do `main` e não copia esse código. No merge, os dois `SecurityConfig` e o `CompanySession` x `CurrentRequestContext` vão conflitar; a recomendação é a `Development` passar a usar `CurrentRequestContext` e o `tenantId` resolvido aqui.

## R16. Integração com a Development: Empresa e Produtos (2026-11-09)

A branch Development trouxe Empresa e Produtos (specs/001-empresa-produtos) com uma segurança própria. Decisões da junção, tomadas pelas ADRs:

- **Uma camada de segurança só, a desta feature** (ADR_022, ADR_032). O `ClerkTokenValidator` da Development saiu. A exigência de `exp` e `nbf` presentes, que ele fazia e o `JwtTimestampValidator` não faz, foi incorporada ao decoder.
- **`CompanySession` montado pelo `CurrentRequestContext`.** O `tenantId` é o id interno do Tenant (UUID, ADR_121), não o `org_id`. O administrador é o papel `admin` da organização. `CLERK_ADMIN_ROLES` foi removida: papéis desconhecidos já dão 403, e DONO é papel de negócio do Membro (ADR_123).
- **A Empresa fica no documento do Tenant** (a Development já usava a coleção "Tenant" para a Empresa; a ADR_121 trata o Tenant como a empresa compradora). Para não apagar `name` e `status` do Tenant, a Empresa é gravada por atualização parcial (data, context, address, version), com a versão como concorrência otimista. O `CompanyRepository` saiu, para ninguém salvar o documento inteiro.
- **403 só para acesso ao Tenant, com revogação; regras de negócio usam 404.** O contrato da Development dizia que o 403 não desloga. Ele foi atualizado: o 403 vem da segurança e revoga as sessões, e produto sem acesso já respondia 404.
- **Tenant de desenvolvimento** (`DEV_TENANT_CLERK_ORG_ID`, só no perfil local): cria um Tenant ACTIVE ao subir e nunca altera um existente. Sem isso, o ambiente integrado (`compose.app.yaml`) deslogaria todo usuário, porque o fluxo de compra ainda não existe.
- **`compose.app.yaml`**: `CLERK_ISSUER` obrigatória; `CLERK_SECRET_KEY`, `CLERK_WEBHOOK_SIGNING_SECRET` e `DEV_TENANT_CLERK_ORG_ID` opcionais.
- **Testes da Development migrados para Testcontainers** e tokens de `TestJwks`. Antes eles dependiam de `TEST_MONGODB_URI` e eram pulados no verify. Verify final: 113 testes, 0 falhas, 0 pulados.

## R17. Convergência (2026-11-13)

Resultado do `/speckit-converge` (tarefas T034 a T037).

- **JWKS indisponível respondia 500.** Quando a Clerk não entrega o JWKS, o decoder do Nimbus lança `JwtException` (não `BadJwtException`), o Spring Security transforma isso em `AuthenticationServiceException` e a requisição termina em 500. A spec pede 401. Decisão: o decoder é envolvido por `UnavailableKeysAsInvalidTokenJwtDecoder`, que converte essa falha em `BadJwtException` (401 básico) e registra um `WARN` só com o tipo da causa, sem o token. As chaves já obtidas continuam em cache no decoder original. Teste: `ClerkTokenAuthenticationTests.unavailableJwksIsUnauthorized` (JWKS de teste respondendo 503 e token com `kid` fora do cache, o que força a busca).
- **Algoritmo diferente de RS256** já era rejeitado (`jwsAlgorithm(RS256)`); faltava o teste. Adicionado `symmetricAlgorithmIsUnauthorized` (HS256).
- **Formato real de `o.rol`**: continua [PENDENTE] (T037). Precisa de um token da instância de dev, que só o desenvolvedor gera. O código aceita as duas formas, então o risco é só de documentação.
