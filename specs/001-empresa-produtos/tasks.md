# Tarefas Empresa e Produtos

Spec e plano neste diretório. Esclarecimentos concluídos na spec. Marcar apenas itens implementados e verificados.

## Preparação
- [x] T001 Investigar fontes e registrar em `../../../lhamalog_docs/entrega-academica/empresa-produtos.md`.
- [x] T002 Preparar branches e templates em `specs/001-empresa-produtos/`.

## Segurança independente US0
- [x] T003 [US0] Escrever testes de JWT real em `src/test/java/br/com/lhamalog/core/config/ClerkTokenValidatorTests.java`.
- [x] T004 [US0] Implementar decoder, validator, CORS e configuração em `src/main/java/br/com/lhamalog/core/config/` e `src/main/resources/application.properties`.
- [x] T005 [US0] Validar filtro HTTP e testes de segurança em `src/test/java/br/com/lhamalog/core/config/`.

## Empresa US1
- [x] T006 [US1] Fechar campos/permissões em `spec.md` e contratos em `contracts/`.
- [x] T007 [US1] Implementar consulta/gravação e isolamento em `src/main/java/br/com/lhamalog/core/company/`.
- [x] T008 [US1] Testar persistência e isolamento em `src/test/java/br/com/lhamalog/core/company/`.

## Produtos US2
- [x] T009 [US2] Implementar cadastro, consulta e contexto em `src/main/java/br/com/lhamalog/core/product/`.
- [x] T010 [US2] Testar persistência, ausência de cota e isolamento em `src/test/java/br/com/lhamalog/core/company/CompanyProductIntegrationTests.java`.

## Integração
- [x] T011 Validar integração das páginas Web e atualizar `quickstart.md` com resultados reais.

Dependências: T001/T002 → T003/T004/T005; esclarecimentos registrados → T006 → T007/T008 → T009/T010 → T011. Testes de decoder e desenho visual podem ser preparados independentemente; implementação Empresa precede Produtos.

## Validação externa pendente
- [ ] T012 Executar verify completo com Docker e registrar resultado em `quickstart.md`.
- [ ] T013 Exercitar login/logout e organizações na instância Clerk real e registrar em `quickstart.md`.

## P05 — Inicialização integrada
- [x] T014 Preparar e validar imagem Java 25 e Compose de Core/Web com dependências saudáveis, mantendo volumes existentes.
- [x] T015 Documentar configuração única, comandos de iniciar/parar e configuração Clerk sem segredos versionados.
- [x] T016 Validar subida integrada, saúde e stop/up sem remover volumes. Persistência funcional dos cadastros após reinício permanece no aceite P06.
