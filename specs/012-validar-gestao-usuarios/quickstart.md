# Quickstart: Validar e fechar a Gestão de usuários

Guia para repetir a validação: a preparação, os comandos do Core e do Web e o roteiro E2E. O resultado de cada passo vai para [relatorio-aceite.md](relatorio-aceite.md).

> **Atualização de 25/11/2026: como o roteiro foi rodado.**
> - **Web:** fica na `Development` (`309e417`), que já tem a 167 e a 168. Não é preciso a branch da 168 (passo 0.3).
> - **Core:** fica na `LLT-119-validar-gestao-usuarios`, com a `Development` mesclada, e roda com o banco `lhamalog_llt119_local` no `mongo-teste`, sem `CLERK_SECRET_KEY` e com OTLP desligado. Para ter várias pessoas logadas no mesmo navegador, cada uma usa um endereço: `localhost`, `bruno.localhost` e `carla.localhost`, todos na porta 5173. O `CLERK_AUTHORIZED_PARTIES` lista as quatro origens.
> - **D3:** resolvida com a instância Clerk de dev do time e contas por apelido do Gmail (`+bruno119` e `+carla119`). O link do convite vem para o portal da Clerk (R-15); para aceitar no endereço certo, troque o começo do link pelo `bruno.localhost:5173` ou `carla.localhost:5173`.
> - **Ana é Dono, e não Administrador.** Com a LLT-120, quem cria a empresa é o Dono, e a linha do Dono não tem rebaixar nem desativar.
> - **Último admin:** a regra só vale em empresa **sem Dono**. O E2E-14 (parte "Ana tenta desativar a si mesma") e o E2E-15 rodam numa empresa sem Dono com um único admin, por exemplo a organização de dev (`DEV_TENANT_CLERK_ORG_ID`). Por segurança, intercepte no navegador as chamadas de rebaixar e desativar durante o teste.
> - **`window.confirm` do Desativar:** trava a automação por extensão. Substitua antes por `window.confirm = () => true`, ou por `false` para cancelar.

## Passo 0: preparar

O documento-base foi escrito antes de a LLT-168 entrar na `Development`. O passo 0 atualizado:

1. Docker Desktop aberto, porque os testes do `member` usam Testcontainers.
2. Em `lhamalog-core`: **Fetch origin** no GitHub Desktop e checkout de `LLT-119-validar-gestao-usuarios`. Essa branch já está igual à `Development`, que contém a 167 e a 168. **Não é preciso** mesclar `origin/LLT-168-...`.
3. Em `lhamalog-web`: **Fetch origin** e checkout de `LLT-168-listar-editar-e-desativar-usuarios` **pelo GitHub Desktop**, só para rodar. Não faça commit nela. O agente não roda Git nesse repositório.
4. `lhamalog-web/.env.local` (ignorado pelo Git) com só:

   ```text
   VITE_API_URL=http://localhost:8080
   VITE_CLERK_PUBLISHABLE_KEY=pk_test_...
   ```

   Nunca coloque chave `sk_` nesse arquivo.

## Core: suíte e testes de aceite

PowerShell, na raiz do `lhamalog-core`.

Suíte inteira com Testcontainers (sem banco externo). Os 34 testes de Produto que exigem `TEST_MONGODB_URI` ficam ignorados:

```powershell
Remove-Item Env:TEST_MONGODB_URI -ErrorAction SilentlyContinue
.\mvnw.cmd -B clean verify
```

Suíte inteira incluindo Produto, no container local `mongo-teste` (porta 27019) com um banco **dedicado** a esta validação:

```powershell
docker start mongo-teste
$env:TEST_MONGODB_URI='mongodb://127.0.0.1:27019/lhamalog_llt119'
.\mvnw.cmd -B clean verify
Remove-Item Env:TEST_MONGODB_URI
```

Nunca aponte para `lhamalog_core` nem para `lhamalog_teste`.

Só os testes do `member` (o original e o de aceite):

```powershell
.\mvnw.cmd -B test '-Dtest=CompanyMemberHttpTests,CompanyMemberAcceptanceTests'
```

O que cada teste de aceite prova está no relatório, na seção "Testes de aceite explicados".

> **Atualização de 25/11/2026:** com a `Development` atual, todos os testes rodam no Testcontainers e nada fica ignorado. O `TEST_MONGODB_URI` não é mais necessário.
>
> | Execução (25/11) | Rodados | Falhas | Erros | Ignorados |
> |---|---|---|---|---|
> | `-Dtest=CompanyMemberAcceptanceTests,CompanyMemberHttpTests,CompanyRegistrationHttpTests` | 36 | 0 | 0 | 0 |
> | `.\mvnw.cmd -B clean verify` | 460 | 0 | 0 | 0 |

## Web: testes e build

Na raiz do `lhamalog-web`, **depois** da troca de branch do passo 0.3:

```powershell
npm ci
npm test
npm run build
```

Sem nenhum comando Git. Se `npm ci` não puder rodar (por exemplo, sem rede), registre como Bloqueado.

## Roteiro E2E (Core + Web + Clerk)

**Pré-requisitos** (D3, pendente com o time):

- Instância Clerk de **desenvolvimento** com convites de organização, cargos `admin`/`member` e validade de 7 dias.
- Organizações **Empresa A** e **Empresa B**.
- Contas:
  - **Ana**: admin da Empresa A;
  - **Bruno**: membro da Empresa B, que será convidado para a Empresa A como `member`;
  - **Carla**: será convidada para a Empresa A como `admin`.
