# Quickstart: Gravar ocorrências a partir do resumo de análise do Finder

Roteiro de validação da feature 013. Use o ambiente local do README (MongoDB e Redis do compose).

## Preparação

1. Gere um segredo de pelo menos 32 bytes e coloque no `.env` local (nunca no Git):

   ```powershell
   $bytes = New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Fill($bytes); [Convert]::ToBase64String($bytes)
   # .env: OCCURRENCE_INTERNAL_JWT_SECRETS=<valor gerado>
   ```

2. Suba o Core com o perfil `local` (ou `docker compose up -d --build`, que repassa `OCCURRENCE_INTERNAL_JWT_*`). No log, procure `event=occurrence.auth.configured` com a quantidade de segredos (nunca o valor).
3. Gere o token do Finder:

   ```powershell
   $env:OCCURRENCE_INTERNAL_JWT_SECRET = "<o mesmo segredo>"
   $token = java scripts/InternalJwt.java --sub lhamalog-finder --days 90
   ```

4. Pegue um `tenantId` e um `productId` do tenant de desenvolvimento (por exemplo, pelo `mongosh`, coleção `Produto`).

## Roteiro

| # | Passo | Esperado |
|---|---|---|
| M1 | `POST /internal/v1/ocorrencias/consultas` sem `Authorization` | 401 `NAO_AUTENTICADO` com `WWW-Authenticate: Bearer` |
| M2 | Mesma chamada com o token de sessão da Clerk | 401 |
| M3 | Token gerado com `--sub outro-servico` | 403 `ACESSO_NEGADO` |
| M4 | Consulta com `productId` de outro tenant | 403 `ACESSO_NEGADO` |
| M5 | Consulta de uma chave nova | 200 `{"estado": null}` |
| M6 | `POST /internal/v1/resumos-analise` com dois achados novos (`revisaoConsultada: null`) | 201 com recibo `repeticao: false`; as duas ocorrências aparecem em `/ocorrencias` na tela, ABERTA |
| M7 | Repetir M6 com o mesmo corpo | 200 com o mesmo `reciboId` e `repeticao: true`; nada muda na coleção |
| M8 | Consulta de um achado de M6 | 200 com `contagemAcumulada`, datas, `ABERTA` e `revisao` |
| M9 | Entrega de outro lote com a revisão de M8 e `contagem` somada | 201; contagem nova na tela; revisão mudou |
| M10 | Entrega de um terceiro lote com a revisão antiga de M8 | 409 `REVISAO_DESATUALIZADA`; nenhuma ocorrência mudou |
| M11 | No `mongosh`, marcar uma ocorrência como `FECHADA`; consultar e entregar um achado dela | 201; a ocorrência volta a `ABERTA` e o histórico mostra `FECHADA → ABERTA` por Sistema na tela |
| M12 | Corpo com `tipo: "FATAL"` ou campo a mais | 422 `DADOS_INVALIDOS`; nada gravado |
| M13 | No log, procurar a assinatura, um trecho de `linhas` e o token | nenhum dos três aparece |

## Homologação com o Finder (US5)

Com o João, o Finder da spec 009 apontado para o Core local (`FINDER_MODULO_OCORRENCIA_URL` com `https://`; no local, um proxy TLS na frente da porta 8080) e `FINDER_MODULO_OCORRENCIA_JWT` com o token do passo 3. Os cinco cenários do contrato DN-009-2:

1. A consulta distingue ocorrência ausente de falha e recusa outro tenant ou produto.
2. A primeira entrega grava uma vez e responde 201; o reenvio depois de perder a resposta, inclusive com o Finder reiniciado, responde 200 com o mesmo `reciboId`, sem duplicar.
3. Dois lotes que consultaram a mesma revisão preservam todos os incrementos; uma revisão desatualizada recusa a entrega inteira antes de qualquer gravação.
4. Confirmação inválida ou inesperada mantém o lote pendente no Finder; um recibo válido vem antes da conclusão e do `XACK`.
5. O JSON mantém as três classes, leva só trechos protegidos e conserva `linhas: []`; a API recusa tipos e contadores inválidos.

## Testes automatizados

Com o Docker ligado (Testcontainers):

```powershell
.\mvnw.cmd verify
```
