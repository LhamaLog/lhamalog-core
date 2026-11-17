# Plano técnico — LLT-173

## Contexto e decisões
Java 25/Spring Boot/MongoDB no Core; React/TypeScript no Web. Reutilizar sessão, autorização de produtos, tratamento HTTP e componentes existentes. Sem dependências novas.
Regras em spec.md. Catálogo vazio é configuração válida, sem valores inventados.

## Contrato
GET /api/user/notification-preferences retorna {web:true,email:false,push:false,products:{all:true,ids:[]},types:{all:true,ids:[]},severities:{all:true,ids:[]},version:null}.
PUT no mesmo caminho recebe esses campos e retorna estado persistido com versão.
GET /api/user/notification-preferences/catalog retorna {types:[{id,label}],severities:[{id,label}]}.
Produtos vêm de GET /api/products, com as permissões existentes.
400: payload/seleção inválida, web falso ou opção fora do catálogo. 401/403: segurança existente. 409: versão concorrente. Falha de infraestrutura não gera sucesso.
Limites: até 500 ids por filtro, ids de até 200 caracteres. Modo todos exige ids vazio; modo específico exige ao menos um item único. Corpo com identidade é recusado ou ignorado, nunca usado para autorização.

## Core
Pacote notificationpreferences: documento com id composto sem colisões (codificação de tenant e usuário), repository, catálogo via @ConfigurationProperties, serviço, controlador e avaliação.
@Version para concorrência; primeira gravação usa insert e converte chave duplicada em conflito.
Produtos específicos validados contra a lista ativa autorizada. Leitura não apaga seleções antigas; UI permite remover as indisponíveis.
Catálogo: lhamalog.notification-preferences.catalog.types e .severities como mapas código→rótulo; vazios por padrão.
Serviço de avaliação recebe CompanySession resolvida em ambiente confiável, canal, produto, tipo, severidade. Revalida produto/acesso, cruza filtros, retorna boolean. Não é endpoint público, não é transporte, nem está ligado automaticamente a publicador obrigatório de saldo baixo.
Sem novos logs com informações pessoais.

## Web
Novo componente NotificationPreferences na tela /perfil, como formulário irmão do perfil, nunca formulário aninhado.
Carga de preferências+catálogo+produtos; validação estrita das respostas. AbortController e desmontagem por usuário/empresa para descartar requisições antigas.
Web marcado/desabilitado com justificativa. E-mail/push editáveis. Seleção Todos e lista específica nos três filtros. Catálogo vazio explica limitação, sem prometer envio.
Salvar só confirma após resposta válida; cancelar restaura baseline. 409 oferece recarregar.
Listas específicas não disponíveis preservadas como itens removíveis, nunca descartadas silenciosamente.

## Verificação
Testes de API com Mongo Testcontainers: defaults, persistência, isolamento, web obrigatório, seleções inválidas, catálogo, concorrência.
Teste de avaliação: canal/filtros/acesso atual e arquivamento.
Web: parser, validações e payload. npm test e build com Node da imagem do projeto; conferir visual quando ambiente permitir.
Executar mvnw verify completo e registrar falhas preexistentes separadamente, sem presumir que qualquer falha seja antiga.
Teste real Clerk e entrega real não realizados por testes automatizados.

## Sequência
Core modelo/regras → API/persistência/avaliação → testes → Web contrato → UI → verificações → relatório.
Sem PR ou push. Mensagem de commit autorizada: LLT-173 Configurar canais e filtros de notificação.
