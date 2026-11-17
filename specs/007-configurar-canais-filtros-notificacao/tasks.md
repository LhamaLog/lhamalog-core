# Tarefas — LLT-173

Executar na ordem. Marcas representam trabalho concluído, não aceite manual.
- [x] T001 Criar modelo, catálogo configurável e validações.
- [x] T002 Implementar persistência/versionamento e API autenticada.
- [x] T003 Implementar avaliação de preferências com acesso atual a produtos.
- [x] T004 Testar API, isolamento, concorrência e avaliação.
- [ ] T005 Executar mvnw verify e registrar resultados.

Validação: os seis testes da LLT-173 passaram com MongoDB no Docker. O build da imagem do Core passou. A suíte completa ficou sem resultado após repetidos tempos limite no download de grafana/otel-lgtm:0.37.0. Reexecutar mvnw verify quando a imagem estiver disponível.
