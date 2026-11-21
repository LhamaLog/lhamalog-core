# Como contribuir

## Padrão de mensagem de commit (ADR_148)

Toda mensagem de commit começa pela chave da tarefa do Jira (projeto LLT), seguida de dois-pontos, um espaço e uma descrição curta:

```
CHAVE-JIRA: descricao
```

Exemplos:

```bash
git commit -m "LLT-82: inicializacao do modulo: Collector"
git commit -m "LLT-259: ajusta healthcheck"
```

- A chave liga o commit à tarefa do Jira (rastreabilidade commit ↔ tarefa).
- A descrição é curta, em português, em minúsculas e sem acentos, como nos exemplos.
- Referência: ADR_148 — Padrão de mensagem de commit (ver Drive da equipe > ADRs).

## Antes de enviar
- Rode os testes localmente (`make test`). O workflow `.github/workflows/pr-validation.yml` também comenta, no pull request, o resultado de `./mvnw -B -DskipTests compile`, `./mvnw -B test` e `./mvnw -B -DskipTests verify`, sem bloquear o merge. O comando `test` nesse comentário só reexecuta a validação depois que o workflow chega em `main`. Detalhes no README, na seção "Validação de pull requests".
- Antes de adicionar qualquer dependência, ver Drive da equipe > Arquitetura > Dependências específicas dos módulos.
