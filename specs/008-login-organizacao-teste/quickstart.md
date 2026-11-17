# Quickstart: login e organização de teste, e roteiro de validação antes do merge

Usa a instância de **desenvolvimento** da Clerk. Nada daqui vai para o repositório: logins, senhas, `org_id`, `sk_test_` e `whsec_` ficam só no seu `.env` (ignorado pelo Git).

## Pré-requisitos

- Docker Desktop aberto; JDK 25 se for rodar fora do Docker.
- `lhamalog-core` na branch do PR #6, com `.env` na raiz (copie de `.env.example`; veja o README, "Docker de desenvolvimento").
- `lhamalog-web` na `Development` com o PR #7 (correção da tela de login depois do segundo fator), com `.env` contendo a `VITE_CLERK_PUBLISHABLE_KEY` da mesma instância.
- No `.env` do Core: `CLERK_ISSUER` da mesma instância e `CLERK_SECRET_KEY` (`sk_test_...`, Dashboard > API keys). Sem a chave secreta, o item V6 não pode ser validado.
- Relógio da máquina certo (diferença de segundos; acima de 5 s o Core responde 401 por `nbf`/`exp`).

## Parte A: preparar o login e as organizações

1. No Web (`http://localhost:5173/sign-up`), crie o seu login. Para contas extras, use e-mails com `+clerk_test` (ex.: `fulano+clerk_test@exemplo.com`) e o código `424242`.
2. Crie a organização A pela etapa "escolher ou criar organização". Para os itens de isolamento, crie também a B.
3. No Dashboard da Clerk > Organizations, copie os ids (`org_...`).
4. No `.env` do Core: `DEV_TENANT_CLERK_ORG_ID=<org A>,<org B>`. Formato em [contracts/dev-tenant-config.md](contracts/dev-tenant-config.md).
5. `docker compose up -d` e confira no log (`docker compose logs api`) uma linha `event=dev_tenant.seed.created` ou `event=dev_tenant.seed.exists` por organização.
6. Para o item V7, convide uma conta `+clerk_test` para a organização A como membro (não admin).
7. Crie também uma organização C e **não** a coloque na lista: ela fica sem Tenant, para o item V6.

## Parte B: roteiro de validação (registrar no PR #6)

| Item | Passo | Esperado |
|---|---|---|
| V1 | Entrar com o seu login e o segundo fator | Depois do código, cai no aplicativo, sem cartão de login vazio |
| V2 | Com a organização A ativa, abrir Empresa, Produtos, Equipe e Perfil | As quatro carregam, sem "Você foi deslogado" |
| V3 | Reiniciar o Core sem mudar o `.env` | Log `dev_tenant.seed.exists` para A e B; nada é recriado |
| V4 | Criar um produto em A, trocar para B e abrir `/produtos` e o endereço do produto | O produto não aparece em B; o endereço responde como inexistente |
| V5 | `curl -i http://localhost:8080/api/company` sem token e com `Authorization: Bearer abc` | 401 `{"error":"unauthorized"}` nas duas |
| V6 | Trocar para a organização C (sem Tenant) e abrir a Empresa | O Core responde 403 e a sessão some da lista de sessões do usuário no Dashboard da Clerk; o Web volta ao login (na mesma ação ou na seguinte, enquanto a spec 006 do web não estiver na `Development`) |
| V7 | Entrar com a conta membro da Parte A, abrir Equipe, tentar salvar o cadastro da Empresa e arquivar um produto | Recusas de negócio com `code` `not_admin` e mensagem; a pessoa continua logada |
| V8 | Preencher `DEV_TENANT_CLERK_ORG_ID=org_exemplo,valor-invalido` e reiniciar | WARN `dev_tenant.seed.invalid_org_id` para o inválido; a API sobe |
| V9 | Webhook pelo túnel: seguir o quickstart da spec 003 (endpoint, `CLERK_WEBHOOK_SIGNING_SECRET`) e mudar o nome ou o papel de um membro de A na Clerk | A mudança aparece na coleção `member` do Mongo local |
| V10 | `git grep` por `org_`, `sk_test`, `whsec_` e pelo seu e-mail nos arquivos alterados do PR | Só os exemplos fictícios aparecem |

## Registro

Comente no PR #6 uma tabela com `Item | Data | Resultado | Observação`. Não cole token, chave, `org_id` real nem e-mail na observação.
