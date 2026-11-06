# lhamalog-core

Empresa e Produtos: endpoints e persistência Mongo. O arranque Docker independente deste repositório está na branch `dev/developer`, criada a partir de `dev/empresa-produtos`. [Especificação](specs/001-empresa-produtos/spec.md), [contrato](specs/001-empresa-produtos/contracts/api.md) e [execução/testes](specs/001-empresa-produtos/quickstart.md).

API pública do LhamaLog: assinatura, multi-tenant (Clerk Organizations), papéis e matriz de alertas, WebSocket e único ponto de contato do front.

Parte da plataforma LhamaLog (SaaS multi-tenant de análise de logs com IA). Pacote base `br.com.lhamalog.core`, groupId `br.com.lhamalog`. Gerado no Spring Initializr: Java 25, Spring Boot 4.1.1, Maven. Estrutura proposta: hexagonal leve.

## Clonar

```bash
git clone https://github.com/LhamaLog/lhamalog-core.git
cd lhamalog-core
git checkout dev/developer
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

Sem `CLERK_ISSUER` e `CLERK_JWKS_URI`, a API sobe e rejeita tokens. `/actuator/health` e `/actuator/info` continuam públicos. Login e telas autenticadas dependem da chave publicável no Web e da mesma instância Clerk. Não há autenticação alternativa.

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

Em uma máquina nova: instale Docker Compose v2, clone `dev/developer`, restaure o `.env` e extraia o arquivo em um volume vazio:

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

## Segurança

`config.SecurityConfig` valida JWT Clerk por JWKS, issuer, validade e origem permitida. A variável `CLERK_AUTHORIZED_PARTIES` alimenta o CORS de `/api/**` e o claim `azp`. `/actuator/health` e `/actuator/info` permanecem públicos. Sem essa configuração, a API rejeita tokens. Empresa vem da organização ativa; Produtos exigem vínculo ou papel administrativo configurado (`CLERK_ADMIN_ROLES`, padrão `admin`). Veja o guia da feature.

## Dependências
Antes de adicionar qualquer biblioteca, ver Drive da equipe > Arquitetura > Dependências específicas dos módulos (evita bibliotecas com a mesma função).

Dependências a adicionar manualmente (fora do Initializr): nenhuma obrigatória; SDK da Clerk/Svix e gateway de pagamento [PENDENTE].

Sem GitHub Actions: rode `make verify` localmente antes de cada push.

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
