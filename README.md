# lhamalog-core

Empresa e Produtos: endpoints e persistência Mongo. O desenvolvimento integra na branch `Development`. [Especificação](specs/001-empresa-produtos/spec.md), [contrato](specs/001-empresa-produtos/contracts/api.md) e [execução/testes](specs/001-empresa-produtos/quickstart.md). A segurança e o Tenant usados por eles são os da LLT-163 (ver "Segurança").

API pública do LhamaLog: assinatura, multi-tenant (Clerk Organizations), papéis e matriz de alertas, WebSocket e único ponto de contato do front.

Parte da plataforma LhamaLog (SaaS multi-tenant de análise de logs com IA). Pacote base `br.com.lhamalog.core`, groupId `br.com.lhamalog`. Gerado no Spring Initializr: Java 25, Spring Boot 4.1.1, Maven. Estrutura proposta: hexagonal leve.

## Clonar

```bash
git clone https://github.com/LhamaLog/lhamalog-core.git
cd lhamalog-core
git checkout Development
```

## Pré-requisitos

- Docker Engine (Ubuntu) ou Docker Desktop (Windows), com Compose v2. O comando é `docker compose`, não o `docker-compose` v1.
- `.env` na raiz, copiado de `.env.example`, com senhas preenchidas só na sua máquina. O `.env` não é versionado.
- JDK 25 somente para rodar ou testar fora do contêiner (`make run`, `./mvnw`).
- make é opcional. Sem make, use os comandos `docker compose` desta página.
- Maven não é necessário: use o wrapper `./mvnw` (no Windows, `mvnw.cmd`).

Estes arquivos não alteram a configuração global do Docker nem do WSL e não definem teto de CPU ou memória.

## Comandos no host

| Ação | Maven | Makefile |
|---|---|---|
| Rodar | `./mvnw spring-boot:run` | `make run` |
| Rodar com perfil local | `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` | `make run-local` |
| Buildar | `./mvnw clean package` | `make build` |
| Testar | `./mvnw test` | `make test` |
| Verificação completa | `./mvnw clean verify` | `make verify` |

Executar o jar gerado:

```bash
java -jar target/lhamalog-core-0.0.1-SNAPSHOT.jar
```

O Spring não lê o `.env` sozinho. Para o processo no host alcançar Mongo e Redis publicados em `127.0.0.1`, exporte `MONGODB_URI` com host `127.0.0.1`, `REDIS_PASSWORD`, `CLERK_AUTHORIZED_PARTIES=http://localhost:5173`, `CLERK_ISSUER`, `CLERK_JWKS_URI` e `OTLP_ENABLED=false` quando o OpenObserve estiver desligado. Não use o hostname `mongo` fora da rede do Compose. O formato da URI está comentado em `.env.example`.

## Docker de desenvolvimento

O `compose.yaml` da raiz sobe a API, o MongoDB e o Redis. O projeto Compose continua `lhamalog-core-local`, o mesmo dos volumes já existentes. Este repositório não constrói o Web.

1. Copie o exemplo e preencha as senhas. Use só letras e dígitos: `@ : / ? # % $`, espaços e aspas quebram a URI ou a interpolação do Compose. A mesma senha serve o init do Mongo e a URI interna da API. Não use a senha root na aplicação. Se já existir `infra/local/.env`, copie-o para `.env` na raiz e não apague o original.

   ```bash
   cp .env.example .env
   ```

   ```powershell
   Copy-Item .env.example .env
   ```

2. Na raiz do repositório:

   ```bash
   docker compose up -d --build
   docker compose ps
   docker compose logs -f
   docker compose down
   ```

`down` remove os contêineres e a rede deste projeto e mantém os volumes. Não execute `docker compose down -v`, `docker volume rm`, `docker volume prune` nem `docker system prune`.

Com make, os atalhos são `make up`, `make status`, `make logs` e `make down`. `make infra-up` sobe só Mongo e Redis, sem construir a API, para quem roda o Java no host. `make infra-down` apenas para esses dois contêineres: não remove a API e não apaga volumes.

A API fica em `http://127.0.0.1:8080`. No navegador da mesma máquina, use `http://localhost:8080`. Mongo e Redis também só em `127.0.0.1` (`MONGO_PORT` 27017 e `REDIS_PORT` 6379). Dentro da rede do Compose a API usa os hostnames `mongo` e `redis`. O Web não precisa entrar nessa rede: o navegador chama a URL publicada.

