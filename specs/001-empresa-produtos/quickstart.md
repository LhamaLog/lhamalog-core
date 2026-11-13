# Execução e validação Empresa e Produtos

Estado em 04/11/2026. Alterações locais em dev/empresa-produtos, sem commit/push/PR.

## Requisitos

Core: Java 25, Maven Wrapper e MongoDB 8.0.34. Infra completa existente inclui Redis e OpenObserve. Web: Node 26.12.1 e npm ci. Use a mesma instância Clerk no backend e frontend, com Organizations habilitadas. Para empresas ilimitadas, configurar o limite de organizações na Clerk. O Core não limita produtos nem implementa cobrança; a unidade comercial informada foi milhão de logs lidos.

## Iniciar Core

Na pasta C:/GitHub/lhamalog-core, preparar a infra conforme README e infra/local/.env.example. O compose existente exige senhas via variáveis/arquivo local ignorado.

```powershell
$env:JAVA_HOME='CAMINHO_DO_JDK_25'
$env:MONGODB_URI='mongodb://localhost:27017/lhamalog_core'
$env:CLERK_ISSUER='https://SUA_INSTANCIA.clerk.accounts.dev'
$env:CLERK_JWKS_URI="$env:CLERK_ISSUER/.well-known/jwks.json"
$env:CLERK_AUTHORIZED_PARTIES='http://localhost:5173'
$env:OTLP_ENABLED='false'
.\mvnw.cmd spring-boot:run
```

Ajuste MongoDB para as credenciais da sua infra. A API usa porta 8080. Sem issuer/JWKS/origens, tokens são rejeitados; não existe fallback Basic. Health/info permanecem públicos, mas o estado de saúde depende também dos serviços configurados.

(Obsoleto desde a integração com a LLT-163, ver o fim deste guia: `CLERK_ADMIN_ROLES` foi removida.) `CLERK_ADMIN_ROLES` definia os slugs confiáveis de administrador/dono da instância, separados por vírgula; padrão `admin`, que corresponde ao papel built-in Clerk. Se a instância usa papel DONO customizado, adicionar seu slug real à variável, sem inferi-lo pelo nome exibido. Isso não cria papéis. Todos os demais membros exigem vínculo AcessoProduto. O criador recebe esse vínculo automaticamente. Gestão de concessão/revogação de acessos de outros membros permanece fora desta feature.

## Iniciar Web

Em C:/GitHub/lhamalog-web:

```powershell
npm ci
Copy-Item .env.example .env.local
# Preencher VITE_CLERK_PUBLISHABLE_KEY com a chave publicável da instância.
# VITE_API_URL=http://localhost:8080
npm run dev
```

Não sobrescrever .env.local existente. Abrir http://localhost:5173. A raiz abre /empresa. /produtos contém cadastro/lista/contexto; /componentes preserva a demonstração antiga. Chave secreta Clerk nunca vai ao frontend. Recompile com a chave publicável real antes de usar npm run preview.

## Roteiro pela interface

1. Sem sessão, abrir /empresa ou /produtos: deve ir ao login. Entrar e conferir o botão Sair; sair deve voltar ao login.
2. Selecionar ou cadastrar empresa pelo componente Clerk. Salvar razão social, documento, endereço e contexto. Recarregar: os valores devem permanecer.
3. Deixar campos de contexto vazios: o aviso permite salvar. Editar o mesmo cadastro em duas abas: a segunda gravação desatualizada retorna conflito; recarregar antes de salvar.
4. Abrir Produtos, cadastrar com nome e contexto opcional. Recarregar e buscar por nome, descrição ou categoria. Editar contexto e recarregar novamente.
5. Clicar duas vezes em Salvar/Cadastrar: há somente um envio em andamento. Simular falha de rede: valores permanecem e erro é informado; consulta permite tentar novamente.
6. Trocar empresa: dados e produtos da anterior desaparecem. Uma resposta antiga não deve preencher a empresa nova.
7. Com outro membro da mesma organização sem vínculo, o produto não aparece. Administrador vê todos da empresa. ID de outra empresa nunca permite consulta ou alteração. Gestão de vínculos de terceiros não tem tela nesta entrega.
8. Conferir navegação e formulário em celular, com teclado e sem rolagem horizontal.

## Comandos de teste

Web: npm test e npm run build, com Node 26.12.1. Sem chave publicável o provider existente falha na inicialização; o build pode remover código inacessível, portanto usar a configuração real para executar o aplicativo.

Core, verificação completa prescrita: .\mvnw.cmd verify. Requer Docker funcionando para os testes originais com Testcontainers.

Os testes novos de persistência aceitam um Mongo de teste separado:

```powershell
$env:TEST_MONGODB_URI='mongodb://127.0.0.1:27019/lhamalog_empresa_produtos_test'
.\mvnw.cmd '-Dtest=ClerkTokenValidatorTests,ApiSecurityTests,CompanySessionTests,CompanyProductIntegrationTests' verify
```

Sem TEST_MONGODB_URI, CompanyProductIntegrationTests é explicitamente omitido. Não usar um banco de produção para testes. Os testes criam organizações aleatórias; não apagam dados existentes.

## Resultados executados

