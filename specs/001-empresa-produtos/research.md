# Pesquisa e decisões técnicas

- Decisão: JWT via Spring Security já instalado. Motivo: ADR_022 permite verificação JWKS, evita nova dependência. Alternativa descartada: SDK Clerk para autenticar cada requisição.
- Decisão: o.id da sessão v2 identifica a organização ativa. Nunca usar unsafeMetadata ou body para autorizar empresa. Papel Clerk não equivale automaticamente a DONO do domínio.
- Decisão: CORS só para origens configuradas; azp também validado. Um controle não substitui o outro.
- Decisão: sessão pending e JWT sem exp/nbf/sub são inválidos. Validadores Spring cobrem assinatura, issuer e janela temporal.
- Decisão: manter 403 de negócio na tela, pois USID_030.1 exige preservar sessão; não alterar o hook existente usado por outros fluxos.
- Decisão: dados cadastrais serão persistidos pelo Core; criação e seleção de organização reutilizam a Clerk. Limite de Organizations é configuração operacional da Clerk, não limite artificial de plano no Core.
- Fontes oficiais: https://clerk.com/docs/guides/sessions/session-tokens ; https://clerk.com/docs/guides/sessions/manual-jwt-verification ; https://clerk.com/docs/guides/organizations/configure ; https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html . Consultadas em 04/11/2026 pela etapa de pesquisa do speckit-plan.
- Sem infraestrutura de sincronização de Membro/Tenant existente no Core. Usuário confirmou acesso por produto e vínculo automático do criador. Membro é identificado pelo sub dentro da organização validada; AcessoProduto armazena esse vínculo sem inventar gestão de membros.
