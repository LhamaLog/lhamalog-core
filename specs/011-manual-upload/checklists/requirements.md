# Qualidade da especificação 011-manual-upload

- [x] Objetivo, fontes e divergência do card Jira (desatualizado) identificados.
- [x] Clarify Q1–Q12 fechadas (20/11/2026 22:45, America/Sao_Paulo) e registradas em `spec.md`.
- [x] Requisitos FR-001–FR-016 e critérios SC conferidos com as decisões de 20/11.
- [x] 403 vs 404 de produto (Q11) e produto em `CONFIGURING` documentados.
- [x] Limites reais do Tomcat (50 partes, 512 B de header, envelope no `max-request-size`) e 413 do contêiner sem `files[]` documentados nos contratos.
- [x] 409 que vira 400/413 com recusa local, arquivo vazio (`INVALID_EXTENSION`/`empty_file`), `Retry-After`, 503/504 como `ApiError` e 504 = resultado desconhecido documentados.
- [x] B1 da LLT-290 herdada (409 em claim em andamento); o Core não inventa 503 local para esse caso.
- [x] MD5 no Collector deve ser calculado em stream a partir do temporário, não em `byte[]` de até 100 MB (contrato interno).
- [x] Implementação do Core em andamento neste worktree; Collector bloqueado pelos commits da LLT-290.
- [x] Caminho dos rascunhos de ADR / planejamento no `lhamalog-docs`: `planejamento/LLT-292-manual-upload/` (sem inventar números de ADR aqui).
