# LLT-292 — Perguntas de clarify (fechadas)

Sessão 20/11/2026 22:45 (America/Sao_Paulo). O Michel confirmou **todas** as 12 recomendações exatamente como estavam neste arquivo (repassado pelo BimBot). Nenhuma ficou aberta. O registro canônico está em `spec.md` (Clarifications) e nos contratos.

1. Rota pública: `POST /api/manual/upload`, JWT Clerk, 401/403 (ADR_032).
2. Formatos: `.txt`, `.json`, `.csv`, `.log`, lista configurável, revalidada no Core e no Collector.
3. Vários arquivos por requisição multipart, um lote.
4. Teto provisório de 100 MB por arquivo **e** por requisição, só em properties/perfil, nunca em `.env`.
5. Mesma cota, assinatura e rate limit da ingestão OTLP (429 sem saldo, 503 sem plano). Teto de tamanho deste caminho: 100 MB, não 5 MB.
6. Collector: `POST /internal/manual/upload` na porta 8080, só `X-LhamaLog-Service-Token`. Segredo só por variável de ambiente (`LHAMALOG_CORE_COLLECTOR_TOKEN` no Core, `LHAMALOG_COLLECTOR_SERVICE_TOKEN` no Collector).
7. `signal=logs`. MD5 dos bytes do arquivo recebido.
8. Recusa por arquivo; recusados listados no corpo do 202; 409 se todos forem duplicata.
9. Destino: fila de leitura existente, `fileSource=upload`. FilaOrdenada/Finder pós-MVP.
10. Proteção no MVP: extensão, tamanho e magic bytes de binário óbvio. Malware e prompt injection depois.
11. Qualquer usuário autenticado que já possa ver o produto no tenant da sessão. Produto arquivado ou de outro tenant: 403.
12. O Core espera a decisão do Collector e só então responde 202 ou o erro (409/413/429/503).

Pendência herdada (não é pergunta nova): decisão B1 da LLT-290 (claim em andamento hoje recebe 409; T041 ainda aberta).
