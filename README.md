# lhamalog-core

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
MongoDB, Redis e Elasticsearch locais pelo docker-compose da equipe (o mesmo do lhamalog-finder: `docker compose up -d` / `make infra-up`). Os testes usam Testcontainers e sobem os containers sozinhos.

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