- 13 testes Core aprovados, nenhum omitido no comando específico: 5 decoder JWT real, 2 filtro HTTP/CORS, 2 resolução de organização/papel e 4 testes integrados com MongoDB real.
- Verify com seleção dos testes novos aprovado, incluindo empacotamento. MongoDB portátil 8.0.34, Java 25.0.5, banco local de teste separado.
- 5 testes Web aprovados: Bearer, validação sem retry de mutação, 401 não JSON, cancelamento e token de organização anterior.
- Build Web aprovado no Node 26.12.1. Para verificar o bundle completo, foi usada chave publicável sintética apenas de compilação, sem salvar .env.local. Não é configuração de uma instância Clerk real. Aviso não bloqueante de bundle acima de 500 kB.
- Navegador Edge automatizado, identidade JWT de teste local, backend real e Mongo real: salvar/recarregar empresa, trocar organização, criar/recarregar produto, alterar contexto, bloqueio de duplo envio em Empresa e Produto, recuperação de consulta após falha de rede. Inspeção visual em 1440 px e 390 px, sem transbordamento horizontal.
- A identidade de teste foi isolada fora dos repositórios; não existe bypass de autenticação nos arquivos da aplicação.
- Verify completo executado e não aprovado: 3 erros nos testes originais (SecurityConfigTests e LhamalogCoreApplicationTests), porque Docker não estava disponível. Os testes novos passaram nessa execução; o mapeamento adicional de papéis foi verificado posteriormente no comando específico.
- Login/logout e criação real de organizações na instância do usuário não foram exercitados por falta de configuração/sessão real Clerk. Componentes de autenticação preservados sem alteração; não confundir inspeção/diff com teste funcional real.

## Limitações de escopo

Sem exclusão, ativação/suspensão de produto, telemetria, faturamento, preferências empresariais, upload de logo, exportação ou gestão de membros/acessos. A lista mostra apenas dados cadastrais persistidos, sem métricas simuladas. Cadastro do produto e vínculo do criador usam duas gravações com compensação em erro; uma interrupção abrupta pode deixar vínculo órfão, que não dá acesso a produto inexistente. Contrato e modelo no mesmo diretório.

## P05 — Docker integrado (04/11/2026)

Compose infra/local/compose.app.yaml validado com config --quiet. Imagens Core Java 25 e Web Node 26.12.1 construídas. Core respondeu health UP e /api/company sem token respondeu 401. Frontend respondeu HTTP 200 e seus 5 testes passaram dentro do container. Infraestrutura reutilizada por include, mesmo projeto/volumes. URI interna Mongo adicionada somente ao .env ignorado, derivada das credenciais locais existentes.

Clerk mantido sem chave por decisão explícita do usuário nesta continuação. HTTP 200 do Vite não significa que as páginas autenticadas estejam funcionando: o AuthProvider exige chave. Login e aceite com sessão real permanecem pendentes. Guia: ../../../lhamalog_docs/planejamento/ambiente-local.md.


Stop e up -d --wait executados com sucesso; serviços reiniciados sem remover volumes. A persistência funcional de cadastros via sessão Clerk segue no aceite P06.


Nova tentativa de verify completo com Docker disponível: interrompida durante download de grafana/otel-lgtm:0.37.0, antes da conclusão da suíte. Não registrar como aprovada. T012 permanece pendente. Log local da sessão: work/full-verify-docker.log. Os 4 testes de persistência novos exigem TEST_MONGODB_URI em banco isolado quando repetir o verify.


## Atualização: Clerk local configurada

Em 04/11/2026, o usuário autorizou criar LhamaLog Local e configurar a instância de desenvolvimento no .env ignorado. Tela de login real conferida no localhost:5173. As observações anteriores sobre ausência de chave são históricas; login e operações autenticadas ainda precisam de aceite. Consulte o progresso em lhamalog_docs/planejamento/progresso-empresa-produtos.md.

## Integração com a LLT-163/164 (09/11/2026)

A segurança desta feature passou a ser a da LLT-163 (specs/002-validacao-token-clerk) e os webhooks da LLT-164 (specs/003-webhooks-clerk) entraram junto. O que mudou para Empresa e Produtos:

- O `ClerkTokenValidator` foi removido: a validação do token (RS256, exp/nbf obrigatórios, iss, azp, sts) é feita pelos validadores da LLT-163.
- O `CompanySession` é montado pelo `CurrentRequestContext`. O `tenantId` é o id interno do Tenant (coleção `tenant`), não o `org_id`. O administrador é o papel `admin` da organização; `CLERK_ADMIN_ROLES` foi removida.
- Toda chamada exige um Tenant `ACTIVE` para a organização ativa. Sem ele: 403 e revogação das sessões. No perfil local, `DEV_TENANT_CLERK_ORG_ID` cria esse Tenant ao subir.
- A Empresa é gravada no documento do Tenant, só nos campos data, context, address e version (gravação parcial com versão otimista), sem tocar em name e status.
- Os testes `ApiSecurityTests`, `CompanySessionTests` e `CompanyProductIntegrationTests` usam Testcontainers e tokens assinados de teste (`support.TestJwks`), sem `TEST_MONGODB_URI`. Comando único: `.\mvnw.cmd clean verify` com o Docker ligado.
- No Docker integrado (`compose.app.yaml`), `CLERK_ISSUER` é obrigatória; `CLERK_SECRET_KEY`, `CLERK_WEBHOOK_SIGNING_SECRET` e `DEV_TENANT_CLERK_ORG_ID` são opcionais.
