# lhamalog-core

Empresa e Produtos: endpoints `/api/company` e `/api/products` com persistência Mongo. [Especificação](specs/001-empresa-produtos/spec.md), [contrato](specs/001-empresa-produtos/contracts/api.md) e [execução/testes](specs/001-empresa-produtos/quickstart.md). A segurança e o Tenant usados por eles são os da LLT-163 (ver "Segurança").

API pública do LhamaLog: assinatura, multi-tenant (Clerk Organizations), papéis e matriz de alertas, WebSocket e único ponto de contato do front.

Parte da plataforma LhamaLog (SaaS multi-tenant de análise de logs com IA). Pacote base `br.com.lhamalog.core`, groupId `br.com.lhamalog`. Gerado no Spring Initializr: Java 25, Spring Boot 4.1.1, Maven. Estrutura proposta: hexagonal leve.

## Pré-requisitos
- JDK 25
- Docker (para a infra local e os testes com Testcontainers)
- make (opcional; Linux/macOS ou WSL)
- Maven não é necessário: use o wrapper `./mvnw`

## Comandos

| Ação | Maven | Makefile |
|---|---|---|
| Rodar | `./mvnw spring-boot:run` | `make run` |
| Buildar | `./mvnw clean package` | `make build` |
| Testar | `./mvnw test` | `make test` |
| Verificação completa | `./mvnw clean verify` | `make verify` |

Executar o jar gerado:

```bash
java -jar target/lhamalog-core-0.0.1-SNAPSHOT.jar
```

Perfil local: `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`.

## Infra local
MongoDB (banco do Core, ADR_116), Redis e OpenObserve (log store da plataforma, ADR_168, substitui o Elasticsearch) sobem pelo compose em `infra/local/compose.yaml`, com tags de imagem fixas (`mongo:8.0.34`, `redis:8.2.10`, `public.ecr.aws/zinclabs/openobserve:v1.1.2`).

1. Copie `infra/local/.env.example` para `infra/local/.env` e preencha as senhas (o `.env` nao e versionado).
2. Suba: `make infra-up` ou `docker compose -f infra/local/compose.yaml up -d`.
3. Exporte no terminal as variaveis da aplicacao (`MONGODB_URI`, `REDIS_PASSWORD`, `OPENOBSERVE_AUTH`, `CLERK_ISSUER` e as demais da Clerk, ver "Segurança"; exemplos no `.env.example`) e rode `make run-local`.
4. Derrube: `make infra-down`.

Os testes usam Testcontainers (`mongo:8.0.34`, `redis:8.2.10`, `grafana/otel-lgtm:0.37.0`) e sobem os containers sozinhos.

## Spec Kit
O repositorio usa o GitHub Spec Kit (`.specify/`, comandos `/speckit-*` em `.cursor/skills/`). Toda feature segue specify, plan, tasks e implement; regras em `.specify/memory/constitution.md`.

## Segurança (LLT-163)
Toda rota exige o session token da Clerk no header `Authorization: Bearer <token>`, exceto `/actuator/health`, `/actuator/info` e `POST /webhooks/clerk` (que tem autenticação própria por assinatura). A configuração fica em `config.SecurityConfig`. Decisões em `specs/002-validacao-token-clerk/`.

- **401** `{"error":"unauthorized"}`: token ausente ou inválido (assinatura RS256 pelo JWKS da Clerk, `exp`/`nbf` com 5 s de tolerância, `iss`, `azp` fora da lista de origens, `sts` diferente de `active`). O motivo não é informado (ADR_032).
- **403** `{"error":"forbidden"}`: token válido, mas sem organização ativa (claim `o`), sem Tenant para a organização, com Tenant não ativo ou com papel desconhecido. No 403 o Core revoga todas as sessões ativas do usuário na Clerk (Backend API). Se a Clerk falhar, o 403 se mantém e o erro vai para o log.
- O restante do código lê a identidade da requisição pela porta `identity.application.port.CurrentRequestContext` (clerkUserId, sessionId, clerkOrgId, role, tenantId). Empresa e Produtos usam essa porta por meio do `shared.CompanySessionResolver`: o `tenantId` é o id interno do Tenant (não o `org_id` da Clerk) e o administrador é o papel `admin` da organização.
- A Empresa fica no próprio documento do Tenant (coleção `tenant`): `/api/company` grava só os dados da Empresa, sem alterar nome e status do Tenant. Produto inacessível ou de outro Tenant responde 404, e não 403, para não deslogar o usuário.
- CORS liberado só para as origens de `CLERK_AUTHORIZED_PARTIES`.

### Variáveis da Clerk
| Variável | Obrigatória | Padrão |
|---|---|---|
| `CLERK_ISSUER` | sim, em todos os perfis (a aplicação não sobe sem ela) | — |
| `CLERK_AUTHORIZED_PARTIES` | em prod | local: `http://localhost:5173` |
| `CLERK_SECRET_KEY` | em prod | local: vazia (revogação pulada, com aviso no log) |
| `CLERK_JWKS_URI` | não | `<CLERK_ISSUER>/.well-known/jwks.json` |
| `CLERK_API_URL` | não | `https://api.clerk.com` |
| `CLERK_CLOCK_SKEW` | não | `5s` |

O `CLERK_ISSUER` é a Frontend API URL da instância (Dashboard da Clerk > API keys). Também é possível obtê-lo da publishable key do front (`pk_test_...`): o trecho depois de `pk_test_` é o Base64 do host seguido de `$`. Os segredos ficam só no ambiente, nunca no repositório.

