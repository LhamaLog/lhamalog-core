# Quickstart: Consultar ocorrências

Roteiro de validação da feature 012. Use o ambiente local do README (MongoDB, Redis e a Clerk de desenvolvimento).

## Preparação

1. Suba a infraestrutura local (`infra/local`) e o Core com o perfil `local` e a carga de exemplo ligada:

   ```powershell
   $env:OCCURRENCE_SAMPLE_DATA = "true"
   .\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
   ```

2. A empresa de teste precisa de pelo menos dois produtos. Se não tiver, cadastre pelo web e reinicie o Core: a carga só cria exemplos para os produtos existentes na subida.
3. No log da subida, procure `event=occurrence.sample.seeded` com a quantidade por produto.
4. Pegue um token de sessão da empresa (como no quickstart da 008) em `$token` e o id de um produto em `$product`.

## Roteiro

| # | Passo | Esperado |
|---|---|---|
| Q1 | `GET /api/occurrences` | 200, 20 itens, `lastSeenAt` decrescente, `totalItems` = 12 × produtos |
| Q2 | `GET /api/occurrences?page=2&size=10` | os 10 itens seguintes da mesma ordem |
| Q3 | `GET /api/occurrences?size=15` | 400 com `fieldErrors.size` |
| Q4 | `GET /api/occurrences?type=ERROR&status=ABERTA&productId=$product` | só ERROR, ABERTA e daquele produto; total coerente |
| Q5 | `GET /api/occurrences?q=TIMEOUT` | itens com "timeout" no título, no resumo ou na assinatura, sem diferenciar maiúsculas |
| Q6 | `GET /api/occurrences?q=(` | 200, sem erro de regex |
| Q7 | `GET /api/occurrences?from=<hoje 00:00>` | só ocorrências que aconteceram hoje |
| Q8 | `GET /api/occurrences?from=<amanhã>&to=<hoje>` | 400 com `fieldErrors.to` |
| Q9 | `GET /api/occurrences?productId=<produto de outra empresa>` | 404 "Produto não encontrado ou sem acesso." |
| Q10 | `GET /api/occurrences/{id}` de um item | 200 com até 5 `samples` (textos), `attributes` ordenados (com `host.name` e `service.name`) e `statusHistory`; numa ocorrência de exemplo sem título, `title` = mensagem da assinatura |
| Q11 | `GET /api/occurrences/{id}` com o token de outra empresa | 404 "Ocorrência não encontrada ou sem acesso." |
| Q12 | Membro sem acesso a produtos: `GET /api/occurrences` | 200, `items` vazio, `totalItems` 0 |
| Q13 | Reiniciar o Core com a carga ligada e repetir Q1 | mesmo `totalItems` (sem duplicar) |
| Q14 | No `mongosh`, apagar o `status` de uma ocorrência; repetir Q1 e abrir o detalhe dela | some da lista e do total; detalhe 404; WARN `event=occurrence.read.invalid` no log |

## Testes automatizados

```powershell
$env:TEST_MONGODB_URI = "<uri do Mongo de teste>"
.\mvnw.cmd verify
```

A classe `OccurrenceQueryIntegrationTests` cobre Q1 a Q14 com documentos gravados pelo próprio teste.