### OpenObserve (opcional)

Não entra no `docker compose up` padrão. O volume `lhamalog-core-local_openobserve-data`, se já existir, permanece no Docker até você voltar a usar o serviço. Para subir o log store junto da API:

```bash
docker compose -f compose.yaml -f compose.observability.yaml up -d --build
```

Preencha `OPENOBSERVE_USERNAME` (e-mail), `OPENOBSERVE_PASSWORD` e `OPENOBSERVE_AUTH` (`Basic ` seguido do base64 de `usuario:senha`). Para a API exportar, defina `OTLP_ENABLED=true`. O atalho é `make obs-up`. `make obs-down` só para o OpenObserve e mantém o volume.

### Ubuntu e Windows

Ubuntu: Docker Engine e o plugin Compose v2. Confira com `docker --version` e `docker compose version`. Os comandos da seção anterior são os do terminal. Não edite `/etc/docker/daemon.json` para este projeto.

Windows: Docker Desktop com contêineres Linux. No PowerShell, os mesmos comandos `docker compose`. O make não é necessário. Não edite `.wslconfig` nem o limite de memória do Docker Desktop por causa deste repositório.

### Web, em separado

O frontend é o repositório `lhamalog-web`, no clone e no compose dele. Contrato local:

- UI: `http://localhost:5173`
- API no navegador: `http://localhost:8080`
- `VITE_API_URL=http://localhost:8080` (o código do Web também usa esse endereço quando a variável não está definida)

Não há checkout irmão obrigatório nem rede Docker compartilhada. Não aponte o navegador para o hostname interno `api`.

No Web, com Node.js 26.12.1: `npm ci`, crie `.env.local` com a chave **publicável** da Clerk (`VITE_CLERK_PUBLISHABLE_KEY`) e rode `npm run dev`. Abra `http://localhost:5173`. A chave secreta não entra no front nem neste Core.

`CLERK_AUTHORIZED_PARTIES` define, ao mesmo tempo, as origens CORS de `/api/**` e o claim `azp` aceito no JWT. O padrão do compose de desenvolvimento é `http://localhost:5173`. Várias origens: separe por vírgula, sem espaço. A origem tem de ser exatamente a que o navegador usa; `localhost` e `127.0.0.1` são origens diferentes.

Sem `CLERK_ISSUER` a API não sobe (o `compose.yaml` para com erro). Uma organização sem Tenant recebe 403 `subscription_required`, sem logout, e o Web leva a pessoa à tela de planos: contrate pelo simulador (ver "Contratar um plano") ou, como atalho, preencha `DEV_TENANT_CLERK_ORG_ID` com o `org_id` da sua organização. `/actuator/health` e `/actuator/info` continuam públicos. Login e telas autenticadas dependem da chave publicável no Web e da mesma instância Clerk. Não há autenticação alternativa.

O compose do Web na branch `dev/empresa-produtos` daquele repositório ainda não repassa `VITE_CLERK_PUBLISHABLE_KEY` ao contêiner; nesse modo a página fica em branco. Para a interface com login, use `npm run dev` e o `.env.local`.

### Volumes, backup e outra máquina

Nenhuma migração de volume é necessária. O nome do projeto não mudou.

| Dado | Volume |
|---|---|
| MongoDB | `lhamalog-core-local_mongo-data` |
| Redis | `lhamalog-core-local_redis-data` |
| OpenObserve | `lhamalog-core-local_openobserve-data` |

O script `infra/local/mongo-init` só roda quando o volume do Mongo está vazio. Volume que já tem dados é reutilizado; a senha não é recriada e o volume não deve ser apagado para "aplicar" o script.

Backup, lendo o volume sem removê-lo:

```bash
mkdir -p backup
docker run --rm -v lhamalog-core-local_mongo-data:/data:ro -v "$PWD/backup":/backup mongo:8.0.34 tar czf /backup/mongo-data.tgz -C /data .
```

Repita com `lhamalog-core-local_redis-data` e, se usar o OpenObserve, `lhamalog-core-local_openobserve-data`. Copie também o `.env` por um canal privado. O Git não leva senhas nem esses volumes.

Em uma máquina nova: instale Docker Compose v2, clone a branch `Development`, restaure o `.env` e extraia o arquivo em um volume vazio:

```bash
docker volume create lhamalog-core-local_mongo-data
docker run --rm -v lhamalog-core-local_mongo-data:/data -v "$PWD/backup":/backup mongo:8.0.34 tar xzf /backup/mongo-data.tgz -C /data
docker compose up -d --build
```

