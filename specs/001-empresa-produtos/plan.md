# Plano Empresa e Produtos

**Branch**: dev/empresa-produtos | **Data**: 04/11/2026 | [Spec](spec.md)

## Resumo

Primeiro validar JWT Clerk no Core, depois implementar Empresa, depois Produtos. Esclarecimentos concluídos em spec.md. Usar campos do modelo e vínculo por produto.

## Continuação P05

Reutilizar `infra/local/compose.yaml` por inclusão em `compose.app.yaml`, com o mesmo nome de projeto e volumes. Acrescentar imagem Java 25 em múltiplos estágios e frontend pelo Dockerfile existente no repositório irmão. Mongo/Redis devem estar saudáveis antes do Core, e Core antes do Web. Por orientação posterior do usuário, Clerk fica sem chave nesta etapa: permitir iniciar serviços, mantendo API fechada e aceite autenticado pendente; nenhuma autenticação alternativa. Segredos somente no `.env` ignorado. Validar configuração sem imprimir segredos, build das imagens, saúde e reinício quando Docker e a configuração real estiverem disponíveis. Registrar pendências explicitamente.

## Contexto técnico

Java 25, Spring Boot 4.1.1, Maven Wrapper, Spring Security resource server já instalado, MongoDB. Testes JUnit/MockMvc e Testcontainers com imagens fixas. Não adicionar SDK Clerk: usar JWKS e o decoder Spring existente. API REST com DTOs explícitos; nunca aceitar documento Mongo direto do cliente. Índices tenantId e isolamento obrigatório nas consultas. Sem meta numérica de performance não documentada.

## Constituição

Core 2.0.0: spec/plan/tasks antes de código, inglês no código, português na documentação, testes locais e sem segredos. Nome dev/empresa-produtos é instrução explícita do usuário e prevalece sobre feature/LLT. Frontend tem constituição ainda não preenchida; não inventar princípios. Não reinstalar Spec Kit. Não realizar commit/push/PR.

## Estrutura

- `src/main/java/br/com/lhamalog/core/config/SecurityConfig.java`: resource server sem Basic e CORS restrito.
- `src/main/java/br/com/lhamalog/core/config/ClerkTokenValidator.java`: claims obrigatórias.
- `src/test/java/br/com/lhamalog/core/config/ClerkTokenValidatorTests.java`: tokens assinados e claims.
- `src/main/resources/application.properties`: issuer, JWKS, origens por ambiente.
- Módulos `company`, `product` e `shared` para controller/service/repository e erros, para o domínio confirmado.
- Web: features/company e products para páginas, serviços/tipos; shared para requisição autenticada; app/routes e layouts para navegação.

## Sequência e validação

US0: testes de decoder real e filtro HTTP; manter health/info públicos. Configuração ausente deve rejeitar tokens sem habilitar fallback inseguro.
US1: contrato, DTOs, persistência Mongo, formulário e troca de organização; testar recarga e acesso cruzado.
US2: cadastro/lista/contexto com vínculo à empresa; testar ID cruzado e envios duplicados.
Por fim: build web, verify Core e verificação visual. Credenciais reais Clerk são pré-requisito para teste de interface autenticada contra a instância do usuário.