### Testar manualmente
Passo a passo em `specs/002-validacao-token-clerk/quickstart.md`. Como o fluxo de compra (que cria o Tenant) ainda não existe, sem Tenant toda chamada responde 403 e desloga o usuário. No perfil local, defina `DEV_TENANT_CLERK_ORG_ID` com o `org_id` da organização usada no lhamalog-web: ao subir, o Core cria um Tenant `ACTIVE` para ela (e não altera um Tenant que já exista). Também é possível inserir à mão no Mongo local:

```javascript
db.tenant.insertOne({ _id: "<um UUID>", clerkOrgId: "<org_id da organizacao>", name: "Teste local", status: "ACTIVE" })
```

## Webhooks da Clerk (LLT-164)
`POST /webhooks/clerk` é público em relação à sessão e protegido pela assinatura do Svix (headers `svix-id`, `svix-timestamp`, `svix-signature`), verificada sobre o corpo bruto com `CLERK_WEBHOOK_SIGNING_SECRET` e tolerância de 5 min. Decisões em `specs/003-webhooks-clerk/`.

- Eventos tratados: `user.created`, `user.updated`, `user.deleted`, `organizationMembership.created`, `organizationMembership.updated` e `organizationMembership.deleted`. Os demais recebem 200 e são ignorados.
- Mantém a coleção `member` (Membro: tenantId, clerkUserId, papel DONO/ADMIN/MEMBRO, nome e e-mail). `org:admin` vira ADMIN e `org:member` vira MEMBRO. Um DONO nunca é rebaixado. O webhook **não cria Tenant** (ADR_121): vínculo de organização sem Tenant é ignorado.
- Idempotência: cada `svix-id` é processado uma vez (coleção `clerk_webhook_event`, TTL de 7 dias), e eventos mais antigos que o estado salvo (`updated_at`) são ignorados. `user.deleted` faz soft delete (`deletedAt`) e o usuário nunca é recriado (coleção `deleted_clerk_user`).
- Respostas: 200 processado/duplicado/ignorado; 400 assinatura ou payload inválido; 500 falha ao gravar (o Svix reenvia).

| Variável | Obrigatória | Padrão |
|---|---|---|
| `CLERK_WEBHOOK_SIGNING_SECRET` | em prod | local: vazia (todo webhook é rejeitado com 400) |
| `CLERK_WEBHOOK_TOLERANCE` | não | `5m` |

### Receber webhooks em desenvolvimento (túnel)
A Clerk precisa alcançar o Core pela internet, então em desenvolvimento é preciso um túnel HTTPS para a porta local.

1. Suba o Core com o perfil local (`make run-local`), com as variáveis da Clerk exportadas.
2. Instale uma ferramenta de túnel (por exemplo, ngrok ou Cloudflare Tunnel) e exponha a porta `8080`. Ela mostra uma URL pública HTTPS.
3. No Dashboard da Clerk (instância de desenvolvimento) > **Webhooks** > **Add Endpoint**, informe `<URL do túnel>/webhooks/clerk` e assine os seis eventos listados acima.
4. Copie o **Signing Secret** do endpoint para a variável `CLERK_WEBHOOK_SIGNING_SECRET` do seu terminal (nunca para um arquivo versionado) e reinicie o Core.
5. Use a aba **Testing** do endpoint para enviar eventos de exemplo e confira a coleção `member` no Mongo local.
6. Nas versões gratuitas a URL do túnel costuma mudar a cada execução: atualize o endpoint no Dashboard quando isso acontecer.

Mais detalhes em `specs/003-webhooks-clerk/quickstart.md`.

## Dependências
Antes de adicionar qualquer biblioteca, ver Drive da equipe > Arquitetura > Dependências específicas dos módulos (evita bibliotecas com a mesma função).

Dependências a adicionar manualmente (fora do Initializr): nenhuma obrigatória. A validação do token da Clerk usa o resource server já presente, e a assinatura do Svix é verificada com o JDK (LLT-163/164), por isso o SDK da Clerk e o `com.svix:svix` não são usados. Gateway de pagamento [PENDENTE].

Sem GitHub Actions: rode `make verify` localmente antes de cada push.

## Padrão de commit

Mensagens no formato `CHAVE-JIRA: descricao` (ex.: `LLT-82: inicializacao do modulo: Collector`), conforme a ADR_148. Detalhes em [CONTRIBUTING.md](CONTRIBUTING.md).

## Log de inicializacao (ADR_149)

Ao subir, o servico registra em INFO uma linha padrao, emitida por `config.StartupLogger` no evento `ApplicationReadyEvent`:

```
[LhamaLog] lhamalog-core iniciado | versao=0.0.1-SNAPSHOT | porta=8080 | perfis=local | java=25
```

## Perfis (ADR_151)
- `local` (padrao): `application-local.properties`, valores de desenvolvimento sobrescreviveis por variavel de ambiente.
- `prod`: `application-prod.properties`, so variaveis de ambiente (`SPRING_PROFILES_ACTIVE=prod`).

## Aplicação completa no Docker

Com Core e Web em pastas irmãs e infra/local/.env configurado, execute: `docker compose --env-file infra/local/.env -f infra/local/compose.app.yaml up -d --build`. O Compose inclui a infraestrutura existente e inicia Spring e Vite. Guia compartilhado: ../lhamalog_docs/planejamento/ambiente-local.md.

No `infra/local/.env`: `CLERK_ISSUER` é obrigatória (sem ela o Compose para com erro) e `VITE_CLERK_PUBLISHABLE_KEY` é a chave publicável do front. Preencha também `DEV_TENANT_CLERK_ORG_ID` com o `org_id` da sua organização; sem Tenant, o usuário é deslogado na primeira chamada. `CLERK_SECRET_KEY` e `CLERK_WEBHOOK_SIGNING_SECRET` são opcionais no local (sem elas, o 403 não revoga sessões e os webhooks são rejeitados). Abra o front em `http://localhost:5173`, não em `127.0.0.1`.