Não extraia por cima de um volume que você não tenha acabado de conferir. Não apague o volume antigo para abrir espaço. O Web é clonado e iniciado à parte.

### Limitações

- Este compose é desenvolvimento local, não publicação.
- As portas no host ficam só em `127.0.0.1`. Outro computador da rede não alcança a API nem os bancos. Se `localhost` resolver apenas para IPv6, use `http://127.0.0.1:8080`.
- A aplicação dentro do contêiner escuta na interface do contêiner. A restrição é a publicação no host. Não force `server.address=127.0.0.1` dentro do contêiner, senão o mapeamento de porta deixa de funcionar.
- Não há teto de recurso nos arquivos Compose e nenhuma mudança de daemon ou WSL.
- OpenObserve é opcional. Com `OTLP_ENABLED=true` e o serviço parado, a exportação falha; o contrato HTTP da API não depende dele.
- `compose.prod.yaml` é um rascunho separado. `docker compose up` não o carrega. Não foi validado em execução: não houve teste de subida, TLS, cofre de segredo, backup automatizado nem aceite. Não o descreva como produção pronta. O projeto é `lhamalog-core-prod`, para não reutilizar nem apagar os volumes `lhamalog-core-local_*`. As variáveis usam o prefixo `PROD_` (veja `.env.prod.example`) e não são satisfeitas pelo `.env` de desenvolvimento. Mongo e Redis desse arquivo não publicam porta no host.
- Uma porta 8080 por vez: contêiner ou `mvnw`.
- Os testes com Testcontainers sobem imagens próprias e não usam estes volumes.

## Segurança (LLT-163)
Toda rota exige o session token da Clerk no header `Authorization: Bearer <token>`, exceto `/actuator/health`, `/actuator/info`, `POST /webhooks/clerk` e `POST /webhooks/asaas` (que têm autenticação própria) e o simulador `/dev/fake-asaas/**` (só no perfil local). A configuração fica em `config.SecurityConfig`. Decisões em `specs/002-validacao-token-clerk/`.

- **401** `{"error":"unauthorized"}`: token ausente ou inválido (assinatura RS256 pelo JWKS da Clerk, `exp`/`nbf` com 5 s de tolerância, `iss`, `azp` fora da lista de origens, `sts` diferente de `active`). O motivo não é informado (ADR_032).
- **403** `{"error":"forbidden"}`: token válido, mas sem organização ativa (claim `o`), com Tenant não ativo ou com papel desconhecido. No 403 o Core revoga todas as sessões ativas do usuário na Clerk (Backend API). Se a Clerk falhar, o 403 se mantém e o erro vai para o log.
- **403** `{"message":"...","code":"subscription_required"}`: a organização ainda não tem Tenant (não contratou um plano). As sessões **não** são revogadas, e as rotas `/api/billing/**` continuam abertas para a contratação (spec 010, ver "Contratar um plano").
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
Passo a passo em `specs/002-validacao-token-clerk/quickstart.md`. Sem Tenant, as rotas respondem 403 `subscription_required` (sem logout) até a organização contratar um plano (ver "Contratar um plano"). Como atalho, no perfil local, defina `DEV_TENANT_CLERK_ORG_ID` com o `org_id` da organização usada no lhamalog-web: ao subir, o Core cria um Tenant `ACTIVE` para ela (e não altera um Tenant que já exista). Também é possível inserir à mão no Mongo local:

```javascript
db.tenant.insertOne({ _id: "<um UUID>", clerkOrgId: "<org_id da organizacao>", name: "Teste local", status: "ACTIVE" })
```

### Login e organização de teste
Continua valendo junto com o fluxo de compra simulado (seção "Contratar um plano"): é o caminho mais curto para ter uma organização com Tenant. Cada desenvolvedor usa o próprio login e a própria organização na instância de **desenvolvimento** da Clerk, a mesma da chave `pk_test_` do Web. Nunca use a instância de produção.

