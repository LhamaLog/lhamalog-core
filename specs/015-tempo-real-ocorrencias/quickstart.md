# Quickstart: Aviso em tempo real de ocorrências novas

## Preparação

Core local com o Redis do compose, o JWT interno configurado (spec 013) e a carga de exemplo; o web da spec 014 aberto em `/ocorrencias`.

## Roteiro

| # | Passo | Esperado |
|---|---|---|
| R1 | Abrir `/ocorrencias` | log `occurrence.stream.authenticated` no Core |
| R2 | Entregar pelo Finder (ou `scripts/InternalJwt.java` + chamada, spec 013) um achado novo de um produto visível | em até 3 s, aviso na tela |
| R3 | Entregar um achado de uma ocorrência FECHADA | aviso de reaberta |
| R4 | Entregar só aumento de contagem de uma ABERTA | nenhum aviso |
| R5 | Membro sem acesso ao produto com a tela aberta | não recebe o aviso de R2 |
| R6 | Deixar a tela aberta 3 minutos | a conexão continua (renovação do token) |
| R7 | Reiniciar o Core com a tela aberta | a tela reconecta e sincroniza (spec 014 do web) |

## Testes automatizados

```powershell
.\mvnw.cmd verify
```