- Core local na 8080, na `Development`. Web local na 5173, na branch da 168.
- O JWT só quando o passo pedir, no console do navegador: `await window.Clerk.session.getToken()`. **Não salve o token** em arquivo, log, print ou relatório.

Para chamadas diretas à API (E2E-16 e E2E-17), cole o token só no terminal:

```powershell
$t = Read-Host -AsSecureString 'token'   # cole o token; ele não aparece na tela
$plain = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($t))
curl.exe -s -i -H "Authorization: Bearer $plain" http://localhost:8080/api/company/members
# E2E-17: curl.exe -s -i -X POST -H "Authorization: Bearer $plain" http://localhost:8080/api/company/members/<idDaAna>/deactivation
Remove-Variable t, plain
```

| ID | Passos | Esperado | Prova |
|---|---|---|---|
| E2E-01 | Ana entra na Empresa A e abre `/equipe` | A tela carrega com as seções de convites e de usuários; Ana aparece como Administrador e Ativo | AC-07 |
| E2E-02 | Ana convida Bruno como Membro e Carla como Administrador | Os dois convites aparecem como Pendentes e os e-mails chegam | AC-01 |
| E2E-03 | Ana tenta convidar com e-mail vazio e depois com `abc` | "Informe o e-mail." e "Informe um e-mail válido."; nenhum convite novo na Clerk | AC-02 |
| E2E-04 | Ana recarrega a página | A lista de pendentes continua lá, sem carregamento infinito. Se falhar, é o R-13 confirmado, não bug novo | AC-04, R-13 |
| E2E-05 | Ana convida `teste+revogar@...` e revoga | O convite some dos pendentes; o link do e-mail é recusado pela Clerk | AC-03, AC-04 |
| E2E-06 | Conferir no dashboard da Clerk (ou no `expiresAt`) a validade dos convites | 7 dias a partir do envio | AC-03 |
| E2E-07 | Ana reenvia o convite da Carla | Chega um e-mail novo, o link antigo deixa de valer e o prazo recomeça | AC-04, R-11 |
| E2E-08 | Bruno e Carla aceitam pelas telas de entrada existentes | Bruno entra na A como membro, sem perder a B; Carla entra como admin | AC-05 |
| E2E-09 | Ana abre `/equipe` | Três linhas com nome, e-mail, cargo, status Ativo e último acesso (ou "Sem acesso registrado"); nada da Empresa B. Anote o que aparece como e-mail | AC-07, R-10 |
| E2E-10 | Ana troca Bruno para Administrador, Bruno sai e entra de novo, e Ana volta Bruno para Membro | O cargo muda na Clerk e o e-mail não muda. No DevTools, nenhuma chamada a `/api/company/members/{id}/role` | AC-17 |
| E2E-11 | Ana desativa Bruno; Bruno, com a sessão aberta, navega em `/produtos` | Bruno recebe "Seu acesso a esta empresa foi desativado."; Ana e Carla seguem normais | AC-11 |
| E2E-12 | Bruno troca para a Empresa B | Acesso normal na B | AC-12 |
| E2E-13 | Ana desativa Bruno de novo e depois reativa duas vezes | Status Desativado, depois Ativo; Bruno volta a acessar a A | AC-13 |
| E2E-14 | Ana desativa Carla e depois tenta desativar a si mesma | Carla é desativada; a desativação da Ana é bloqueada no Web com "Não é possível desativar o último administrador." | AC-14 |
| E2E-14b | Com um admin novo convidado que ainda não acessou, conferir se o Web o conta | O Web conta pela Clerk; registrar o comportamento | R-03 |
| E2E-15 | Com só a Ana como admin ativa, ela tenta se rebaixar a Membro | "Não é possível rebaixar o último administrador."; a Clerk não é chamada | AC-15 |
| E2E-16 | Bruno (membro) abre `/equipe` e chama `GET /api/company/members` com o token dele | O Web não oferece convite nem gestão; a API responde 403 `not_admin` | AC-06, AC-08 |
| E2E-17 | Carla reativada e admin. Ana rebaixa a Carla no Web e a Carla **não** navega. Ana chama `POST /api/company/members/{idDaAna}/deactivation` com o próprio token | Se vier 200, o R-02 está confirmado (empresa sem admin ativo de fato). Desfazer com `DELETE` pela Carla depois que ela navegar | R-02 |
| E2E-18 | Procurar `sk_` e `CLERK_SECRET_KEY` nos dois repositórios, no estado testado | Nenhum **valor** versionado | AC-19 |

Para o E2E-18, o agente roda a busca no Core. No Web ela só faz sentido com a branch da 168 aberta, e é feita por leitura de arquivos, sem Git:

```powershell
Get-ChildItem -Recurse -File -Exclude *.jar | Where-Object { $_.FullName -notmatch '\\(node_modules|target|\.git)\\' } | Select-String -Pattern 'sk_(test|live)_[A-Za-z0-9]', 'CLERK_SECRET_KEY\s*=\s*\S'
```

## Resultado esperado da validação

- A matriz AC-01 a AC-20 do relatório com status e prova.
- R-01 a R-14 com status final.
- A lista de bloqueios para fechar a LLT-119.