1. **Criar o login.** Com o Web rodando, abra `http://localhost:5173/sign-up` e cadastre-se. Também é possível criar pelo Dashboard da Clerk > **Users** > **Create user**. Na instância de desenvolvimento, um e-mail com `+clerk_test` (ex.: `fulano+clerk_test@exemplo.com`) aceita o código de verificação `424242` sem enviar e-mail. Se a instância exigir segundo fator, configure um app autenticador na etapa "configurar MFA" e guarde os códigos de recuperação.
2. **Criar a organização.** Depois do login, o Web abre a etapa "escolher ou criar organização": crie uma (ex.: "Teste - <seu nome>"). Quem cria fica com o papel `admin`. Pelo Dashboard: **Organizations** > **Create organization** e adicione o seu usuário como `admin`.
3. **Copiar o `org_id`.** No Dashboard da Clerk > **Organizations** > a sua organização, copie o ID (`org_...`).
4. **Criar o Tenant no Core.** No `.env` da raiz deste repositório, preencha `DEV_TENANT_CLERK_ORG_ID=<org_id>` e reinicie a API (`docker compose up -d`). Para mais de uma organização (isolamento entre empresas, convites), separe por vírgula: `DEV_TENANT_CLERK_ORG_ID=<org A>,<org B>`. No log (`docker compose logs api`) aparece uma linha por organização: `event=dev_tenant.seed.created` na primeira subida e `event=dev_tenant.seed.exists` nas próximas. O Tenant fica no volume do Mongo e não é recriado. Valor que não é `org_...` é ignorado com o aviso `event=dev_tenant.seed.invalid_org_id`, e a API sobe mesmo assim.
5. **Conferir.** Entre no Web com essa organização ativa e abra `/empresa`: a tela carrega sem "Você foi deslogado". Para a revogação de sessão no 403 funcionar, preencha também `CLERK_SECRET_KEY` (`sk_test_...`, Dashboard > **API keys**) só no seu `.env`.

Se não funcionar:
- **Toda chamada responde 401**: o Web e o Core apontam para instâncias diferentes da Clerk (confira se o host do `CLERK_ISSUER` é o mesmo da `pk_test_` do Web), ou o relógio da máquina está fora da tolerância de 5 s (`exp`/`nbf`).
- **403 e logout com a organização certa**: o log mostra `event=dev_tenant.seed.inactive`. O Tenant existe, mas não está `ACTIVE`, e o Core nunca o altera. Corrija o `status` para `ACTIVE` no Mongo local (coleção `tenant`) ou apague o documento e reinicie a API.
- **Web leva a `/plano` com outra organização** (403 `subscription_required`, sem logout): ela não tem Tenant. Contrate um plano pelo simulador ou acrescente o `org_id` em `DEV_TENANT_CLERK_ORG_ID` e reinicie.

Roteiro completo de validação com a Clerk real: `specs/008-login-organizacao-teste/quickstart.md`.

Limitações enquanto isso:
- Qualquer organização sem Tenant, inclusive uma criada agora pela tela "Cadastrar empresa", recebe 403 `subscription_required` (sem logout) até contratar um plano pelo simulador (seção "Contratar um plano") ou entrar em `DEV_TENANT_CLERK_ORG_ID`.
- Login, senha e `org_id` de teste não entram no repositório nem em issue; os segredos ficam só no `.env` (ignorado pelo Git).

## Contratar um plano (LLT-289, Asaas simulado)
O Tenant nasce na compra (ADR_121). Um administrador de organização sem Tenant escolhe um plano, paga na página do gateway e, quando o gateway confirma pelo webhook, o Core cria o Tenant `ACTIVE`, a Assinatura (coleção `tenant_subscription`) e o Membro DONO. O gateway escolhido é o Asaas; por enquanto existe só o **simulador local**, que envia a confirmação no formato do Asaas para o endpoint real `POST /webhooks/asaas`. Decisões em `specs/010-criar-tenant-pagamento/`.

- `GET /api/billing/plans`, `POST /api/billing/checkout` (`{"plan":"STARTER","companyName":"..."}`) e `GET /api/billing/order` aceitam organização sem Tenant. Só o `admin` contrata. Contrato completo em `specs/010-criar-tenant-pagamento/contracts/api.md`.
- O `paymentUrl` do checkout abre a página do simulador (`/dev/fake-asaas/checkouts/<id>`). O botão **Simular pagamento** envia um `PAYMENT_RECEIVED` ao webhook, e a página mostra o link de volta ao Web (`BILLING_RETURN_URL`).
- Todo plano, inclusive o FREE, só ativa depois da confirmação. Os preços em `application.properties` são provisórios.
- Cada evento do gateway é registrado uma vez (coleção `gateway_event`, pelo `id` do evento do Asaas). Reenvio não duplica nada; falha na ativação responde 500 e o reenvio retoma.
- Fora do perfil local não há simulador: a contratação responde 503 `payment_unavailable` até o adaptador real do Asaas existir.

