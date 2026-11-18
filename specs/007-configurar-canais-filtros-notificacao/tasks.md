# Tarefas — LLT-173

Executar na ordem. Marcas representam trabalho concluído, não aceite manual.
- [x] T001 Criar modelo, catálogo configurável e validações.
- [x] T002 Implementar persistência/versionamento e API autenticada.
- [x] T003 Implementar avaliação de preferências com acesso atual a produtos.
- [x] T004 Testar API, isolamento, concorrência e avaliação.
- [x] T005 Executar mvnw verify e registrar resultados.

Os seis testes da LLT-173 passaram com MongoDB no Docker. O build da imagem do Core passou.

Vinicius executou a suíte completa com mvnw verify em 17/11/2026, concluída às 08:25:07 (America/Sao_Paulo). O Maven registrou 186 testes, 1 falha, 0 erros e 34 pulados, com BUILD FAILURE. A falha conhecida CompanyRegistrationHttpTests.adminDeletesRegistrationAndMemberCannot esperava HTTP 409 e recebeu 200. Os seis testes da LLT-173 passaram nessa execução. O resultado substitui o registro anterior de interrupção no download da imagem de observabilidade.

T005 concluída representa execução e registro do resultado. A suíte completa continua com a falha conhecida de cadastro de empresa.
