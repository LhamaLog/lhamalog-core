# Execução e validação — LLT-173

## O que está disponível
Preferências por usuário e empresa em /perfil, formulário Notificações.
Web sempre ligado; e-mail e push opcionais e desligados inicialmente.
Todos os produtos/tipos/severidades por padrão; seleções específicas exigem ao menos um item.
API GET/PUT /api/user/notification-preferences e GET /api/user/notification-preferences/catalog.
O usuário e a empresa vêm da sessão. Produtos vêm de /api/products com autorização existente.

## Catálogo configurável
Por decisão de Vinicius, não existem tipos/severidades predefinidos nesta entrega.
Propriedades Spring (mapas identificador → rótulo):
- lhamalog.notification-preferences.catalog.types
- lhamalog.notification-preferences.catalog.severities

São configuradas no ambiente que executa o Core, pelos mecanismos de configuração Spring já existentes. O Compose atual não repassa automaticamente variáveis extras do host: alterações de catálogo exigem configuração explícita no processo/container, a definir após a equipe aprovar os valores.
Mapas vazios são válidos. Chaves devem ser estáveis, não vazias, sem espaços nas extremidades, até 200 caracteres; rótulos até 200 caracteres. Máximo de 500 opções por mapa.
Não copiar test-event/test-level dos testes para produção: são dados sintéticos, não decisões de negócio.
A UI recebe os rótulos pelo endpoint de catálogo, sem duplicar enumerações no Web.
Se uma opção for retirada, uma seleção antiga é preservada e mostrada como indisponível para remoção; salvar exige corrigir a seleção. A avaliação não permite valor retirado em filtro específico.

## Avaliação e integração
NotificationPreferencesService.allows recebe sessão confiável de membro ativo, canal WEB/EMAIL/PUSH, produto, tipo e severidade.
Canal habilitado E produto acessível/ativo E filtro de produto E filtro de tipo E filtro de severidade.
A sessão deve ser resolvida pelo chamador confiável; nunca construir sessão com dados não autenticados.
Essa função é um ponto de integração, não um transporte nem um motor de seleção de destinatários. A entrega real ainda precisa ser conectada e validada. O publicador obrigatório de saldo baixo foi preservado.
Configurar preferências não comprova recebimento de e-mail/push/web. A LLT-173 continua dependendo do catálogo e da integração para aceite integral.

## Roteiro manual com Clerk real (a executar por Vinicius)
1. Abrir http://localhost:5173/perfil com empresa selecionada.
2. Conferir web marcado e desabilitado; e-mail/push inicialmente desligados.
3. Ligar somente e-mail, salvar, recarregar e conferir persistência; depois verificar push independentemente.
4. Desmarcar Todos os produtos, selecionar um disponível, salvar e recarregar.
5. Sem item selecionado, conferir recusa local; Cancelar alterações restaura o último estado confirmado.
6. Conferir que o catálogo vazio explica a ausência de tipos/severidades específicos.
7. Trocar de empresa e de usuário; preferências devem ser independentes. Trocar durante uma carga/gravação não deve aplicar a resposta na empresa seguinte.
8. Em duas abas, salvar na primeira e depois na segunda com versão antiga: segunda deve pedir recarga e não sobrescrever.
9. Conferir erro de rede e botão de recarregar; não mostrar mensagem de sucesso em falha.
10. Conferir navegação por teclado e layout estreito.
11. Quando houver catálogo aprovado e motor integrado, verificar entrega e supressão reais em cada canal e combinação de filtros.

## Comandos
Core: .\\mvnw.cmd -Dtest=NotificationPreferencesHttpTests test; .\\mvnw.cmd verify.
Web: npm test; npm run build.
Docker, cada repositório separadamente: docker compose up -d --build.
Não apagar volumes. Não alterar Clerk. Não abrir PR automaticamente.