| Variável | Obrigatória | Padrão |
|---|---|---|
| `ASAAS_WEBHOOK_TOKEN` | em prod (32 a 255 caracteres; sem ele a API não sobe) | local: gerado ao subir e usado pelo simulador |
| `BILLING_RETURN_URL` | em prod | `http://localhost:5173/empresa` |
| `BILLING_FAKE_CHECKOUT_BASE_URL` | não | `http://localhost:8080` (no compose, segue `API_PORT`) |
| `BILLING_FAKE_WEBHOOK_URL` | não | `http://localhost:${SERVER_PORT}/webhooks/asaas` |

Roteiro manual: `specs/010-criar-tenant-pagamento/quickstart.md`.

## Ocorrências (LLT-135)
O Core só **lê** as ocorrências que o Módulo Ocorrência grava na coleção `Ocorrencia` (a gravação a partir do resumo do Finder é a LLT-293). Quem vê o quê segue a regra de produto: dono e administrador veem todos os produtos, inclusive arquivados; membro, só os produtos a que tem acesso. Decisões em `specs/012-consultar-ocorrencias/`.

- `GET /api/occurrences`: lista paginada, da última vez mais recente para a mais antiga. Parâmetros: `page` (padrão 1), `size` (10, 20 ou 50; padrão 20), `type` e `status` repetíveis, `productId` repetível, `from` e `to` (ISO-8601 com fuso; entra o que aconteceu no período) e `q` (até 200 caracteres, em título, resumo e assinatura, ou o id exato).
- `GET /api/occurrences/{id}`: detalhe com resumo, os 5 exemplos de log mais recentes (já mascarados, cortados em 512 caracteres), atributos técnicos e histórico de status. Outra empresa, produto sem acesso ou id inexistente respondem o mesmo 404.
- Sem título gravado, o título é a mensagem normalizada da assinatura do Finder. Documento fora do formato fica fora da lista e gera `WARN event=occurrence.read.invalid` no detalhe.
- **Dados de exemplo (perfil local):** com `OCCURRENCE_SAMPLE_DATA=true`, o Core grava 12 ocorrências de exemplo para cada produto existente ao subir, sem duplicar nem alterar as que já existem. Produto criado depois só ganha exemplos na próxima subida.

Contrato completo em `specs/012-consultar-ocorrencias/contracts/api.md`; roteiro em `quickstart.md`.

**Ciclo de vida (LLT-136, `specs/014-ciclo-de-vida-ocorrencias/`):** quem vê o produto da ocorrência pode mudar o status e arquivar (permissão `OCCURRENCES_MANAGE` da matriz da LLT-120).

- `PUT /api/occurrences/{id}/status` `{status, revision}`: ABERTA, EM_ANALISE ou FECHADA, de qualquer um para qualquer outro; a mudança entra no histórico com o usuário e a data.
- `POST /api/occurrences/{id}/archive` e `/unarchive` `{revision}`: arquivada sai da lista padrão e aparece com `archived=true`.
- `revision` é a que o detalhe devolveu; se a ocorrência mudou depois (outra pessoa ou o Finder), a resposta é 409 `occurrence_changed`.
- Um achado novo do Finder desarquiva e reabre a ocorrência como ABERTA.

## Módulo Ocorrência (LLT-293)

O Finder grava as ocorrências pelo Módulo Ocorrência, que mora neste Core. [Especificação](specs/013-gravar-ocorrencias/spec.md), [contrato das rotas](specs/013-gravar-ocorrencias/contracts/internal-api.md) e [roteiro](specs/013-gravar-ocorrencias/quickstart.md).

- `POST /internal/v1/ocorrencias/consultas`: estado da ocorrência de um achado (contagem, datas, status e revisão) ou `estado: null`.
- `POST /internal/v1/resumos-analise`: grava os achados de um lote, tudo ou nada, e devolve um recibo; reenviar a mesma chave (`batchId` + `execId`) devolve o mesmo recibo sem gravar de novo. Revisão desatualizada responde `409 REVISAO_DESATUALIZADA`.
- As rotas exigem `Authorization: Bearer <JWT interno>` HS256, com `iss`, `aud` e `sub` da configuração. O token da Clerk não serve aqui, e o JWT interno não serve nas rotas de usuário.

