# lhamalog-core

Empresa e Produtos: endpoints e persistência Mongo implementados na branch local `dev/empresa-produtos`. [Especificação](specs/001-empresa-produtos/spec.md), [contrato](specs/001-empresa-produtos/contracts/api.md) e [execução/testes](specs/001-empresa-produtos/quickstart.md).

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
3. Exporte no terminal as variaveis da aplicacao (`MONGODB_URI`, `REDIS_PASSWORD`, `OPENOBSERVE_AUTH`; exemplos no `.env.example`) e rode `make run-local`.
4. Derrube: `make infra-down`.

Os testes usam Testcontainers (`mongo:8.0.34`, `redis:8.2.10`, `grafana/otel-lgtm:0.37.0`) e sobem os containers sozinhos.

## Spec Kit
O repositorio usa o GitHub Spec Kit (`.specify/`, comandos `/speckit-*` em `.cursor/skills/`). Toda feature segue specify, plan, tasks e implement; regras em `.specify/memory/constitution.md`.

## Seguranca
`config.SecurityConfig` valida JWT Clerk por JWKS, issuer, validade e origem permitida. `/actuator/health` e `/actuator/info` permanecem publicos. Configure CLERK_ISSUER, CLERK_JWKS_URI e CLERK_AUTHORIZED_PARTIES. Sem configuracao, a API rejeita tokens. Empresa vem da organizacao ativa; Produtos exigem vinculo ou papel administrativo configurado. Veja o guia da feature.

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
- `local` (padrao): `application-local.properties`, valores de desenvolvimento sobrescreviveis por variavel de ambiente.
- `prod`: `application-prod.properties`, so variaveis de ambiente (`SPRING_PROFILES_ACTIVE=prod`).
## Aplicação completa no Docker

Com Core e Web em pastas irmãs e infra/local/.env configurado, execute: `docker compose --env-file infra/local/.env -f infra/local/compose.app.yaml up -d --build`. O Compose inclui a infraestrutura existente e inicia Spring e Vite. Guia compartilhado: ../lhamalog_docs/planejamento/ambiente-local.md. Sem chave Clerk, os serviços iniciam, mas login/telas autenticadas ficam indisponíveis e a API permanece protegida.
