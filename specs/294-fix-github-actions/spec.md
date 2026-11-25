# LLT-294: Separar o comando test em um workflow próprio

## Objetivo

`.github/workflows/pr-validation.yml` escutava `pull_request` e `issue_comment` no mesmo arquivo. Em cada abertura ou push de pull request o job `reexecutar` aparecia como skipped, e em cada comentário o job `validar` aparecia como skipped. Cada gatilho passa a ter o seu workflow, para o run mostrar só o job que se aplica.

## Decisão

- `pr-validation.yml` fica só com `pull_request` (types `opened`, `synchronize`, `reopened`, `edited`, `ready_for_review`; branches `Development` e `main`) e só o job `validar`. Permanecem `runs-on: ubuntu-22.04`, Java Temurin 25 com cache Maven, os três comandos `./mvnw` e o comentário `<!-- llt-285-validacao-prs -->`. Saem o gatilho `issue_comment` e a permissão `issues: write`, usada só pela reexecução.
- `.github/workflows/pr-rerun.yml` ("Reexecutar validação de PRs") fica só com `issue_comment` types `[created]` e o job `reexecutar`, com os mesmos filtros (`OWNER`, `MEMBER`, `COLLABORATOR`, sem bots), as mesmas reações, o mesmo marcador e os mesmos comandos Maven, também em `ubuntu-22.04`.
- O texto do comentário e o script `scripts/pr-validation.mjs` não mudam. Código de produção e testes Java não entram neste diff.

## Arquivos

- `.github/workflows/pr-validation.yml`
- `.github/workflows/pr-rerun.yml`
- `README.md`
- `CONTRIBUTING.md`
- `specs/285-validacao-de-prs/plan.md`
- `specs/285-validacao-de-prs/tasks.md`
- `specs/294-fix-github-actions/spec.md`

## Como testar

- `node --test tests/`
- Conferir a sintaxe dos dois YAML (actionlint, ou um parser YAML se o actionlint não estiver instalado).
- No pull request desta tarefa, o Action "Validação de PRs" deve listar só o job `validar`. O comentário de validação continua com o mesmo formato.
- O comando `test` ou `/test` não é exercitado no GitHub nesta entrega. O gatilho `issue_comment` lê `.github/workflows/pr-rerun.yml` da branch padrão (`main`). Ele só passa a valer depois que esse arquivo chega em `main`.