Variáveis (no `.env` local, nunca no Git):

| Variável | Padrão | Uso |
|---|---|---|
| `OCCURRENCE_INTERNAL_JWT_SECRETS` | vazio | segredo atual e, na rotação, o anterior, separados por vírgula; mínimo de 32 bytes cada. Vazio: as rotas recusam tudo com 401 |
| `OCCURRENCE_INTERNAL_JWT_ISSUER` | `lhamalog-internal` | `iss` exigido |
| `OCCURRENCE_INTERNAL_JWT_AUDIENCE` | `lhamalog-modulo-ocorrencia` | valor exigido em `aud` |
| `OCCURRENCE_INTERNAL_JWT_SUBJECTS` | `lhamalog-finder` | serviços (`sub`) autorizados |

Token do Finder (vai em `FINDER_MODULO_OCORRENCIA_JWT`), com o mesmo segredo do Core:

```powershell
$env:OCCURRENCE_INTERNAL_JWT_SECRET = "<segredo>"
java scripts/InternalJwt.java --sub lhamalog-finder --days 90
```

Rotação: ponha o segredo novo na frente da lista (`novo,antigo`), gere o token novo, troque no Finder e depois tire o antigo. Os recibos ficam 30 dias na coleção `ReciboResumoAnalise` e somem sozinhos (índice TTL).

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

O workflow [`.github/workflows/pr-validation.yml`](.github/workflows/pr-validation.yml) comenta o resultado da compilação, dos testes e do build em cada pull request e não bloqueia o merge. Rode `make verify` localmente antes de cada push. Detalhes em [Validação de pull requests](#validação-de-pull-requests).

## Validação de pull requests

O workflow `.github/workflows/pr-validation.yml` comenta em pull requests para `Development` e `main`. O comentário é único, reconhecido pelo marcador `<!-- llt-285-validacao-prs -->`, e a verificação não bloqueia o merge. As checagens de código são `./mvnw -B -DskipTests compile`, `./mvnw -B test` e `./mvnw -B -DskipTests verify`. Se a compilação falha, testes e build não rodam. Os testes usam Testcontainers (Mongo, Redis e `grafana/otel-lgtm`); o runner `ubuntu-latest` já tem Docker, e o workflow não sobe um serviço de Mongo. Em pull request para `main`, o comentário só mostra essas três checagens, com a nota `PR de release (Development → main): só testes e build são verificados`. Pull request de fork não roda. Rascunho também é validado.

Para repetir essa validação, o workflow `.github/workflows/pr-rerun.yml` observa quando uma pessoa com associação `OWNER`, `MEMBER` ou `COLLABORATOR` comenta `test` ou `/test` na conversa do pull request. Espaços e quebras só nas pontas não mudam o comando. `test por favor`, um comentário em issue, um comentário editado, um bot e um fork não disparam a reexecução. O comentário `test` recebe 👀 quando a rodada começa. No fim, recebe o foguete se todos os itens passaram ou o polegar para baixo se algum falhou. O comentário de validação é atualizado no lugar; não surge um segundo.

Limitação: um workflow disparado por comentário (`issue_comment`) usa a versão do arquivo que está na branch padrão (`main`). O comando `test` só funciona de verdade depois que `.github/workflows/pr-rerun.yml` chega em `main`. Enquanto esse arquivo existir só na branch do pull request ou em `Development`, comentar `test` não reexecuta a validação.

## Padrão de commit

Mensagens no formato `CHAVE-JIRA: descricao` (ex.: `LLT-82: inicializacao do modulo: Collector`), conforme a ADR_148. Detalhes em [CONTRIBUTING.md](CONTRIBUTING.md).

## Log de inicializacao (ADR_149)

Ao subir, o servico registra em INFO uma linha padrao, emitida por `config.StartupLogger` no evento `ApplicationReadyEvent`:

```
[LhamaLog] lhamalog-core iniciado | versao=0.0.1-SNAPSHOT | porta=8080 | perfis=local | java=25
```

## Perfis (ADR_151)
- `local` (padrao): `application-local.properties`, valores de desenvolvimento sobrescreviveis por variavel de ambiente. O compose de desenvolvimento define `SPRING_PROFILES_ACTIVE=local`.
- `prod`: `application-prod.properties`, so variaveis de ambiente (`SPRING_PROFILES_ACTIVE=prod`). O arquivo `compose.prod.yaml` apenas esboça esse perfil e não está validado para uso.
